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
 * You should have received a copy of the GNU General Public License
 * along with BreweryX. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.commands.subcommands;

import com.dre.brewery.brew.Brew;
import com.dre.brewery.brew.BreweryIngredients;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.commands.CommandUtil;
import com.dre.brewery.recipe.BestRecipeResult;
import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.recipe.RecipeEvaluation;
import com.dre.brewery.recipe.items.Ingredient;
import com.dre.brewery.recipe.items.RecipeItem;
import com.dre.brewery.utility.Logging;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Writes detailed debugging information about the brew in the players hand into the log.
 */
public class DebugInfoCommand {

    private DebugInfoCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("debuginfo", "brewery.cmd.debuginfo", "Help_DebugInfo")
            .optional("recipe", StringParser.greedyStringParser(),
                SuggestionProvider.blockingStrings((context, input) -> CommandUtil.recipeNamesAndIds()))
            .handler(context -> {
                Player player = CommandUtil.requirePlayer(context.sender().source(), commands.lang());
                if (player == null) {
                    return;
                }
                debugInfo(player, context.optional("recipe").map(Object::toString).orElse(null));
            }));
    }

    private static void debugInfo(Player player, String recipeName) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Brew brew = Brew.get(hand);
        if (brew == null) {
            return;
        }

        Logging.log(brew.toString());
        BreweryIngredients ingredients = brew.getIngredients();

        if (recipeName == null) {
            logAllRecipes(ingredients, brew);
        } else {
            logSpecificRecipe(player, ingredients, brew, recipeName);
        }

        Logging.message(player, "Debug Info for item written into Log");
    }

    private static void logAllRecipes(BreweryIngredients ingredients, Brew brew) {
        Logging.log("&lIngredients:");
        for (Ingredient ing : ingredients.getIngredientList()) {
            Logging.log(ing.toString());
        }
        Logging.log("&lTesting Recipes");
        for (BreweryRecipe recipe : BreweryRecipe.getAllRecipes()) {
            logRecipe(recipe, brew);
        }
        BestRecipeResult distill = ingredients.getBestRecipeFull(brew.getWood(), brew.getAgeTime(), true);
        Logging.log("&lDistill-Recipe: &r" + ChatColor.stripColor(distill.toString()));
        BestRecipeResult nonDistill = ingredients.getBestRecipeFull(brew.getWood(), brew.getAgeTime(), false);
        Logging.log("&lRecipe: &r" + ChatColor.stripColor(nonDistill.toString()));
    }

    private static void logSpecificRecipe(Player player, BreweryIngredients ingredients, Brew brew, String recipeName) {
        BreweryRecipe recipe = BreweryRecipe.getMatching(recipeName);
        if (recipe == null) {
            Logging.message(player, "Could not find Recipe " + recipeName);
            return;
        }
        Logging.log("&lIngredients in Recipe " + recipe.getRecipeName() + "&r&l:&r");
        for (RecipeItem ri : recipe.getIngredients()) {
            Logging.log(ri.toString());
        }
        Logging.log("&lIngredients in Brew:");
        for (Ingredient ingredient : ingredients.getIngredientList()) {
            int amountInRecipe = recipe.amountOf(ingredient);
            Logging.log(ingredient.toString() + ": " + amountInRecipe + " of this are in the Recipe");
        }
        logRecipe(recipe, brew);
    }

    private static void logRecipe(BreweryRecipe recipe, Brew brew) {
        BreweryIngredients ingredients = brew.getIngredients();
        RecipeEvaluation ingQ = ingredients.getIngredientQualityFull(recipe);
        Logging.log(String.format("%s&r ingQlty: %s", recipe.getRecipeName(), ingQ));
        RecipeEvaluation cookQ = ingredients.getCookingQualityFull(recipe, false);
        Logging.log(String.format("%s&r cookQlty: %s", recipe.getRecipeName(), cookQ));
        RecipeEvaluation cookDistQ = ingredients.getCookingQualityFull(recipe, true);
        Logging.log(String.format("%s&r cook+DistQlty: %s", recipe.getRecipeName(), cookDistQ));
        RecipeEvaluation ageQ = ingredients.getAgeQualityFull(recipe, brew.getAgeTime());
        Logging.log(String.format("%s&r ageQlty: %s", recipe.getRecipeName(), ageQ));
        RecipeEvaluation woodQ = ingredients.getWoodQualityFull(recipe, brew.getWood());
        Logging.log(String.format("%s&r woodQlty: %s", recipe.getRecipeName(), woodQ));
    }
}
