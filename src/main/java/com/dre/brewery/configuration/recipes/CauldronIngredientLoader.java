/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2026 Ph0sphorW
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

package com.dre.brewery.configuration.recipes;

import com.dre.brewery.recipe.BreweryCauldronRecipe;
import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.utility.Logging;

import java.util.List;
import java.util.Map;

/**
 * Loads the configured cauldron ingredients into {@link BreweryCauldronRecipe#getConfigRecipes()}.
 * <p>
 * See {@link DefinitionFile} for where the ingredients come from and how a single one is parsed.
 * Depends on {@link CustomItemLoader}, as cauldron ingredients may reference custom items.
 */
public final class CauldronIngredientLoader {

    private static final DefinitionFile<CauldronRecipeDefinition> FILE = new DefinitionFile<>(
        "cauldron.yml", "cauldronFile", "cauldron", "Cauldron-Recipe", CauldronRecipeDefinition.class);

    private CauldronIngredientLoader() {
    }

    public static void loadCauldronIngredients() {
        List<BreweryCauldronRecipe> configRecipes = BreweryCauldronRecipe.getConfigRecipes();
        configRecipes.clear();

        for (Map.Entry<String, CauldronRecipeDefinition> entry : FILE.load().entrySet()) {
            try {
                configRecipes.add(entry.getValue().toBreweryRecipe(entry.getKey()));
            } catch (RecipeParseException e) {
                Logging.errorLog("Failed to load the Cauldron-Recipe with id: '" + entry.getKey() + "': " + e.getMessage());
            }
        }
        BreweryCauldronRecipe.setNumConfigRecipes(configRecipes.size());

        // Recalculating Cauldron-Accepted Items for non-config recipes
        for (BreweryRecipe recipe : BreweryRecipe.getAddedRecipes()) {
            recipe.updateAcceptedLists();
        }
        for (BreweryCauldronRecipe recipe : BreweryCauldronRecipe.getAddedRecipes()) {
            recipe.updateAcceptedLists();
        }
    }
}
