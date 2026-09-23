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

package com.dre.brewery.utility;

import com.dre.brewery.commands.subcommands.ReloadCommand;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.utility.utils.BreweryUtil;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

public final class Logging {

    public enum LogLevel {
        INFO,
        WARNING,
        ERROR,
        DEBUG
    }

    /**
     * The tag every console message of this plugin is prefixed with, in the BreweryShiroi color.
     * <p>
     * Messages sent to players keep using the configurable {@code pluginPrefix} instead.
     */
    public static final String PREFIX = "&#D7FFFFBrewShiroi » &f";

    /**
     * Resolved lazily on purpose: touching this class from {@code BreweryPlugin}'s constructor must not
     * trigger a config load, since that needs the {@code mcVersion} which is only known in {@code onEnable}.
     */
    private static Config config() {
        return ConfigManager.getConfig(Config.class);
    }

    public static void message(CommandSender sender, String message) {
        sender.sendMessage(BreweryUtil.color(config().getPluginPrefix() + message));
    }

    public static void log(String message) {
        Bukkit.getConsoleSender().sendMessage(BreweryUtil.color(PREFIX + message));
    }

    public static void log(LogLevel level, String message) {
        log(level, message, null);
    }

    public static void log(LogLevel level, String message, @Nullable Throwable throwable) {
        switch (level) {
            case INFO -> log(message);
            case WARNING -> warningLog(message);
            case ERROR -> {
                if (throwable != null) {
                    errorLog(message, throwable);
                } else {
                    errorLog(message);
                }
            }
            case DEBUG -> debugLog(message);
        }
    }

    public static void debugLog(String message) {
        if (config().isDebug()) {
            log("&2[Debug] &f" + message);
        }
    }

    public static void warningLog(String message) {
        Bukkit.getConsoleSender().sendMessage(BreweryUtil.color(PREFIX + "&eWARNING: " + message));
    }

    public static void errorLog(String message) {
        String text = BreweryUtil.color(PREFIX + "&cERROR: " + message);
        Bukkit.getConsoleSender().sendMessage(text);
        if (ReloadCommand.getReloader() != null) { // I hate this, but I'm too lazy to go change all of it - Jsinco
            ReloadCommand.getReloader().sendMessage(text);
        }
    }

    public static void errorLog(String message, Throwable throwable) {
        errorLog(message);
        errorLog("&6" + throwable);
        printFrames(throwable, "");
        for (Throwable cause = throwable.getCause(); cause != null; cause = cause.getCause()) {
            errorLog("&6Caused by: " + cause);
            printFrames(cause, "&6     ");
        }
    }

    private static void printFrames(Throwable throwable, String prefix) {
        for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
            String frame = stackTraceElement.toString();
            int jarMarker = frame.indexOf(".jar//");
            errorLog(prefix + (jarMarker < 0 ? frame : frame.substring(jarMarker + 6)));
        }
    }


    public static String getEnvironmentAsString() {
        if (MinecraftVersion.isFolia()) {
            return "Folia";
        }
        return PaperLib.getEnvironment().getName();
    }

}
