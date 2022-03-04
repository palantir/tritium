/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.microbenchmarks;

import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmark to measure the typical lifecycle of utilizing a metric in the registry.
 * We expect that the effort should be dominated by the metrics underlying calculation,
 * and minimal overhead for creating and adding metrics to the registry.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings({"designforextension", "NullAway", "PreferJavaTimeOverload"})
public class MetricsLifecycleBenchmark {
    private static final MetricName baseMetricName =
            MetricName.builder().safeName("name").putSafeTags("tag", "value").build();

    private final AtomicLong metricCounter = new AtomicLong();
    private final TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();

    private MetricName metricName() {
        // assume 95% of metric names will be reused, 5% will be unique new metric names
        return ThreadLocalRandom.current().nextDouble() < 0.05
                ? baseMetricName
                : MetricName.builder()
                        .from(baseMetricName)
                        .safeName(baseMetricName.safeName() + metricCounter.incrementAndGet())
                        .build();
    }

    @Benchmark
    public void counter() {
        metrics.counter(metricName()).inc();
    }

    @Benchmark
    public void histogram() {
        metrics.histogram(metricName()).update(1);
    }

    @Benchmark
    public void meter() {
        metrics.meter(metricName()).mark();
    }

    @Benchmark
    public void timer() {
        metrics.timer(metricName()).update(1_000_000L, TimeUnit.NANOSECONDS);
    }

    public static void main(String[] _args) throws Exception {
        new Runner(new OptionsBuilder()
                        .include(MetricsLifecycleBenchmark.class.getSimpleName())
                        .addProfiler(GCProfiler.class)
                        .addProfiler(JavaFlightRecorderProfiler.class)
                        .build())
                .run();
    }
}
