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
import com.dre.brewery.commands.CommandUtil;
import com.dre.brewery.configuration.files.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Makes the sender or another player puke.
 */
public class PukeCommand {

    private PukeCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("puke", "brewery.cmd.puke", "Help_Puke")
            .optional("player", StringParser.stringParser(),
                SuggestionProvider.blockingStrings((context, input) -> CommandUtil.playerNames()))
            .optional("amount", IntegerParser.integerParser(1))
            .handler(context -> {
                Lang lang = commands.lang();
                CommandSender sender = context.sender().source();
                String playerName = context.optional("player").map(Object::toString).orElse(null);

                Player player = null;
                if (playerName != null) {
                    player = BreweryPlugin.getInstance().getServer().getPlayer(playerName);
                    if (player == null) {
                        lang.sendEntry(sender, "Error_NoPlayer", playerName);
                        return;
                    }
                }

                if (!(sender instanceof Player) && player == null) {
                    lang.sendEntry(sender, "Error_PlayerCommand");
                    return;
                }

                if (player == null) {
                    player = (Player) sender;
                } else if (!sender.hasPermission("brewery.cmd.pukeOther") && !player.equals(sender)) {
                    lang.sendEntry(sender, "Error_NoPermissions");
                    return;
                }

                int count = context.<Integer>optional("amount").orElse(0);
                if (count <= 0) {
                    count = 20 + (int) (Math.random() * 40);
                }
                BreweryPlayer.addPuke(player, count);
            }));
    }
}
