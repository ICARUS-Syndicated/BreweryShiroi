/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024-2026 The Brewery Team
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

package com.dre.brewery.utility.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

public final class NBTUtil {

    public static boolean NewNbtVer;

    /**
     * MC 1.13 uses a different NBT API than the newer versions..
     * We decide here which to use, the new or the old
     *
     * @return true if we can use nbt at all
     */
    public static boolean initNbt() {
        try {
            Class.forName("org.bukkit.persistence.PersistentDataContainer");
            NewNbtVer = true;
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("org.bukkit.inventory.meta.tags.CustomItemTagContainer");
                NewNbtVer = false;
                return true;
            } catch (ClassNotFoundException ex) {
                NewNbtVer = false;
                return false;
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void writeBytesItem(byte[] bytes, ItemMeta meta, NamespacedKey key) {
        if (NewNbtVer) {
            meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE_ARRAY, bytes);
        } else {
            meta.getCustomTagContainer().setCustomTag(key, org.bukkit.inventory.meta.tags.ItemTagType.BYTE_ARRAY, bytes);
        }
    }

    @SuppressWarnings("deprecation")
    public static byte[] readBytesItem(ItemMeta meta, NamespacedKey key) {
        if (NewNbtVer) {
            return meta.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.BYTE_ARRAY);
        } else {
            return meta.getCustomTagContainer().getCustomTag(key, org.bukkit.inventory.meta.tags.ItemTagType.BYTE_ARRAY);
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean hasBytesItem(ItemMeta meta, NamespacedKey key) {
        if (NewNbtVer) {
            return meta.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE_ARRAY);
        } else {
            return meta.getCustomTagContainer().hasCustomTag(key, org.bukkit.inventory.meta.tags.ItemTagType.BYTE_ARRAY);
        }
    }
}
