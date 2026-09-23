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
import com.dre.brewery.utility.Logging;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.incendo.cloud.parser.standard.IntegerParser;

/**
 * Distills the brew in the players hand.
 */
public class DistillCommand {

    private DistillCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("distill", "brewery.cmd.create", "Help_Distill")
            .optional("runs", IntegerParser.integerParser(1, 10))
            .handler(context -> {
                Lang lang = commands.lang();
                Player player = CommandUtil.requirePlayer(context.sender().source(), lang);
                if (player == null) {
                    return;
                }
                distill(lang, player, context.<Integer>optional("runs").orElse(1));
            }));
    }

    private static void distill(Lang lang, Player player, int distillRuns) {
        ItemStack item = player.getInventory().getItemInMainHand();
        Brew brew = Brew.get(item);
        if (brew == null) {
            lang.sendEntry(player, "Error_ItemNotPotion");
            return;
        }
        PotionMeta itemMeta = (PotionMeta) item.getItemMeta();

        for (int i = 0; i < distillRuns; i++) {
            brew.distillSlot(item, itemMeta);
        }
        Logging.debugLog(String.format("distill: distilled for %d runs: %s",
            distillRuns, ChatColor.stripColor(brew.toString())));
        player.getInventory().setItemInMainHand(item);
        lang.sendEntry(player, "CMD_Distilled", distillRuns);
    }
}
