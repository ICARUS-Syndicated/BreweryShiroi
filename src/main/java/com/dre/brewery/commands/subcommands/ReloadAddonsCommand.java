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
import com.dre.brewery.api.addons.AddonManager;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.utility.Logging;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Reloads all addons, which requires an explicit confirmation as it can break addons.
 */
public class ReloadAddonsCommand {

    private ReloadAddonsCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("reloadaddons", "brewery.cmd.reloadaddons", "Help_ReloadAddons")
            .optional("confirmation", StringParser.stringParser(),
                SuggestionProvider.suggestingStrings("confirm"))
            .handler(context -> {
                CommandSender sender = context.sender().source();
                String confirmation = context.optional("confirmation").map(Object::toString).orElse("");

                if (!confirmation.equalsIgnoreCase("confirm")) {
                    Logging.msg(sender, "&rThis command should be avoided as it can cause unpredictable behavior within addons, use &6/brewery reloadaddons confirm &r to confirm.");
                    Logging.msg(sender, "&aMost addons support reloading without using this command! Try using &6/brewery reload &ainstead.");
                    return;
                }

                AddonManager addonManager = BreweryPlugin.getAddonManager();
                addonManager.unloadAddons();
                addonManager.loadAddons();
                addonManager.enableAddons();
                Logging.msg(sender, "Finished loading " + addonManager.getAddons().size() + " addon(s)");
                Logging.msg(sender, "&eUsing this command should be avoided as it can cause unpredictable behavior within addons!");
            }));
    }
}
