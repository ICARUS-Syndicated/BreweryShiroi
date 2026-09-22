/*
 * BreweryShiroi Bukkit-Plugin for an alternate brewing process
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

package com.dre.brewery.configuration.recipes;

import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.utility.Logging;

import java.util.List;
import java.util.Map;

/**
 * Loads the configured brewing recipes into {@link BreweryRecipe#getConfigRecipes()}.
 * <p>
 * Uses the same file and DSL that older BreweryShiroi versions used, see {@link DefinitionFile} for where the
 * recipes come from and how a single one is parsed.
 */
public final class RecipeLoader {

    private static final DefinitionFile<RecipeDefinition> FILE = new DefinitionFile<>(
        "recipes.yml", "recipesFile", "recipes", "Recipe", RecipeDefinition.class);

    private RecipeLoader() {
    }

    public static void loadRecipes() {
        List<BreweryRecipe> configRecipes = BreweryRecipe.getConfigRecipes();
        configRecipes.clear();

        for (Map.Entry<String, RecipeDefinition> entry : FILE.load().entrySet()) {
            BreweryRecipe recipe = entry.getValue().toBreweryRecipe(entry.getKey());
            if (recipe != null && recipe.isValid()) {
                configRecipes.add(recipe);
            } else {
                Logging.errorLog("Loading the Recipe with id: '" + entry.getKey() + "' failed!");
            }
        }
        BreweryRecipe.setNumConfigRecipes(configRecipes.size());
    }
}
