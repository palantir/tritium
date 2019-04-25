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

    private static final TaggedMetricRegistry DEFAULT = new SlidingWindowTaggedMetricRegistry(1, TimeUnit.MINUTES);

    /** Singleton, for use when it is infeasible to plumb through a user supplied instance. */
    public static TaggedMetricRegistry getDefault() {
        return DEFAULT;
    }

    private SharedTaggedMetricRegistries() {}
}
