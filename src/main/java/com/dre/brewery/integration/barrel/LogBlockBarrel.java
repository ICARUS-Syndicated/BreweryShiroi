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

package com.dre.brewery.integration.barrel;

import de.diddiz.LogBlock.Actor;
import de.diddiz.LogBlock.Consumer;
import de.diddiz.LogBlock.LogBlock;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static de.diddiz.LogBlock.config.Config.isLogging;
import static de.diddiz.util.BukkitUtils.compareInventories;
import static de.diddiz.util.BukkitUtils.compressInventory;

public class LogBlockBarrel {

    private static final List<LogBlockBarrel> opened = new ArrayList<>();

    public static final Consumer consumer = LogBlock.getInstance().getConsumer();

    private final HumanEntity player;
    private final ItemStack[] items;
    private final Location location;

    public LogBlockBarrel(HumanEntity player, ItemStack[] items, Location location) {
        this.player = player;
        this.items = items;
        this.location = location;
        opened.add(this);
    }

    private void compareInv(ItemStack[] after) {
        if (consumer == null) {
            return;
        }
        for (ItemStack item : compareInventories(items, after)) {
            ItemStack logged = item;
            if (item.getAmount() < 0) {
                logged = item.clone();
                logged.setAmount(Math.abs(item.getAmount()));
            }
            consumer.queueChestAccess(Actor.actorFromEntity(player), location, location.getBlock().getBlockData(), logged, item.getAmount() < 0);
        }
    }

    public static LogBlockBarrel get(HumanEntity player) {
        return opened.stream()
            .filter(open -> open.player.equals(player))
            .findFirst()
            .orElse(null);
    }

    public static void openBarrel(HumanEntity player, Inventory inventory, Location spigotLoc) {
        if (!isLogging(player.getWorld(), de.diddiz.LogBlock.Logging.CHESTACCESS)) return;
        new LogBlockBarrel(player, compressInventory(inventory.getContents()), spigotLoc);
    }

    public static void closeBarrel(HumanEntity player, Inventory inventory) {
        if (!isLogging(player.getWorld(), de.diddiz.LogBlock.Logging.CHESTACCESS)) return;
        LogBlockBarrel open = get(player);
        if (open != null) {
            open.compareInv(compressInventory(inventory.getContents()));
            opened.remove(open);
        }
    }

    public static void breakBarrel(Player player, ItemStack[] contents, Location location) {
        if (consumer == null || !isLogging(location.getWorld(), de.diddiz.LogBlock.Logging.CHESTACCESS)) {
            return;
        }
        for (ItemStack item : compressInventory(contents)) {
            consumer.queueChestAccess(Actor.actorFromEntity(player), location, location.getBlock().getBlockData(), item, false);
        }
    }

    public static void clear() {
        opened.clear();
    }
}
