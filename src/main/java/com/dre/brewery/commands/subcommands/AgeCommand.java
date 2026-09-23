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

package com.dre.brewery.commands.subcommands;

import com.dre.brewery.brew.Brew;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.commands.CommandUtil;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.instruments.barrel.BarrelWoodType;
import com.dre.brewery.utility.Logging;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Ages the brew in the players hand in a barrel of the given wood type.
 */
public class AgeCommand {

    private AgeCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("age", "brewery.cmd.create", "Help_Age")
            .required("wood", org.incendo.cloud.parser.standard.StringParser.stringParser(),
                SuggestionProvider.suggestingStrings(BarrelWoodType.TAB_COMPLETIONS))
            .required("time", DoubleParser.doubleParser(0.001))
            .handler(context -> {
                Lang lang = commands.lang();
                Player player = CommandUtil.requirePlayer(context.sender().source(), lang);
                if (player == null) {
                    return;
                }

                String woodName = context.get("wood").toString();
                BarrelWoodType woodType = BarrelWoodType.fromName(woodName);
                if (woodType == null || !woodType.isSpecific()) {
                    lang.sendEntry(player, "CMD_Invalid_Wood_Type", woodName);
                    return;
                }

                double ageTime = context.get("time");
                age(lang, player, woodType, (float) ageTime, ageTime);
            }));
    }

    private static void age(Lang lang, Player player, BarrelWoodType woodType, float ageTime, double rawAgeTime) {
        ItemStack item = player.getInventory().getItemInMainHand();
        Brew brew = Brew.get(item);
        if (brew == null) {
            lang.sendEntry(player, "Error_ItemNotPotion");
            return;
        }

        brew.age(item, ageTime, woodType);
        Logging.debugLog(String.format("age: aged for %s years in %s barrel: %s",
            rawAgeTime, woodType.getFormattedName(), ChatColor.stripColor(brew.toString())));
        lang.sendEntry(player, "CMD_Aged", rawAgeTime);
    }
}
