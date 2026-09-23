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

import com.dre.brewery.utility.utils.ClassUtil;
import com.dre.brewery.utility.utils.NBTUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enum for major Minecraft versions where Brewery needs
 * to handle things differently.
 */
@Getter
public enum MinecraftVersion {

    // 1.21 is the lowest supported version, anything below it is reported as UNKNOWN.
    V1_21("1.21"),
    V1_21_4("1.21.4"), // min version for setItemModel
    V1_21_5("1.21.5"), // Int CustomModelData is deprecated since version 1.21.5
    V1_21_10("1.21.10", "1.21.9"), // 1.21.10 & 1.21.9 are one and the same
    V1_21_11("1.21.11"),
    V26_1("26.1"),
    UNKNOWN("Unknown");

    private static final Pattern VERSION_PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?");

    private @Getter static final boolean isFolia = ClassUtil.exists("io.papermc.paper.threadedregions.RegionizedServer");
    // private @Getter static final boolean isCanvas = ClassUtil.exists("io.canvasmc.canvas.Config");
    // No, canvas is a piece of AI slop shit. With their thief developers, this **best** fork
    // very successfully stopped Luminol's development. Hooray for these motherfuckers.
    private @Getter static final boolean useNBT = NBTUtil.initNbt();

    private final String[] versions;

    MinecraftVersion(String... version) {
        this.versions = version;
    }

    public static MinecraftVersion get(String major, String minor, @Nullable String patch) {
        String withoutPatch = major + "." + minor;
        String withPatch = patch == null ? withoutPatch : withoutPatch + "." + patch;

        // A match on major.minor.patch is preferred over one on major.minor only, so the partial
        // match is only remembered and returned once no exact match has been found.
        MinecraftVersion partialMatch = null;
        for (MinecraftVersion candidate : values()) {
            for (String versionString : candidate.versions) {
                if (versionString.equals(withPatch)) {
                    return candidate;
                }
                if (partialMatch == null && versionString.equals(withoutPatch)) {
                    partialMatch = candidate;
                }
            }
        }
        return partialMatch != null ? partialMatch : UNKNOWN;
    }

    public static MinecraftVersion getIt() {
        String rawVersion = Bukkit.getMinecraftVersion();

        Matcher matcher = VERSION_PATTERN.matcher(rawVersion);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not parse Minecraft version from: " + rawVersion);
        }
        return get(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    public boolean isOrLater(MinecraftVersion version) {
        return this.ordinal() >= version.ordinal();
    }

    public boolean isOrEarlier(MinecraftVersion version) {
        return this.ordinal() <= version.ordinal();
    }

    public String getVersion() {
        return versions[0];
    }
}
