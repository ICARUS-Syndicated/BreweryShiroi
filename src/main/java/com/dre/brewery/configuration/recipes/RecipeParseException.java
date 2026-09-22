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

package com.dre.brewery.configuration.recipes;

/**
 * Thrown when a recipe entry of a configuration file can not be translated into its structured form.
 * <p>
 * The message is already user facing (it is resolved through the language file) so it can be logged as-is.
 */
public class RecipeParseException extends RuntimeException {

    public RecipeParseException(String message) {
        super(message);
    }

    public RecipeParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
