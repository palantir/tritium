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

package com.palantir.tritium.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class UtcTimestampTest {

    @Test
    void nowInUtc() {
        assertThat(UtcTimestamp.now())
                .isCloseTo(OffsetDateTime.now(ZoneOffset.UTC), byLessThan(1L, ChronoUnit.SECONDS));

        Instant now = Instant.now();
        assertThat(UtcTimestamp.utc(now))
                .isCloseTo(OffsetDateTime.now(ZoneOffset.UTC), byLessThan(1L, ChronoUnit.SECONDS))
                .isCloseTo(OffsetDateTime.now(Clock.systemUTC()), byLessThan(1L, ChronoUnit.SECONDS))
                .isEqualTo(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @MethodSource("clocks")
    void utcClock(Clock clock) {
        Instant instant = clock.instant();
        assertThat(UtcTimestamp.now(clock))
                .isCloseTo(OffsetDateTime.now(clock), byLessThan(1L, ChronoUnit.SECONDS))
                .isCloseTo(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC), byLessThan(1L, ChronoUnit.SECONDS));

        assertThat(UtcTimestamp.utc(instant))
                .isCloseTo(OffsetDateTime.now(clock), byLessThan(1L, ChronoUnit.SECONDS))
                .isEqualTo(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    @SuppressWarnings({"JavaTimeSystemDefaultTimeZone", "JavaTimeDefaultTimeZone"
    }) // explicitly testing system default time zone
    static List<Clock> clocks() {
        return List.of(
                Clock.systemUTC(),
                Clock.systemDefaultZone(),
                Clock.system(ZoneOffset.UTC),
                Clock.system(ZoneOffset.ofHours(-4)),
                Clock.system(ZoneOffset.ofHours(-5)),
                Clock.system(ZoneOffset.ofHours(5)),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                Clock.fixed(Instant.EPOCH, ZoneOffset.ofHours(7)));
    }
}
