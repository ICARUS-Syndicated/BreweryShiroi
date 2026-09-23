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

import com.dre.brewery.api.addons.AddonManager;
import com.dre.brewery.api.addons.BreweryAddon;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.releases.ReleaseChecker;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Shows version information and the loaded addons.
 */
public class VersionCommand {

    private VersionCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("version", "brewery.cmd.version", "Help_Version")
            .handler(context -> show(context.sender().source())));
    }

    private static void show(CommandSender sender) {
        StringBuilder addonString = new StringBuilder();

        List<BreweryAddon> addons = List.copyOf(AddonManager.LOADED_ADDONS);
        for (BreweryAddon addon : addons) {
            addonString.append(addon.getClass().getSimpleName());
            if (addons.indexOf(addon) < addons.size() - 1) {
                addonString.append("&f, &a");
            }
        }

        ReleaseChecker rc = ReleaseChecker.getInstance();

        Logging.message(sender, "&2BreweryShiroi version&7: &av" + rc.localVersion() + " &7(Latest: v" + rc.getResolvedLatestVersion() + ")");
        Logging.message(sender, "&2Original authors&7: &aGrafe, TTTheKing, Sn0wStorm");
        Logging.message(sender, "&dBreweryX authors&7: &aJsinco, Mitality, Nadwey, Szarkans, Vutka1");
        Logging.message(sender, "&dBreweryShiroi authors&7: &aPh0sphorW");
        Logging.message(sender, "&2Loaded addons&7: &a" + addonString);
    }
}
