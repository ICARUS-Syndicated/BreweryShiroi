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

import com.dre.brewery.utility.utils.BreweryUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Parses the {@code customModelData} DSL of a recipe ("1" or "1/2/3") into one value per quality.
 * <p>
 * Values may be ranges, they are resolved to a random value inside the range. Missing qualities inherit the
 * previous one, the first one defaults to 0.
 */
public class CustomModelDataDeserializer extends JsonDeserializer<int[]> {

    @Override
    public int[] deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = context.readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        String[] parts = node.asText().split("/");
        int[] customModelData = new int[3];
        for (int i = 0; i < customModelData.length; i++) {
            if (parts.length > i) {
                customModelData[i] = BreweryUtil.getRandomIntInRange(parts[i]);
            } else {
                customModelData[i] = i == 0 ? 0 : customModelData[i - 1];
            }
        }
        return customModelData;
    }
}
