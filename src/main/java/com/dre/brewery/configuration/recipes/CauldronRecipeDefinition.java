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

package com.dre.brewery.configuration.recipes;

import com.dre.brewery.configuration.recipes.serdes.LoreStringsDeserializer;
import com.dre.brewery.configuration.recipes.serdes.RecipeItemsDeserializer;
import com.dre.brewery.configuration.recipes.serdes.StringListDeserializer;
import com.dre.brewery.recipe.BreweryCauldronRecipe;
import com.dre.brewery.recipe.PotionColor;
import com.dre.brewery.recipe.items.RecipeItem;
import com.dre.brewery.utility.Tuple;
import com.dre.brewery.utility.utils.BreweryUtil;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.bukkit.Color;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One top level entry of {@code cauldron.yml} in its parsed, strongly typed form.
 * <p>
 * A cauldron recipe defines which ingredients the cauldron accepts and the base potion they result in.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CauldronRecipeDefinition {

    private String name;

    @JsonDeserialize(using = RecipeItemsDeserializer.class)
    private List<RecipeItem> ingredients;

    private String color;

    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> cookParticles;

    @JsonDeserialize(using = LoreStringsDeserializer.class)
    private List<Tuple<Integer, String>> lore;

    private Integer customModelData;

    /**
     * Builds the runtime recipe for this definition.
     *
     * @param id The key this definition was declared under, serves as the recipe id
     * @return The recipe
     * @throws RecipeParseException When the definition can not be used as a recipe
     */
    public BreweryCauldronRecipe toBreweryRecipe(String id) {
        if (this.name == null || this.name.isEmpty()) {
            throw new RecipeParseException("name is missing");
        }

        BreweryCauldronRecipe recipe = new BreweryCauldronRecipe(id, BreweryUtil.color(this.name));

        List<RecipeItem> ingredients = this.ingredients != null ? new ArrayList<>(this.ingredients) : new ArrayList<>();
        if (ingredients.isEmpty()) {
            throw new RecipeParseException("no ingredients were given");
        }
        recipe.setIngredients(ingredients);
        recipe.setColor(parseColor(this.color, recipe.getName()));

        recipe.setParticleColor(parseCookParticles(recipe.getName()));
        recipe.setCmData(this.customModelData != null ? this.customModelData : 0);

        List<String> lore = new ArrayList<>();
        if (this.lore != null) {
            this.lore.forEach(line -> lore.add(line.second()));
        }
        recipe.setLore(lore);

        return recipe;
    }

    /**
     * An unknown color is not fatal, the cauldron base just falls back to cyan.
     */
    private static PotionColor parseColor(@Nullable String color, String recipeName) {
        if (color == null) {
            return PotionColor.CYAN;
        }
        PotionColor parsed = PotionColor.fromString(color);
        if (parsed == PotionColor.WATER && !color.equals("WATER")) {
            // Don't throw an error here as old mc versions will not know even the default colors
            return PotionColor.CYAN;
        }
        return parsed;
    }

    /**
     * @return The color transitions of the particles above the cauldron, ordered by their minute
     * @throws RecipeParseException When an entry is malformed
     */
    private List<Tuple<Integer, Color>> parseCookParticles(String recipeName) {
        List<Tuple<Integer, Color>> particleColor = new ArrayList<>();
        if (this.cookParticles == null) {
            return particleColor;
        }

        for (String entry : this.cookParticles) {
            String[] split = entry.split("/");
            int minute;
            if (split.length == 1) {
                minute = 10;
            } else if (split.length == 2) {
                minute = BreweryUtil.parseIntOrZero(split[1]);
            } else {
                throw new RecipeParseException("cookParticle: '" + entry + "' in: " + recipeName);
            }
            if (minute < 1) {
                throw new RecipeParseException("cookParticle: '" + entry + "' in: " + recipeName);
            }

            PotionColor particleColorEntry = PotionColor.fromString(split[0]);
            if (particleColorEntry == PotionColor.WATER && !split[0].equals("WATER")) {
                throw new RecipeParseException("Color of cookParticle: '" + entry + "' in: " + recipeName);
            }
            particleColor.add(new Tuple<>(minute, particleColorEntry.getColor()));
        }

        particleColor.sort(Comparator.comparing(Tuple::first));
        return particleColor;
    }
}
