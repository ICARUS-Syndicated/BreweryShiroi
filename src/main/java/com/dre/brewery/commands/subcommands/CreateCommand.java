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
import com.dre.brewery.utility.Tuple;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Creates a brew of a recipe, optionally with a specific quality and for another player.
 * <p>
 * Also available as {@code /breweryx give}.
 */
public class CreateCommand {

    private CreateCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("create", "brewery.cmd.create", "Help_Create")
            .required("recipe", StringParser.quotedStringParser(),
                SuggestionProvider.blockingStrings((context, input) -> CommandUtil.recipeNamesAndIds()))
            .optional("quality", IntegerParser.integerParser(1, 10))
            .optional("player", StringParser.stringParser(),
                SuggestionProvider.blockingStrings((context, input) -> CommandUtil.playerNames()))
            .handler(context -> run(commands, context.sender().source(),
                context.get("recipe").toString(),
                context.<Integer>optional("quality").orElse(null),
                context.<String>optional("player").orElse(null))));

        commands.manager().command(commands.command("give", "brewery.cmd.create", "Help_Give")
            .required("recipe", StringParser.quotedStringParser(),
                SuggestionProvider.blockingStrings((context, input) -> CommandUtil.recipeNamesAndIds()))
            .optional("quality", IntegerParser.integerParser(1, 10))
            .optional("player", StringParser.stringParser(),
                SuggestionProvider.blockingStrings((context, input) -> CommandUtil.playerNames()))
            .handler(context -> run(commands, context.sender().source(),
                context.get("recipe").toString(),
                context.<Integer>optional("quality").orElse(null),
                context.<String>optional("player").orElse(null))));
    }

    private static void run(BreweryCommandManager commands, CommandSender sender,
                            String recipeName, Integer quality, String playerName) {
        Lang lang = commands.lang();

        Tuple<Brew, Player> brewForPlayer = CommandUtil.createBrew(sender, recipeName, quality, playerName, lang);
        if (brewForPlayer == null) {
            return;
        }

        if (brewForPlayer.b().getInventory().firstEmpty() == -1) {
            lang.sendEntry(sender, "CMD_Copy_Error", "1");
            return;
        }

        ItemStack item = brewForPlayer.a().createItem(null, brewForPlayer.b());
        if (item != null) {
            brewForPlayer.b().getInventory().addItem(item);
            lang.sendEntry(sender, "CMD_Created");
        }
    }
}
