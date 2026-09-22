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

package com.dre.brewery.commands;

import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.utils.BreweryUtil;
import lombok.Getter;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.caption.CaptionProvider;
import org.incendo.cloud.caption.CaptionRegistry;
import org.incendo.cloud.caption.StandardCaptionKeys;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.minecraft.extras.AudienceProvider;
import org.incendo.cloud.minecraft.extras.ImmutableMinecraftHelp;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the {@link PaperCommandManager} of BreweryX and registers every command on it.
 * <p>
 * The plugin main class only calls {@link #register()}, all commands live in this package, see
 * {@link BreweryCommands} for the built-in ones.
 * <p>
 * Commands of addons may still implement the legacy {@link SubCommand} interface. Those are bridged onto the
 * Cloud command tree by {@link #addSubCommand(String, SubCommand)} and receive the raw argument array, so addons
 * written against the old API keep working.
 */
public class BreweryCommandManager {

    private final BreweryPlugin plugin;
    private final PaperCommandManager<Source> manager;

    /**
     * The bridged legacy {@link SubCommand}s, keyed by the name they are registered under.
     */
    private final Map<String, SubCommand> bridgedCommands = new LinkedHashMap<>();

    @Getter
    private final MinecraftHelp<Source> help;

    public BreweryCommandManager(@NotNull BreweryPlugin plugin) {
        this.plugin = plugin;
        this.manager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper())
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildOnEnable(plugin);

        this.help = ImmutableMinecraftHelp.<Source>builder()
            .commandManager(this.manager)
            .audienceProvider(audienceProvider())
            .commandPrefix("/" + rootName() + " help")
            .messages(helpMessages())
            .build();
    }

    public PaperCommandManager<Source> manager() {
        return this.manager;
    }

    public BreweryPlugin plugin() {
        return this.plugin;
    }

    public Lang lang() {
        return ConfigManager.getConfig(Lang.class);
    }

    /**
     * Registers every command of BreweryX, this is the only method the plugin main class has to call.
     */
    public void register() {
        registerLocalizedCaptions();

        // The bare command shows the help, just like it did before
        this.manager.command(root().handler(context -> showHelp(context.sender(), "")));
        this.manager.command(root().literal("help")
            .optional("query", StringParser.greedyStringParser())
            .handler(context -> showHelp(context.sender(), context.optional("query").map(Object::toString).orElse(""))));

        BreweryCommands.registerAll(this);
    }

    /**
     * Replaces the English captions of the command framework with the values of the language file, so errors
     * like a missing permission or a malformed argument are translated as well.
     */
    private void registerLocalizedCaptions() {
        Lang lang = lang();
        CaptionRegistry<Source> registry = this.manager.captionRegistry();
        registry.registerProvider(CaptionProvider.constantProvider(
            StandardCaptionKeys.EXCEPTION_NO_PERMISSION, lang.getPlainEntry("Error_NoPermissions")));
        registry.registerProvider(CaptionProvider.constantProvider(
            StandardCaptionKeys.EXCEPTION_NO_SUCH_COMMAND, lang.getPlainEntry("Error_UnknownCommand")));
        registry.registerProvider(CaptionProvider.constantProvider(
            StandardCaptionKeys.EXCEPTION_INVALID_SENDER, lang.getPlainEntry("Error_PlayerCommand")));
        registry.registerProvider(CaptionProvider.constantProvider(
            StandardCaptionKeys.EXCEPTION_INVALID_SENDER_LIST, lang.getPlainEntry("Error_PlayerCommand")));
        registry.registerProvider(CaptionProvider.constantProvider(
            StandardCaptionKeys.EXCEPTION_INVALID_SYNTAX, lang.getPlainEntry("Error_InvalidSyntax")));
        registry.registerProvider(CaptionProvider.constantProvider(
            StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_NUMBER, lang.getPlainEntry("Error_InvalidNumber")));
        registry.registerProvider(CaptionProvider.constantProvider(
            StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_STRING, lang.getPlainEntry("Error_InvalidArgument")));
        registry.registerProvider(CaptionProvider.constantProvider(
            StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_ENUM, lang.getPlainEntry("Error_InvalidArgument")));
    }

    /**
     * Shows the help screen, optionally filtered by a search query.
     * <p>
     * A plain number is understood as a page of the command index, anything else as a search query. Queries are
     * resolved against the full command path, so {@code help create} is turned into {@code help breweryx create}
     * internally, which is what the help screen expects.
     *
     * @param source The sender to show the help to
     * @param query  The search query or page number, empty to show every command
     */
    public void showHelp(Source source, String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            this.help.queryCommands("", source);
        } else if (trimmed.chars().allMatch(Character::isDigit)) {
            // The help screen reads a trailing number as the page to show
            this.help.queryCommands(" " + trimmed, source);
        } else if (trimmed.startsWith(rootName())) {
            this.help.queryCommands(trimmed, source);
        } else {
            this.help.queryCommands(rootName() + " " + trimmed, source);
        }
    }

    /**
     * Creates a builder for a subcommand of {@code /breweryx}.
     *
     * @param name           The name of the subcommand
     * @param permission     The permission node required to use it, may be null
     * @param descriptionKey The {@code Help_*} translation key describing the subcommand
     * @return The command builder with name, permission and description already applied
     */
    public Command.Builder<Source> command(String name, @Nullable String permission, String descriptionKey) {
        Command.Builder<Source> builder = root().literal(name)
            .commandDescription(Description.of(lang().getCommandDescription(descriptionKey)));
        if (permission != null) {
            builder.permission(Permission.of(permission));
        }
        return builder;
    }

    /**
     * Creates a builder for a subcommand of {@code /breweryx} that needs no permission.
     */
    public Command.Builder<Source> command(String name, String descriptionKey) {
        return command(name, null, descriptionKey);
    }

    /**
     * @return A builder for the root command, with the configured aliases attached
     */
    public Command.Builder<Source> root() {
        List<String> names = new ArrayList<>();
        names.add(rootName());
        names.addAll(rootAliases());
        return this.manager.commandBuilder(names.remove(0), names.toArray(String[]::new));
    }

    /**
     * @return The name of the root command, {@code breweryx}
     */
    public static String rootName() {
        return "breweryx";
    }

    /**
     * @return The aliases configured in the config, excluding the root name itself
     */
    public static List<String> rootAliases() {
        Set<String> aliases = new LinkedHashSet<>();
        for (String alias : ConfigManager.getConfig(Config.class).getCommandAliases()) {
            if (alias != null && !alias.isBlank() && !alias.equalsIgnoreCase(rootName())) {
                aliases.add(alias.toLowerCase());
            }
        }
        return List.copyOf(aliases);
    }

    /**
     * Bridges a legacy {@link SubCommand} onto the command tree so addons keep working.
     * <p>
     * The bridged command receives its own name as first argument, followed by everything the sender typed
     * (split on whitespace, quotes respected), exactly like the old command system did.
     *
     * @param name    The name of the subcommand
     * @param command The command to execute
     */
    public void addSubCommand(String name, SubCommand command) {
        String key = name.toLowerCase();
        if (this.bridgedCommands.containsKey(key)) {
            Logging.warningLog("SubCommand with name: &6" + key + " &ealready exists! It's being overwritten!");
            removeSubCommand(key);
        }
        this.bridgedCommands.put(key, command);
        registerBridgedCommand(key, command);
    }

    /**
     * Registers a command of an addon under several names.
     */
    public void addSubCommand(SubCommand command, String... names) {
        for (String name : names) {
            addSubCommand(name, command);
        }
    }

    /**
     * Removes a bridged command by its name.
     */
    public void removeSubCommand(String name) {
        String key = name.toLowerCase();
        if (this.bridgedCommands.remove(key) != null) {
            unregister(key);
        }
    }

    /**
     * Removes every bridged command of the given command.
     */
    public void removeSubCommand(SubCommand command) {
        for (Map.Entry<String, SubCommand> entry : List.copyOf(this.bridgedCommands.entrySet())) {
            if (entry.getValue() == command) {
                removeSubCommand(entry.getKey());
            }
        }
    }

    private void registerBridgedCommand(String name, SubCommand command) {
        this.manager.command(command(name, command.permission(), "Help_Help")
            .optional("args", StringParser.greedyStringParser())
            .handler(context -> {
                // The command may have been unregistered after it was put on the command tree
                SubCommand current = this.bridgedCommands.get(name);
                if (current == null) {
                    this.lang().sendEntry(context.sender().source(), "Error_UnknownCommand");
                    return;
                }
                CommandSender sender = context.sender().source();
                if (current.playerOnly() && !(sender instanceof Player)) {
                    this.lang().sendEntry(sender, "Error_NotPlayer");
                    return;
                }
                List<String> arguments = new ArrayList<>();
                arguments.add(name);
                context.optional("args").ifPresent(raw ->
                    arguments.addAll(BreweryUtil.splitStringKeepingQuotes(raw.toString())));
                current.execute(this.plugin, this.lang(), sender, name, arguments.toArray(String[]::new));
            }));
    }

    /**
     * Removes a bridged subcommand from the command tree.
     * <p>
     * Brigadier can not forget a literal again, so the client may still suggest it until the server restarts.
     * Invoking it reports the command as unknown, see {@link #registerBridgedCommand}.
     */
    private void unregister(String name) {
        CommandNode<Source> rootNode = this.manager.commandTree().getNamedNode(rootName());
        if (rootNode == null) {
            return;
        }
        for (CommandNode<Source> child : List.copyOf(rootNode.children())) {
            if (child.component().name().equals(name)) {
                rootNode.removeChild(child);
                return;
            }
        }
    }

    /**
     * @return The provider used by the help screen to send messages to a sender
     */
    private static AudienceProvider<Source> audienceProvider() {
        return source -> {
            CommandSender sender = source.source();
            return sender instanceof Audience audience ? audience : Audience.empty();
        };
    }

    /**
     * Cloud ships its help captions in English only, they are replaced with the values of the language file.
     * <p>
     * Placeholders like {@code <page>} are kept as they are, the help screen fills them in itself.
     */
    private Map<String, String> helpMessages() {
        Lang lang = lang();
        Map<String, String> messages = new LinkedHashMap<>();
        messages.put(MinecraftHelp.MESSAGE_HELP_TITLE, lang.getPlainEntry("Help_Title"));
        messages.put(MinecraftHelp.MESSAGE_COMMAND, lang.getPlainEntry("Help_Command"));
        messages.put(MinecraftHelp.MESSAGE_DESCRIPTION, lang.getPlainEntry("Help_Description"));
        messages.put(MinecraftHelp.MESSAGE_NO_DESCRIPTION, lang.getPlainEntry("Help_NoDescription"));
        messages.put(MinecraftHelp.MESSAGE_ARGUMENTS, lang.getPlainEntry("Help_Arguments"));
        messages.put(MinecraftHelp.MESSAGE_OPTIONAL, lang.getPlainEntry("Help_Optional"));
        messages.put(MinecraftHelp.MESSAGE_AVAILABLE_COMMANDS, lang.getPlainEntry("Help_AvailableCommands"));
        messages.put(MinecraftHelp.MESSAGE_SHOWING_RESULTS_FOR_QUERY, lang.getPlainEntry("Help_ShowingResultsFor"));
        messages.put(MinecraftHelp.MESSAGE_NO_RESULTS_FOR_QUERY, lang.getPlainEntry("Help_NoResultsFor"));
        messages.put(MinecraftHelp.MESSAGE_CLICK_TO_SHOW_HELP, lang.getPlainEntry("Help_ClickToShow"));
        messages.put(MinecraftHelp.MESSAGE_PAGE_OUT_OF_RANGE, lang.getPlainEntry("Help_PageOutOfRange"));
        messages.put(MinecraftHelp.MESSAGE_CLICK_FOR_NEXT_PAGE, lang.getPlainEntry("Help_ClickNextPage"));
        messages.put(MinecraftHelp.MESSAGE_CLICK_FOR_PREVIOUS_PAGE, lang.getPlainEntry("Help_ClickPreviousPage"));
        return messages;
    }
}
