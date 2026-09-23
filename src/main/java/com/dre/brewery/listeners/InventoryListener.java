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

import com.dre.brewery.instruments.BreweryDistiller;
import com.dre.brewery.instruments.BrewerySealer;
import com.dre.brewery.instruments.barrel.BreweryBarrel;
import com.dre.brewery.brew.Brew;
import com.dre.brewery.instruments.barrel.VanillaBarrel;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.lore.BrewLore;
import com.dre.brewery.utility.Logging;
import io.papermc.lib.PaperLib;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class InventoryListener implements Listener {

    private final Config config = ConfigManager.getConfig(Config.class);
    private static final Set<InventoryAction> CLICKED_INVENTORY_ITEM_MOVE = Set.of(InventoryAction.PLACE_SOME,
        InventoryAction.PLACE_ONE, InventoryAction.PLACE_ALL, InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
        InventoryAction.PICKUP_SOME, InventoryAction.PICKUP_ONE);
    private static final Set<String> BANNED_ACTIONS = Set.of("PICKUP_ALL_INTO_BUNDLE", "PICKUP_FROM_BUNDLE",
        "PICKUP_SOME_INTO_BUNDLE", "PLACE_ALL_INTO_BUNDLE", "PLACE_SOME_INTO_BUNDLE");

    /* === Recreating manually the prior BrewEvent behavior. === */
    private final Set<UUID> trackedBrewmen = new HashSet<>();


    // Helper: checks if an item is a valid Brewery brew
    private boolean isBrewItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getItemMeta() instanceof PotionMeta potionMeta) {
            return Brew.get(potionMeta) != null;
        }
        return false;
    }

    /**
     * Start tracking distillation for a person when they open the brewer window.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewerOpen(InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof BrewerInventory)) return;

        Logging.debugLog("Starting brew inventory tracking");
        trackedBrewmen.add(event.getPlayer().getUniqueId());
    }

    /**
     * Stop tracking distillation for a person when they close the brewer window.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewerClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof BrewerInventory)) return;

        Logging.debugLog("Stopping brew inventory tracking");
        trackedBrewmen.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewerDrag(InventoryDragEvent event) {
        // Workaround the Drag event when only clicking a slot
        if (event.getInventory() instanceof BrewerInventory) {
            onBrewerClick(new InventoryClickEvent(event.getView(), InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PLACE_ALL));
        }
    }

    /**
     * Clicking can either start or stop the new brew distillation tracking.
     * <p>Note that server restart will halt any ongoing brewing processes and
     * they will _not_ restart until a new click event.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewerClick(InventoryClickEvent event) {
        HumanEntity player = event.getWhoClicked();
        Inventory inventory = event.getInventory();
        if (!(inventory instanceof BrewerInventory)) return;

        UUID puid = player.getUniqueId();
        if (!trackedBrewmen.contains(puid)) return;

        if (InventoryType.BREWING != inventory.getType()) return;
        if (event.getAction() == InventoryAction.NOTHING) return; // Ignore clicks that do nothing

        BreweryDistiller.distillerClick(event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (BreweryDistiller.hasBrew(event.getContents(), BreweryDistiller.getDistillContents(event.getContents())) != 0) {
            event.setCancelled(true);
        }
    }

    /**
     * Migrates brews that were created before Minecraft 1.11 onto the modern potion colour system.
     * <p>
     * Pre-1.11 brews carry no colour, so their colour has to be derived from the recipe once they are seen again.
     * This stays in place because the affected items may still exist in player inventories or container data.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInventoryClickLow(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.POTION) {
            return;
        }
        if (!(item.getItemMeta() instanceof PotionMeta potion) || potion.getColor() != null) {
            return;
        }
        Brew brew = Brew.get(potion);
        if (brew != null) {
            brew.convertPre1_11(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = PaperLib.getHolder(event.getInventory(), true).getHolder();
        boolean isVanillaBarrel = holder instanceof Barrel;
        if (isVanillaBarrel && config.isExemptVanillaBarrels()) {
            return;
        }
        if (!(holder instanceof BreweryBarrel) && !isVanillaBarrel) {
            return;
        }
        InventoryAction action = event.getAction();
        if (action == InventoryAction.NOTHING) {
            return;
        }
        boolean upperInventoryIsClicked = event.getClickedInventory() == event.getInventory();
        if (!upperInventoryIsClicked && CLICKED_INVENTORY_ITEM_MOVE.contains(action)) {
            return;
        }
        ItemStack hoveredItem = event.getCurrentItem();
        Stream<ItemStack> relatedItems;
        if (upperInventoryIsClicked && hoveredItem != null) {
            if (hoveredItem.getItemMeta() instanceof PotionMeta potionMeta) {
                Brew brew = Brew.get(potionMeta);
                if (brew != null) {
                    BrewLore lore = new BrewLore(brew, potionMeta);
                    if (BrewLore.hasColorLore(potionMeta)) {
                        lore.convertLore(false);
                        lore.write();
                    } else if (!config.isAlwaysShowAlc() && event.getInventory().getType() == InventoryType.BREWING) {
                        lore.updateAlc(false);
                        lore.write();
                    }
                    hoveredItem.setItemMeta(potionMeta);
                }
            }
        }
        if (!config.isOnlyAllowBrewsInBarrels()) {
            return;
        }
        if (BANNED_ACTIONS.contains(action.name())) {
            event.setResult(Event.Result.DENY);
            return;
        }
        InventoryView view = event.getView();
        // getHotbarButton also returns -1 for offhand clicks
        ItemStack hotbarItem = event.getHotbarButton() == -1 ?
            (event.getClick() == ClickType.SWAP_OFFHAND
                ? event.getWhoClicked().getInventory().getItemInOffHand()
                : null)
            : view.getBottomInventory().getItem(event.getHotbarButton());
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // player takes something out
            if (upperInventoryIsClicked && hotbarItem == null) {
                return;
            }
            relatedItems = Stream.of(hotbarItem, hoveredItem);
        } else if (action == InventoryAction.HOTBAR_SWAP) {
            // barrel not involved
            if (!upperInventoryIsClicked) {
                return;
            }
            relatedItems = Stream.of(hotbarItem, hoveredItem);
        } else {
            ItemStack cursor = event.getCursor();
            relatedItems = Stream.of(cursor);
        }
        Stream<ItemStack> itemsToCheck = relatedItems
            .filter(Objects::nonNull)
            .filter(item -> !item.getType().isAir());
        if (itemsToCheck.anyMatch(item -> !isBrewItem(item))) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        Inventory topInventory = view.getTopInventory();
        InventoryHolder holder = PaperLib.getHolder(topInventory, true).getHolder();
        boolean isVanillaBarrel = holder instanceof Barrel;
        if (isVanillaBarrel && config.isExemptVanillaBarrels()) {
            return;
        }
        if (!(holder instanceof BreweryBarrel) && !isVanillaBarrel) {
            return;
        }

        int topSize = topInventory.getSize();
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            int rawSlot = entry.getKey();
            if (rawSlot < topSize) {
                ItemStack item = entry.getValue();
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                if (!isBrewItem(item)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }


    // Check if the player tries to add more than the allowed amount of brews into an mc-barrel
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClickMCBarrel(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.BARREL) return;
        if (!config.isAgeInMCBarrels()) return;

        Inventory inventory = event.getInventory();
        VanillaBarrel barrel = VanillaBarrel.openBarrels.computeIfAbsent(inventory, VanillaBarrel::new);
        barrel.clickInv(event);
    }

    // Handle the Brew Sealer Inventory
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClickBSealer(InventoryClickEvent event) {
        InventoryHolder holder = PaperLib.getHolder(event.getInventory(), true).getHolder();
        if (!(holder instanceof BrewerySealer sealer)) {
            return;
        }
        sealer.clickInv();
    }

    //public static boolean opening = false;

    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = false)
    public void onInventoryOpenLegacyConvert(InventoryOpenEvent event) {
        if (Brew.noLegacy()) {
            return;
        }
        if (event.getInventory().getType() == InventoryType.PLAYER) {
            return;
        }
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && item.getType() == Material.POTION) {
                int uniqueId = Brew.getUID(item);
                // Check if the unique id exists first, otherwise it will log that it can't find the id
                if (uniqueId < 0 && Brew.legacyPotions.containsKey(uniqueId)) {
                    // This will convert the Brew
                    Brew.get(item);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!config.isAgeInMCBarrels()) return;

        // Check for MC Barrel
        if (event.getInventory().getType() == InventoryType.BARREL) {
            Inventory inventory = event.getInventory();
            VanillaBarrel barrel = VanillaBarrel.openBarrels.computeIfAbsent(inventory, VanillaBarrel::new);
            barrel.open();
        }
    }

    // block the pickup of items where getPickupDelay is > 1000 (puke)
    @EventHandler(ignoreCancelled = true)
    public void onHopperPickupPuke(InventoryPickupItemEvent event) {
        if (event.getItem().getPickupDelay() > 1000 && config.getPukeItem().contains(event.getItem().getItemStack().getType())) {
            event.setCancelled(true);
        }
    }

    // Block taking out items from running distillers,
    // Convert Color Lore from MC Barrels back into normal color on taking out
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (event.getSource() instanceof BrewerInventory inventory && PaperLib.getHolder(inventory, true).getHolder() instanceof BrewingStand holder) {
            if (BreweryDistiller.isTrackingDistiller(holder.getBlock())) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getSource().getType() == InventoryType.BARREL) {
            ItemStack item = event.getItem();
            if (item.getType() == Material.POTION && Brew.isBrew(item)) {
                PotionMeta itemMeta = (PotionMeta) item.getItemMeta();
                assert itemMeta != null;
                if (BrewLore.hasColorLore(itemMeta)) {
                    // has color lore, convert lore back to normal
                    Brew brew = Brew.get(itemMeta);
                    if (brew != null) {
                        BrewLore lore = new BrewLore(brew, itemMeta);
                        lore.convertLore(false);
                        lore.write();
                        item.setItemMeta(itemMeta);
                        event.setItem(item);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (PaperLib.getHolder(event.getInventory(), true).getHolder() instanceof BrewerySealer holder) {
            holder.closeInv();
        }

        // Barrel Closing Sound
        if (PaperLib.getHolder(event.getInventory(), true).getHolder() instanceof BreweryBarrel breweryBarrel) {
            breweryBarrel.playClosingSound();
        }

        // Check for MC Barrel
        if (config.isAgeInMCBarrels() && event.getInventory().getType() == InventoryType.BARREL) {
            Inventory inventory = event.getInventory();
            VanillaBarrel barrel = VanillaBarrel.openBarrels.get(inventory);
            if (barrel != null) {
                barrel.close();
                if (inventory.getViewers().size() == 1) {
                    // Last viewer, remove Barrel from open Barrel tracking
                    VanillaBarrel.openBarrels.remove(inventory, barrel);
                }
                return;
            }
            new VanillaBarrel(inventory).close();
        }
    }
}
