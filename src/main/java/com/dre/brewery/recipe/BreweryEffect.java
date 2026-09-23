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

package com.dre.brewery.recipe;

import com.dre.brewery.utility.utils.BreweryUtil;
import com.dre.brewery.utility.BukkitEffectConstants;
import com.dre.brewery.utility.Logging;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BreweryEffect implements Cloneable {

    @Getter private final PotionEffectType type;
    private short minLevel;
    private short maxLevel;
    private short minDuration;
    private short maxDuration;
    @Getter private boolean hidden = false;


    public BreweryEffect(PotionEffectType type, short minLevel, short maxLevel, short minDuration, short maxDuration, boolean hidden) {
        this.type = type;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.hidden = hidden;
    }

    public BreweryEffect(String effectString) {
        String[] effectSplit = effectString.split("/");
        String effect = effectSplit[0];
        if (effect.equalsIgnoreCase("WEAKNESS") ||
            effect.equalsIgnoreCase("INCREASE_DAMAGE") ||
            effect.equalsIgnoreCase("SLOW") ||
            effect.equalsIgnoreCase("SPEED") ||
            effect.equalsIgnoreCase("REGENERATION")) {
            // hide these effects as they put crap into lore
            // Dont write Regeneration into Lore, its already there storing data!
            hidden = true;
        } else if (effect.endsWith("X")) {
            hidden = true;
            effect = effect.substring(0, effect.length() - 1);
        }
        type = BukkitEffectConstants.nullablePotionEffectType(effect.toLowerCase());
        if (type == null) {
            Logging.errorLog("Effect: " + effect + " does not exist!");
            return;
        }

        if (effectSplit.length == 3) {
            String[] range = effectSplit[1].split("-");
            if (type.isInstant()) {
                setLevel(range);
            } else {
                setLevel(range);
                range = effectSplit[2].split("-");
                setDuration(range);
            }
        } else if (effectSplit.length == 2) {
            String[] range = effectSplit[1].split("-");
            if (type.isInstant()) {
                setLevel(range);
            } else {
                setDuration(range);
                maxLevel = 3;
                minLevel = 1;
            }
        } else {
            maxDuration = 20;
            minDuration = 10;
            maxLevel = 3;
            minLevel = 1;
        }
    }

    private void setLevel(String[] range) {
        if (range.length == 1) {
            maxLevel = (short) BreweryUtil.getRandomIntInRange(range[0]);
            minLevel = 1;
        } else {
            maxLevel = (short) BreweryUtil.getRandomIntInRange(range[1]);
            minLevel = (short) BreweryUtil.getRandomIntInRange(range[0]);
        }
    }

    private void setDuration(String[] range) {
        if (range.length == 1) {
            maxDuration = (short) BreweryUtil.getRandomIntInRange(range[0]);
            minDuration = (short) (maxDuration / 8);
        } else {
            maxDuration = (short) BreweryUtil.getRandomIntInRange(range[1]);
            minDuration = (short) BreweryUtil.getRandomIntInRange(range[0]);
        }
    }

    public PotionEffect generateEffect(int quality) {
        int duration = calcDuration(quality);
        int lvl = calcLvl(quality);

        if (lvl < 1 || (duration < 1 && !type.isInstant())) {
            return null;
        }

        duration *= 20;
        return type.createEffect(duration, lvl - 1);
    }

    public void apply(int quality, Player player) {
        PotionEffect effect = generateEffect(quality);
        if (effect != null) {
            BreweryUtil.reapplyPotionEffect(player, effect, true);
        }
    }

    public int calcDuration(float quality) {
        return (int) Math.round(minDuration + ((maxDuration - minDuration) * (quality / 10.0)));
    }

    public int calcLvl(float quality) {
        return (int) Math.round(minLevel + ((maxLevel - minLevel) * (quality / 10.0)));
    }

    public void writeInto(PotionMeta itemMeta, int quality) {
        if ((calcDuration(quality) > 0 || type.isInstant()) && calcLvl(quality) > 0) {
            itemMeta.addCustomEffect(type.createEffect(0, 0), true);
        } else {
            itemMeta.removeCustomEffect(type);
        }
    }

    public boolean isValid() {
        return type != null && minLevel >= 0 && maxLevel >= 0 && minDuration >= 0 && maxDuration >= 0;
    }

    @Override
    public String toString() {
        return "BreweryEffect{" +
            "type = " + type.getName() +
            ", level = " + minLevel + '-' + maxLevel +
            ", duration = " + minDuration + '-' + maxDuration +
            ", hidden = " + hidden +
            '}';
    }

    @Override
    public BreweryEffect clone() {
        try {
            BreweryEffect clone = (BreweryEffect) super.clone();
            clone.minLevel = minLevel;
            clone.maxLevel = maxLevel;
            clone.minDuration = minDuration;
            clone.maxDuration = maxDuration;
            clone.hidden = hidden;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
