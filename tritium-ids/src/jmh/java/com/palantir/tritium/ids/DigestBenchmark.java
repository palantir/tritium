/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.ids;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 20, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Threads(Threads.MAX)
@Fork(value = 1, jvmArgsPrepend = "--add-modules=jdk.incubator.vector")
@SuppressWarnings({
    "checkstyle:hideutilityclassconstructor",
    "VisibilityModifier",
    "DesignForExtension",
    "ForLoopReplaceableByForEach"
})
public class DigestBenchmark {

    private static final int SIZE = 4;
    private static final HashCode SENTINEL_HASH_CODE =
            Hashing.sha256().hashString("test" + (SIZE / 2), StandardCharsets.UTF_8);
    private static final byte[] SENTINEL_BYTES = SENTINEL_HASH_CODE.asBytes();

    private final List<byte[]> hashes = IntStream.range(0, SIZE)
            .mapToObj(i -> Hashing.sha256()
                    .hashString("test" + i, StandardCharsets.UTF_8)
                    .asBytes())
            .toList();

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int arrays() {
        int count = 0;
        for (int i = 0; i < hashes.size(); i++) {
            count += Arrays.equals(hashes.get(i), SENTINEL_BYTES) ? 1 : 0;
        }
        return count;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int messageDigest() {
        int count = 0;
        for (int i = 0; i < hashes.size(); i++) {
            count += MessageDigest.isEqual(hashes.get(i), SENTINEL_BYTES) ? 1 : 0;
        }
        return count;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int constantTimeEquals() {
        int count = 0;
        for (int i = 0; i < hashes.size(); i++) {
            count += Digests.constantTimeEquals(hashes.get(i), SENTINEL_BYTES) ? 1 : 0;
        }
        return count;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int constantTimeEqualsUnrolled() {
        int count = 0;
        for (int i = 0; i < hashes.size(); i++) {
            count += Digests.constantTimeEqualsUnrolled(hashes.get(i), SENTINEL_BYTES) ? 1 : 0;
        }
        return count;
    }

    public static void main(String[] _args) throws Exception {
        new Runner(new OptionsBuilder()
                        .include(DigestBenchmark.class.getSimpleName())
                        .forks(1)
                        .jvmArgsPrepend("--add-modules=jdk.incubator.vector")
                        .threads(4)
                        .warmupIterations(3)
                        .warmupTime(TimeValue.seconds(3))
                        .measurementIterations(3)
                        .measurementTime(TimeValue.seconds(1))
                        .build())
                .run();
    }
}
