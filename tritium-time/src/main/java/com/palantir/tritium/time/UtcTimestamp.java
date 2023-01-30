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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class UtcTimestamp {
    private UtcTimestamp() {}

    /**
     * Returns an {@link OffsetDateTime} from the system clock in the {@link ZoneOffset#UTC UTC} time zone.
     * Effectively the same as {@link OffsetDateTime#now(java.time.ZoneId)} with argument {@link ZoneOffset#UTC
     * ZoneOffset.UTC}, but optimized to avoid zone rules lookup given UTC zone offset is zero.
     * <p>
     * Superseded by <a href="https://bugs.openjdk.org/browse/JDK-8283681">JDK-8283681</a> in JDK 19+.
     *
     * @return OffsetDateTime representing now in {@link ZoneOffset#UTC UTC} zone
     */
    public static OffsetDateTime now() {
        return utc(Instant.now());
    }

    /**
     * Returns an {@link OffsetDateTime} from the specified clock in the {@link ZoneOffset#UTC UTC} time zone.
     * Effectively the same as {@link OffsetDateTime#ofInstant(Instant, ZoneId)} with arguments
     * {@code clock.instant()}, {@link ZoneOffset#UTC ZoneOffset.UTC}, but optimized to avoid zone rules lookup
     * given UTC zone offset is zero.
     * <p>
     * Superseded by <a href="https://bugs.openjdk.org/browse/JDK-8283681">JDK-8283681</a> in JDK 19+.
     *
     * @return OffsetDateTime representing clock's instant in {@link ZoneOffset#UTC UTC} zone
     */
    public static OffsetDateTime now(Clock clock) {
        return utc(clock.instant());
    }

    /**
     * Returns an {@link OffsetDateTime} at the specified instant in the {@link ZoneOffset#UTC UTC} time zone.
     * Effectively the same as {@link OffsetDateTime#ofInstant(Instant, ZoneId)}  with arguments instant and
     * {@link ZoneOffset#UTC ZoneOffset.UTC}, but optimized to avoid zone rules lookup given UTC zone offset is zero.
     * <p>
     * Superseded by <a href="https://bugs.openjdk.org/browse/JDK-8283681">JDK-8283681</a> in JDK 19+.
     *
     * @return OffsetDateTime representing instant in {@link ZoneOffset#UTC UTC} zone
     */
    public static OffsetDateTime utc(Instant instant) {
        // explicitly not using OffsetDateTime.ofInstant to avoid allocations of ZoneOffset.getRules
        LocalDateTime utcDateTime =
                LocalDateTime.ofEpochSecond(instant.getEpochSecond(), instant.getNano(), ZoneOffset.UTC);
        return OffsetDateTime.of(utcDateTime, ZoneOffset.UTC);
    }
}
