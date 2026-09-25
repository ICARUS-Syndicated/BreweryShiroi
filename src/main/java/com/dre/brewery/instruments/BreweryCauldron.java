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

package com.dre.brewery.instruments;

import com.dre.brewery.brew.BreweryIngredients;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.api.events.IngredientAddEvent;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.recipe.BreweryCauldronRecipe;
import com.dre.brewery.recipe.items.RecipeItem;
import com.dre.brewery.utility.utils.BreweryUtil;
import com.dre.brewery.utility.BukkitEffectConstants;
import com.dre.brewery.utility.utils.MaterialUtil;
import com.dre.brewery.utility.MinecraftVersion;
import com.dre.brewery.utility.BinaryTuple;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Setter
public class BreweryCauldron {

    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);
    public static final int PARTICLE_PAUSE = 15;
    private static final Set<UUID> playerInteracted = new HashSet<>(); // Interact Event helper
    @Getter
    public static Map<Block, BreweryCauldron> breweryCauldrons = new ConcurrentHashMap<>(); // All active cauldrons. Mapped to their block for fast retrieve

    private BreweryIngredients ingredients = new BreweryIngredients();
    private final Block block;
    private int cookingTime = 0;
    private boolean changed = false; // Not really needed anymore
    private BreweryCauldronRecipe particleRecipe; // null if we haven't checked, empty if there is none
    private Color particleColor;
    private final Location particleLocation;
    private final UUID id;
    private MyScheduledTask foliaParticleTask;

    public BreweryCauldron(Block block) {
        this.block = block;
        this.particleLocation = block.getLocation().add(0.5, 0.9, 0.5);
        this.id = UUID.randomUUID();
    }

    // loading from file
    public BreweryCauldron(Block block, BreweryIngredients ingredients, int cookingTime, UUID id) {
        this.block = block;
        this.cookingTime = cookingTime;
        this.ingredients = ingredients;
        particleLocation = block.getLocation().add(0.5, 0.9, 0.5);
        this.id = id;
    }

    /**
     * Updates this Cauldron, increasing the cook time and checking for heat source
     *
     * @return false if Cauldron needs to be removed
     */
    public boolean onUpdate() {
        // add a minute to cooking time
        if (!BreweryUtil.isChunkLoaded(block)) {
            increaseCookingTime();
        } else {
            if (!MaterialUtil.isWaterCauldron(block.getType())) {
                // Catch any WorldEdit etc. removal
                return false;
            }
            // Check if fire still alive
            if (MaterialUtil.isCauldronHeatSource(block.getRelative(BlockFace.DOWN))) {
                increaseCookingTime();
            }
        }
        return true;
    }

    /**
     * Will add a minute to the cooking time
     */
    public void increaseCookingTime() {
        cookingTime++;
        if (changed) {
            ingredients = ingredients.copy();
            changed = false;
        }
        particleColor = null;
    }

    // add an ingredient to the cauldron
    public void add(ItemStack ingredient, RecipeItem rItem) {
        if (ingredient == null || ingredient.getType() == Material.AIR) return;
        if (changed) {
            ingredients = ingredients.copy();
            changed = false;
        }

        particleRecipe = null;
        particleColor = null;
        ingredients.add(ingredient, rItem);
        block.getWorld().playEffect(block.getLocation(), Effect.EXTINGUISH, 0);
        if (cookingTime > 0) {
            cookingTime--;
        }
        if (config.isEnableCauldronParticles() && !config.isMinimalParticles()) {
            // Few little sparks and lots of water splashes. Offset by 0.2 in x and z
            block.getWorld().spawnParticle(BukkitEffectConstants.INSTANT_EFFECT, particleLocation, 2, 0.2, 0, 0.2, BukkitEffectConstants.instantEffectData(Color.WHITE, 1f));
            block.getWorld().spawnParticle(BukkitEffectConstants.SPLASH, particleLocation, 10, 0.2, 0, 0.2);
        }
    }


    // get cauldron by Block
    @Nullable
    public static BreweryCauldron get(Block block) {
        return breweryCauldrons.get(block);
    }

    // get cauldron from block and add given ingredient
    // Calls the IngredientAddEvent and may be canceled or changed
    public static boolean ingredientAdd(Block block, ItemStack ingredient, Player player) {
        // if not empty
        if (MaterialUtil.getFillLevel(block) != MaterialUtil.EMPTY) {

            if (!BreweryCauldronRecipe.acceptedMaterials.contains(ingredient.getType()) && !ingredient.hasItemMeta()) {
                // Extremely fast way to check for most items
                return false;
            }
            // If the Item is on the list, or customized, we have to do more checks
            RecipeItem rItem = RecipeItem.getMatchingRecipeItem(ingredient, false);
            if (rItem == null) {
                return false;
            }

            BreweryCauldron breweryCauldron = get(block);
            if (breweryCauldron == null) {
                breweryCauldron = new BreweryCauldron(block);
                BreweryCauldron.breweryCauldrons.put(block, breweryCauldron);
                breweryCauldron.startFoliaParticleTask();
            }

            IngredientAddEvent event = new IngredientAddEvent(player, block, breweryCauldron, ingredient.clone(), rItem);
            BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                breweryCauldron.add(event.getIngredient(), event.getRecipeItem());
                player.swingMainHand();
                //P.p.debugLog("Cauldron add: t2 " + ((t2 - t1) / 1000) + " t3: " + ((t3 - t2) / 1000) + " t4: " + ((t4 - t3) / 1000) + " t5: " + ((t5 - t4) / 1000) + "µs");
                return event.willTakeItem();
            } else {
                return false;
            }
        }
        return false;
    }

    // fills players bottle with cooked brew
    public boolean fill(Player player, Block block) {
        if (!player.hasPermission("brewery.cauldron.fill")) {
            lang.sendEntry(player, "Perms_NoCauldronFill");
            return true;
        }
        // Recipes may demand a specific fire, and only the cauldron knows what it is sitting on
        ingredients.setHeatSource(BreweryHeatSource.of(block.getRelative(BlockFace.DOWN)));
        ItemStack potion = ingredients.cook(cookingTime, player);
        if (potion == null) return false;

        BlockData data = block.getBlockData();
        if (!(data instanceof Levelled cauldron)) {
            remove(block);
            return false;
        }
        if (cauldron.getLevel() <= 0) {
            remove(block);
            return false;
        }

        // On the last level the cauldron is emptied completely instead of lowering it
        if (cauldron.getLevel() == 1) {
            block.setType(Material.CAULDRON);
            remove(block);
        } else {
            cauldron.setLevel(cauldron.getLevel() - 1);

            // Update the new Level to the Block
            // We have to use the BlockData variable "data" here instead of the cast "cauldron"
            block.setBlockData(data);

            if (cauldron.getLevel() <= 0) {
                remove(block);
            } else {
                changed = true;
            }
        }
        block.getWorld().playSound(block.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1f);
        // Bukkit Bug, inventory not updating while in event so this
        // will delay the give
        // but could also just use deprecated updateInventory()
        giveItem(player, potion);
        // player.getInventory().addItem(potion);
        // player.getInventory().updateInventory();
        return true;
    }

    // prints the current cooking time to the player
    public static void printTime(Player player, Block block) {
        if (!player.hasPermission("brewery.cauldron.time")) {
            lang.sendEntry(player, "Error_NoPermissions");
            return;
        }
        BreweryCauldron breweryCauldron = get(block);
        if (breweryCauldron != null) {
            if (breweryCauldron.cookingTime >= 1) {
                lang.sendEntry(player, "Player_CauldronInfo1", "" + breweryCauldron.cookingTime);
            } else {
                lang.sendEntry(player, "Player_CauldronInfo2");
            }
        }
    }

    public void cookEffect() {
        assert !MinecraftVersion.isFolia() || BreweryPlugin.getScheduler().isRegionThread(block.getLocation())
            : "cookEffect must run on owning region thread";
        if (!BreweryUtil.isChunkLoaded(block) || !MaterialUtil.isCauldronHeatSource(block.getRelative(BlockFace.DOWN))) {
            return;
        }
        Color color = getParticleColor();
        // Colorable spirally spell, 0 count enables color instead of the offset variables
        block.getWorld().spawnParticle(BukkitEffectConstants.ENTITY_EFFECT, getRandParticleLoc(), 0, color);

        if (config.isMinimalParticles()) {
            return;
        }
        if (ThreadLocalRandom.current().nextFloat() > 0.85f) {
            // Dark pixely smoke cloud at 0.4 random in x and z
            // 0 count enables direction, send to y = 1 with speed 0.09
            block.getWorld().spawnParticle(BukkitEffectConstants.LARGE_SMOKE, getRandParticleLoc(), 0, 0, 1, 0, 0.09);
        }
        if (ThreadLocalRandom.current().nextFloat() > 0.2f) {
            // A Water Splash with 0.2 offset in x and z
            block.getWorld().spawnParticle(BukkitEffectConstants.SPLASH, particleLocation, 1, 0.2, 0, 0.2);
        }
        if (ThreadLocalRandom.current().nextFloat() > 0.4f) {
            // Two hovering pixely dust clouds, a bit of offset and with DustOptions to give some color and size
            block.getWorld().spawnParticle(BukkitEffectConstants.DUST, particleLocation, 2, 0.15, 0.2, 0.15, new Particle.DustOptions(color, 1.5f));
        }
    }

    private Location getRandParticleLoc() {
        return new Location(particleLocation.getWorld(),
            particleLocation.getX() + (ThreadLocalRandom.current().nextDouble() * 0.8) - 0.4,
            particleLocation.getY(),
            particleLocation.getZ() + (ThreadLocalRandom.current().nextDouble() * 0.8) - 0.4);
    }

    /**
     * Get or calculate the particle color from the current best Cauldron Recipe
     * Also calculates the best Cauldron Recipe if not yet done
     *
     * @return the Particle Color, after potentially calculating it
     */
    @NotNull
    public Color getParticleColor() {
        if (cookingTime < 1) {
            return Color.fromRGB(153, 221, 255); // Bright Blue
        }
        if (particleColor != null) {
            return particleColor;
        }
        if (particleRecipe == null) {
            // Check for Cauldron Recipe
            particleRecipe = ingredients.getCauldronRecipe();
        }

        List<BinaryTuple<Integer, Color>> colorList = null;
        if (particleRecipe != null) {
            colorList = particleRecipe.getParticleColor();
        }

        if (colorList == null || colorList.isEmpty()) {
            // No color List configured, or no recipe found
            colorList = new ArrayList<>(1);
            colorList.add(new BinaryTuple<>(10, Color.fromRGB(77, 166, 255))); // Dark Aqua kind of Blue
        }
        int index = 0;
        while (index < colorList.size() - 1 && colorList.get(index).first() < cookingTime) {
            // Find the first index where the colorList Minute is higher than the state
            index++;
        }

        int minute = colorList.get(index).first();
        if (minute > cookingTime) {
            // going towards the minute
            int previousPosition;
            Color previousColor;
            if (index > 0) {
                // has previous colors
                previousPosition = colorList.get(index - 1).first();
                previousColor = colorList.get(index - 1).second();
            } else {
                previousPosition = 0;
                previousColor = Color.fromRGB(153, 221, 255); // Bright Blue
            }

            particleColor = BreweryUtil.weightedMixColor(previousColor, previousPosition, cookingTime, colorList.get(index).second(), minute);
        } else if (minute == cookingTime) {
            // reached the minute
            particleColor = colorList.get(index).second();
        } else {
            // passed the last minute configured
            if (index > 0) {
                // We have more than one color, just use the last one
                particleColor = colorList.get(index).second();
            } else {
                // Only have one color, go towards a Gray
                Color nextColor = Color.fromRGB(138, 153, 168); // Dark Teal, Gray
                int nextPosition = (int) (minute * 2.6f);

                if (nextPosition <= cookingTime) {
                    // We are past the next color (Gray) as well, keep using it
                    particleColor = nextColor;
                } else {
                    particleColor = BreweryUtil.weightedMixColor(colorList.get(index).second(), minute, cookingTime, nextColor, nextPosition);
                }
            }
        }
        //P.p.log("RGB: " + particleColor.getRed() + "|" + particleColor.getGreen() + "|" + particleColor.getBlue());
        return particleColor;
    }

    public static void processCookEffects() {
        if (MinecraftVersion.isFolia()) return;
        if (!config.isEnableCauldronParticles()) return;
        if (breweryCauldrons.isEmpty()) {
            return;
        }
        final float chance = 1f / PARTICLE_PAUSE;

        for (BreweryCauldron cauldron : breweryCauldrons.values()) {
            if (ThreadLocalRandom.current().nextFloat() < chance) {
                BreweryPlugin.getScheduler().runTask(cauldron.block.getLocation(), cauldron::cookEffect);
            }
        }
    }

    private synchronized void startFoliaParticleTask() {
        if (!MinecraftVersion.isFolia()) {
            return;
        }
        if (!config.isEnableCauldronParticles()) {
            stopFoliaParticleTask();
            return;
        }
        if (foliaParticleTask != null && !foliaParticleTask.isCancelled()) {
            return;
        }
        long delay = ThreadLocalRandom.current().nextLong(1, PARTICLE_PAUSE + 1L);
        foliaParticleTask = BreweryPlugin.getScheduler().runTaskTimer(block.getLocation(), () -> {
            if (config.isMinimalParticles() && ThreadLocalRandom.current().nextFloat() > 0.5f) {
                return;
            }
            cookEffect();
        }, delay, PARTICLE_PAUSE);
    }

    private synchronized void stopFoliaParticleTask() {
        if (!MinecraftVersion.isFolia()) {
            return;
        }
        if (foliaParticleTask != null) {
            foliaParticleTask.cancel();
            foliaParticleTask = null;
        }
    }

    public static void startAllFoliaParticleTasks() {
        if (!MinecraftVersion.isFolia()) {
            return;
        }
        if (!config.isEnableCauldronParticles()) {
            stopAllFoliaParticleTasks();
            return;
        }
        for (BreweryCauldron cauldron : breweryCauldrons.values()) {
            cauldron.startFoliaParticleTask();
        }
    }

    public static void stopAllFoliaParticleTasks() {
        if (!MinecraftVersion.isFolia()) {
            return;
        }
        for (BreweryCauldron cauldron : breweryCauldrons.values()) {
            cauldron.stopFoliaParticleTask();
        }
    }

    public static void clickCauldron(PlayerInteractEvent event) {
        Material materialInHand = event.getMaterial();
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        assert clickedBlock != null;

        switch (materialInHand) {
            // Skip if empty or using buckets
            case Material.AIR, Material.BUCKET, Material.WATER_BUCKET -> {
                return;
            }

            case Material.CLOCK -> {
                printTime(player, clickedBlock);
                return;
            }

            case Material.GLASS_BOTTLE -> {
                assert item != null;

                // Are you guys couldn't write even a line of early-exit???
                if (player.getInventory().firstEmpty() == -1 && item.getAmount() != 1) {
                    event.setCancelled(true);
                    return;
                }

                BreweryCauldron breweryCauldron = get(clickedBlock);
                if (breweryCauldron == null) return;
                if (!breweryCauldron.fill(player, clickedBlock)) return;

                event.setCancelled(true);
                if (player.hasPermission("brewery.cauldron.fill")) {
                    if (player.getGameMode() == GameMode.CREATIVE) {
                        return;
                    }

                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                    } else {
                        BreweryUtil.setItemInHand(event, Material.AIR, false);
                    }
                }

                return;
            }
        }

        // Check if fire alive below cauldron when adding ingredients
        Block down = clickedBlock.getRelative(BlockFace.DOWN);
        // Early-exit
        if (!MaterialUtil.isCauldronHeatSource(down)) return;

        event.setCancelled(true);
        boolean handSwap = false;

        // Check permission first to return faster
        if (!player.hasPermission("brewery.cauldron.insert")) {
            lang.sendEntry(player, "Perms_NoCauldronInsert");
            return;
        }

        // Interact event is called twice, once for each hand.
        // Certain Items in Hand cause one of them to be canceled or not called at all sometimes.
        // We mark if a player had the event for the main hand
        // If not, we handle the main hand in the event for the offhand
        if (event.getHand() == EquipmentSlot.HAND) {
            final UUID id = player.getUniqueId();
            playerInteracted.add(id);
            BreweryPlugin.getScheduler().runTask(() -> playerInteracted.remove(id));
        } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
            if (!playerInteracted.remove(player.getUniqueId())) {
                item = player.getInventory().getItemInMainHand();
                if (item.getType() != Material.AIR) {
                    item.getType();
                    handSwap = true;
                } else {
                    item = config.isUseOffhandForCauldron() ? event.getItem() : null;
                }
            }
        }

        // Return if item is invalid or non-exist
        if (item == null) return;
        if (!ingredientAdd(clickedBlock, item, player)) return;

        boolean isBucket = item.getType().name().endsWith("_BUCKET");
        boolean isBottle = MaterialUtil.isBottle(item.getType());
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            if (isBucket) {
                giveItem(player, new ItemStack(Material.BUCKET));
            } else if (isBottle) {
                giveItem(player, new ItemStack(Material.GLASS_BOTTLE));
            }
        } else {
            if (isBucket) {
                BreweryUtil.setItemInHand(event, Material.BUCKET, handSwap);
            } else if (isBottle) {
                BreweryUtil.setItemInHand(event, Material.GLASS_BOTTLE, handSwap);
            } else {
                item.setAmount(0);
            }
        }
    }

    /**
     * Recalculate the Cauldron Particle Recipe
     */
    public static void reload() {
        if (!config.isEnableCauldronParticles()) {
            stopAllFoliaParticleTasks();
            return;
        }
        startAllFoliaParticleTasks();

        var scheduler = BreweryPlugin.getScheduler();
        for (BreweryCauldron cauldron : breweryCauldrons.values()) {
            cauldron.particleRecipe = null;
            cauldron.particleColor = null;

            scheduler.execute(cauldron.block.getLocation(), () -> {
                if (BreweryUtil.isChunkLoaded(cauldron.block) && MaterialUtil.isCauldronHeatSource(cauldron.block.getRelative(BlockFace.DOWN))) {
                    cauldron.getParticleColor();
                }
            });
        }
    }

    /**
     * reset to normal cauldron
     */
    public static boolean remove(Block block) {
        BreweryCauldron removed = breweryCauldrons.remove(block);
        if (removed != null) {
            removed.stopFoliaParticleTask();
            return true;
        }
        return false;
    }

    /**
     * Are any Cauldrons in that World
     */
    public static boolean hasDataInWorld(World world) {
        return breweryCauldrons.keySet().stream().anyMatch(block -> block.getWorld().equals(world));
    }

    // unloads cauldrons that are in an unloading world
    // as they were written to file just before, this is safe to do
    public static void onUnload(World world) {
        List<Block> blocksToRemove = breweryCauldrons.keySet().stream()
            .filter(block -> block.getWorld().equals(world))
            .toList();
        blocksToRemove.forEach(BreweryCauldron::remove);
    }

    /**
     * Unload all Cauldrons that have are in an unloaded World
     */
    public static void unloadWorlds() {
        List<World> worlds = BreweryPlugin.getInstance().getServer().getWorlds();
        List<Block> blocksToRemove = breweryCauldrons.keySet().stream()
            .filter(block -> !worlds.contains(block.getWorld()))
            .toList();
        blocksToRemove.forEach(BreweryCauldron::remove);
    }

    public static void save(ConfigurationSection config, ConfigurationSection oldData) {
        BreweryUtil.createWorldSections(config);

        if (!breweryCauldrons.isEmpty()) {
            int id = 0;
            for (BreweryCauldron cauldron : breweryCauldrons.values()) {
                String worldName = cauldron.block.getWorld().getName();
                String prefix;

                if (worldName.startsWith("DXL_")) {
                    prefix = BreweryUtil.getDxlName(worldName) + "." + id;
                } else {
                    prefix = cauldron.block.getWorld().getUID() + "." + id;
                }

                config.set(prefix + ".block", cauldron.block.getX() + "/" + cauldron.block.getY() + "/" + cauldron.block.getZ());
                if (cauldron.cookingTime != 0) {
                    config.set(prefix + ".state", cauldron.cookingTime);
                }
                config.set(prefix + ".ingredients", cauldron.ingredients.serializeIngredients());
                id++;
            }
        }
        // copy cauldrons that are not loaded
        if (oldData != null) {
            for (String uuid : oldData.getKeys(false)) {
                if (!config.contains(uuid)) {
                    config.set(uuid, oldData.get(uuid));
                }
            }
        }
    }

    // bukkit bug not updating the inventory while executing event, have to
    // schedule the give
    public static void giveItem(final Player player, final ItemStack item) {
        BreweryPlugin.getScheduler().runTaskLater(() -> player.getInventory().addItem(item), 1L);
    }

}
