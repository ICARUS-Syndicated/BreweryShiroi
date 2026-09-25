/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024 The Brewery Team
 *
 * This file is part of BreweryX.
 *
 * BreweryX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with BreweryX. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.commands.subcommands;

import com.dre.brewery.brew.Brew;
import com.dre.brewery.brew.BreweryIngredients;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.instruments.BreweryHeatSource;
import com.dre.brewery.instruments.barrel.BarrelWoodType;
import com.dre.brewery.recipe.BreweryCauldronRecipe;
import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.recipe.items.RecipeItem;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.OptionalFloat;
import com.dre.brewery.utility.utils.BreweryUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * Simulates the brewing process without needing a cauldron, barrel or brewing stand.
 * <p>
 * The options keep the syntax of the old hand written parser, so
 * {@code /breweryx simulate -c 7 -d 4 -a oak 1 <ingredients...>} keeps working. The arguments are therefore
 * parsed here instead of by the command framework, which would only accept flags after the ingredients.
 * <ul>
 *     <li>{@code -r/--recipe <recipe>}</li>
 *     <li>{@code -c/--cook <minutes>}</li>
 *     <li>{@code -d/--distill <runs>}</li>
 *     <li>{@code -a/--age <barrel type> <years>}</li>
 *     <li>{@code -h/--heat <heat source>} (a single block, or {@code all} to not check)</li>
 *     <li>{@code -b/--brewer <player>}</li>
 *     <li>{@code -p/--player <player>}</li>
 * </ul>
 */
public class SimulateCommand {

    private static final String OPTION_RECIPE = "r";
    private static final String OPTION_COOK = "c";
    private static final String OPTION_DISTILL = "d";
    private static final String OPTION_AGE = "a";
    private static final String OPTION_HEAT = "h";
    private static final String OPTION_BREWER = "b";
    private static final String OPTION_PLAYER = "p";

    private SimulateCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("simulate", "brewery.cmd.create", "Help_Simulate")
            .optional("options", StringParser.greedyStringParser(),
                SuggestionProvider.blockingStrings((context, input) -> suggestions(input.input())))
            .handler(context -> simulate(commands,
                context.sender().source(),
                context.<String>optional("options").orElse(""))));
    }

    private static void simulate(BreweryCommandManager commands, CommandSender sender, String rawArguments) {
        Lang lang = commands.lang();
        Arguments arguments = new Arguments(rawArguments);
        if (arguments.failure != null) {
            arguments.failure.send(lang, sender);
            return;
        }

        BreweryRecipe recipe = null;
        if (arguments.recipe != null) {
            recipe = BreweryRecipe.getMatching(arguments.recipe);
            if (recipe == null) {
                lang.sendEntry(sender, "Error_NoBrewName", arguments.recipe);
                return;
            }
        }

        List<RecipeItem> ingredients = new ArrayList<>();
        for (String raw : arguments.ingredients) {
            BreweryRecipe.IngredientResult result = BreweryRecipe.loadIngredientVerbose(raw);
            if (result instanceof BreweryRecipe.IngredientResult.Error error) {
                lang.sendEntry(sender, error.error().getTranslationKey(), error.invalidPart());
                return;
            }
            ingredients.add(((BreweryRecipe.IngredientResult.Success) result).ingredient());
        }

        int cookedTime;
        if (arguments.cook != null) {
            cookedTime = arguments.cook;
        } else if (recipe != null) {
            cookedTime = recipe.getCookingTime();
        } else {
            lang.sendEntry(sender, "CMD_Missing_Cook_Time");
            return;
        }

        OptionalInt distill;
        if (arguments.distill != null) {
            distill = OptionalInt.of(arguments.distill);
        } else if (recipe != null && recipe.needsDistilling()) {
            distill = OptionalInt.of(recipe.getDistillRuns());
        } else {
            distill = OptionalInt.empty();
        }

        Age age;
        if (arguments.age != null) {
            age = arguments.age;
        } else if (recipe != null) {
            age = Age.of(recipe);
        } else {
            age = null;
        }
        if (ingredients.isEmpty()) {
            if (recipe == null) {
                lang.sendEntry(sender, "CMD_Missing_Ingredients");
                return;
            }
            ingredients.addAll(recipe.getIngredients());
        }

        Player brewer = null;
        if (arguments.brewer != null) {
            brewer = resolvePlayer(lang, sender, arguments.brewer);
            if (brewer == null) {
                return;
            }
        }

        Player player = null;
        if (arguments.player != null) {
            player = resolvePlayer(lang, sender, arguments.player);
            if (player == null) {
                return;
            }
        }

        run(lang, sender, cookedTime, distill, age, arguments.heatSource, ingredients, brewer, player);
    }

    @Nullable
    private static Player resolvePlayer(Lang lang, CommandSender sender, String name) {
        Player player = BreweryUtil.getPlayerfromString(name);
        if (player == null) {
            lang.sendEntry(sender, "Error_NoPlayer", name);
        }
        return player;
    }

    private static void run(Lang lang, CommandSender sender, int cookedTime, OptionalInt distillRuns,
                            @Nullable Age age, @Nullable BreweryHeatSource heatSource, List<RecipeItem> ingredients,
                            @Nullable Player brewer, @Nullable Player player) {
        BreweryIngredients ingredientsHolder = new BreweryIngredients();
        for (RecipeItem item : ingredients) {
            for (int i = 0; i < item.getAmount(); i++) {
                ingredientsHolder.addGeneric(item);
            }
        }
        // Without this the recipe lookup has no heat source to judge, which is what a barrel also sees
        ingredientsHolder.setHeatSource(heatSource);

        ItemStack item = ingredientsHolder.cook(cookedTime, brewer);
        Brew brew = new Brew(ingredientsHolder);

        if (distillRuns.isPresent()) {
            if (!(item.getItemMeta() instanceof PotionMeta itemMeta)) {
                lang.sendEntry(sender, "CMD_Cannot_Distill");
                return;
            }

            int runs = distillRuns.getAsInt();
            for (int i = 0; i < runs; i++) {
                brew.distillSlot(item, itemMeta);
            }
            Logging.debugLog(String.format("simulate: distilled for %d runs: %s",
                runs, ChatColor.stripColor(brew.toString())));

            if (!brew.hasRecipe()) {
                lang.sendEntry(sender, "CMD_Distill_Ruined");
                giveBrew(lang, sender, item, player);
                return;
            }
        }

        if (age != null) {
            brew.age(item, age.ageTime(), age.barrelType());
            if (!brew.hasRecipe()) {
                lang.sendEntry(sender, "CMD_Age_Ruined");
            }
        }

        giveBrew(lang, sender, item, player);
    }

    private static void giveBrew(Lang lang, CommandSender sender, ItemStack item, @Nullable Player player) {
        if (player != null) {
            player.getInventory().addItem(item);
        } else if (sender instanceof Player self) {
            self.getInventory().addItem(item);
        } else {
            Brew fromItem = Brew.get(item);
            if (fromItem == null) {
                // this message should never appear since simulation was successful
                sender.sendMessage(ChatColor.RED + "Could not get brew from item");
                return;
            }
            sender.sendMessage(fromItem.toString());
        }
        lang.sendEntry(sender, "CMD_Simulated");
    }

    /**
     * @return The option names and recipe names a sender may type next
     */
    private static List<String> suggestions(String input) {
        List<String> options = new ArrayList<>(List.of(
            "-r", "--recipe", "-c", "--cook", "-d", "--distill",
            "-a", "--age", "-h", "--heat", "-b", "--brewer", "-p", "--player"));
        options.addAll(recipeNames());
        options.addAll(ingredientNames());

        String current = input.contains(" ") ? input.substring(input.lastIndexOf(' ') + 1) : "";
        if (current.isEmpty()) {
            return options;
        }
        String lower = current.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private static List<String> recipeNames() {
        return Stream.concat(
                BreweryCauldronRecipe.getAllRecipes().stream().map(BreweryCauldronRecipe::getName),
                BreweryRecipe.getAllRecipes().stream()
                    .mapMulti((recipe, consumer) -> {
                        consumer.accept(recipe.getRecipeName());
                        consumer.accept(recipe.getId());
                    }))
            .sorted()
            .distinct()
            .toList();
    }

    private static List<String> ingredientNames() {
        return Stream.concat(
                BreweryCauldronRecipe.getAllRecipes().stream().map(BreweryCauldronRecipe::getIngredients),
                BreweryRecipe.getAllRecipes().stream().map(BreweryRecipe::getIngredients))
            .flatMap(List::stream)
            .map(RecipeItem::toConfigStringNoAmount)
            .sorted()
            .distinct()
            .toList();
    }

    private record Age(BarrelWoodType barrelType, float ageTime) {
        public static @Nullable Age of(BreweryRecipe recipe) {
            if (recipe.needsToAge()) {
                BarrelWoodType barrelType = recipe.getWood();
                return new Age(barrelType.isSpecific() ? barrelType : BarrelWoodType.OAK, recipe.getAge());
            }
            return null;
        }
    }

    /**
     * The options and ingredients of a simulate call, in the syntax older BreweryShiroi versions used.
     */
    private static final class Arguments {

        @Nullable
        private String recipe;
        @Nullable
        private Integer cook;
        @Nullable
        private Integer distill;
        @Nullable
        private Age age;
        @Nullable
        private BreweryHeatSource heatSource;
        @Nullable
        private String brewer;
        @Nullable
        private String player;
        private final List<String> ingredients = new ArrayList<>();
        /**
         * Set when an option could not be parsed, so the user is told what was wrong instead of the
         * option silently counting as absent. The first failure wins.
         */
        @Nullable
        private ParseFailure failure;

        private Arguments(String raw) {
            String[] parts = raw.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isBlank()) {
                    continue;
                }

                String option = normalizeOption(part);
                if (option == null) {
                    // The user probably meant "ingredient/#" instead of "ingredient #"
                    if (BreweryUtil.isInt(part) && !ingredients.isEmpty()) {
                        ingredients.set(ingredients.size() - 1, ingredients.get(ingredients.size() - 1) + "/" + part);
                    } else {
                        ingredients.add(part);
                    }
                    continue;
                }

                switch (option) {
                    case OPTION_RECIPE -> {
                        String value = next(parts, ++i);
                        if (value == null) {
                            fail("--recipe");
                            return;
                        }
                        recipe = value;
                    }
                    case OPTION_COOK -> {
                        Integer value = parseNumber(next(parts, ++i), "--cook");
                        if (value == null) {
                            return;
                        }
                        cook = value;
                    }
                    case OPTION_DISTILL -> {
                        Integer value = parseNumber(next(parts, ++i), "--distill");
                        if (value == null) {
                            return;
                        }
                        distill = value;
                    }
                    case OPTION_AGE -> {
                        String wood = next(parts, ++i);
                        String time = next(parts, ++i);
                        Age value = parseAge(wood, time);
                        if (value == null) {
                            return;
                        }
                        age = value;
                    }
                    case OPTION_HEAT -> {
                        if (!parseHeat(next(parts, ++i))) {
                            return;
                        }
                    }
                    case OPTION_BREWER -> {
                        String value = next(parts, ++i);
                        if (value == null) {
                            fail("--brewer");
                            return;
                        }
                        brewer = value;
                    }
                    case OPTION_PLAYER -> {
                        String value = next(parts, ++i);
                        if (value == null) {
                            fail("--player");
                            return;
                        }
                        player = value;
                    }
                    default -> ingredients.add(part);
                }
            }
        }

        /**
         * @return The option letter, or null when the token is no option at all
         */
        @Nullable
        private static String normalizeOption(String token) {
            if (!token.startsWith("-")) {
                return null;
            }
            String name = token.startsWith("--") ? token.substring(2) : token.substring(1);
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "r", "recipe" -> OPTION_RECIPE;
                case "c", "cook" -> OPTION_COOK;
                case "d", "distill" -> OPTION_DISTILL;
                case "a", "age" -> OPTION_AGE;
                case "h", "heat" -> OPTION_HEAT;
                case "b", "brewer" -> OPTION_BREWER;
                case "p", "player" -> OPTION_PLAYER;
                default -> null;
            };
        }

        @Nullable
        private static String next(String[] parts, int index) {
            return index < parts.length ? parts[index] : null;
        }

        /**
         * Reads an integer option value, registering a failure when the value is absent or not a number.
         */
        @Nullable
        private Integer parseNumber(@Nullable String value, String optionName) {
            if (value == null) {
                fail(optionName);
                return null;
            }
            OptionalInt parsed = BreweryUtil.parseInt(value);
            if (parsed.isEmpty()) {
                fail(value);
                return null;
            }
            return parsed.getAsInt();
        }

        @Nullable
        private Age parseAge(@Nullable String wood, @Nullable String time) {
            BarrelWoodType woodType = wood == null ? null : BarrelWoodType.fromName(wood);
            if (woodType == null || !woodType.isSpecific()) {
                failBarrelType();
                return null;
            }
            if (time == null) {
                fail("--age");
                return null;
            }
            OptionalFloat parsedTime = BreweryUtil.parseFloat(time);
            if (parsedTime.isEmpty() || parsedTime.getAsFloat() <= 0) {
                fail(time);
                return null;
            }
            return new Age(woodType, parsedTime.getAsFloat());
        }

        /**
         * Reads the heat source a simulated brew was cooked over.
         * <p>
         * Only a single block makes sense here, a cauldron sits on one of them, so the groups a recipe may
         * name are deliberately not accepted. {@code all} is the exception: it means no check at all, which is
         * what leaving the option out already does.
         *
         * @return Whether the option was usable
         */
        private boolean parseHeat(@Nullable String value) {
            if (value == null) {
                fail("--heat");
                return false;
            }
            if (value.equalsIgnoreCase(BreweryHeatSource.HeatSourceRequirement.ALL.name())) {
                heatSource = null;
                return true;
            }
            BreweryHeatSource source = BreweryHeatSource.fromName(value);
            if (source == null) {
                failure = new ParseFailure("Error_InvalidHeatSource", value);
                return false;
            }
            heatSource = source;
            return true;
        }

        /**
         * Records that the given input was not usable, either as an option value or because the option had none.
         */
        private void fail(String input) {
            if (failure == null) {
                failure = new ParseFailure("Error_InvalidAmount", input);
            }
        }

        /**
         * Records that the age option named something that is not a specific barrel wood type.
         */
        private void failBarrelType() {
            if (failure == null) {
                failure = new ParseFailure("Error_MissingBarrelType", null);
            }
        }

        /**
         * A reason why the arguments could not be parsed, as a language key plus its argument.
         */
        private record ParseFailure(String translationKey, @Nullable String argument) {

            private void send(Lang lang, CommandSender sender) {
                if (argument == null) {
                    lang.sendEntry(sender, translationKey);
                } else {
                    lang.sendEntry(sender, translationKey, argument);
                }
            }
        }
    }
}
