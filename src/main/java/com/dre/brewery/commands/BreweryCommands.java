/*
 * BreweryShiroi Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2026 Ph0sphorW
 *
 * This file is part of BreweryShiroi.
 *
 * BreweryShiroi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryShiroi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryShiroi. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.commands;

import com.dre.brewery.commands.subcommands.AgeCommand;
import com.dre.brewery.commands.subcommands.CopyCommand;
import com.dre.brewery.commands.subcommands.CreateCommand;
import com.dre.brewery.commands.subcommands.DataManagerCommand;
import com.dre.brewery.commands.subcommands.DebugInfoCommand;
import com.dre.brewery.commands.subcommands.DeleteCommand;
import com.dre.brewery.commands.subcommands.DistillCommand;
import com.dre.brewery.commands.subcommands.DrinkCommand;
import com.dre.brewery.commands.subcommands.InfoCommand;
import com.dre.brewery.commands.subcommands.ItemName;
import com.dre.brewery.commands.subcommands.PukeCommand;
import com.dre.brewery.commands.subcommands.ReloadAddonsCommand;
import com.dre.brewery.commands.subcommands.ReloadCommand;
import com.dre.brewery.commands.subcommands.SealCommand;
import com.dre.brewery.commands.subcommands.SetCommand;
import com.dre.brewery.commands.subcommands.ShowStatsCommand;
import com.dre.brewery.commands.subcommands.SimulateCommand;
import com.dre.brewery.commands.subcommands.StaticCommand;
import com.dre.brewery.commands.subcommands.UnLabelCommand;
import com.dre.brewery.commands.subcommands.VersionCommand;
import com.dre.brewery.commands.subcommands.WakeupCommand;

/**
 * Registers every built-in command of BreweryShiroi.
 * <p>
 * The help command is registered by {@link BreweryCommandManager} itself, as it is backed by the help screen.
 */
public final class BreweryCommands {

    private BreweryCommands() {
    }

    public static void registerAll(BreweryCommandManager commands) {
        // Player commands
        ItemName.register(commands);
        InfoCommand.register(commands);
        SealCommand.register(commands);
        CopyCommand.register(commands);
        DeleteCommand.register(commands);
        StaticCommand.register(commands);
        UnLabelCommand.register(commands);
        DrinkCommand.register(commands);
        PukeCommand.register(commands);
        ShowStatsCommand.register(commands);

        // Brewing commands
        CreateCommand.register(commands);
        DistillCommand.register(commands);
        AgeCommand.register(commands);
        SimulateCommand.register(commands);
        DebugInfoCommand.register(commands);

        // Admin commands
        ReloadCommand.register(commands);
        ReloadAddonsCommand.register(commands);
        DataManagerCommand.register(commands);
        WakeupCommand.register(commands);
        SetCommand.register(commands);
        VersionCommand.register(commands);
    }
}
