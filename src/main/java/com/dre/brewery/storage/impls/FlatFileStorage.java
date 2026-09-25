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

package com.dre.brewery.storage.impls;

import com.dre.brewery.instruments.BreweryCauldron;
import com.dre.brewery.brew.BreweryIngredients;
import com.dre.brewery.mechanics.BreweryPlayer;
import com.dre.brewery.instruments.barrel.BreweryBarrel;
import com.dre.brewery.mechanics.Wakeup;
import com.dre.brewery.configuration.sector.capsule.ConfiguredDataManager;
import com.dre.brewery.storage.DataManager;
import com.dre.brewery.storage.StorageInitException;
import com.dre.brewery.storage.interfaces.SerializableThing;
import com.dre.brewery.storage.records.BreweryMiscData;
import com.dre.brewery.storage.serialization.BukkitSerialization;
import com.dre.brewery.storage.serialization.SQLDataSerializer;
import com.dre.brewery.utility.utils.BreweryUtil;
import com.dre.brewery.utility.BoundingBox;
import com.dre.brewery.utility.utils.FutureUtil;
import com.dre.brewery.utility.Logging;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FlatFileStorage extends DataManager {

    private final File rawFile;
    private final YamlConfiguration dataFile;
    private final SQLDataSerializer serializer = new SQLDataSerializer();

    public FlatFileStorage(ConfiguredDataManager record) throws StorageInitException {
        super(record.getType());
        String fileName = record.getDatabase() + ".yml";
        this.rawFile = new File(plugin.getDataFolder(), fileName);

        if (!rawFile.exists()) {
            try {
                boolean ignored = rawFile.createNewFile();
            } catch (IOException e) {
                throw new StorageInitException("Failed to create file! " + fileName, e);
            }
        }

        this.dataFile = YamlConfiguration.loadConfiguration(rawFile);
    }


    private void save() {
        try {
            dataFile.save(rawFile);
        } catch (IOException e) {
            Logging.errorLog("Failed to save to FlatFile!", e);
        }
    }

    @Override
    public boolean createTable(String name, int maxIdLength) {
        if (dataFile.contains(name)) {
            return false;
        }
        dataFile.createSection(name);
        save();
        return true;
    }

    @Override
    public boolean dropTable(String name) {
        dataFile.set(name, null);
        save();
        return true;
    }


    @Override
    public <T extends SerializableThing> T getGeneric(String id, String table, Class<T> type) {
        ConfigurationSection section = dataFile.getConfigurationSection(table + "." + id);
        if (section == null) {
            return null;
        }
        // Go through JsonElement, Gson writes ints as doubles sometimes,
        // but they seem to serialize back to ints just fine.
        return serializer.getGson().fromJson(serializer.getGson().toJsonTree(section.getValues(false)), type);
    }

    @Override
    public <T extends SerializableThing> List<T> getAllGeneric(String table, Class<T> type) {
        ConfigurationSection section = dataFile.getConfigurationSection(table);
        if (section == null) {
            return Collections.emptyList();
        }
        return section.getKeys(false).stream()
            .map(key -> getGeneric(key, table, type))
            .toList();
    }

    @Override
    public <T extends SerializableThing> void saveAllGeneric(List<T> serializableThings, String table, @Nullable Class<T> type) {
        ConfigurationSection section = dataFile.getConfigurationSection(table);
        if (section != null) {
            section.getKeys(false).forEach(key -> dataFile.set(table + "." + key, null));
        } else {
            dataFile.createSection(table);
        }

        for (T thing : serializableThings) {
            saveGeneric(thing, table);
        }
        save();
    }

    @Override
    public <T extends SerializableThing> void saveGeneric(T serializableThing, String table) {
        String path = table + "." + serializableThing.getId();

        Gson gson = serializer.getGson();
        JsonObject jsonObject = gson.toJsonTree(serializableThing).getAsJsonObject();
        Type mapType = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> map = gson.fromJson(jsonObject, mapType);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            dataFile.set(path + "." + entry.getKey(), entry.getValue());
        }
        save();
    }

    @Override
    public void deleteGeneric(String id, String table) {
        dataFile.set(table + "." + id, null);
        save();
    }

    @Override
    public CompletableFuture<BreweryBarrel> getBarrel(UUID id) {
        String path = "barrels." + id;

        Location spigotLoc = deserializeLocation(dataFile.getString(path + ".spigot"));
        if (spigotLoc == null) {
            return CompletableFuture.completedFuture(null);
        }

        int[] bounds = Arrays.stream(
                dataFile.getString(path + ".bounds").split(",")
            )
            .mapToInt(Integer::parseInt).toArray();

        BoundingBox boundingBox = BoundingBox.fromPoints(bounds);
        float time = (float) dataFile.getDouble(path + ".time", 0.0);
        byte sign = (byte) dataFile.getInt(path + ".sign", 0);
        ItemStack[] items = BukkitSerialization.itemStackArrayFromBase64(dataFile.getString(path + ".items", null));


        return BreweryBarrel.computeSmall(spigotLoc).thenApplyAsync(small ->
            new BreweryBarrel(spigotLoc.getBlock(), sign, boundingBox, items, time, id, small)
        );
    }

    @Override
    public CompletableFuture<List<BreweryBarrel>> getAllBarrels() {
        ConfigurationSection section = dataFile.getConfigurationSection("barrels");
        if (section == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return FutureUtil.mergeFutures(section.getKeys(false).stream()
            .map(key -> getBarrel(BreweryUtil.uuidFromString(key)))
            .toList());
    }

    @Override
    public void saveAllBarrels(Collection<BreweryBarrel> breweryBarrels) {
        dataFile.set("barrels", null);
        for (BreweryBarrel breweryBarrel : breweryBarrels) {
            saveBarrel(breweryBarrel);
        }
    }

    @Override
    public void saveBarrel(BreweryBarrel breweryBarrel) {
        if (breweryBarrel.getBounds() == null) {
            return;
        }
        String path = "barrels." + breweryBarrel.getId();

        dataFile.set(path + ".spigot", serializeLocation(breweryBarrel.getSpigot().getLocation()));
        dataFile.set(path + ".bounds", breweryBarrel.getBounds().serialize());
        dataFile.set(path + ".time", breweryBarrel.getTime());
        dataFile.set(path + ".sign", breweryBarrel.getSignOffset());
        dataFile.set(path + ".items", BukkitSerialization.itemStackArrayToBase64(breweryBarrel.getInventory().getContents()));
        save();
    }

    @Override
    public void deleteBarrel(UUID id) {
        dataFile.set("barrels." + id, null);
        save();
    }

    @Override
    public BreweryCauldron getCauldron(UUID id) {
        String path = "cauldrons." + id;

        Location location = deserializeLocation(dataFile.getString(path + ".block"));
        if (location == null) {
            return null;
        }
        BreweryIngredients ingredients = BreweryIngredients.deserializeIngredients(dataFile.getString(path + ".ingredients"));
        int state = dataFile.getInt(path + ".state", 0);

        return new BreweryCauldron(location.getBlock(), ingredients, state, id);
    }

    @Override
    public Collection<BreweryCauldron> getAllCauldrons() {
        ConfigurationSection section = dataFile.getConfigurationSection("cauldrons");
        if (section == null) {
            return Collections.emptyList();
        }
        return section.getKeys(false).stream()
            .map(key -> getCauldron(BreweryUtil.uuidFromString(key)))
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public void saveAllCauldrons(Collection<BreweryCauldron> cauldrons) {
        dataFile.set("cauldrons", null);
        for (BreweryCauldron cauldron : cauldrons) {
            saveCauldron(cauldron);
        }
    }

    @Override
    public void saveCauldron(BreweryCauldron cauldron) {
        String path = "cauldrons." + cauldron.getId();

        dataFile.set(path + ".block", serializeLocation(cauldron.getBlock().getLocation()));
        dataFile.set(path + ".ingredients", cauldron.getIngredients().serializeIngredients());
        dataFile.set(path + ".state", cauldron.getCookingTime());
        save();
    }


    @Override
    public void deleteCauldron(UUID id) {
        dataFile.set("cauldrons." + id, null);
        save();
    }


    @Override
    public BreweryPlayer getPlayer(UUID playerUUID) {
        String path = "players." + playerUUID;

        int quality = dataFile.getInt(path + ".quality", 0);
        int drunkenness = dataFile.getInt(path + ".drunkenness", 0);
        int offlineDrunkenness = dataFile.getInt(path + ".offlineDrunkenness", 0);
        return new BreweryPlayer(playerUUID, quality, drunkenness, offlineDrunkenness);
    }

    @Override
    public Collection<BreweryPlayer> getAllPlayers() {
        ConfigurationSection section = dataFile.getConfigurationSection("players");
        if (section == null) {
            return Collections.emptyList();
        }
        return section.getKeys(false).stream()
            .map(key -> getPlayer(BreweryUtil.uuidFromString(key)))
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public void saveAllPlayers(Collection<BreweryPlayer> players) {
        dataFile.set("players", null);
        for (BreweryPlayer player : players) {
            savePlayer(player);
        }
    }

    @Override
    public void savePlayer(BreweryPlayer player) {
        String path = "players." + player.getUuid();

        dataFile.set(path + ".quality", player.getQuality());
        dataFile.set(path + ".drunkenness", player.getDrunkenness());
        dataFile.set(path + ".offlineDrunkenness", player.getOfflineDrunkenness());
        save();
    }

    @Override
    public void deletePlayer(UUID playerUUID) {
        dataFile.set("players." + playerUUID, null);
        save();
    }

    @Override
    public Wakeup getWakeup(UUID id) {
        String path = "wakeups." + id;
        Location wakeupLocation = deserializeLocation(dataFile.getString(path + ".location"), true);
        if (wakeupLocation == null) {
            return null;
        }
        return new Wakeup(wakeupLocation, id);
    }

    @Override
    public Collection<Wakeup> getAllWakeups() {
        ConfigurationSection section = dataFile.getConfigurationSection("wakeups");
        if (section == null) {
            return Collections.emptyList();
        }
        return section.getKeys(false).stream()
            .map(key -> getWakeup(BreweryUtil.uuidFromString(key)))
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public void saveAllWakeups(Collection<Wakeup> wakeups) {
        dataFile.set("wakeups", null);
        for (Wakeup wakeup : wakeups) {
            saveWakeup(wakeup);
        }
    }

    @Override
    public void saveWakeup(Wakeup wakeup) {
        String path = "wakeups." + wakeup.getId();
        dataFile.set(path + ".location", serializeLocation(wakeup.getLocation(), true));
        save();
    }

    @Override
    public void deleteWakeup(UUID id) {
        dataFile.set("wakeups." + id, null);
        save();
    }

    @Override
    public BreweryMiscData getBreweryMiscData() {
        return new BreweryMiscData(
            dataFile.getLong("misc.installTime", System.currentTimeMillis()),
            dataFile.getLong("misc.mcBarrelTime", 0),
            dataFile.getLongList("misc.previousSaveSeeds"),
            dataFile.getIntegerList("misc.brewsCreated"),
            dataFile.getInt("misc.brewsCreatedHash", 0)
        );
    }

    @Override
    public void saveBreweryMiscData(BreweryMiscData data) {
        dataFile.set("misc.installTime", data.installTime());
        dataFile.set("misc.mcBarrelTime", data.mcBarrelTime());
        dataFile.set("misc.previousSaveSeeds", data.prevSaveSeeds());
        dataFile.set("misc.brewsCreated", data.brewsCreated());
        dataFile.set("misc.brewsCreatedHash", data.brewsCreatedHash());
        save();
    }
}
