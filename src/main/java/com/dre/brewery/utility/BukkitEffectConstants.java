/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024-2025 The Brewery Team
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

import com.dre.brewery.BreweryPlugin;
import org.bukkit.Color;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class BukkitEffectConstants {

    private BukkitEffectConstants() {}


    // More constants can be added as required, these are just the ones Brewery currently uses
    public static PotionEffectType HUNGER = potionEffectType("hunger");
    public static PotionEffectType NAUSEA = potionEffectType("nausea");
    public static PotionEffectType BLINDNESS = potionEffectType("blindness");
    public static PotionEffectType SLOWNESS = potionEffectType("slowness");
    public static PotionEffectType REGENERATION = potionEffectType("regeneration");
    public static PotionEffectType POISON = potionEffectType("poison");
    public static PotionEffectType WEAKNESS = potionEffectType("weakness");
    public static PotionEffectType FIRE_RESISTANCE = potionEffectType("fire_resistance");
    public static PotionEffectType INSTANT_HEALTH = potionEffectType("instant_health");
    public static PotionEffectType INSTANT_DAMAGE = potionEffectType("instant_damage");
    public static PotionEffectType WATER_BREATHING = potionEffectType("water_breathing");
    public static PotionEffectType NIGHT_VISION = potionEffectType("night_vision");
    public static PotionEffectType SPEED = potionEffectType("speed");
    public static PotionEffectType HASTE = potionEffectType("haste");

    public static Particle INSTANT_EFFECT = particle("instant_effect");
    public static Particle SPLASH = particle("splash");
    public static Particle ENTITY_EFFECT = particle("entity_effect");
    public static Particle LARGE_SMOKE = particle("large_smoke");
    public static Particle DUST = particle("dust");

    public static PotionType POTION_REGENERATION = potionType("regeneration");
    public static PotionType POTION_SWIFTNESS = potionType("swiftness");
    public static PotionType POTION_FIRE_RESISTANCE = potionType("fire_resistance");
    public static PotionType POTION_POISON = potionType("poison");
    public static PotionType POTION_HEALING = potionType("healing");
    public static PotionType POTION_NIGHT_VISION = potionType("night_vision");
    public static PotionType POTION_WEAKNESS = potionType("weakness");
    public static PotionType POTION_STRENGTH = potionType("strength");
    public static PotionType POTION_SLOWNESS = potionType("slowness");
    public static PotionType POTION_WATER_BREATHING = potionType("water_breathing");
    public static PotionType POTION_HARMING = potionType("harming");
    public static PotionType POTION_INVISIBILITY = potionType("invisibility");

    public static final Material SHORT_GRASS = Material.SHORT_GRASS;


    public static Particle particle(String key) {
        return Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(key));
    }

    public static PotionEffectType potionEffectType(String key) {
        return getOrThrow(Registry.EFFECT, key);
    }

    public static PotionType potionType(String key) {
        return getOrThrow(Registry.POTION, key);
    }

    @Nullable
    public static PotionEffectType nullablePotionEffectType(String key) {
        return Registry.EFFECT.get(NamespacedKey.minecraft(key));
    }


    private static <T extends Keyed> T getOrThrow(Registry<T> registry, String key) {
        T value = registry.get(NamespacedKey.minecraft(key));
        if (value == null) {
            throw new IllegalArgumentException("No value found in registry for key: " + key);
        }
        return value;
    }

    /**
     * {@link Particle#INSTANT_EFFECT} only accepts its colour data since 1.21.10, older versions want {@code null}.
     */
    @Nullable
    public static Particle.Spell instantEffectData(@NotNull Color color, float size) {
        return BreweryPlugin.getMCVersion().isOrLater(MinecraftVersion.V1_21_10) ? new Particle.Spell(color, size) : null;
    }

    /**
     * Holds the constants of this class that were defined in code, keyed by their registry key.
     */
    private static final Map<String, Keyed> MAPPED_VALUES = new HashMap<>();

    static {
        try {
            for (Field field : BukkitEffectConstants.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !Keyed.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                if (field.get(null) instanceof Keyed keyed) {
                    MAPPED_VALUES.put(keyed.getKey().getKey(), keyed);
                }
            }
        } catch (IllegalAccessException e) {
            Logging.errorLog("BukkitConstants failed to initialize mapped values", e);
        }
    }

    @Nullable
    public static <T extends Keyed> T getMappedValue(String key, Class<T> type) {
        Keyed keyed = MAPPED_VALUES.get(key);
        return type.isInstance(keyed) ? type.cast(keyed) : null;
    }


    public static <T extends Keyed> Collection<T> getMappedValues(Class<T> type) {
        return MAPPED_VALUES.values().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .toList();
    }

    @Nullable
    public static Keyed getMappedValue(String key) {
        return MAPPED_VALUES.get(key);
    }

    public static Collection<Keyed> getMappedValues() {
        return MAPPED_VALUES.values();
    }
}
