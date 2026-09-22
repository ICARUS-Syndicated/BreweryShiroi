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

import com.dre.brewery.instruments.barrel.BarrelWoodType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the {@code wood} DSL of a recipe into {@link BarrelWoodType}s.
 * <p>
 * Accepts an index, a name, a {@link org.bukkit.Material}, or a list of any of those.
 */
public class BarrelWoodTypesDeserializer extends JsonDeserializer<List<BarrelWoodType>> {

    @Override
    public List<BarrelWoodType> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = context.readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        return BarrelWoodType.listFromAny(toJava(node));
    }

    /**
     * Converts a YAML node into the plain Java object tree {@link BarrelWoodType#listFromAny(Object)} expects.
     */
    private static Object toJava(JsonNode node) {
        if (node.isArray()) {
            List<Object> values = new ArrayList<>(node.size());
            for (JsonNode element : node) {
                values.add(toJava(element));
            }
            return values;
        }
        return toScalar(node);
    }

    @Nullable
    private static Object toScalar(JsonNode node) {
        if (node.isNumber()) {
            return node.isFloatingPointNumber() ? node.floatValue() : node.intValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }
}
