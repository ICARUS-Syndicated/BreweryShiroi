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

import com.dre.brewery.mechanics.BreweryPlayer;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.commands.CommandUtil;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.utility.BukkitEffectConstants;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Sets the drunkenness and quality of a player.
 */
public class SetCommand {

    private SetCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("set", "brewery.cmd.set", "Help_Set")
            .required("player", StringParser.stringParser(),
                SuggestionProvider.blockingStrings((context, input) -> CommandUtil.playerNames()))
            .required("drunkenness", IntegerParser.integerParser(0, 100))
            .optional("quality", IntegerParser.integerParser(0, 10))
            .handler(context -> {
                Lang lang = commands.lang();
                CommandSender sender = context.sender().source();
                String playerName = context.get("player").toString();

                Player target = Bukkit.getPlayer(playerName);
                if (target == null) {
                    lang.sendEntry(sender, "Error_NoPlayer", playerName);
                    return;
                }

                int drunkenness = context.get("drunkenness");
                int quality = context.<Integer>optional("quality").orElse(10);

                BreweryPlayer breweryPlayer = BreweryPlayer.get(Bukkit.getOfflinePlayer(target.getUniqueId()));
                if (breweryPlayer == null) {
                    breweryPlayer = BreweryPlayer.addPlayer(Bukkit.getOfflinePlayer(target.getUniqueId()));
                }

                breweryPlayer.setDrunkenness(drunkenness);
                breweryPlayer.setQuality(quality * drunkenness);

                lang.sendEntry(sender, "CMD_Set", playerName, String.valueOf(drunkenness), String.valueOf(quality));

                // Stop long nausea effects when drunkenness is 0
                if (drunkenness == 0) {
                    target.removePotionEffect(BukkitEffectConstants.NAUSEA);
                }
            }));
    }
}
