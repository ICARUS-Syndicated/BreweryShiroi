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

package com.dre.brewery.instruments;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One block that can heat a cauldron, as far as brewing cares about it.
 * <p>
 * This is what a cauldron is actually sitting on. What a recipe accepts is a {@link HeatSourceRequirement},
 * which either names one of these or a group of them.
 * <p>
 * Adding a heat source means adding a constant here and putting it in the group it belongs to in
 * {@link HeatSourceRequirement}, which makes {@code all} accept it as well.
 */
@Getter
public enum BreweryHeatSource {

    FIRE(Material.FIRE),
    SOUL_FIRE(Material.SOUL_FIRE),
    CAMPFIRE(Material.CAMPFIRE),
    SOUL_CAMPFIRE(Material.SOUL_CAMPFIRE),
    MAGMA_BLOCK(Material.MAGMA_BLOCK),
    LAVA(Material.LAVA);

    private static final Map<Material, BreweryHeatSource> BY_MATERIAL = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(BreweryHeatSource::getMaterial, Function.identity()));

    private final Material material;

    BreweryHeatSource(Material material) {
        this.material = material;
    }

    /**
     * The heat source a block provides.
     * <p>
     * A campfire only counts while it is lit, an extinguished one heats nothing. The other sources have no
     * such state, so being the right material is enough for them.
     *
     * @param block The block a cauldron stands on
     * @return The heat source, or null if this block is not heating a cauldron
     */
    @Nullable
    public static BreweryHeatSource of(Block block) {
        BreweryHeatSource source = BY_MATERIAL.get(block.getType());
        if (source == null || !source.isBurning(block)) {
            return null;
        }
        return source;
    }

    /**
     * @param name A heat source block name, for example {@code soul_campfire}
     * @return The heat source, or null if no heat source is named like that
     */
    @Nullable
    public static BreweryHeatSource fromName(String name) {
        for (BreweryHeatSource source : values()) {
            if (source.material.name().equalsIgnoreCase(name)) {
                return source;
            }
        }
        return null;
    }

    private boolean isBurning(Block block) {
        if (this != CAMPFIRE && this != SOUL_CAMPFIRE) {
            return true;
        }
        return block.getBlockData() instanceof Lightable lightable && lightable.isLit();
    }

    /**
     * The {@code heatsource} option of a recipe: which heat sources a brew may be cooked over.
     * <p>
     * The option either names a single {@link BreweryHeatSource} by its block name, such as {@code soul_campfire}, or
     * one of the groups below, or {@code all}. A list may name several of those, in which case any of them
     * counts as correct. Not setting the option means {@link #ALL}.
     *
     * @param name     What the option said, in the form it is shown back to players
     * @param accepted The heat sources the recipe is happy with
     */
    public record HeatSourceRequirement(String name, Set<BreweryHeatSource> accepted) {

        /**
         * Every heat source. This is what a recipe that does not set the option accepts.
         */
        public static final HeatSourceRequirement ALL =
            new HeatSourceRequirement("all", EnumSet.allOf(BreweryHeatSource.class));

        /**
         * The names that stand for more than one heat source.
         * <p>
         * A newly added heat source belongs in whichever of these it fits, so that recipes naming the group
         * accept it without being changed.
         */
        private static final Map<String, Set<BreweryHeatSource>> GROUPS = Map.of(
            // Keeps a cauldron warm without an open flame
            "mild", EnumSet.of(MAGMA_BLOCK),
            "fires", EnumSet.of(FIRE, SOUL_FIRE),
            "soulfire", EnumSet.of(SOUL_FIRE, SOUL_CAMPFIRE),
            "campfires", EnumSet.of(CAMPFIRE, SOUL_CAMPFIRE)
        );

        public HeatSourceRequirement {
            accepted = Collections.unmodifiableSet(accepted);
        }

        /**
         * @param name One of the group names, {@code all}, or the block name of a heat source
         * @return The requirement, or null if nothing is known by that name
         */
        @Nullable
        public static BreweryHeatSource.HeatSourceRequirement fromName(String name) {
            String key = name.toLowerCase(Locale.ROOT).trim();
            if (key.equals(ALL.name)) {
                return ALL;
            }
            Set<BreweryHeatSource> group = GROUPS.get(key);
            if (group != null) {
                return new HeatSourceRequirement(key, EnumSet.copyOf(group));
            }
            BreweryHeatSource source = BreweryHeatSource.fromName(key);
            return source == null ? null : new HeatSourceRequirement(key, EnumSet.of(source));
        }

        /**
         * @param requirements The requirements to merge, must not be empty
         * @return A requirement accepting the heat sources of all of them
         */
        public static HeatSourceRequirement combine(List<HeatSourceRequirement> requirements) {
            Set<BreweryHeatSource> accepted = EnumSet.noneOf(BreweryHeatSource.class);
            List<String> names = new ArrayList<>(requirements.size());
            for (HeatSourceRequirement requirement : requirements) {
                accepted.addAll(requirement.accepted);
                names.add(requirement.name);
            }
            return new HeatSourceRequirement(String.join(", ", names), accepted);
        }

        public boolean accepts(BreweryHeatSource source) {
            return accepted.contains(source);
        }

        @Override
        public String toString() {
            return "HeatSourceRequirement{name = " + name + ", accepted = " + accepted + "}";
        }
    }
}
