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

package com.palantir.tritium.metrics.jvm;

import com.codahale.metrics.Gauge;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Report duration spent waiting at safepoints. This could indicate badness in terms of STW GCing, or biased locking
 * going badly. See https://stackoverflow.com/questions/29666057/analyzing-gc-logs/29673564#29673564 for details. This
 * essentially provides the information of '+PrintGCApplicationStoppedTime' programmatically.
 */
final class SafepointMetrics {
    private static final Logger log = LoggerFactory.getLogger(SafepointMetrics.class);

    static void register(TaggedMetricRegistry registry) {
        try {
            InternalJvmMetrics.of(registry).safepointTime(HotspotSafepointMetrics.getTotalSafepointTime());
        } catch (NoClassDefFoundError e) {
            log.info("Could not get the total safepoint time, these metrics will not be registered.", e);
        }
    }

    @SuppressWarnings({"UnnecessarilyFullyQualified", "restriction"}) // would otherwise be an illegal import
    private static final class HotspotSafepointMetrics {
        private static final sun.management.HotspotRuntimeMBean runtime =
                sun.management.ManagementFactoryHelper.getHotspotRuntimeMBean();

        public static Gauge<Long> getTotalSafepointTime() {
            runtime.getTotalSafepointTime();
            return runtime::getTotalSafepointTime;
        }
    }

    private SafepointMetrics() {
        throw new UnsupportedOperationException();
    }
}
