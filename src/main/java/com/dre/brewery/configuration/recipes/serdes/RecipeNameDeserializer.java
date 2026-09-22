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

package com.dre.brewery.configuration.recipes.serdes;

import com.dre.brewery.recipe.BreweryRecipe;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Turns the recipe name DSL into the per-quality name array used by {@link BreweryRecipe}.
 * <p>
 * Accepts {@code "Name"} or {@code "Bad Name/Normal Name/Good Name"}. Only when all three qualities are given
 * are they kept, otherwise the first one is used for every quality.
 */
public class RecipeNameDeserializer extends JsonDeserializer<String[]> {

    @Override
    public String[] deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String raw = parser.getValueAsString();
        if (raw == null) {
            return null;
        }
        String[] names = raw.split("/");
        return names.length > 2 ? names : new String[]{ names[0] };
    }
}
