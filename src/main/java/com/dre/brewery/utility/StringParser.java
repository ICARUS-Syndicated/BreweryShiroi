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

package com.dre.brewery.utility;

import com.dre.brewery.utility.utils.BreweryUtil;

public class StringParser {

    /**
     * Colour given to lore lines that the recipe did not colour itself.
     * <p>
     * Recipe lore stays in the legacy string format on purpose: it ends up in the item's
     * lore and in the serialized brew data, both of which are plain strings.
     */
    private static final String DEFAULT_LORE_COLOR = "&9";

    public static BinaryTuple<Integer, String> parseQuality(String line, ParseType type) {
        int plus = 0;
        if (line.startsWith("+++")) {
            plus = 3;
            line = line.substring(3);
        } else if (line.startsWith("++")) {
            plus = 2;
            line = line.substring(2);
        } else if (line.startsWith("+")) {
            plus = 1;
            line = line.substring(1);
        }
        if (line.startsWith(" ")) {
            line = line.substring(1);
        }

        if (type == ParseType.CMD && line.startsWith("/")) {
            line = line.substring(1);
        }

        if (type == ParseType.LORE && !line.startsWith("&") && !line.startsWith("\u00A7")) {
            line = DEFAULT_LORE_COLOR + line;
        }
        return new BinaryTuple<>(plus, BreweryUtil.color(line));
    }

    public enum ParseType {
        LORE,
        CMD,
        OTHER
    }
}
