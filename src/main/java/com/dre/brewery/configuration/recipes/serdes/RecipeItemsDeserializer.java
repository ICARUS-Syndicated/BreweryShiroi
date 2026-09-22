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

package com.dre.brewery.configuration.recipes.serdes;

import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.configuration.recipes.RecipeParseException;
import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.recipe.items.RecipeItem;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the {@code ingredients} DSL of a recipe into ready to use {@link RecipeItem}s.
 * <p>
 * Accepts a single string or a list of strings, each written as {@code material/amount}, {@code plugin:id/amount}
 * or {@code custom-item-id/amount}. Parsing and the "accepted item" bookkeeping is delegated to
 * {@link BreweryRecipe#loadIngredientVerbose(String)}, so the cauldron acceptance lists stay in sync.
 */
public class RecipeItemsDeserializer extends JsonDeserializer<List<RecipeItem>> {

    @Override
    public List<RecipeItem> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = context.readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        List<RecipeItem> ingredients = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode element : node) {
                addIngredient(ingredients, element.asText());
            }
        } else {
            addIngredient(ingredients, node.asText());
        }
        return ingredients;
    }

    private static void addIngredient(List<RecipeItem> ingredients, @Nullable String raw) {
        if (raw == null) {
            return;
        }
        BreweryRecipe.IngredientResult result = BreweryRecipe.loadIngredientVerbose(raw);
        if (result instanceof BreweryRecipe.IngredientResult.Success success) {
            ingredients.add(success.ingredient());
        } else if (result instanceof BreweryRecipe.IngredientResult.Error error) {
            String message = ConfigManager.getConfig(Lang.class)
                .getEntry(error.error().getTranslationKey(), error.invalidPart());
            throw new RecipeParseException(message);
        }
    }
}
