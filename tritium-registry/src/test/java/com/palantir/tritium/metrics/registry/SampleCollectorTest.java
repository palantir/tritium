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

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Snapshot;
import com.codahale.metrics.WeightedSnapshot;
import com.codahale.metrics.WeightedSnapshot.WeightedSample;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.LongSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link SampleCollector} must build exactly what {@link WeightedSnapshot} builds from the same samples. */
class SampleCollectorTest {

    private static final double[] QUANTILES = {0, 1e-9, 0.001, 0.25, 0.5, 0.75, 0.95, 0.98, 0.99, 0.999, 1};

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 7, 8, 9, 100, 1027, 1028, 1029, 2048, 5000})
    void matchesWeightedSnapshot(int size) {
        SplittableRandom random = new SplittableRandom(size);
        for (int trial = 0; trial < 50; trial++) {
            LongSupplier values = values(random, trial);
            List<WeightedSample> samples = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                samples.add(new WeightedSample(values.getAsLong(), weight(random)));
            }
            // the reservoir's count can be stale, so the collector must cope with any expected size
            Collector collector = new Collector(random.nextInt(size + 2));
            samples.forEach(sample -> collector.accept(0.0, sample));
            assertIdentical(collector.snapshot(), new WeightedSnapshot(samples));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 50, 1028})
    void matchesWeightedSnapshotOfTheRetainedSamples(int size) {
        SplittableRandom random = new SplittableRandom(size);
        for (int trial = 0; trial < 50; trial++) {
            LongSupplier values = values(random, trial);
            List<WeightedSample> samples = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                // weights spanning far more than the negligible threshold, as in a reservoir hours old
                samples.add(new WeightedSample(values.getAsLong(), Math.exp(random.nextDouble(-60, 0))));
            }
            Collector collector = new Collector(size);
            samples.forEach(sample -> collector.accept(0.0, sample));
            double threshold = collector.negligibleBelow();
            List<WeightedSample> retained = new ArrayList<>();
            for (WeightedSample sample : samples) {
                if (sample.weight >= threshold) {
                    retained.add(sample);
                }
            }
            if (threshold > 0) {
                collector.retainAtLeast(threshold);
                assertThat(retained).hasSizeLessThan(samples.size());
            } else {
                assertThat(retained).hasSameSizeAs(samples);
            }
            assertIdentical(collector.snapshot(), new WeightedSnapshot(retained));
        }
    }

    /** Values that exercise the sort: spread out, heavily duplicated, negative, and too large to pack. */
    private static LongSupplier values(SplittableRandom random, int trial) {
        return switch (trial % 6) {
            case 0 -> () -> random.nextLong(1, 10_000_000_000L);
            case 1 -> () -> random.nextLong(0, 4);
            case 2 -> () -> random.nextLong(-1_000_000, 1_000_000);
            case 3 -> () -> random.nextBoolean() ? Long.MAX_VALUE : Long.MIN_VALUE + random.nextInt(3);
            case 4 -> () -> random.nextLong(1L << 51, 1L << 53);
            default -> () -> (long) Math.exp(random.nextDouble(0, 40));
        };
    }

    private static double weight(SplittableRandom random) {
        return random.nextInt(20) == 0 ? 1.0 : Math.exp(random.nextDouble(-20, 20));
    }

    private static void assertIdentical(Snapshot actual, Snapshot expected) {
        assertThat(actual.size()).isEqualTo(expected.size());
        assertThat(actual.getValues()).containsExactly(expected.getValues());
        assertThat(actual.getMax()).isEqualTo(expected.getMax());
        assertThat(actual.getMin()).isEqualTo(expected.getMin());
        // bit for bit, not within a tolerance
        assertThat(Double.doubleToRawLongBits(actual.getMean()))
                .isEqualTo(Double.doubleToRawLongBits(expected.getMean()));
        assertThat(Double.doubleToRawLongBits(actual.getStdDev()))
                .isEqualTo(Double.doubleToRawLongBits(expected.getStdDev()));
        for (double quantile : QUANTILES) {
            assertThat(actual.getValue(quantile)).as("q%s", quantile).isEqualTo(expected.getValue(quantile));
        }
        assertThat(dump(actual)).isEqualTo(dump(expected));
    }

    private static String dump(Snapshot snapshot) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        snapshot.dump(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static final class Collector extends SampleCollector<WeightedSample> {
        Collector(int expectedSize) {
            super(expectedSize);
        }

        @Override
        long sampleValue(WeightedSample sample) {
            return sample.value;
        }

        @Override
        double sampleWeight(WeightedSample sample) {
            return sample.weight;
        }
    }
}
