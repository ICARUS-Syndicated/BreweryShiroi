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

import com.dre.brewery.configuration.recipes.serdes.IntListDeserializer;
import com.dre.brewery.configuration.recipes.serdes.StringListDeserializer;
import com.dre.brewery.recipe.items.RecipeItem;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.utils.BreweryUtil;
import com.dre.brewery.utility.utils.MaterialUtil;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One top level entry of {@code custom-items.yml} in its parsed, strongly typed form.
 * <p>
 * A custom item is an item that is matched by properties instead of just its material, so recipes can use
 * items added by other plugins or items renamed in an anvil.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CustomItemDefinition {

    private Boolean matchAny;

    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> material;

    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> name;

    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> lore;

    @JsonDeserialize(using = IntListDeserializer.class)
    private List<Integer> customModelData;

    /**
     * Builds the runtime item this definition describes.
     *
     * @param id The key this definition was declared under, serves as the config id
     * @return The item, or null if the definition matches nothing at all
     */
    @Nullable
    public RecipeItem toRecipeItem(String id) {
        List<Material> materials = resolveMaterials(id);
        List<String> names = color(this.name);
        List<String> lore = color(this.lore);
        List<Integer> customModelDatas = this.customModelData != null ? this.customModelData : List.of();

        if (materials.isEmpty() && names.isEmpty() && lore.isEmpty() && customModelDatas.isEmpty()) {
            Logging.warningLog("Custom Item '" + id + "' defines nothing to match on and will be ignored!");
            return null;
        }

        return RecipeItem.fromConfigCustom(id, Boolean.TRUE.equals(this.matchAny), materials, names, lore, customModelDatas);
    }

    /**
     * Materials that do not exist are reported and skipped, so one typo does not discard all the other
     * materials of the same item.
     */
    private List<Material> resolveMaterials(String id) {
        List<Material> materials = new ArrayList<>();
        if (this.material == null) {
            return materials;
        }
        for (String name : this.material) {
            Material material = MaterialUtil.getMaterialSafely(name);
            if (material == null) {
                Logging.warningLog("Custom Item '" + id + "' uses the unknown material '" + name + "', it will be ignored!");
            } else {
                materials.add(material);
            }
        }
        return materials;
    }

    private static List<String> color(@Nullable List<String> lines) {
        return lines == null ? new ArrayList<>() : BreweryUtil.colorArrayList(new ArrayList<>(lines));
    }
}
