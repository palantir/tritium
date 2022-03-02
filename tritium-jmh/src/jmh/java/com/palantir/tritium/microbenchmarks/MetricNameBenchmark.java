/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.tritium.metrics.registry.MetricName;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings({"designforextension", "NullAway"})
public class MetricNameBenchmark {

    @Benchmark
    public MetricName benchmarkName3Tags() {
        return MetricName.builder()
                .safeName("someMetric")
                .putSafeTags("some tag", "some tag value")
                .putSafeTags("libraryName", "tritium")
                .putSafeTags("libraryVersion", "1.2.3")
                .build();
    }

    @Benchmark
    public MetricName benchmarkName7Tags() {
        return MetricName.builder()
                .safeName("someMetric")
                .putSafeTags("some tag", "some tag value")
                .putSafeTags("libraryName", "tritium")
                .putSafeTags("libraryVersion", "1.2.3")
                .putSafeTags("libraryName1", "tritium1")
                .putSafeTags("libraryVersion1", "2.3.4")
                .putSafeTags("libraryName2", "tritium2")
                .putSafeTags("libraryVersion2", "5.6.7")
                .build();
    }

    public static void main(String[] _args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(MetricNameBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
