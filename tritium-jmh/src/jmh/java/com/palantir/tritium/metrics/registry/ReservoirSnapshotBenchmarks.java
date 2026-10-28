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
import com.palantir.tritium.metrics.registry.WeightedSnapshotWithExemplars.WeightedSampleWithExemplar;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** Compares snapshot construction from identical samples, including stale counts and values too large to pack. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
@SuppressWarnings({"designforextension", "NullAway"})
public class ReservoirSnapshotBenchmarks {
    private static final ExemplarMetadataProvider<String> PROVIDER = () -> "metadata";

    @Param({"RANDOM", "DUPLICATE", "LARGE", "LARGE_DUPLICATE", "LARGE_SORTED", "LARGE_REVERSED", "LARGE_ALMOST_SORTED"})
    private String distribution;

    @Param("1028")
    private int size;

    @Param({"0", "1"})
    private int countOverestimate;

    private ConcurrentSkipListMap<Double, WeightedSample> samples;
    private ConcurrentSkipListMap<Double, WeightedSampleWithExemplar> exemplarSamples;

    @Setup
    public void before() {
        samples = new ConcurrentSkipListMap<>();
        exemplarSamples = new ConcurrentSkipListMap<>();
        SplittableRandom random = new SplittableRandom(42);
        for (int i = 0; i < size; i++) {
            long value = switch (distribution) {
                case "DUPLICATE" -> random.nextLong(4);
                case "LARGE" -> random.nextLong();
                case "LARGE_DUPLICATE" -> Long.MIN_VALUE + random.nextLong(4);
                case "LARGE_SORTED" -> Long.MIN_VALUE + i;
                case "LARGE_REVERSED" -> Long.MAX_VALUE - i;
                case "LARGE_ALMOST_SORTED" -> Long.MIN_VALUE + (i ^ 1);
                default -> random.nextLong(1, 1_000_000_000);
            };
            double weight = Math.exp(random.nextDouble(-10, 10));
            double priority = switch (distribution) {
                case "LARGE_SORTED", "LARGE_REVERSED", "LARGE_ALMOST_SORTED" -> i;
                default -> random.nextDouble();
            };
            samples.put(priority, new WeightedSample(value, weight));
            exemplarSamples.put(priority, new WeightedSampleWithExemplar(value, weight, "metadata"));
        }
    }

    @Benchmark
    public Snapshot dropwizardSnapshot() {
        return new WeightedSnapshot(samples.values());
    }

    @Benchmark
    public Snapshot snapshot() {
        Collector collector = new Collector(size + countOverestimate);
        samples.forEach(collector);
        return collector.snapshot();
    }

    @Benchmark
    public Snapshot snapshotWithExemplars() {
        WeightedSnapshotWithExemplars.Collector collector =
                new WeightedSnapshotWithExemplars.Collector(size + countOverestimate);
        exemplarSamples.forEach(collector);
        return collector.snapshot(PROVIDER);
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
