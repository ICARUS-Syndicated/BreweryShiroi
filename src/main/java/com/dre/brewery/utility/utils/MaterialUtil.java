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

package com.dre.brewery.utility.utils;

import com.dre.brewery.instruments.BreweryHeatSource;
import com.dre.brewery.utility.BukkitEffectConstants;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Stairs;
import org.jetbrains.annotations.Nullable;

public final class MaterialUtil {

    // Cauldron stuff
    public static final byte EMPTY = 0, SOME = 1, FULL = 2;
    public static final Material WATER_CAULDRON = getMaterialSafely("WATER_CAULDRON");
    public static final Material CLOCK = getMaterialSafely("CLOCK");


    @Nullable
    public static Material getMaterialSafely(String name) {
        try {
            for (Material material : BukkitEffectConstants.getMappedValues(Material.class)) {
                if (material.name().equalsIgnoreCase(name)) {
                    return material;
                }
            }
            return Material.matchMaterial(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    /**
     * @return Whether the given block is heating the cauldron above it, see {@link BreweryHeatSource}
     */
    public static boolean isCauldronHeatSource(Block block) {
        return BreweryHeatSource.of(block) != null;
    }

    public static boolean isBottle(Material type) {
        return type == Material.POTION
            || type == Material.LINGERING_POTION
            || type == Material.SPLASH_POTION
            || type == Material.EXPERIENCE_BOTTLE
            || type == Material.DRAGON_BREATH
            || type == Material.HONEY_BOTTLE;
    }

    public static boolean areStairsInverted(Block block) {
        return block.getBlockData() instanceof Stairs stairs && stairs.getHalf() == Stairs.Half.TOP;
    }


    /**
     * Test if this Material Type is a Cauldron filled with water
     */
    public static boolean isWaterCauldron(Material type) {
        return type == WATER_CAULDRON;
    }

    /**
     * Get The Fill Level of a Cauldron Block, 0 = empty, 1 = something in, 2 = full
     *
     * @return 0 = empty, 1 = something in, 2 = full
     */
    public static byte getFillLevel(Block block) {
        if (!isWaterCauldron(block.getType()) || !(block.getBlockData() instanceof Levelled cauldron)) {
            return EMPTY;
        }
        int level = cauldron.getLevel();
        if (level == 0) {
            return EMPTY;
        }
        return level == cauldron.getMaximumLevel() ? FULL : SOME;
    }
}
