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

import com.dre.brewery.Wakeup;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.configuration.files.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.World;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.List;

/**
 * Manages the wakeup points, which are the places a player may wake up at after drinking too much.
 */
public class WakeupCommand {

    private static final String PERMISSION = "brewery.cmd.wakeup";

    private WakeupCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("wakeup", PERMISSION, "Help_Wakeup")
            .literal("add")
            .handler(context -> Wakeup.set(context.sender().source())));

        commands.manager().command(commands.command("wakeup", PERMISSION, "Help_WakeupList")
            .literal("list")
            .optional("page", IntegerParser.integerParser(1))
            .optional("world", StringParser.stringParser(),
                SuggestionProvider.blockingStrings((context, input) -> worldNames()))
            .handler(context -> Wakeup.list(context.sender().source(),
                context.<Integer>optional("page").orElse(1),
                context.<String>optional("world").orElse(null))));

        commands.manager().command(commands.command("wakeup", PERMISSION, "Help_WakeupRemove")
            .literal("remove")
            .required("id", IntegerParser.integerParser(0))
            .handler(context -> Wakeup.remove(context.sender().source(), context.get("id"))));

        commands.manager().command(commands.command("wakeup", PERMISSION, "Help_WakeupCheck")
            .literal("check")
            .optional("id", IntegerParser.integerParser(0))
            .handler(context -> context.<Integer>optional("id").ifPresentOrElse(
                id -> Wakeup.check(context.sender().source(), id, false),
                () -> Wakeup.check(context.sender().source(), -1, true))));

        commands.manager().command(commands.command("wakeup", PERMISSION, "Help_WakeupCheckSpecific")
            .literal("cancel")
            .handler(context -> Wakeup.cancel(context.sender().source())));

        // Without a subcommand the usage of the wakeup command is shown
        commands.manager().command(commands.command("wakeup", PERMISSION, "Help_Wakeup")
            .handler(context -> showUsage(context.sender().source(), commands.lang())));
    }

    private static void showUsage(CommandSender sender, Lang lang) {
        lang.sendEntry(sender, "Etc_Usage");
        lang.sendEntry(sender, "Help_Wakeup");
        lang.sendEntry(sender, "Help_WakeupList");
        lang.sendEntry(sender, "Help_WakeupCheck");
        lang.sendEntry(sender, "Help_WakeupCheckSpecific");
        lang.sendEntry(sender, "Help_WakeupAdd");
        lang.sendEntry(sender, "Help_WakeupRemove");
    }

    private static List<String> worldNames() {
        return org.bukkit.Bukkit.getWorlds().stream().map(World::getName).sorted().toList();
    }
}
