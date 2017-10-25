/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.tritium.metrics;

import com.codahale.metrics.Clock;
import java.util.concurrent.TimeUnit;

public final class TestClock extends Clock {
    private long tick = 0;

    @Override
    public long getTick() {
        return tick;
    }

    public void advance(long increment, TimeUnit timeUnit) {
        tick += TimeUnit.NANOSECONDS.convert(increment, timeUnit);
    }

    @Override
    public String toString() {
        return String.valueOf(tick);
    }
}
