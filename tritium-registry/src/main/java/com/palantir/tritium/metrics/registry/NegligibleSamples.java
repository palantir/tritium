/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.tritium.metrics.registry;

import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Removes samples from an exponentially decaying reservoir whose weight is negligible next to its newest sample.
 *
 * <p>Without this, a sample only leaves the reservoir when the reservoir is full or when its weight underflows to
 * zero on a rescale, which takes about 15 hours with the default alpha of 0.015. A reservoir updated once a minute
 * therefore holds about 900 samples even though only the last few minutes of them influence its quantiles.
 *
 * <p>Samples weighing less than {@link #RELATIVE_WEIGHT_THRESHOLD} of the newest sample (about 15 minutes older with
 * the default alpha) are candidates. Removing weight moves every quantile's rank by at most the removed share of the
 * total weight, but a rank error can still change a value: a reservoir full of old slow samples followed by one fast
 * one would lose its whole p99.9 tail, and its mean, if all of them went. So candidates are removed only while
 * everything a reservoir has evicted stays within {@link #EVICTED_WEIGHT_BUDGET} of the weight it would otherwise
 * hold, lightest (oldest) first. That bounds every quantile's rank error at 0.001%, far inside p99.9's 0.1% tail, and
 * the mean's error at 0.001% of the value range. Under steady traffic the samples past the threshold only carry about
 * {@code RELATIVE_WEIGHT_THRESHOLD} of the weight, so the budget rarely binds; it does after a burst followed by a
 * trickle, keeping the burst until newer samples outweigh it. Concurrent snapshots each check the budget before the
 * others' evictions land, so k racing snapshots can exceed it up to k times; a metric reporter snapshots from one thread.
 *
 * <p>Comparing against the newest sample rather than the clock means an idle reservoir keeps its last samples, and so
 * keeps reporting the same quantiles.
 */
final class NegligibleSamples {

    static final double RELATIVE_WEIGHT_THRESHOLD = 1e-6;
    static final double EVICTED_WEIGHT_BUDGET = 1e-5;

    private NegligibleSamples() {}

    /**
     * The weight below which samples can be removed, or 0 if none can: below {@link #RELATIVE_WEIGHT_THRESHOLD} of
     * {@code maxWeight}, and only as many of the lightest as keep everything evicted, {@code evicted} so far plus these,
     * within {@link #EVICTED_WEIGHT_BUDGET} of the weight the reservoir would hold without eviction.
     */
    static double evictionThreshold(double[] weights, int size, double maxWeight, double totalWeight, double evicted) {
        double threshold = maxWeight * RELATIVE_WEIGHT_THRESHOLD;
        int candidates = 0;
        double candidateWeight = 0;
        for (int i = 0; i < size; i++) {
            if (weights[i] < threshold) {
                candidates++;
                candidateWeight += weights[i];
            }
        }
        double budget = EVICTED_WEIGHT_BUDGET * (totalWeight + evicted) - evicted;
        if (candidates == 0 || budget <= 0) {
            return 0;
        }
        if (candidateWeight <= budget) {
            return threshold;
        }
        // Over budget, which takes a burst followed by a trickle: take the lightest candidates that fit.
        return lightestWithin(weights, size, threshold, candidates, budget);
    }

    /** The weight below which the lightest of the {@code candidates} under {@code threshold} sum to {@code budget}. */
    private static double lightestWithin(double[] weights, int size, double threshold, int candidates, double budget) {
        double[] lightest = new double[candidates];
        for (int i = 0, j = 0; i < size; i++) {
            if (weights[i] < threshold) {
                lightest[j++] = weights[i];
            }
        }
        Arrays.sort(lightest);
        double removed = 0;
        for (int i = 0; i < lightest.length; i++) {
            if (removed + lightest[i] > budget) {
                // everything strictly lighter than this one fits
                return i == 0 ? 0 : lightest[i];
            }
            removed += lightest[i];
        }
        return threshold;
    }

    /**
     * Removes samples weighing less than {@code threshold} from {@code values}, returning how many were removed and
     * their weight. Entries are removed only if they still map to the sample that was examined, so concurrent updates
     * and evictions are not double-counted.
     */
    static <S> Evicted evictBelow(
            ConcurrentSkipListMap<Double, S> values, ToDoubleFunction<S> weight, double threshold) {
        // forEach walks the nodes directly, where entrySet() would allocate an entry per sample.
        Evictor<S> evictor = new Evictor<>(values, weight, threshold);
        values.forEach(evictor);
        return new Evicted(evictor.removed, evictor.removedWeight);
    }

    record Evicted(int samples, double weight) {}

    /** The weight a reservoir state has evicted so far, in that state's units: rescaled along with its samples. */
    static final class EvictedWeight {
        private final AtomicLong bits;

        EvictedWeight(double initial) {
            this.bits = new AtomicLong(Double.doubleToRawLongBits(initial));
        }

        double get() {
            return Double.longBitsToDouble(bits.get());
        }

        void add(double weight) {
            bits.accumulateAndGet(
                    Double.doubleToRawLongBits(weight),
                    (current, added) -> Double.doubleToRawLongBits(
                            Double.longBitsToDouble(current) + Double.longBitsToDouble(added)));
        }

        EvictedWeight scaled(double scalingFactor) {
            return new EvictedWeight(get() * scalingFactor);
        }
    }

    private static final class Evictor<S> implements BiConsumer<Double, S> {
        private final ConcurrentSkipListMap<Double, S> values;
        private final ToDoubleFunction<S> weight;
        private final double threshold;
        private int removed;
        private double removedWeight;

        Evictor(ConcurrentSkipListMap<Double, S> values, ToDoubleFunction<S> weight, double threshold) {
            this.values = values;
            this.weight = weight;
            this.threshold = threshold;
        }

        @Override
        public void accept(Double priority, S sample) {
            double sampleWeight = weight.applyAsDouble(sample);
            if (sampleWeight < threshold && values.remove(priority, sample)) {
                removed++;
                removedWeight += sampleWeight;
            }
        }
    }
}
