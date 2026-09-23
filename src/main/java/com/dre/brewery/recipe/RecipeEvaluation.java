/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024-2025 The Brewery Team
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

import com.dre.brewery.brew.BrewDefect;
import com.dre.brewery.utility.utils.BreweryUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class that keeps track of {@link BrewDefect BrewDefects} and their quality deductions.
 * Quality starts at 10, and is reduced by each defect.
 * <p>
 * The aggregate values behind {@link #getQuality()} and {@link #compareMostToLeastComplexity} are
 * maintained as deductions are added, because both are called once per loaded recipe on the recipe lookup
 * path. Recomputing them from the deduction list on every call meant walking a stream pipeline per recipe.
 */
public class RecipeEvaluation {

    private final List<QualityDeduction> deductions = new ArrayList<>();

    /**
     * The quality left after subtracting every non-fatal deduction, in the order they were added. Kept in
     * step with the list rather than recomputed, and deliberately subtracted one by one: adding the
     * deductions up first and subtracting the total would round differently and could flip a comparison.
     */
    private float runningQuality = 10f;

    private int fatalCount;

    /**
     * Deducts quality by the specified amount.
     * @param defect the defect that caused the quality deduction
     * @param qualityDeduction the amount to deduct by
     * @throws IllegalArgumentException if qualityDeduction is negative
     */
    public void deduct(BrewDefect defect, float qualityDeduction) {
        if (qualityDeduction < 0) {
            throw new IllegalArgumentException("qualityDeduction cannot be negative");
        }
        add(QualityDeduction.deduction(defect, qualityDeduction));
    }

    /**
     * Adds a fatal defect that prevents the recipe from being used.
     * @param defect the defect
     */
    public void fatal(BrewDefect defect) {
        add(QualityDeduction.fatal(defect));
    }

    /**
     * The only way a deduction enters this evaluation, so the aggregates cannot drift from the list.
     */
    private void add(QualityDeduction deduction) {
        deductions.add(deduction);
        if (deduction.isFatal()) {
            fatalCount++;
        } else {
            runningQuality -= deduction.getQualityDeduction();
        }
    }

    /**
     * Combines multiple RecipeEvaluations into one.
     * If there are {@code n} evaluations, each evaluation contributes {@code 1/n} of the quality.
     * @param evals the evaluations
     * @return the combined evaluation
     */
    public static RecipeEvaluation combine(RecipeEvaluation... evals) {
        RecipeEvaluation combined = new RecipeEvaluation();
        float factor = 1.0f / evals.length;
        for (RecipeEvaluation evaluation : evals) {
            for (QualityDeduction deduction : evaluation.deductions) {
                combined.add(deduction.scale(factor));
            }
        }
        return combined;
    }

    /**
     * @return whether {@link #getQuality()} is -1
     */
    public boolean isFatal() {
        return getTrueQuality() < 0;
    }

    /**
     * @return whether this evaluation found no defects at all, i.e. the recipe matches perfectly
     */
    public boolean isPerfect() {
        return deductions.isEmpty();
    }

    /**
     * @return whether there are any fatal defects
     */
    private boolean hasFatalDefect() {
        return fatalCount > 0;
    }

    /**
     * Gets the quality of the recipe.
     * If there are fatal defects, or if the quality is deducted to less than 0, the quality will be -1.
     * @return the quality, or -1 if the recipe is not usable
     */
    public float getQuality() {
        float quality = getTrueQuality();
        if (quality < 0) {
            return -1;
        }
        return quality;
    }

    /**
     * Gets the true quality of the recipe, without rounding or bounds.
     * Can be any number 10 or below. Will be negative infinity if there are fatal defects.
     * @return the true quality
     */
    public float getTrueQuality() {
        return hasFatalDefect() ? Float.NEGATIVE_INFINITY : runningQuality;
    }

    /**
     * @return all quality deductions, in arbitrary order
     */
    public List<QualityDeduction> getDeductions() {
        return Collections.unmodifiableList(deductions);
    }

    /**
     * Gets the defect that deducts the most quality from the recipe.
     * If there is a tie, multiple defects are returned.
     * @return list of defects, possibly empty
     */
    public List<BrewDefect> getWorstDefects() {
        if (hasFatalDefect()) {
            return deductions.stream()
                .filter(QualityDeduction::isFatal)
                .map(QualityDeduction::getDefect)
                .toList();
        } else {
            return BreweryUtil.multiMin(deductions).stream()
                .map(QualityDeduction::getDefect)
                .toList();
        }
    }

    /**
     * Compares two RecipeEvaluations. A positive result means this evaluation is the better recipe, decided
     * by, in order:
     * <ul>
     *     <li>Number of total defects, fewest first</li>
     *     <li>Whether the evaluation has fatal defects, non-fatal first</li>
     *     <li>Number of fatal defects, fewest first</li>
     *     <li>{@link #getTrueQuality()}, highest first</li>
     * </ul>
     * Note that the name describes the order these keys are declared in, not the preference: the caller
     * wants the recipe with the <em>fewest</em> defects, so a positive result is the less complex evaluation.
     * The ordering itself lives in {@link ComparisonKeys#isBetterThan}, so that the recipe lookup and this
     * method cannot drift apart.
     * @param other the other evaluation
     * @return -1, 0, or 1 if <, =, or >
     * @throws NullPointerException if other is null
     */
    public int compareMostToLeastComplexity(RecipeEvaluation other) {
        if (other == null) {
            throw new NullPointerException("other cannot be null");
        }
        ComparisonKeys thisKeys = new ComparisonKeys();
        thisKeys.combine(this);
        ComparisonKeys otherKeys = new ComparisonKeys();
        otherKeys.combine(other);
        if (thisKeys.isBetterThan(otherKeys)) {
            return 1;
        }
        if (otherKeys.isBetterThan(thisKeys)) {
            return -1;
        }
        return 0;
    }

    /**
     * The three aggregates {@link #compareMostToLeastComplexity} decides on, for a combination of
     * evaluations, computed without building the combined deduction list.
     * <p>
     * Scoring one recipe against every loaded recipe has to rank each combination before it knows whether
     * the combination wins. Producing an evaluation for the losers too would mean copying their deductions
     * into scaled duplicates for nothing, so the ranking is done on these keys and only the winning
     * combination is turned into an evaluation at the end.
     * <p>
     * Instances are meant to be reused: {@link #combine} overwrites the previous values.
     */
    public static final class ComparisonKeys {

        private int defectCount;
        private int fatalCount;
        private float trueQuality;

        /**
         * Recomputes the keys for the given evaluations.
         * @param evals the evaluations to combine, must not be empty
         */
        public void combine(RecipeEvaluation... evals) {
            int defects = 0;
            int fatals = 0;
            float quality = 10f;
            float factor = 1.0f / evals.length;
            for (RecipeEvaluation evaluation : evals) {
                for (QualityDeduction deduction : evaluation.deductions) {
                    defects++;
                    if (deduction.isFatal()) {
                        fatals++;
                    } else {
                        quality -= deduction.getQualityDeduction() * factor;
                    }
                }
            }
            this.defectCount = defects;
            this.fatalCount = fatals;
            this.trueQuality = quality;
        }

        /**
         * @return whether the combination has no defects at all
         */
        public boolean isPerfect() {
            return defectCount == 0;
        }

        /**
         * The same comparison as {@link RecipeEvaluation#compareMostToLeastComplexity}, reduced to the
         * question of whether these keys beat the other ones.
         * @param other the keys to compare against
         * @return whether the combination behind these keys is the better one
         */
        public boolean isBetterThan(ComparisonKeys other) {
            if (defectCount != other.defectCount) {
                return defectCount < other.defectCount;
            }
            boolean thisFatal = fatalCount > 0;
            boolean otherFatal = other.fatalCount > 0;
            if (!thisFatal && !otherFatal) {
                return Float.compare(trueQuality, other.trueQuality) > 0;
            }
            if (thisFatal && otherFatal) {
                return fatalCount < other.fatalCount;
            }
            return !thisFatal;
        }
    }

    @Override
    public String toString() {
        float quality = getTrueQuality();
        String qualityStr = quality == Float.NEGATIVE_INFINITY ? "fatal" : String.format("%.3f", quality);

        String deductionsStr = deductions.stream()
            .map(QualityDeduction::toString)
            .collect(Collectors.joining(", ", "[", "]"));

        return new StringBuilder("RecipeEvaluation{")
            .append("quality = ").append(qualityStr)
            .append(", deductions = ").append(deductionsStr)
            .append('}')
            .toString();
    }

}
