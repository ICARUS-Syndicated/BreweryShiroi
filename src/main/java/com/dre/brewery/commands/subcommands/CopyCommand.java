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

import com.dre.brewery.Brew;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.commands.CommandUtil;
import com.dre.brewery.configuration.files.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.parser.standard.IntegerParser;

/**
 * Copies the brew in the players hand.
 */
public class CopyCommand {

    private CopyCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("copy", "brewery.cmd.copy", "Help_Copy")
            .optional("count", IntegerParser.integerParser(1, 36))
            .handler(context -> {
                Lang lang = commands.lang();
                CommandSender sender = context.sender().source();
                Player player = CommandUtil.requirePlayer(sender, lang);
                if (player == null) {
                    return;
                }
                copy(lang, player, context.<Integer>optional("count").orElse(1));
            }));
    }

    private static void copy(Lang lang, Player player, int count) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !Brew.isBrew(hand)) {
            lang.sendEntry(player, "Error_ItemNotPotion");
            return;
        }

        while (count > 0) {
            ItemStack item = hand.clone();
            if (!player.getInventory().addItem(item).isEmpty()) {
                lang.sendEntry(player, "CMD_Copy_Error", "" + count);
                return;
            }
            count--;
        }
    }
}
