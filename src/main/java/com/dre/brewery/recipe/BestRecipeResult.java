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

package com.dre.brewery.recipe;

import com.dre.brewery.brew.BrewDefect;
import com.dre.brewery.utility.utils.BreweryUtil;
import org.jetbrains.annotations.Nullable;

/**
 * The set of possible results from trying to find the best recipe given some ingredients.
 */
public sealed interface BestRecipeResult {

    /**
     * Gets the best recipe, if one with quality above 0 was found.
     * @return the recipe, or null if not found
     */
    @Nullable BreweryRecipe getSuccessRecipe();

    /**
     * If no recipe was found, gets the worst defect of the next best recipe.
     * If there are multiple equally bad defects, one is chosen at random.
     * @return the worst defect, or null if a recipe was found
     */
    @Nullable BrewDefect getWorstDefect();

    /**
     * A recipe matching the ingredients was found.
     * @param recipe     the best recipe with the highest quality
     * @param evaluation the recipe's evaluation
     */
    record Found(BreweryRecipe recipe, RecipeEvaluation evaluation) implements BestRecipeResult {

        @Override
        public @Nullable BreweryRecipe getSuccessRecipe() {
            return recipe;
        }

        @Override
        public @Nullable BrewDefect getWorstDefect() {
            return null;
        }

        @Override
        public String toString() {
            return "Found{recipe = " + recipe + ", evaluation = " + evaluation + '}';
        }

    }

    /**
     * No recipe with quality above 0 was found.
     * @param guess      the failed recipe with the highest quality (or least problems),
     *                   a "best guess" of what the user was trying to brew
     * @param evaluation the recipe's evaluation
     */
    record Error(BreweryRecipe guess, RecipeEvaluation evaluation) implements BestRecipeResult {

        @Override
        public @Nullable BreweryRecipe getSuccessRecipe() {
            return null;
        }

        @Override
        public @Nullable BrewDefect getWorstDefect() {
            return BreweryUtil.choose(evaluation.getWorstDefects());
        }

        @Override
        public String toString() {
            return "Error{guess = " + guess + ", evaluation = " + evaluation + '}';
        }

    }

    /**
     * Somehow, no recipes are loaded.
     */
    record NoRecipesRegistered() implements BestRecipeResult {

        @Override
        public @Nullable BreweryRecipe getSuccessRecipe() {
            return null;
        }

        @Override
        public BrewDefect getWorstDefect() {
            return new BrewDefect.NoRecipesRegistered();
        }

        @Override
        public String toString() {
            return "NoRecipesRegistered{}";
        }

    }

}
