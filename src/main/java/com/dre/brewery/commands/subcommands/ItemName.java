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

import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.utility.MinecraftVersion;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Prints the material name of the item in the players hand, for use in the config.
 */
public class ItemName {

    private ItemName() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("itemName", "brewery.cmd.itemname", "Help_ItemName")
            .handler(context -> {
                CommandSender sender = context.sender().source();
                if (!(sender instanceof Player player)) {
                    commands.lang().sendEntry(sender, "Error_PlayerCommand");
                    return;
                }
                Lang lang = commands.lang();
                @SuppressWarnings("deprecation")
                ItemStack hand = BreweryPlugin.getMCVersion().isOrLater(MinecraftVersion.V1_9)
                    ? player.getInventory().getItemInMainHand()
                    : player.getItemInHand();
                if (hand != null) {
                    lang.sendEntry(sender, "CMD_Configname", hand.getType().name().toLowerCase(Locale.ENGLISH));
                } else {
                    lang.sendEntry(sender, "CMD_Configname_Error");
                }
            }));
    }
}
