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

package com.dre.brewery.commands;

import com.dre.brewery.Brew;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.utility.Tuple;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared helpers of the commands, mostly argument resolution and suggestions.
 */
public class CommandUtil {

    private static Set<Tuple<String, String>> mainSet;
    private static Set<Tuple<String, String>> altSet;

    private CommandUtil() {
    }

    /**
     * Resolves the sender as a player.
     *
     * @return The player, or null after an error was sent to the sender
     */
    @Nullable
    public static Player requirePlayer(CommandSender sender, Lang lang) {
        if (sender instanceof Player player) {
            return player;
        }
        lang.sendEntry(sender, "Error_PlayerCommand");
        return null;
    }

    /**
     * Resolves the brew in the main hand of the sender.
     *
     * @return The brew, or null after an error was sent to the sender
     */
    @Nullable
    public static Brew requireBrewInHand(CommandSender sender, Lang lang) {
        Player player = requirePlayer(sender, lang);
        if (player == null) {
            return null;
        }
        Brew brew = Brew.get(player.getInventory().getItemInMainHand());
        if (brew == null) {
            lang.sendEntry(sender, "Error_ItemNotPotion");
        }
        return brew;
    }

    /**
     * Creates a brew of the given recipe for the given player.
     *
     * @param sender     The sender that executed the command
     * @param recipeName The name or id of the recipe
     * @param quality    The quality of the brew, 1-10, defaults to 10
     * @param playerName The player that receives the brew, defaults to the sender
     * @return The brew and the player it is meant for, or null after an error was sent
     */
    @Nullable
    public static Tuple<Brew, Player> createBrew(CommandSender sender, String recipeName,
                                                 @Nullable Integer quality, @Nullable String playerName, Lang lang) {
        int brewQuality = quality != null ? quality : 10;

        Player player;
        if (playerName == null) {
            player = requirePlayer(sender, lang);
            if (player == null) {
                return null;
            }
        } else {
            player = BreweryPlugin.getInstance().getServer().getPlayer(playerName);
            if (player == null) {
                lang.sendEntry(sender, "Error_NoPlayer", playerName);
                return null;
            }
        }

        BreweryRecipe recipe = BreweryRecipe.getMatching(recipeName);
        if (recipe == null) {
            lang.sendEntry(sender, "Error_NoBrewName", recipeName);
            return null;
        }
        return new Tuple<>(recipe.createBrew(brewQuality), player);
    }

    /**
     * @return Every name and id of every recipe, for tab completion
     */
    public static List<String> recipeNamesAndIds() {
        if (mainSet == null) {
            mainSet = new HashSet<>();
            altSet = new HashSet<>();
            for (BreweryRecipe recipe : BreweryRecipe.getAllRecipes()) {
                mainSet.addAll(createLookupFromName(recipe.getName(5)));

                Set<String> altNames = new HashSet<>(3);
                altNames.add(recipe.getName(1));
                altNames.add(recipe.getName(10));
                if (recipe.getId() != null) { // Leaving a null check JUST in case. But ids are never null in the current implementation
                    altNames.add(recipe.getId());
                }

                for (String altName : altNames) {
                    altSet.addAll(createLookupFromName(altName));
                }
            }
        }

        List<String> options = mainSet.stream().map(Tuple::second).collect(Collectors.toList());
        options.addAll(altSet.stream().map(Tuple::second).toList());
        return options.stream().distinct().toList();
    }

    /**
     * @return The names of all online players, for tab completion
     */
    public static List<String> playerNames() {
        return BreweryPlugin.getInstance().getServer().getOnlinePlayers().stream()
            .map(Player::getName)
            .sorted()
            .toList();
    }

    private static List<Tuple<String, String>> createLookupFromName(final String name) {
        return Arrays.stream(name.split(" "))
            .map(word -> new Tuple<>(word.toLowerCase(), name))
            .collect(Collectors.toList());
    }

    /**
     * Clears the cached recipe names, has to be called after the recipes changed.
     */
    public static void reloadTabCompleter() {
        mainSet = null;
        altSet = null;
    }
}
