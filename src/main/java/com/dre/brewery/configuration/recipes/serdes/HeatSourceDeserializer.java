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

import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.configuration.recipes.RecipeParseException;
import com.dre.brewery.instruments.BreweryHeatSource;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the {@code heatsource} DSL of a recipe into a {@link BreweryHeatSource.HeatSourceRequirement}.
 * <p>
 * Accepts a single name or a list of them, each being a heat source block name, one of the groups
 * {@code mild}, {@code fires}, {@code soulfire} and {@code campfires}, or {@code all}.
 */
public class HeatSourceDeserializer extends JsonDeserializer<BreweryHeatSource.HeatSourceRequirement> {

    @Override
    public BreweryHeatSource.HeatSourceRequirement deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = context.readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }

        List<String> names = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode element : node) {
                names.add(element.asText());
            }
        } else {
            names.add(node.asText());
        }
        return parse(names);
    }

    /**
     * Every name is resolved on its own so that an unknown one can be named in the error.
     *
     * @throws RecipeParseException if one of the names is not a heat source or a known group
     */
    private static BreweryHeatSource.HeatSourceRequirement parse(List<String> names) {
        List<BreweryHeatSource.HeatSourceRequirement> parts = new ArrayList<>(names.size());
        for (String name : names) {
            BreweryHeatSource.HeatSourceRequirement part = BreweryHeatSource.HeatSourceRequirement.fromName(name);
            if (part == null) {
                throw new RecipeParseException(invalidNameMessage(name));
            }
            parts.add(part);
        }
        return BreweryHeatSource.HeatSourceRequirement.combine(parts);
    }

    private static String invalidNameMessage(@Nullable String name) {
        return ConfigManager.getConfig(Lang.class).getEntry("Error_InvalidHeatSource", name);
    }
}
