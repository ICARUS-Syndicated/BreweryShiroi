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

import com.dre.brewery.BreweryPlayer;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.configuration.files.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Shows the drunkenness of a player, either your own or, with permission, of somebody else.
 */
public class InfoCommand {

    private InfoCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("info", "brewery.cmd.info", "Help_Info")
            .optional("player", StringParser.stringParser(), SuggestionProvider.blockingStrings(
                (context, input) -> BreweryPlugin.getInstance().getServer().getOnlinePlayers().stream()
                    .map(Player::getName).toList()))
            .handler(context -> {
                Lang lang = commands.lang();
                CommandSender sender = context.sender().source();
                String playerName = context.optional("player").map(Object::toString).orElse(null);

                if (playerName == null) {
                    if (!(sender instanceof Player player)) {
                        lang.sendEntry(sender, "Error_PlayerCommand");
                        return;
                    }
                    playerName = player.getName();
                } else if (!sender.hasPermission("brewery.cmd.infoOther")) {
                    lang.sendEntry(sender, "Error_NoPermissions");
                    return;
                }
                showInfo(sender, playerName, lang);
            }));
    }

    private static void showInfo(CommandSender sender, String playerName, Lang lang) {
        boolean selfInfo = sender instanceof Player player && player.getName().equals(playerName);

        Player player = BreweryPlugin.getInstance().getServer().getPlayerExact(playerName);
        BreweryPlayer breweryPlayer;
        if (player == null) {
            breweryPlayer = BreweryPlayer.getByName(playerName);
        } else {
            breweryPlayer = BreweryPlayer.get(player);
        }

        if (breweryPlayer == null) {
            lang.sendEntry(sender, "CMD_Info_NotDrunk", playerName);
        } else if (selfInfo) {
            breweryPlayer.showDrunkeness((Player) sender);
        } else {
            lang.sendEntry(sender, "CMD_Info_Drunk", playerName,
                "" + breweryPlayer.getDrunkeness(), "" + breweryPlayer.getQuality());
        }
    }
}
