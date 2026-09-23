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

import com.dre.brewery.brew.Brew;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.commands.BreweryCommandManager;
import com.dre.brewery.commands.CommandUtil;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.configurer.TranslationManager;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.instruments.BreweryCauldron;
import com.dre.brewery.instruments.BrewerySealer;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.releases.ReleaseChecker;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/**
 * Reloads every configuration file, recipe and addon of BreweryShiroi.
 */
public class ReloadCommand {

    @Getter
    private static CommandSender reloader;

    private ReloadCommand() {
    }

    public static void register(BreweryCommandManager commands) {
        commands.manager().command(commands.command("reload", "brewery.cmd.reload", "Help_Reload")
            .handler(context -> reload(context.sender().source())));
    }

    /**
     * Reloads everything, all messages keep being sent to the given sender while this runs.
     *
     * @param sender The sender that requested the reload
     */
    public static void reload(CommandSender sender) {
        BreweryPlugin breweryPlugin = BreweryPlugin.getInstance();
        Lang lang = ConfigManager.getConfig(Lang.class);

        if (!sender.equals(Bukkit.getConsoleSender())) {
            reloader = sender;
        }

        try {
            // Reload translation manager
            TranslationManager.newInstance(breweryPlugin.getDataFolder());
            TranslationManager.getInstance().updateTranslationFiles();

            // Reload each config
            for (var file : ConfigManager.LOADED_CONFIGS.values()) {
                try {
                    file.reload();
                } catch (Throwable e) {
                    Logging.errorLog("Something went wrong trying to load " + file.getBindFile().getFileName() + "!", e);
                }
            }

            // Reload the custom items first, the cauldron ingredients and recipes below may reference them by id
            ConfigManager.loadCustomItems();
            // Reload Cauldron Ingredients
            ConfigManager.loadCauldronIngredients();
            // Reload Recipes
            ConfigManager.loadRecipes();
            // Reload Seed
            ConfigManager.loadSeed();

            // Reload Cauldron Particle Recipes
            BreweryCauldron.reload();

            // Clear Recipe completions
            CommandUtil.reloadTabCompleter();

            // Sealing table recipe
            BrewerySealer.registerRecipe();

            // Let addons know this command was executed
            BreweryPlugin.getAddonManager().reloadAddons();

            // Re-resolve the recipe reference of every legacy brew, since the recipes were just replaced
            boolean allReloaded = true;
            for (Brew brew : Brew.legacyPotions.values()) {
                if (!brew.reloadRecipe()) {
                    allReloaded = false;
                }
            }

            if (allReloaded) {
                lang.sendEntry(sender, "CMD_Reload");
            } else {
                lang.sendEntry(sender, "Error_Recipeload");
            }

            ReleaseChecker releaseChecker = ReleaseChecker.getInstance(true);
            releaseChecker.checkForUpdate().thenAccept(updateAvailable -> {
                if (!(sender instanceof ConsoleCommandSender consoleSender)) {
                    releaseChecker.notify(sender);
                } else {
                    releaseChecker.notify(consoleSender);
                }
            });
        } catch (Throwable e) {
            Logging.errorLog("Something went wrong trying to reload Brewery!", e);
        }
        // Make sure this reloader is set to null after
        reloader = null;
    }
}
