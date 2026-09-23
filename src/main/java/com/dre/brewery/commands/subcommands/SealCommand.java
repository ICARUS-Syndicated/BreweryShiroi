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

import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.commands.CommandUtil;
import com.dre.brewery.instruments.BrewerySealer;
import org.bukkit.entity.Player;

/**
 * Opens the Brew Sealer.
 */
public class SealCommand {

    private SealCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("seal", "brewery.cmd.seal", "Help_Seal")
            .handler(context -> {
                Player player = CommandUtil.requirePlayer(context.sender().source(), commands.lang());
                if (player == null) {
                    return;
                }
                player.openInventory(new BrewerySealer(player).getInventory());
            }));
    }
}
