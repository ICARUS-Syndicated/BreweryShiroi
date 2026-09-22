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

import com.dre.brewery.utility.StringParser;
import com.dre.brewery.utility.Tuple;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a list of quality-prefixed strings into tuples of {@code quality -> text}.
 * <p>
 * Accepts a single string or a list of strings. The concrete {@link StringParser.ParseType} decides how
 * the text is post-processed (lore is colourised, commands get a leading slash stripped).
 */
public abstract class QualityStringsDeserializer extends JsonDeserializer<List<Tuple<Integer, String>>> {

    protected abstract StringParser.ParseType parseType();

    @Override
    public List<Tuple<Integer, String>> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = context.readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        List<Tuple<Integer, String>> lines = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode element : node) {
                lines.add(StringParser.parseQuality(element.asText(), parseType()));
            }
        } else {
            lines.add(StringParser.parseQuality(node.asText(), parseType()));
        }
        return lines;
    }
}
