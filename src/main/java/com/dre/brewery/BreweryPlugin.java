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

package com.dre.brewery;

import com.dre.brewery.api.addons.AddonManager;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.configurer.TranslationManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.instruments.BreweryCauldron;
import com.dre.brewery.instruments.BrewerySealer;
import com.dre.brewery.instruments.barrel.BreweryBarrel;
import com.dre.brewery.instruments.barrel.VanillaBarrel;
import com.dre.brewery.integration.BlockLockerHook;
import com.dre.brewery.integration.Hook;
import com.dre.brewery.integration.LandsHook;
import com.dre.brewery.integration.PlaceholderAPIHook;
import com.dre.brewery.integration.barrel.BlockLockerBarrel;
import com.dre.brewery.integration.bstats.BreweryStats;
import com.dre.brewery.integration.bstats.BreweryXStats;
import com.dre.brewery.integration.listeners.ChestShopListener;
import com.dre.brewery.integration.listeners.IntegrationListener;
import com.dre.brewery.integration.listeners.ShopKeepersListener;
import com.dre.brewery.integration.listeners.SlimefunListener;
import com.dre.brewery.integration.listeners.movecraft.CraftDetectListener;
import com.dre.brewery.integration.listeners.movecraft.RotationListener;
import com.dre.brewery.integration.listeners.movecraft.SinkListener;
import com.dre.brewery.integration.listeners.movecraft.TranslationListener;
import com.dre.brewery.integration.listeners.movecraft.properties.BreweryProperties;
import com.dre.brewery.listeners.BlockListener;
import com.dre.brewery.listeners.CauldronListener;
import com.dre.brewery.listeners.EntityListener;
import com.dre.brewery.listeners.InventoryListener;
import com.dre.brewery.listeners.PlayerListener;
import com.dre.brewery.recipe.items.CustomItem;
import com.dre.brewery.recipe.items.Ingredient;
import com.dre.brewery.recipe.items.ItemLoader;
import com.dre.brewery.recipe.items.PluginItem;
import com.dre.brewery.recipe.items.SimpleItem;
import com.dre.brewery.storage.DataManager;
import com.dre.brewery.storage.StorageInitException;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.MinecraftVersion;
import com.dre.brewery.utility.releases.ReleaseChecker;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public final class BreweryPlugin extends JavaPlugin {

    private @Getter static AddonManager addonManager;
    private @Getter static TaskScheduler scheduler;
    private @Getter static BreweryPlugin instance;
    private @Getter static MinecraftVersion MCVersion;
    private @Getter @Setter static DataManager dataManager;


    private final Map<String, Function<ItemLoader, Ingredient>> ingredientLoaders = new HashMap<>(); // Registrations
    private BreweryStats breweryStats; // Metrics
    private BreweryCommandManager commandManager; // Commands and their aliases

    {
        // Basically just racing to be the first code to execute.
        // Okaeri configs are used in static fields, so we need this code to execute before Okaeri
        // can start loading.
        instance = this;
        this.migrateBreweryDataFolder();
        MCVersion = MinecraftVersion.getIt();
        scheduler = UniversalScheduler.getScheduler(this);
        TranslationManager.newInstance(this.getDataFolder());
    }

    @Override
    public void onLoad() {

        // movecraft properties must be registered in the onLoad
        try {
            Class.forName("net.countercraft.movecraft.craft.type.property.Property");
            BreweryProperties.register();
        } catch (Exception ignored) {
        }

        // Lands flags must be registered onLoad
        if (getServer().getPluginManager().getPlugin("Lands") != null) LandsHook.load();

        if (getMCVersion().isOrLater(MinecraftVersion.V1_14)) {
            // Campfires are weird. Initialize once now, so it doesn't lag later when we check for campfires under Cauldrons
            getServer().createBlockData(Material.CAMPFIRE);
        }
    }

    @Override
    public void onEnable() {

        // Register Item Loaders
        CustomItem.registerItemLoader(this);
        SimpleItem.registerItemLoader(this);
        PluginItem.registerItemLoader(this);

        // Load config
        Config config = ConfigManager.getConfig(Config.class);
        if (config.isFirstCreation()) {
            config.onFirstCreation();
        }

        // Load lang
        TranslationManager.getInstance().updateTranslationFiles();
        ConfigManager.newInstance(Lang.class, false);

        BrewerySealer.registerRecipe(); // Sealing table recipe
        ConfigManager.registerDefaultPluginItems(); // Register plugin items

        // Load Addons
        addonManager = new AddonManager(this);
        addonManager.loadAddons();

        // Custom items first, the cauldron ingredients and recipes below may reference them by id
        ConfigManager.loadCustomItems();
        ConfigManager.loadCauldronIngredients();
        ConfigManager.loadRecipes();
        ConfigManager.loadDistortWords();
        ConfigManager.loadSeed();
        this.breweryStats = new BreweryStats(); // Load metrics


        Logging.log("Minecraft version&7:&a " + MCVersion.getVersion());
        if (MCVersion == MinecraftVersion.UNKNOWN) {
            Logging.warningLog("This version of Minecraft is not known to Brewery! Please be wary of bugs or other issues that may occur in this version.");
        }


        // Load DataManager
        try {
            dataManager = DataManager.createDataManager(config.getStorage());
        } catch (StorageInitException e) {
            Logging.errorLog("Failed to initialize DataManager!", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Load objects
        DataManager.loadMiscData(dataManager.getBreweryMiscData());
        dataManager.getAllBarrels().thenAcceptAsync(barrels -> barrels.stream()
            .filter(Objects::nonNull)
            .forEach(BreweryBarrel::registerBarrel)
        );
        BreweryCauldron.getBcauldrons().putAll(dataManager.getAllCauldrons().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                BreweryCauldron::getBlock, Function.identity(),
                (existing, replacement) -> replacement // Issues#68
            )));
        BreweryCauldron.startAllFoliaParticleTasks();
        BreweryPlayer.getPlayers().putAll(dataManager.getAllPlayers()
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                BreweryPlayer::getUuid,
                Function.identity()
            )));
        Wakeup.getWakeups().addAll(dataManager.getAllWakeups()
            .stream()
            .filter(Objects::nonNull)
            .toList());

        addonManager.enableAddons();
        // Setup Metrics
        this.breweryStats.setupBStats();
        new BreweryXStats().setupBStats();

        // Register the commands, including the aliases configured in the config
        this.commandManager = new BreweryCommandManager(this);
        this.commandManager.register();

        // Register Listeners
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new BlockListener(), this);
        pluginManager.registerEvents(new PlayerListener(), this);
        pluginManager.registerEvents(new EntityListener(), this);
        pluginManager.registerEvents(new InventoryListener(), this);
        pluginManager.registerEvents(new IntegrationListener(), this);
        if (getMCVersion().isOrLater(MinecraftVersion.V1_9))
            pluginManager.registerEvents(new CauldronListener(), this);
        if (Hook.CHESTSHOP.isEnabled() && getMCVersion().isOrLater(MinecraftVersion.V1_13))
            pluginManager.registerEvents(new ChestShopListener(), this);
        if (Hook.SHOPKEEPERS.isEnabled())
            pluginManager.registerEvents(new ShopKeepersListener(), this);
        if (Hook.SLIMEFUN.isEnabled() && getMCVersion().isOrLater(MinecraftVersion.V1_14))
            pluginManager.registerEvents(new SlimefunListener(), this);
        if (Hook.MOVECRAFT.isEnabled()) {
            pluginManager.registerEvents(new CraftDetectListener(), this);
            pluginManager.registerEvents(new TranslationListener(), this);
            pluginManager.registerEvents(new RotationListener(), this);
            pluginManager.registerEvents(new SinkListener(), this);
        }

        // Heartbeat
        BreweryPlugin.getScheduler().runTaskTimer(new BreweryRunnable(), 650, 1200);
        BreweryPlugin.getScheduler().runTaskTimer(new DrunkRunnable(), 120, 120);
        if (getMCVersion().isOrLater(MinecraftVersion.V1_9) && !MinecraftVersion.isFolia())
            BreweryPlugin.getScheduler().runTaskTimer(new CauldronParticles(), 1, 1);


        // Register PlaceholderAPI Placeholders
        PlaceholderAPIHook placeholderAPIHook = PlaceholderAPIHook.PLACEHOLDERAPI;
        if (placeholderAPIHook.isEnabled()) {
            placeholderAPIHook.getInstance().register();
        }

        Logging.log("Using scheduler&7: &a" + scheduler.getClass().getSimpleName());
        Logging.log("Environment&7: &a" + Logging.getEnvironmentAsString());
        if (!PaperLib.isPaper()) {
            Logging.log("&aBreweryShiroi performs best on Paper-based servers. Please consider switching to Paper for the best experience. &7https://papermc.io");
        }
        Logging.log("BreweryShiroi enabled!");

        ReleaseChecker releaseChecker = ReleaseChecker.getInstance();
        releaseChecker.checkForUpdate().thenAccept(updateAvailable -> {
            releaseChecker.notify(Bukkit.getConsoleSender());
        });
    }

    @Override
    public void onDisable() {
        if (addonManager != null) addonManager.unloadAddons();

        // Disable listeners
        HandlerList.unregisterAll(this);

        BreweryCauldron.stopAllFoliaParticleTasks();

        // Stop schedulers
        BreweryPlugin.getScheduler().cancelTasks(this);

        // save Data to Disk
        if (dataManager != null) dataManager.exit(true, false);

        PlaceholderAPIHook placeholderAPIHook = PlaceholderAPIHook.PLACEHOLDERAPI;
        if (placeholderAPIHook.isEnabled()) {
            placeholderAPIHook.getInstance().unregister();
        }

        Logging.log("BreweryShiroi disabled!");
    }


    /**
     * For loading ingredients from ItemMeta.
     * <p>Register a Static function that takes an ItemLoader, containing a DataInputStream.
     * <p>Using the Stream it constructs a corresponding Ingredient for the chosen SaveID
     *
     * @param saveID  The SaveID should be a small identifier like "AB"
     * @param loadFct The Static Function that loads the Item, i.e.
     *                public static AItem loadFrom(ItemLoader loader)
     */
    public void registerForItemLoader(String saveID, Function<ItemLoader, Ingredient> loadFct) {
        ingredientLoaders.put(saveID, loadFct);
    }

    /**
     * Unregister the ItemLoader
     *
     * @param saveID the chosen SaveID
     */
    public void unRegisterItemLoader(String saveID) {
        ingredientLoaders.remove(saveID);
    }


    // Runnables

    public static class DrunkRunnable implements Runnable {
        @Override
        public void run() {
            if (!BreweryPlayer.isEmpty()) {
                BreweryPlayer.drunkenness();
            }
        }
    }

    public static class BreweryRunnable implements Runnable {
        @Override
        public void run() {
            long start = System.currentTimeMillis();

            // runs every min to update cooking time

            for (BreweryCauldron breweryCauldron : BreweryCauldron.bcauldrons.values()) {
                BreweryPlugin.getScheduler().runTask(breweryCauldron.getBlock().getLocation(), () -> {
                    if (!breweryCauldron.onUpdate()) {
                        BreweryCauldron.remove(breweryCauldron.getBlock());
                    }
                });
            }


            BreweryBarrel.onUpdate();// runs every min to check and update ageing time

            if (getMCVersion().isOrLater(MinecraftVersion.V1_14)) VanillaBarrel.onUpdate();
            if (BlockLockerHook.BLOCKLOCKER.isEnabled()) BlockLockerBarrel.clearBarrelSign();

            BreweryPlayer.onUpdate();// updates players drunkenness


            //DataSave.autoSave();
            dataManager.tryAutoSave();

            Logging.debugLog("BreweryRunnable: " + (System.currentTimeMillis() - start) + "ms");
        }

    }

    public static class CauldronParticles implements Runnable {


        @Override
        public void run() {
            Config config = ConfigManager.getConfig(Config.class);

            if (!config.isEnableCauldronParticles()) return;
            if (config.isMinimalParticles() && ThreadLocalRandom.current().nextFloat() > 0.5f) {
                return;
            }
            BreweryCauldron.processCookEffects();
        }
    }


    // BreweryShiroi was formerly known as BreweryX, which itself forked the original Brewery.
    // Servers upgrading to the new name get their old data folder moved over, before Okaeri loads anything.
    public void migrateBreweryDataFolder() {
        File dataFolder = getDataFolder();
        if (dataFolder.exists()) {
            return;
        }

        File pluginsFolder = dataFolder.getParentFile();
        for (String oldName : new String[]{ "BreweryX", "Brewery" }) {
            File oldFolder = new File(pluginsFolder, oldName);
            if (!oldFolder.exists()) {
                continue;
            }

            dataFolder.mkdirs();
            File[] files = oldFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    try {
                        Files.copy(file.toPath(), new File(dataFolder, file.getName()).toPath());
                    } catch (IOException e) {
                        Logging.errorLog("Failed to move file: " + file.getName(), e);
                    }
                }
                Logging.log("&5Moved files from " + oldName + " to BreweryShiroi's data folder");
            }
            return;
        }
    }
}
