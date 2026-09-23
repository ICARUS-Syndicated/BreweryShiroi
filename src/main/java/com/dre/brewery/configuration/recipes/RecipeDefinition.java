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

import com.dre.brewery.configuration.recipes.serdes.BEffectsDeserializer;
import com.dre.brewery.configuration.recipes.serdes.BarrelWoodTypesDeserializer;
import com.dre.brewery.configuration.recipes.serdes.CommandStringsDeserializer;
import com.dre.brewery.configuration.recipes.serdes.CustomModelDataDeserializer;
import com.dre.brewery.configuration.recipes.serdes.ItemModelDeserializer;
import com.dre.brewery.configuration.recipes.serdes.LoreStringsDeserializer;
import com.dre.brewery.configuration.recipes.serdes.RecipeItemsDeserializer;
import com.dre.brewery.configuration.recipes.serdes.RecipeNameDeserializer;
import com.dre.brewery.instruments.barrel.BarrelWoodType;
import com.dre.brewery.recipe.BreweryEffect;
import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.recipe.PotionColor;
import com.dre.brewery.recipe.items.RecipeItem;
import com.dre.brewery.utility.utils.BreweryUtil;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.BinaryTuple;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One top level entry of {@code recipes.yml} in its parsed, strongly typed form.
 * <p>
 * Every field of this class maps a node of the recipe DSL onto the Java type the runtime actually needs
 * ({@link RecipeItem}s, {@link PotionColor}s, {@link BarrelWoodType}s, ...), so no raw strings have to be
 * handled after loading. See the {@code serdes} package for the individual converters.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RecipeDefinition {

    @JsonDeserialize(using = RecipeNameDeserializer.class)
    private String[] name;

    @JsonDeserialize(using = RecipeItemsDeserializer.class)
    private List<RecipeItem> ingredients;

    @JsonProperty("cookingtime")
    private Integer cookingTime;

    @JsonProperty("distillruns")
    private Integer distillRuns;

    @JsonProperty("distilltime")
    private Integer distillTime;

    @JsonDeserialize(using = BarrelWoodTypesDeserializer.class)
    private List<BarrelWoodType> wood;

    private Integer age;
    private String color;
    private Integer difficulty;
    private Integer alcohol;

    @JsonDeserialize(using = LoreStringsDeserializer.class)
    private List<BinaryTuple<Integer, String>> lore;

    @JsonProperty("servercommands")
    @JsonDeserialize(using = CommandStringsDeserializer.class)
    private List<BinaryTuple<Integer, String>> serverCommands;

    @JsonProperty("playercommands")
    @JsonDeserialize(using = CommandStringsDeserializer.class)
    private List<BinaryTuple<Integer, String>> playerCommands;

    @JsonProperty("drinkmessage")
    private String drinkMessage;

    @JsonProperty("drinktitle")
    private String drinkTitle;

    private Boolean glint;

    @JsonDeserialize(using = CustomModelDataDeserializer.class)
    private int[] customModelData;

    @JsonDeserialize(using = ItemModelDeserializer.class)
    private String[] itemModel;

    @JsonDeserialize(using = BEffectsDeserializer.class)
    private List<BreweryEffect> effects;

    /**
     * Builds the runtime recipe for this definition.
     *
     * @param id The key this definition was declared under, serves as the recipe id
     * @return The recipe, or null if the definition is invalid
     */
    @Nullable
    public BreweryRecipe toBreweryRecipe(String id) {
        BreweryRecipe recipe = new BreweryRecipe();
        recipe.setId(id);

        if (this.name == null || this.name.length == 0) {
            Logging.errorLog(id + ": Recipe Name missing or invalid!");
            return null;
        }
        recipe.setName(this.name);
        if (recipe.getRecipeName() == null || recipe.getRecipeName().isEmpty()) {
            Logging.errorLog(id + ": Recipe Name invalid");
            return null;
        }

        recipe.setIngredients(this.ingredients != null ? new ArrayList<>(this.ingredients) : new ArrayList<>());
        if (recipe.getIngredients().isEmpty()) {
            Logging.errorLog("No ingredients for: " + recipe.getRecipeName());
            return null;
        }

        recipe.setCookingTime(this.cookingTime != null ? this.cookingTime : 0);

        int distillRuns = this.distillRuns != null ? this.distillRuns : 0;
        recipe.setDistillRuns(distillRuns > Byte.MAX_VALUE ? Byte.MAX_VALUE : (byte) distillRuns);

        recipe.setDistillTime((this.distillTime != null ? this.distillTime : 0) * 20);
        recipe.setBarrelTypes(this.wood != null ? this.wood : BarrelWoodType.listFromAny(null));
        recipe.setAge(this.age != null ? this.age : 0);
        recipe.setDifficulty(this.difficulty != null ? this.difficulty : 0);
        recipe.setAlcohol(this.alcohol != null ? this.alcohol : 0);

        String colorName = this.color != null ? this.color : "BLUE";
        recipe.setColor(PotionColor.fromString(colorName));
        if (recipe.getColor() == PotionColor.WATER && !colorName.equals("WATER")) {
            Logging.errorLog("Invalid Color '" + colorName + "' in Recipe: " + recipe.getRecipeName());
            return null;
        }

        recipe.setLore(this.lore != null ? this.lore : new ArrayList<>());
        recipe.setServerCommands(this.serverCommands != null ? this.serverCommands : new ArrayList<>());
        recipe.setPlayerCommands(this.playerCommands != null ? this.playerCommands : new ArrayList<>());

        recipe.setDrinkMessage(BreweryUtil.color(this.drinkMessage));
        recipe.setDrinkTitle(BreweryUtil.color(this.drinkTitle));
        recipe.setGlint(this.glint != null && this.glint);

        if (this.customModelData != null) {
            recipe.setCustomModelData(this.customModelData);
        }
        if (this.itemModel != null) {
            recipe.setItemModel(this.itemModel);
        }

        List<BreweryEffect> validEffects = new ArrayList<>();
        if (this.effects != null) {
            for (BreweryEffect effect : this.effects) {
                if (effect.isValid()) {
                    validEffects.add(effect);
                } else {
                    Logging.errorLog("Error adding Effect to Recipe: " + recipe.getRecipeName());
                }
            }
        }
        recipe.setEffects(validEffects);

        return recipe;
    }
}
