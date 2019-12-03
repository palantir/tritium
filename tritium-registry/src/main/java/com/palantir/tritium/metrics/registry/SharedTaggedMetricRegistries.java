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

package com.palantir.tritium.metrics.registry;

import com.codahale.metrics.SharedMetricRegistries;
import java.util.concurrent.TimeUnit;

/** Analogous to codahale's {@link SharedMetricRegistries}. */
public final class SharedTaggedMetricRegistries {

    /**
     * With a window size of 35 seconds, metric loggers should dump this registry more frequently (e.g. every 30
     * seconds). Reading from this registry on a slower interval (e.g. every 5 minutes) is not recommended because
     * the sample would only represent the last 35seconds, and information from the preceding 4m25 would be lost.
     *
     * Increasing the window size would also increase the memory footprint. SlidingTimeWindowArrayReservoir takes
     * ~128 bits per stored measurement, 10K measurements / sec with reservoir storing time of 35s is:
     * 10_000 * 35 * 128 / 8 = 5600000 bytes ~ 5 megabytes.
     */
    private static final TaggedMetricRegistry DEFAULT = new SlidingWindowTaggedMetricRegistry(35, TimeUnit.SECONDS);

    /**
     * Single shared instance for use when it is infeasible to plumb through a user supplied instance.
     *
     * @deprecated avoid using the global singleton
     */
    @Deprecated
    public static TaggedMetricRegistry getSingleton() {
        return DEFAULT;
    }

    private SharedTaggedMetricRegistries() {}
}
