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

package com.dre.brewery.configuration;

import com.dre.brewery.brew.Brew;
import com.dre.brewery.mechanics.DistortChat;
import com.dre.brewery.configuration.annotation.OkaeriConfigFileOptions;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.recipes.CauldronIngredientLoader;
import com.dre.brewery.configuration.recipes.CustomItemLoader;
import com.dre.brewery.configuration.recipes.RecipeLoader;
import com.dre.brewery.configuration.sector.capsule.ConfigDistortWord;
import com.dre.brewery.integration.Hook;
import com.dre.brewery.integration.item.BreweryPluginItem;
import com.dre.brewery.integration.item.ItemsAdderPluginItem;
import com.dre.brewery.integration.item.MMOItemsPluginItem;
import com.dre.brewery.integration.item.NexoPluginItem;
import com.dre.brewery.integration.item.OraxenPluginItem;
import com.dre.brewery.integration.item.SlimefunPluginItem;
import com.dre.brewery.recipe.items.PluginItem;
import eu.okaeri.configs.configurer.Configurer;
import eu.okaeri.configs.serdes.OkaeriSerdesPack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;

public class ConfigManager {

    private static final ConfigHead INSTANCE = new ConfigHead();

    public static final Map<Class<? extends AbstractOkaeriConfigFile>, AbstractOkaeriConfigFile> LOADED_CONFIGS = INSTANCE.LOADED_CONFIGS;

    /**
     * Get a config instance from the LOADED_CONFIGS map, or create a new instance if it doesn't exist
     *
     * @param configClass The class of the config to get
     * @param <T>         The type of the config
     * @return The config instance
     */
    public static <T extends AbstractOkaeriConfigFile> T getConfig(Class<T> configClass) {
        return INSTANCE.getConfig(configClass);
    }

    /**
     * Replaces a config instance in the LOADED_CONFIGS map with a new instance of the same class
     *
     * @param configClass The class of the config to replace
     * @param <T>         The type of the config
     */
    public static <T extends AbstractOkaeriConfigFile> void newInstance(Class<T> configClass, boolean overwrite) {
        INSTANCE.newInstance(configClass, overwrite);
    }


    /**
     * Get the file path of a config class
     *
     * @param configClass The class of the config to get the file name of
     * @param <T>         The type of the config
     * @return The file name
     */
    public static <T extends AbstractOkaeriConfigFile> Path getFilePath(Class<T> configClass) {
        return INSTANCE.getFilePath(configClass);
    }


    /**
     * Create a new config instance with a custom file name, configurer, serdes pack, and puts it in the LOADED_CONFIGS map
     *
     * @param configClass The class of the config to create
     * @param file        The file to use
     * @param configurer  The configurer to use
     * @param serdesPack  The serdes pack to use
     * @param <T>         The type of the config
     * @return The new config instance
     */
    private static <T extends AbstractOkaeriConfigFile> T createConfig(Class<T> configClass, Path file, Configurer configurer, OkaeriSerdesPack serdesPack, boolean update, boolean removeOrphans) {
        return INSTANCE.createConfig(configClass, file, configurer, serdesPack, update, removeOrphans);
    }

    /**
     * Create a new config instance using a config class' annotation
     *
     * @param configClass The class of the config to create
     * @param <T>         The type of the config
     * @return The new config instance
     */
    private static <T extends AbstractOkaeriConfigFile> T createConfig(Class<T> configClass) {
        return INSTANCE.createConfig(configClass);
    }

    @Nullable
    private static <T extends AbstractOkaeriConfigFile> T createBlankConfigInstance(Class<T> configClass) {
        return INSTANCE.createBlankConfigInstance(configClass);
    }


    // Util

    public static void createFileFromResources(String resourcesPath, Path destination) {
        INSTANCE.createFileFromResources(resourcesPath, destination);
    }


    private static OkaeriConfigFileOptions getOkaeriConfigFileOptions(Class<? extends AbstractOkaeriConfigFile> configClass) {
        return INSTANCE.getOkaeriConfigFileOptions(configClass);
    }


    // Not really what I want to do, but I have to move these from BConfig right now

    public static void loadRecipes() {
        RecipeLoader.loadRecipes();
    }

    public static void loadCustomItems() {
        CustomItemLoader.loadCustomItems();
    }

    public static void loadCauldronIngredients() {
        CauldronIngredientLoader.loadCauldronIngredients();
    }

    public static void loadDistortWords() {
        // Loading Words
        Config config = getConfig(Config.class);

        if (config.isEnableChatDistortion()) {
            for (ConfigDistortWord distortWord : config.getWords()) {
                new DistortChat(distortWord);
            }
            for (String bypass : config.getDistortBypass()) {
                DistortChat.getIgnoreText().add(bypass.split(","));
            }
            DistortChat.getCommands().addAll(config.getDistortCommands());
        }
    }

    public static void loadSeed() {
        Config config = getConfig(Config.class);
        if (config.isEnableEncode()) {
            Brew.loadSeed(config.getEncodeKey());
        }
    }

    public static void registerDefaultPluginItems() {
        PluginItem.registerForConfig("brewery", BreweryPluginItem::new);
        if (Hook.MMOITEMS.isEnabled()) {
            PluginItem.registerForConfig("mmoitems", MMOItemsPluginItem::new);
        }
        if (Hook.SLIMEFUN.isEnabled()) {
            PluginItem.registerForConfig("slimefun", SlimefunPluginItem::new);
            PluginItem.registerForConfig("exoticgarden", SlimefunPluginItem::new);
        }
        if (Hook.ORAXEN.isEnabled()) {
            PluginItem.registerForConfig("oraxen", OraxenPluginItem::new);
        }
        if (Hook.ITEMSADDER.isEnabled()) {
            PluginItem.registerForConfig("itemsadder", ItemsAdderPluginItem::new);
        }
        if (Hook.NEXO.isEnabled()) {
            PluginItem.registerForConfig("nexo", NexoPluginItem::new);
        }
    }
}
