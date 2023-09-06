/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public final class UniqueIdsTest {

    private final Random random = new Random(1234567890L);

    @Test
    public void deterministicRandomSource() {
        UUID uuid = UniqueIds.randomUuidV4(random);
        assertThat(uuid).isEqualTo(UUID.fromString("ea6addb7-2596-428e-8a77-acf9a43797b3"));
        assertThat(uuid.variant()).isEqualTo(2); //  IETF RFC 4122
        assertThat(uuid.version()).isEqualTo(4); // v4 Randomly generated UUID
        assertThatThrownBy(uuid::clockSequence)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Not a time-based UUID");
        assertThatThrownBy(uuid::timestamp)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Not a time-based UUID");
        assertThat(uuid.getMostSignificantBits()).isEqualTo(0xea6addb72596428eL);
        assertThat(uuid.getLeastSignificantBits()).isEqualTo(0x8a77acf9a43797b3L);
        assertThat(uuid).isEqualTo(UUID.fromString(uuid.toString()));

        assertThat(UniqueIds.randomUuidV4(random))
                .isEqualTo(UUID.fromString("4b58fcf1-a036-4cf3-9ec0-4f2620d015d0"))
                .extracting(UUID::version)
                .isEqualTo(4);
        assertThat(UniqueIds.randomUuidV4(random))
                .isEqualTo(UUID.fromString("174cafe6-1614-44fb-b3af-b63e0b344571"))
                .extracting(UUID::version)
                .isEqualTo(4);
    }

    static Stream<Arguments> inputs() {
        return Stream.<Callable<UUID>>of(UniqueIds::pseudoRandomUuidV4, UniqueIds::randomUuidV4)
                .flatMap(uuidGen ->
                        IntStream.of(1, 1_000, 100_000, 256 * 1024).mapToObj(size -> Arguments.of(uuidGen, size)));
    }

    @ParameterizedTest
    @MethodSource("inputs")
    void concurrentRequests(Callable<UUID> callable, int size) throws Exception {
        ExecutorService executor = ForkJoinPool.commonPool();
        try {
            List<Future<UUID>> futures = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                futures.add(executor.submit(callable));
            }
            executor.shutdown();
            Iterator<Future<UUID>> iterator = futures.iterator();
            assertRfc4122UuidV4(() -> Futures.getUnchecked(iterator.next()), size);
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @MethodSource("inputs")
    void generate(Callable<UUID> callable, int size) throws Exception {
        assertRfc4122UuidV4(callable, size);
    }

    private static void assertRfc4122UuidV4(Callable<UUID> supplier, int size) throws Exception {
        Set<UUID> uuids = Sets.newHashSetWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            UUID uuid = supplier.call();
            assertRfc4122UuidV4(uuid);
            assertThat(uuids.add(uuid)).as("should be unique: %s", uuid).isTrue();
        }
        assertThat(uuids).hasSize(size);
    }

    private static void assertRfc4122UuidV4(UUID uuid) {
        assertThat(uuid)
                .extracting(UUID::variant)
                .as("'%s' should be IETF RFC 4122", uuid)
                .isEqualTo(2);
        assertThat(uuid)
                .extracting(UUID::version)
                .as("'%s' should be v4 random version", uuid)
                .isEqualTo(4);
        assertThatThrownBy(uuid::clockSequence)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Not a time-based UUID");
        assertThatThrownBy(uuid::timestamp)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Not a time-based UUID");
    }
}
