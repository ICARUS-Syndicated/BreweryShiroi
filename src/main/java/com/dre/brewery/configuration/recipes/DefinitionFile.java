/*
 * BreweryShiroi Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2026 Ph0sphorW
 *
 * This file is part of BreweryShiroi.
 *
 * BreweryShiroi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryShiroi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryShiroi. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.configuration.recipes;

import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.utility.Logging;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One of the YAML files of the recipe system, holding a flat map of independent definitions.
 * <p>
 * Definitions of a file may either be its top level nodes or live below a wrapper node (the shape older
 * BreweryShiroi versions wrote). For backwards compatibility the same wrapper node of {@code config.yml} is read
 * as a second source, its entries taking priority.
 * <p>
 * A file is <b>only ever written when it does not exist yet</b>, in which case the bundled defaults are placed
 * in it together with the localized header/footer. An existing file is never touched, so user files keep their
 * comments, formatting and content.
 *
 * @param <T> The definition type one entry of the file is parsed into
 */
public final class DefinitionFile<T> {

    private final String fileName;
    private final String translationPrefix;
    private final String sectionKey;
    private final String label;
    private final Class<T> type;
    private final Set<String> knownKeys;

    /**
     * @param fileName          The name of the file inside the plugin data folder, i.e. {@code recipes.yml}
     * @param translationPrefix The key prefix of the translations, see {@link LocalizedYaml#write}
     * @param sectionKey        The wrapper node of the file and the matching section of {@code config.yml},
     *                          i.e. {@code recipes}
     * @param label             How a single entry is called in log messages, i.e. {@code Recipe}
     * @param type              The definition type one entry is parsed into
     */
    public DefinitionFile(String fileName, String translationPrefix, String sectionKey, String label, Class<T> type) {
        this.fileName = fileName;
        this.translationPrefix = translationPrefix;
        this.sectionKey = sectionKey;
        this.label = label;
        this.type = type;
        this.knownKeys = knownKeys(type);
    }

    /**
     * @return The definitions of the file and of the {@code config.yml} section, keyed by their id
     */
    public Map<String, T> load() {
        Path file = filePath();
        createIfMissing(file);

        Map<String, JsonNode> nodes = new LinkedHashMap<>(entries(LocalizedYaml.read(file)));
        // Definitions declared in config.yml take priority, same as before
        nodes.putAll(entries(embeddedSection()));

        Map<String, T> definitions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : nodes.entrySet()) {
            // A disabled entry is allowed to be incomplete, so it is never parsed
            if (!isEnabled(entry.getValue())) {
                continue;
            }
            warnUnknownKeys(entry.getKey(), entry.getValue());
            try {
                definitions.put(entry.getKey(), LocalizedYaml.mapper().treeToValue(entry.getValue(), type));
            } catch (Exception e) {
                RecipeParseException parseError = asParseError(e);
                if (parseError != null) {
                    // A problem with the entry itself, the message is already translated and clear
                    Logging.errorLog("Failed to load the " + label + " with id: '" + entry.getKey() + "': " + parseError.getMessage());
                } else {
                    Logging.errorLog("Failed to load the " + label + " with id: '" + entry.getKey() + "'!", e);
                }
            }
        }
        return definitions;
    }

    private Path filePath() {
        return ConfigManager.getFilePath(Config.class).resolveSibling(fileName);
    }

    /**
     * Places the bundled defaults in the file when it does not exist yet. Existing files are left alone.
     */
    private void createIfMissing(Path file) {
        if (Files.exists(file)) {
            return;
        }

        Map<String, JsonNode> defaults = entries(LocalizedYaml.readBundled(fileName));
        if (defaults.isEmpty()) {
            return;
        }

        ObjectMapper mapper = LocalizedYaml.mapper();
        ObjectNode section = mapper.createObjectNode();
        defaults.forEach(section::set);

        ObjectNode root = mapper.createObjectNode();
        root.set(sectionKey, section);
        LocalizedYaml.write(file, translationPrefix, root);
        Logging.log("Created a new configurable file: &6" + fileName);
    }

    /**
     * @return The {@code sectionKey} node of {@code config.yml}, or null when it is absent
     */
    @Nullable
    private JsonNode embeddedSection() {
        Path configFile = ConfigManager.getFilePath(Config.class);
        if (!Files.exists(configFile)) {
            return null;
        }
        try {
            JsonNode root = LocalizedYaml.mapper().readTree(configFile.toFile());
            return root == null ? null : root.get(sectionKey);
        } catch (IOException e) {
            Logging.errorLog("Could not read the '" + sectionKey + "' section of config.yml!", e);
            return null;
        }
    }

    /**
     * @return The entries of the given node, unwrapping the optional {@code sectionKey} node
     */
    private Map<String, JsonNode> entries(@Nullable JsonNode root) {
        Map<String, JsonNode> entries = new LinkedHashMap<>();
        if (root == null || !root.isObject()) {
            return entries;
        }

        JsonNode wrapped = root.get(sectionKey);
        JsonNode section = wrapped != null && wrapped.isObject() ? wrapped : root;
        for (Iterator<Map.Entry<String, JsonNode>> fields = section.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> field = fields.next();
            entries.put(field.getKey(), field.getValue());
        }
        return entries;
    }

    /**
     * An entry that is explicitly disabled is allowed to be incomplete, so it is never parsed.
     */
    private boolean isEnabled(@Nullable JsonNode node) {
        if (node == null) {
            return false;
        }
        JsonNode enabled = node.get("enabled");
        return enabled == null || enabled.isNull() || enabled.asBoolean(true);
    }

    /**
     * Unknown options are ignored by the parser, so they are reported to make typos visible.
     */
    private void warnUnknownKeys(String id, @Nullable JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        List<String> unknown = new ArrayList<>();
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!knownKeys.contains(name)) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            Logging.warningLog(label + " '" + id + "' has unknown option(s) that will be ignored: " + String.join(", ", unknown));
        }
    }

    /**
     * Jackson wraps anything a deserializer throws, so the meaningful error has to be dug back out.
     *
     * @return The {@link RecipeParseException} of the cause chain, or null if this is not an entry problem
     */
    @Nullable
    private static RecipeParseException asParseError(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof RecipeParseException parseError) {
                return parseError;
            }
        }
        return null;
    }

    /**
     * @return All option names the definition type understands, as they appear in the file
     */
    private static Set<String> knownKeys(Class<?> type) {
        Set<String> keys = new HashSet<>();
        keys.add("enabled");
        for (Field field : type.getDeclaredFields()) {
            JsonProperty renamed = field.getAnnotation(JsonProperty.class);
            keys.add(renamed != null ? renamed.value() : field.getName());
        }
        return Set.copyOf(keys);
    }
}
