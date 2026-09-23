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
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.mechanics.Wakeup;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.instruments.BreweryCauldron;
import com.dre.brewery.instruments.barrel.BreweryBarrel;
import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.utility.Logging;
import org.bukkit.command.CommandSender;

/**
 * Prints a few statistics about the running server.
 */
public class ShowStatsCommand {

    private ShowStatsCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("showstats", "brewery.cmd.showstats", "Help_ShowStats")
            .handler(context -> show(context.sender().source())));
    }

    private static void show(CommandSender sender) {
        Logging.message(sender, "Drunk Players: " + BreweryPlayer.numDrunkPlayers());
        Logging.message(sender, "Brews created: " + BreweryPlugin.getInstance().getBreweryStats().brewsCreated);
        Logging.message(sender, "Barrels built: " + BreweryBarrel.getAllBarrels().size());
        Logging.message(sender, "Cauldrons boiling: " + BreweryCauldron.breweryCauldrons.size());
        Logging.message(sender, "Number of Recipes: " + BreweryRecipe.getAllRecipes().size());
        Logging.message(sender, "Wakeups: " + Wakeup.wakeups.size());
    }
}
