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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * YAML reading/writing for the recipe files, backed by Jackson.
 * <p>
 * Unlike the rest of the configuration this deliberately does not use Okaeri: a recipe file is a flat map of
 * independent recipe nodes, so each node can be parsed on its own and a broken entry never takes the whole
 * file down with it. When writing, the file is wrapped in the localized header/footer of the active language.
 */
public final class RecipeYaml {

    private static final ObjectMapper MAPPER = new ObjectMapper(
        YAMLFactory.builder()
            // Matches the compact output of the previous config system
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build()
    ).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String IMPORTANT_HEADER =
        "!!! IMPORTANT: BreweryX configuration files do NOT support external comments! If you add any comments, they will be overwritten !!!";

    private RecipeYaml() {
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
     * Writes the given tree to the file, surrounded by the localized header and footer comments.
     *
     * @param file The file to write
     * @param root The tree to write, null is treated as an empty document
     */
    public static void write(Path file, @Nullable JsonNode root) {
        try {
            StringBuilder contents = new StringBuilder();
            appendComments(contents, List.of(IMPORTANT_HEADER));
            appendComments(contents, translationLines("recipesFile.header"));
            contents.append('\n').append(root != null ? MAPPER.writeValueAsString(root) : "");

            List<String> footer = translationLines("recipesFile.footer");
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
     * @param key The translation key below the {@code config-langs/<language>.yml} root
     * @return The translated text split into lines, empty when there is no translation
     */
    private static List<String> translationLines(String key) {
        String translation = TranslationManager.getInstance().getTranslationWithFallback(key);
        if (translation == null) {
            return List.of();
        }
        return translation.lines().toList();
    }
}
