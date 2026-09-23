/*
 * BreweryShiroi - Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2026 Ph0sphorW
 *
 * This file is part of BreweryShiroi.
 *
 * BreweryShiroi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryShiroi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryShiroi. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.commands.subcommands;

import com.dre.brewery.brew.BreweryIngredients;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.instruments.barrel.BarrelWoodType;
import com.dre.brewery.recipe.BestRecipeResult;
import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.recipe.items.Ingredient;
import com.dre.brewery.recipe.items.RecipeItem;
import com.dre.brewery.recipe.items.SimpleItem;
import com.dre.brewery.utility.Logging;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Measures and verifies the recipe lookup.
 * <p>
 * {@code /brewery bench timings [recipes] [iterations]} reports how long one
 * {@link BreweryIngredients#getBestRecipeFull} call takes, optionally with the recipe list artificially
 * inflated to the given size. The interesting figure is the projection onto a full large barrel, because
 * opening one re-evaluates every brew it contains in a single interaction.
 * <p>
 * {@code /brewery bench verify} builds a matching brew for every loaded recipe and prints an aggregated
 * checksum of all resulting evaluations. Two builds can be compared by running it before and after a
 * change to the recipe matching code.
 */
public class BenchCommand {

    /**
     * A full large barrel. Opening one re-evaluates all of its brews in a single interaction, which is the
     * worst case a player can trigger on purpose.
     */
    private static final int LARGE_BARREL_SIZE = 54;

    /** A material no recipe is expected to use, so a brew made of it matches nothing. */
    private static final Material NON_MATCHING_MATERIAL = Material.NETHERITE_BLOCK;

    /**
     * Accumulates something derived from every benchmark result, so the JIT cannot discard the measured
     * calls as dead code.
     */
    private static long blackhole;

    private BenchCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("bench", "brewery.cmd.bench", "Help_Bench")
            .optional("mode", StringParser.stringParser(),
                SuggestionProvider.blockingStrings((context, input) -> List.of("timings", "verify")))
            .optional("recipes", IntegerParser.integerParser(1, 100_000))
            .optional("iterations", IntegerParser.integerParser(1, 10_000_000))
            .handler(context -> run(commands,
                context.sender().source(),
                context.<String>optional("mode").orElse("timings").toLowerCase(Locale.ROOT),
                context.<Integer>optional("recipes").orElse(-1),
                context.<Integer>optional("iterations").orElse(-1))));
    }

    private static void run(BreweryCommandManager commands, CommandSender sender, String mode,
                            int targetRecipeCount, int iterations) {
        List<BreweryRecipe> realRecipes = new ArrayList<>(BreweryRecipe.getAllRecipes());
        if (realRecipes.isEmpty()) {
            Logging.message(sender, "No recipes are loaded, nothing to benchmark");
            return;
        }

        List<BreweryRecipe> syntheticRecipes = List.of();
        try {
            if (targetRecipeCount > 0 && targetRecipeCount != realRecipes.size()) {
                syntheticRecipes = addSyntheticRecipes(targetRecipeCount - realRecipes.size());
            }
            Logging.message(sender, "Recipes: " + BreweryRecipe.getAllRecipes().size()
                + " (" + realRecipes.size() + " loaded, " + syntheticRecipes.size() + " synthetic)");

            if (mode.equals("verify")) {
                verify(sender, realRecipes);
            } else {
                timings(sender, realRecipes, iterations > 0 ? iterations : 2000);
            }
        } finally {
            BreweryRecipe.getAllRecipes().removeAll(syntheticRecipes);
        }
    }

    private static void timings(CommandSender sender, List<BreweryRecipe> realRecipes, int iterations) {
        // Debug logging is suppressed for the measurements: with it on, one lookup emits one line per
        // recipe, which would drown out the work being measured.
        Logging.withoutDebugLogging(() -> {
            BreweryIngredients noMatch = brewOf(List.of(new SimpleItem(NON_MATCHING_MATERIAL, (short) 8)), 3);
            // A brew that matches nothing has to be scored against every recipe, so this is the upper bound.
            // Ageing is measured separately because it adds the wood and age dimensions to each score.
            report(sender, "no match, not aged", measure(noMatch, BarrelWoodType.OAK, 5, false, iterations), iterations);
            report(sender, "no match, aged    ", measure(noMatch, BarrelWoodType.OAK, 5, true, iterations), iterations);

            // A brew that matches stops the scan at the recipe it matches, so this is the lower bound.
            // It only reflects the best case: the synthetic recipes are appended after the loaded ones, so a
            // brew matching a loaded recipe always stops within the first few, whatever the total count is.
            if (realRecipes.isEmpty() || matchingBrewOf(realRecipes.getFirst()) == null) {
                Logging.message(sender, "Some recipe has no ingredients, skipping the matching case");
                return;
            }
            report(sender, "matches 1st recipe", measureAgainst(realRecipes.getFirst(), iterations), iterations);
        });
    }

    /**
     * Times looking up a brew built to match the given recipe exactly.
     */
    private static long measureAgainst(BreweryRecipe recipe, int iterations) {
        return measure(matchingBrewOf(recipe), recipe.getWood(), recipe.getAge(),
            recipe.needsDistilling(), iterations);
    }

    /**
     * Builds a brew that exactly matches every loaded recipe in turn and hashes the outcome, so a change to
     * the matching code can be caught by comparing the checksum of two builds.
     * <p>
     * Both the distilled and the non-distilled verdict are hashed, which is what the brewing code queries.
     */
    private static void verify(CommandSender sender, List<BreweryRecipe> realRecipes) {
        Verification verification = new Verification();
        // Suppressed to keep this fast, the outcome does not depend on whether messages get logged
        Logging.withoutDebugLogging(() -> verification.run(realRecipes));

        Logging.message(sender, "verify: checksum=" + verification.checksum
            + " found=" + verification.found + " errors=" + verification.errors
            + " skipped=" + verification.skipped);
        Logging.message(sender, "verify: the brew built from a recipe was resolved back to that recipe "
            + verification.intendedRecipeWon + "/" + (verification.found + verification.errors) + " times");
    }

    /**
     * Accumulates the outcome of {@link #verify}, in a holder because the work has to run inside a lambda.
     */
    private static final class Verification {

        private long checksum = 1;
        private int found;
        private int errors;
        private int skipped;
        private int intendedRecipeWon;

        private void run(List<BreweryRecipe> realRecipes) {
            for (BreweryRecipe recipe : realRecipes) {
                BreweryIngredients brew = matchingBrewOf(recipe);
                if (brew == null) {
                    skipped++;
                    continue;
                }
                for (boolean distilled : new boolean[]{true, false}) {
                    BestRecipeResult result = brew.getBestRecipeFull(recipe.getWood(), recipe.getAge(), distilled);
                    checksum = 31 * checksum + result.toString().hashCode();
                    if (result.getSuccessRecipe() != null) {
                        found++;
                        if (result.getSuccessRecipe() == recipe) {
                            intendedRecipeWon++;
                        }
                    } else {
                        errors++;
                    }
                }
            }
        }
    }

    /**
     * Builds a brew that exactly matches the given recipe, or null if the recipe has no ingredients.
     */
    private static BreweryIngredients matchingBrewOf(BreweryRecipe recipe) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (RecipeItem recipeItem : recipe.getIngredients()) {
            ingredients.add(recipeItem.toIngredientGeneric());
        }
        if (ingredients.isEmpty()) {
            return null;
        }
        return brewOf(ingredients, recipe.getCookingTime());
    }

    private static BreweryIngredients brewOf(List<Ingredient> ingredients, int cookedTime) {
        return new BreweryIngredients(new ArrayList<>(ingredients), cookedTime);
    }

    /**
     * Adds the given number of recipes that deliberately match nothing, so the scan cannot stop early on
     * them. They are clones of real recipes with a single non-matching ingredient, which keeps difficulties,
     * cooking times and barrel types realistic while still forcing a full evaluation of all four quality
     * dimensions.
     *
     * @return The added recipes, so the caller can take them out again
     */
    private static List<BreweryRecipe> addSyntheticRecipes(int count) {
        if (count <= 0) {
            return List.of();
        }
        List<BreweryRecipe> template = new ArrayList<>(BreweryRecipe.getAllRecipes());
        if (template.isEmpty()) {
            return List.of();
        }

        List<BreweryRecipe> added = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BreweryRecipe clone = template.get(i % template.size()).clone();
            clone.setName(new String[]{ "bench_" + i });
            clone.setIngredients(new ArrayList<>(List.of(new SimpleItem(NON_MATCHING_MATERIAL, (short) 8))));
            added.add(clone);
        }
        BreweryRecipe.getAllRecipes().addAll(added);
        return added;
    }

    /**
     * Warms the code up on a quarter of the iterations, then times the rest of them.
     *
     * @return The nanos the measured iterations took in total
     */
    private static long measure(BreweryIngredients brew, BarrelWoodType wood, float age, boolean distilled,
                                int iterations) {
        int warmup = Math.max(1, iterations / 4);
        for (int i = 0; i < warmup; i++) {
            consume(brew, wood, age, distilled);
        }

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            consume(brew, wood, age, distilled);
        }
        return System.nanoTime() - start;
    }

    private static void consume(BreweryIngredients brew, BarrelWoodType wood, float age, boolean distilled) {
        BestRecipeResult result = brew.getBestRecipeFull(wood, age, distilled);
        blackhole += result.getClass().hashCode() + (result.getSuccessRecipe() == null ? 0 : 1);
    }

    private static void report(CommandSender sender, String label, long totalNanos, int iterations) {
        double millisPerCall = totalNanos / 1_000_000.0 / iterations;
        Logging.message(sender, "  " + label + ": " + String.format(Locale.ROOT, "%.4f ms/call", millisPerCall)
            + "  ->  a full large barrel (" + LARGE_BARREL_SIZE + " brews) would take "
            + String.format(Locale.ROOT, "%.1f ms", millisPerCall * LARGE_BARREL_SIZE)
            + "  [" + blackhole + "]");
    }
}
