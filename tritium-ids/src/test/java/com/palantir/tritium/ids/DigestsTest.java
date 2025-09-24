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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DigestsTest {
    @Test
    void basic() {
        assertThat(Digests.constantTimeEquals(new byte[0], new byte[0])).isTrue();
        assertThat(Digests.constantTimeEqualsUnrolled(new byte[0], new byte[0])).isTrue();

        assertThat(Digests.SPECIES.length()).isGreaterThanOrEqualTo(16);

        // Test basic equality
        byte[] arr1 = "Hello World".getBytes(StandardCharsets.UTF_8);
        byte[] arr2 = "Hello World".getBytes(StandardCharsets.UTF_8);
        byte[] arr3 = "Hello Wyrld".getBytes(StandardCharsets.UTF_8);

        assertThat(Digests.constantTimeEquals(arr1, arr2)).isTrue();
        assertThat(Digests.constantTimeEquals(arr1, arr3)).isFalse();
        assertThat(Digests.constantTimeEqualsUnrolled(arr1, arr2)).isTrue();
        assertThat(Digests.constantTimeEqualsUnrolled(arr1, arr3)).isFalse();

        // Test with different sizes
        byte[] arr4 = "Hello".getBytes(StandardCharsets.UTF_8);
        assertThat(Digests.constantTimeEquals(arr1, arr4)).isFalse();
        assertThat(Digests.constantTimeEqualsUnrolled(arr1, arr4)).isFalse();
    }

    @Test
    void sha256() {
        assertThat(Digests.constantTimeEquals(new byte[32], new byte[32])).isTrue();
        assertThat(Digests.constantTimeEqualsUnrolled(new byte[32], new byte[32]))
                .isTrue();
        ThreadLocalRandom.current().nextBytes(generateRandom(32));
        assertThat(Digests.constantTimeEquals(generateRandom(32), generateRandom(32)))
                .isFalse();
        ThreadLocalRandom.current().nextBytes(generateRandom(32));
        assertThat(Digests.constantTimeEquals(generateRandom(32), generateRandom(32)))
                .isFalse();
        ThreadLocalRandom.current().nextBytes(generateRandom(32));

        ThreadLocalRandom.current().nextBytes(generateRandom(32));
        assertThat(Digests.constantTimeEquals(generateRandom(32), generateRandom(32)))
                .isFalse();
        ThreadLocalRandom.current().nextBytes(generateRandom(32));
        assertThat(Digests.constantTimeEquals(generateRandom(32), generateRandom(32)))
                .isFalse();
        ThreadLocalRandom.current().nextBytes(generateRandom(32));

        HashCode hashCode = Hashing.sha256().hashString("test", StandardCharsets.UTF_8);
        assertThat(Digests.constantTimeEquals(hashCode.asBytes(), hashCode.asBytes()))
                .isTrue();
        assertThat(Digests.constantTimeEquals(
                        hashCode.asBytes(),
                        Hashing.sha256()
                                .hashString("test2", StandardCharsets.UTF_8)
                                .asBytes()))
                .isFalse();
    }

    @ParameterizedTest
    @MethodSource("lengths")
    void constantBytes(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 0x0123);
        byte[] cloned = bytes.clone();
        assertThat(bytes).isEqualTo(cloned).isNotSameAs(cloned);
        assertThat(Digests.constantTimeEquals(bytes, bytes)).isTrue();
        assertThat(Digests.constantTimeEquals(bytes, cloned)).isTrue();
        assertThat(Digests.constantTimeEquals(cloned, cloned)).isTrue();
        assertThat(Digests.constantTimeEquals(cloned, bytes)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("mixedLengths")
    void randomBytes(int first, int second) {
        byte[] firstBytes = generateRandom(first);
        byte[] secondBytes = generateRandom(second);

        assertThat(Digests.constantTimeEquals(firstBytes, secondBytes)).isFalse();
        assertThat(Digests.constantTimeEquals(secondBytes, firstBytes)).isFalse();
        assertThat(Digests.constantTimeEquals(firstBytes, firstBytes)).isTrue();
        assertThat(Digests.constantTimeEquals(secondBytes, secondBytes)).isTrue();

        assertThat(Digests.constantTimeEqualsUnrolled(firstBytes, secondBytes)).isFalse();
        assertThat(Digests.constantTimeEqualsUnrolled(secondBytes, firstBytes)).isFalse();
        assertThat(Digests.constantTimeEqualsUnrolled(firstBytes, firstBytes)).isTrue();
        assertThat(Digests.constantTimeEqualsUnrolled(secondBytes, secondBytes)).isTrue();
    }

    static Stream<Arguments> mixedLengths() {
        return lengths()
                .mapToObj(a -> lengths().mapToObj(b -> Arguments.of(a, b)))
                .flatMap(s -> s);
    }

    private static IntStream lengths() {
        return IntStream.concat(
                IntStream.rangeClosed(1, 9).flatMap(i -> IntStream.of((i * 8) - 1, (i * 8), (i * 8) + 1)),
                IntStream.rangeClosed(1, 26).map(i -> 1 << i));
    }

    private static byte[] generateRandom(int size) {
        byte[] bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }
}
