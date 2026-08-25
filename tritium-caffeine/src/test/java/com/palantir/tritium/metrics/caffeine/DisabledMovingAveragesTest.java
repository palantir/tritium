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

package com.palantir.tritium.metrics.caffeine;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Meter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class DisabledMovingAveragesTest {

    @Test
    void countsWithoutRates() {
        AtomicLong nanos = new AtomicLong();
        Meter meter = new Meter(DisabledMovingAverages.INSTANCE, new Clock() {
            @Override
            public long getTick() {
                return nanos.get();
            }
        });

        meter.mark(5);
        nanos.addAndGet(Duration.ofMinutes(1).toNanos());

        assertThat(meter.getCount()).isEqualTo(5L);
        assertThat(meter.getOneMinuteRate()).isZero();
        assertThat(meter.getFiveMinuteRate()).isZero();
        assertThat(meter.getFifteenMinuteRate()).isZero();
    }
}
