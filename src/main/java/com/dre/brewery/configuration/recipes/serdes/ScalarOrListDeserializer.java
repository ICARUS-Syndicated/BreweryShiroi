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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a node that is either a single value or a list of values into a list.
 * <p>
 * Older BreweryShiroi versions accepted both spellings for most options, so all of them do here as well.
 *
 * @param <T> The element type the values are converted into
 */
public abstract class ScalarOrListDeserializer<T> extends JsonDeserializer<List<T>> {

    /**
     * @param node The node holding a single value
     * @return The converted value, or null when the node does not hold a valid value
     */
    @Nullable
    protected abstract T convert(JsonNode node);

    @Override
    public List<T> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = context.readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }

        List<T> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode element : node) {
                add(values, element);
            }
        } else {
            add(values, node);
        }
        return values;
    }

    private void add(List<T> values, JsonNode node) {
        T value = convert(node);
        if (value != null) {
            values.add(value);
        }
    }
}
