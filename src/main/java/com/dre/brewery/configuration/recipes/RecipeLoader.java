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

import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.recipe.BreweryRecipe;
import com.dre.brewery.utility.Logging;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
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
 * Loads the configured brewing recipes.
 * <p>
 * Recipes come from the dedicated {@code recipes.yml} (created from the bundled default on first run) and,
 * for backwards compatibility, from the {@code recipes} section of {@code config.yml}. Both sources use the
 * same DSL and are parsed by {@link RecipeDefinition} via Jackson, entry by entry, so a single broken recipe
 * only fails on its own.
 * <p>
 * Recipes may either be the top level nodes of the file or live below a {@code recipes} node; the latter is
 * what older BreweryX versions wrote and is also the shape this loader writes back.
 */
public final class RecipeLoader {

    private static final String RECIPES_FILE_NAME = "recipes.yml";

    /**
     * The wrapper older BreweryX versions used for the recipes in {@code recipes.yml}.
     */
    private static final String RECIPES_KEY = "recipes";

    /**
     * All keys a recipe understands, used to point users at typos instead of silently ignoring them.
     */
    private static final Set<String> KNOWN_KEYS = knownKeys();

    private RecipeLoader() {
    }

    /**
     * Reloads all config recipes into {@link BreweryRecipe#getConfigRecipes()}.
     */
    public static void loadRecipes() {
        List<BreweryRecipe> configRecipes = BreweryRecipe.getConfigRecipes();
        configRecipes.clear();

        for (Map.Entry<String, RecipeDefinition> entry : loadDefinitions().entrySet()) {
            BreweryRecipe recipe = entry.getValue().toBRecipe(entry.getKey());
            if (recipe != null && recipe.isValid()) {
                configRecipes.add(recipe);
            } else {
                Logging.errorLog("Loading the Recipe with id: '" + entry.getKey() + "' failed!");
            }
        }
        BreweryRecipe.setNumConfigRecipes(configRecipes.size());
    }

    /**
     * @return The recipe definitions of all sources, keyed by their id
     */
    private static Map<String, RecipeDefinition> loadDefinitions() {
        ObjectMapper mapper = RecipeYaml.mapper();
        Path file = recipesFilePath();
        prepareFile(mapper, file);

        Map<String, JsonNode> nodes = new LinkedHashMap<>(rawRecipes(RecipeYaml.read(file)));
        // Recipes declared in config.yml take priority, same as before
        nodes.putAll(rawRecipes(embeddedRecipes()));

        Map<String, RecipeDefinition> definitions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : nodes.entrySet()) {
            // Disabled recipes are dropped before parsing, so an intentionally disabled entry may be incomplete
            if (!isEnabled(entry.getValue())) {
                continue;
            }
            warnUnknownKeys(entry.getKey(), entry.getValue());
            try {
                definitions.put(entry.getKey(), mapper.treeToValue(entry.getValue(), RecipeDefinition.class));
            } catch (Exception e) {
                RecipeParseException recipeError = asRecipeError(e);
                if (recipeError != null) {
                    // A problem with the recipe itself, the message is already translated and clear
                    Logging.errorLog("Failed to load the Recipe with id: '" + entry.getKey() + "': " + recipeError.getMessage());
                } else {
                    Logging.errorLog("Failed to load the Recipe with id: '" + entry.getKey() + "'!", e);
                }
            }
        }
        return definitions;
    }

    /**
     * Jackson wraps anything a deserializer throws, so the meaningful error has to be dug back out.
     *
     * @return The {@link RecipeParseException} of the cause chain, or null if this is not a recipe problem
     */
    @Nullable
    private static RecipeParseException asRecipeError(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof RecipeParseException recipeError) {
                return recipeError;
            }
        }
        return null;
    }

    private static Path recipesFilePath() {
        return ConfigManager.getFilePath(Config.class).resolveSibling(RECIPES_FILE_NAME);
    }

    /**
     * Creates {@code recipes.yml} from the bundled default on first run and adds newly shipped default
     * recipes to already existing files.
     */
    private static void prepareFile(ObjectMapper mapper, Path file) {
        Map<String, JsonNode> defaults = rawRecipes(readBundledDefaults(mapper));
        if (defaults.isEmpty()) {
            return;
        }

        JsonNode userRoot = null;
        if (Files.exists(file)) {
            try {
                userRoot = mapper.readTree(file.toFile());
            } catch (IOException e) {
                // Never touch a file we could not understand
                Logging.errorLog("Could not parse " + RECIPES_FILE_NAME + ", not updating it: " + e.getMessage());
                return;
            }
        }

        ObjectNode recipes;
        boolean changed;
        JsonNode wrapped = userRoot != null && userRoot.isObject() ? userRoot.get(RECIPES_KEY) : null;
        if (wrapped instanceof ObjectNode wrappedNode) {
            recipes = wrappedNode;
            changed = false;
        } else if (wrapped == null && userRoot instanceof ObjectNode bareRoot) {
            // A file without the wrapper is still ours, it just gets the wrapper added on write
            recipes = bareRoot;
            changed = true;
        } else if (userRoot == null) {
            recipes = mapper.createObjectNode();
            changed = true;
        } else {
            Logging.errorLog("The '" + RECIPES_KEY + "' node of " + RECIPES_FILE_NAME + " is not a map, not updating it!");
            return;
        }

        for (Map.Entry<String, JsonNode> entry : defaults.entrySet()) {
            if (!recipes.has(entry.getKey())) {
                recipes.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }

        if (!changed) {
            return;
        }
        ObjectNode root = mapper.createObjectNode();
        root.set(RECIPES_KEY, recipes);
        RecipeYaml.write(file, root);
    }

    @Nullable
    private static JsonNode readBundledDefaults(ObjectMapper mapper) {
        try (InputStream inputStream = RecipeLoader.class.getClassLoader().getResourceAsStream(RECIPES_FILE_NAME)) {
            if (inputStream == null) {
                Logging.errorLog("Bundled resource " + RECIPES_FILE_NAME + " is missing!");
                return null;
            }
            return mapper.readTree(inputStream);
        } catch (IOException e) {
            Logging.errorLog("Could not read the bundled " + RECIPES_FILE_NAME + "!", e);
            return null;
        }
    }

    @Nullable
    private static JsonNode embeddedRecipes() {
        Path configFile = ConfigManager.getFilePath(Config.class);
        if (!Files.exists(configFile)) {
            return null;
        }
        try {
            JsonNode root = RecipeYaml.mapper().readTree(configFile.toFile());
            return root == null ? null : root.get("recipes");
        } catch (IOException e) {
            Logging.errorLog("Could not read the recipes section of config.yml!", e);
            return null;
        }
    }

    /**
     * @return The recipes of the given node, unwrapping the {@code recipes} node older versions wrote
     */
    private static Map<String, JsonNode> rawRecipes(@Nullable JsonNode root) {
        Map<String, JsonNode> recipes = new LinkedHashMap<>();
        if (root == null || !root.isObject()) {
            return recipes;
        }

        JsonNode wrapped = root.get(RECIPES_KEY);
        JsonNode recipesNode = wrapped != null && wrapped.isObject() ? wrapped : root;
        for (Iterator<Map.Entry<String, JsonNode>> fields = recipesNode.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> field = fields.next();
            recipes.put(field.getKey(), field.getValue());
        }
        return recipes;
    }

    /**
     * A recipe that is explicitly disabled is allowed to be incomplete, so it is never parsed.
     */
    private static boolean isEnabled(@Nullable JsonNode node) {
        if (node == null) {
            return false;
        }
        JsonNode enabled = node.get("enabled");
        return enabled == null || enabled.isNull() || enabled.asBoolean(true);
    }

    /**
     * Unknown recipe options are ignored by the parser, so they are reported to make typos visible.
     */
    private static void warnUnknownKeys(String id, @Nullable JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        List<String> unknown = new ArrayList<>();
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!KNOWN_KEYS.contains(name)) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            Logging.warningLog("Recipe '" + id + "' has unknown option(s) that will be ignored: " + String.join(", ", unknown));
        }
    }

    private static Set<String> knownKeys() {
        Set<String> keys = new HashSet<>();
        keys.add("enabled");
        for (Field field : RecipeDefinition.class.getDeclaredFields()) {
            JsonProperty renamed = field.getAnnotation(JsonProperty.class);
            keys.add(renamed != null ? renamed.value() : field.getName());
        }
        return Set.copyOf(keys);
    }
}
