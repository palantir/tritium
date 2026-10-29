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

import com.codahale.metrics.Snapshot;
import com.codahale.metrics.WeightedSnapshot;
import com.codahale.metrics.WeightedSnapshot.WeightedSample;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Copies a reservoir's samples, in the map's iteration order, into primitive arrays with one {@code forEach} pass, and
 * builds the snapshot from them. The result is identical to {@code new WeightedSnapshot(map.values())} but avoids its
 * intermediate {@code ArrayList}, {@code WeightedSample[]} copy and object sort: only the snapshot's own three arrays are
 * allocated. The arrays can have spare capacity if the expected size was too large or they grew while collecting.
 *
 * <p>The sort packs each value with its iteration index, {@code value << bits | index}, and sorts the {@code long[]}.
 * Equal values therefore keep their iteration order, exactly as the stable sort in {@link WeightedSnapshot} does, so
 * every quantile, mean and standard deviation is bit for bit the same. Values too large to pack fall back to
 * {@link WeightedSnapshot}.
 */
abstract class SampleCollector<S> implements BiConsumer<Double, S> {

    private static final SortedWeightedSnapshot EMPTY =
            new SortedWeightedSnapshot(new long[0], new double[0], new double[0], 0);

    private long[] values;
    private double[] weights;
    private int size;
    private long minValue = Long.MAX_VALUE;
    private long maxValue = Long.MIN_VALUE;

    /** {@code expectedSize} is the reservoir's sample count; the arrays grow if concurrent updates add more. */
    SampleCollector(int expectedSize) {
        int capacity = Math.max(expectedSize, 0);
        this.values = new long[capacity];
        this.weights = new double[capacity];
    }

    abstract long sampleValue(S sample);

    abstract double sampleWeight(S sample);

    /** Called for each collected sample, at its index. */
    void collected(int _index, S _sample) {}

    /** Called when the arrays grow. */
    void grown(int _capacity) {}

    @Override
    public final void accept(Double _priority, S sample) {
        if (size == values.length) {
            int capacity = size + (size >> 3) + 8;
            values = Arrays.copyOf(values, capacity);
            weights = Arrays.copyOf(weights, capacity);
            grown(capacity);
        }
        long value = sampleValue(sample);
        double weight = sampleWeight(sample);
        values[size] = value;
        weights[size] = weight;
        collected(size, sample);
        size++;
        minValue = Math.min(minValue, value);
        maxValue = Math.max(maxValue, value);
    }

    final int size() {
        return size;
    }

    final int capacity() {
        return values.length;
    }

    final long value(int index) {
        return values[index];
    }

    /** Builds the snapshot. The collector can't be used afterwards: its arrays become the snapshot's. */
    final Snapshot snapshot() {
        int count = size;
        if (count == 0) {
            return EMPTY;
        }
        int bits = 32 - Integer.numberOfLeadingZeros(count - 1);
        if (!packable(bits)) {
            return fallback();
        }
        long[] keys = sortedKeys(count, bits);
        long mask = (1L << bits) - 1;
        // summed in sorted order, as WeightedSnapshot does, so the normalized weights round the same way
        double sumWeight = 0;
        for (int i = 0; i < count; i++) {
            sumWeight += weights[(int) (keys[i] & mask)];
        }
        double[] normWeights = new double[count];
        for (int i = 0; i < count; i++) {
            long key = keys[i];
            normWeights[i] = sumWeight != 0 ? weights[(int) (key & mask)] / sumWeight : 0;
            keys[i] = key >> bits;
        }
        // the weights in iteration order aren't needed any more, so they become the quantiles
        double[] quantiles = weights;
        quantiles[0] = 0;
        for (int i = 1; i < count; i++) {
            quantiles[i] = quantiles[i - 1] + normWeights[i - 1];
        }
        return new SortedWeightedSnapshot(keys, normWeights, quantiles, count);
    }

    /** Whether every value still fits once shifted left by {@code bits}. */
    private boolean packable(int bits) {
        if (bits == 0) {
            return true;
        }
        long limit = 1L << (63 - bits);
        return minValue >= -limit && maxValue < limit;
    }

    /** Each value shifted left with its iteration index in the low {@code bits}, sorted. */
    private long[] sortedKeys(int count, int bits) {
        long[] keys = values;
        for (int i = 0; i < count; i++) {
            keys[i] = (keys[i] << bits) | i;
        }
        Arrays.sort(keys, 0, count);
        return keys;
    }

    private Snapshot fallback() {
        List<WeightedSample> samples = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            samples.add(new WeightedSample(values[i], weights[i]));
        }
        return new WeightedSnapshot(samples);
    }
}
