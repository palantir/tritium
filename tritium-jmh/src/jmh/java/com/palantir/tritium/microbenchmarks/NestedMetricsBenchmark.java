/*
 * Copyright 2019 Palantir Technologies, Inc. All rights reserved.
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

import com.codahale.metrics.Meter;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings({"designforextension", "NullAway"})
public class NestedMetricsBenchmark {
    private TaggedMetricRegistry metrics;

    @Setup
    public void before() {
        metrics = constructBaseRegistry();
    }

    @Benchmark
    public void benchmarkGetMetrics(Blackhole blackhole) {
        metrics.getMetrics().forEach((name, metric) -> {
            blackhole.consume(name);
            blackhole.consume(metric);
        });
    }

    @Benchmark
    public void benchmarkForEach(Blackhole blackhole) {
        metrics.forEachMetric((name, metric) -> {
            blackhole.consume(name);
            blackhole.consume(metric);
        });
    }

    private static TaggedMetricRegistry constructBaseRegistry() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        for (int i = 0; i < 50; i++) {
            registry.addMetrics("registry id", Integer.toString(i), constructSubRegistry());
        }
        return registry;
    }

    private static TaggedMetricRegistry constructSubRegistry() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        for (int i = 0; i < 100; i++) {
            Meter meter = registry.meter(MetricName.builder()
                    .safeName("some metric " + i)
                    .putSafeTags("some tag", "some tag value")
                    .build());
            for (int j = 0; j < 1000; j++) {
                meter.mark();
            }
        }
        return registry;
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(NestedMetricsBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
