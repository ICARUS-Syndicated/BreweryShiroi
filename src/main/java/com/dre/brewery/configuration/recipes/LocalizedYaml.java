/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2026 Ph0sphorW
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

package com.dre.brewery.configuration.recipes;

import com.dre.brewery.configuration.configurer.TranslationManager;
import com.dre.brewery.utility.Logging;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * YAML reading/writing for the files of the recipe system, backed by Jackson.
 * <p>
 * Unlike the rest of the configuration these do not use Okaeri: each file is a flat map of independent
 * entries, so every entry can be parsed on its own and a broken one never takes the whole file down with it.
 * When a file is written it is wrapped in the localized header/footer of the active language.
 */
public final class LocalizedYaml {

    private static final ObjectMapper MAPPER = new ObjectMapper(
        YAMLFactory.builder()
            // Matches the compact output of the previous config system
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build()
    ).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String IMPORTANT_HEADER =
        "!!! IMPORTANT: BreweryX configuration files do NOT support external comments! If you add any comments, they will be overwritten !!!";

    private LocalizedYaml() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Reads a YAML file into a node tree.
     *
     * @param file The file to read
     * @return The parsed tree, or null when the file could not be read
     */
    @Nullable
    public static JsonNode read(Path file) {
        try {
            return MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            Logging.errorLog("Could not read " + file.getFileName() + "!", e);
            return null;
        }
    }

    /**
     * Reads one of the default files shipped inside the jar.
     *
     * @param resourceName The name of the resource to read
     * @return The parsed tree, or null when the resource is missing or unreadable
     */
    @Nullable
    public static JsonNode readBundled(String resourceName) {
        try (InputStream inputStream = LocalizedYaml.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                Logging.errorLog("Bundled resource " + resourceName + " is missing!");
                return null;
            }
            return MAPPER.readTree(inputStream);
        } catch (IOException e) {
            Logging.errorLog("Could not read the bundled " + resourceName + "!", e);
            return null;
        }
    }

    /**
     * Writes the given tree to the file, surrounded by the localized header and footer comments.
     *
     * @param file              The file to write
     * @param translationPrefix The key prefix of the translations,
     *                          i.e. {@code cauldronFile} for the {@code cauldronFile.header}/{@code .footer} keys
     * @param root              The tree to write, null is treated as an empty document
     */
    public static void write(Path file, String translationPrefix, @Nullable JsonNode root) {
        try {
            StringBuilder contents = new StringBuilder();
            appendComments(contents, List.of(IMPORTANT_HEADER));
            appendComments(contents, translationLines(translationPrefix + ".header", false));
            contents.append('\n').append(root != null ? MAPPER.writeValueAsString(root) : "");

            List<String> footer = translationLines(translationPrefix + ".footer", true);
            if (!footer.isEmpty()) {
                contents.append('\n').append('\n');
                appendComments(contents, footer);
            }

            Files.createDirectories(file.getParent());
            Files.writeString(file, contents.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logging.errorLog("Could not save " + file.getFileName() + "!", e);
        }
    }

    private static void appendComments(StringBuilder contents, List<String> lines) {
        for (String line : lines) {
            contents.append(line.isEmpty() ? "#" : "# " + line).append('\n');
        }
    }

    /**
     * @param key      The translation key below the {@code config-langs/<language>.yml} root
     * @param optional True when a missing translation is expected and must not be warned about (i.e. a footer)
     * @return The translated text split into lines, empty when there is no translation
     */
    private static List<String> translationLines(String key, boolean optional) {
        TranslationManager translationManager = TranslationManager.getInstance();
        String translation = optional
            ? translationManager.getOptionalTranslation(key)
            : translationManager.getTranslationWithFallback(key);
        if (translation == null) {
            return List.of();
        }
        return translation.lines().toList();
    }
}
