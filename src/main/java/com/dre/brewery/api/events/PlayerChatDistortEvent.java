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

package com.dre.brewery.api.events;

import com.dre.brewery.mechanics.BreweryPlayer;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The Player writes something in Chat or on a Sign and his words are distorted.
 *
 * <p>This Event may be Async if the Chat Event is Async!
 */
public class PlayerChatDistortEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    /** The Player that wrote the message. */
    @Getter private final Player player;
    /** The brewer of the player, if any. */
    @Getter private final BreweryPlayer breweryPlayer;
    /** The Message the Player had actually written. */
    @Getter private final String writtenMessage;
    /** The message after it was distorted. */
    @Getter private String distortedMessage;
    private boolean cancelled;

    public PlayerChatDistortEvent(boolean async, Player player, BreweryPlayer breweryPlayer, String writtenMessage, String distortedMessage) {
        super(async);
        this.player = player;
        this.breweryPlayer = breweryPlayer;
        this.writtenMessage = writtenMessage;
        this.distortedMessage = distortedMessage;
    }

    /**
     * @return The drunkenness of the player that is writing the message
     */
    public int getDrunkenness() {
        return breweryPlayer.getDrunkenness();
    }

    /**
     * Set the Message that the player will say instead of what he wrote
     */
    public void setDistortedMessage(String distortedMessage) {
        this.distortedMessage = Objects.requireNonNull(distortedMessage);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    // Required by Bukkit
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
