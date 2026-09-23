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

package com.dre.brewery.listeners;

import com.dre.brewery.instruments.BreweryCauldron;
import com.dre.brewery.mechanics.BreweryPlayer;
import com.dre.brewery.instruments.BrewerySealer;
import com.dre.brewery.instruments.barrel.BreweryBarrel;
import com.dre.brewery.instruments.barrel.BarrelAsset;
import com.dre.brewery.brew.Brew;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.mechanics.DistortChat;
import com.dre.brewery.mechanics.Wakeup;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.utility.utils.BreweryUtil;
import com.dre.brewery.utility.utils.MaterialUtil;
import com.dre.brewery.utility.utils.PermissionUtil;
import com.dre.brewery.utility.releases.ReleaseChecker;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;


public class PlayerListener implements Listener {

    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        handlePlayerInteract(event);
    }

    public static void handlePlayerInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Material type = clickedBlock.getType();

        // -- Clicking an Hopper --
        if (type == Material.HOPPER) {
            if (config.isBrewHopperDump() && player.isSneaking() && event.getHand() == EquipmentSlot.HAND) {
                ItemStack item = event.getItem();
                if (Brew.isBrew(item)) {
                    event.setCancelled(true);
                    BreweryUtil.setItemInHand(event, Material.GLASS_BOTTLE, false);
                    clickedBlock.getWorld().playSound(clickedBlock.getLocation(), Sound.ITEM_BOTTLE_EMPTY, 1f, 1f);
                }
            }
            return;
        }

        // -- Opening a Sealing Table --
        if (BrewerySealer.isBSealer(clickedBlock)) {
            if (player.isSneaking()) {
                event.setUseInteractedBlock(Event.Result.DENY);
                return;
            }
            event.setCancelled(true);
            if (config.isEnableSealingTable()) {
                BrewerySealer sealer = new BrewerySealer(player);
                event.getPlayer().openInventory(sealer.getInventory());
            } else {
                lang.sendEntry(player, "Error_SealingTableDisabled");
            }
            return;
        }

        Material heldItem = event.getItem() != null ? event.getItem().getType() : null;
        if (player.isSneaking() && type != Material.BARREL && !BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, heldItem)) {
            return;
        }

        // -- Interacting with a Cauldron --
        if (MaterialUtil.isWaterCauldron(type) && !player.isSneaking()) {
            // Handle the Cauldron Interact
            // The Event might get cancelled in here
            BreweryCauldron.clickCauldron(event);
            return;
        }

        // -- Opening a Minecraft Barrel --
        if (type == Material.BARREL) {
            if (!player.hasPermission("brewery.openbarrel.mc")) {
                event.setCancelled(true);
                lang.sendEntry(player, "Error_NoPermissions");
            }
            return;
        }

        // Do not process Off Hand for Barrel interaction
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // Allow Waxing and Scraping of copper barrels when sneaking
        if (clickedBlock.getType().name().toUpperCase().contains("CUT_COPPER") && player.isSneaking()) {
            return;
        }

        // -- Access a Barrel --
        BreweryBarrel breweryBarrel = null;
        if (BarrelAsset.isBarrelAsset(BarrelAsset.PLANKS, type)) {
            if (config.isOpenLargeBarrelEverywhere()) {
                breweryBarrel = BreweryBarrel.getByWood(clickedBlock);
            }
        } else if (BarrelAsset.isBarrelAsset(BarrelAsset.STAIRS, type)) {
            breweryBarrel = BreweryBarrel.getByWood(clickedBlock);
            if (breweryBarrel != null) {
                if (!config.isOpenLargeBarrelEverywhere() && breweryBarrel.isLarge()) {
                    breweryBarrel = null;
                }
            }
        } else if (BarrelAsset.isBarrelAsset(BarrelAsset.FENCE, type) || BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, type)) {
            breweryBarrel = BreweryBarrel.getBySpigot(clickedBlock);
        }

        if (breweryBarrel != null) {
            event.setCancelled(true);

            if (!breweryBarrel.hasPermsOpen(player, event)) {
                return;
            }

            breweryBarrel.open(player);

            // When right clicking a normal Block with a potion or any edible item in hand,
            // even when cancelled, the consume animation will continue playing while opening the Barrel inventory.
            // The Animation and sound will play endlessly while the inventory is open, though no item is consumed.
            // This seems to be a client bug.
            // This workaround switches the currently selected slot to another for a short time, it needs to be a slot with a different item in it.
            // This seems to make the client stop animating a consumption
            // If there is a better way to do this please let me know
            Material hand = event.getMaterial();
            if ((hand == Material.POTION || hand.isEdible()) && !BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, type)) {
                PlayerInventory inventory = player.getInventory();
                final int held = inventory.getHeldItemSlot();
                int useSlot = -1;
                for (int i = 0; i < 9; i++) {
                    ItemStack item = inventory.getItem(i);
                    if (item == null || item.getType() == Material.AIR) {
                        useSlot = i;
                        break;
                    } else if (useSlot == -1 && item.getType() != hand) {
                        useSlot = i;
                    }
                }
                if (useSlot != -1) {
                    inventory.setHeldItemSlot(useSlot);
                    BreweryPlugin.getScheduler().runTaskLater(() -> player.getInventory().setHeldItemSlot(held), 2);
                }
            }

            breweryBarrel.playOpeningSound();
        }
    }

    @EventHandler
    public void onClickAir(PlayerInteractEvent event) {
        if (Wakeup.checkPlayer == null) return;

        if (event.getAction() == Action.LEFT_CLICK_AIR) {
            if (!event.hasItem()) {
                if (event.getPlayer() == Wakeup.checkPlayer) {
                    Wakeup.tpNext();
                }
            }
        }
    }

    // player drinks a custom potion
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item.getType() == Material.POTION) {
            Brew brew = Brew.get(item);
            if (brew != null) {
                if (!BreweryPlayer.drink(brew, player, item.getItemMeta(), event)) {
                    event.setCancelled(true);
                    return;
                }
                /*if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    brew.remove(item);
                }*/
                if (player.getGameMode() != GameMode.CREATIVE) {
                    // replace the potion with an empty potion to avoid effects
                    event.setItem(new ItemStack(Material.POTION));
                } else {
                    // Don't replace the item when keeping the potion, just cancel the event
                    event.setCancelled(true);
                }
            }
        } else if (BreweryUtil.getMaterialMap(config.getDrainItems()).containsKey(item.getType())) {
            BreweryPlayer breweryPlayer = BreweryPlayer.get(player);
            if (breweryPlayer != null) {
                breweryPlayer.drainByItem(player, item.getType());
                if (config.isShowStatusOnDrink()) {
                    breweryPlayer.showDrunkeness(player);
                }
            }
        }
    }

    // Player has died! Decrease Drunkeness by 20
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        BreweryPlayer breweryPlayer = BreweryPlayer.get(event.getPlayer());
        if (breweryPlayer == null) {
            return;
        }
        if (breweryPlayer.getDrunkenness() > 20) {
            breweryPlayer.setData(breweryPlayer.getDrunkenness() - 20, 0);
        } else {
            BreweryPlayer.remove(event.getPlayer());
        }
    }

    // player walks while drunk, push him around!
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (BreweryPlayer.hasPlayer(event.getPlayer())) {
            BreweryPlayer.playerMove(event);
        }
    }

    // player talks while drunk, but he cant speak very well
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        DistortChat.playerChat(event);
    }

    // player commands while drunk, distort chat commands
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
        DistortChat.playerCommand(event);
    }

    // player joins while passed out
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        Player player = event.getPlayer();
        BreweryPlayer breweryPlayer = BreweryPlayer.get(player);
        if (breweryPlayer == null) {
            return;
        }
        if (player.hasPermission("brewery.bypass.logindeny")) {
            if (breweryPlayer.getDrunkenness() > 100) {
                breweryPlayer.setData(100, 0);
            }
            return;
        }
        switch (breweryPlayer.canJoin()) {
            case 2 -> event.disallow(PlayerLoginEvent.Result.KICK_OTHER, lang.getEntry("Player_LoginDeny"));
            case 3 -> event.disallow(PlayerLoginEvent.Result.KICK_OTHER, lang.getEntry("Player_LoginDenyLong"));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        BreweryPlayer breweryPlayer = BreweryPlayer.get(event.getPlayer());
        if (breweryPlayer != null) {
            breweryPlayer.join(event.getPlayer());
        }
        ReleaseChecker.getInstance().notify(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BreweryPlayer breweryPlayer = BreweryPlayer.get(event.getPlayer());
        if (breweryPlayer != null) {
            breweryPlayer.disconnecting();
        }
        PermissionUtil.logout(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        BreweryPlayer breweryPlayer = BreweryPlayer.get(event.getPlayer());
        if (breweryPlayer != null) {
            breweryPlayer.disconnecting();
        }
        PermissionUtil.logout(event.getPlayer());
    }
}
