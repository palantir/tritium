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

import com.palantir.jvm.diagnostics.JvmDiagnostics;
import com.palantir.jvm.diagnostics.SafepointTimeAccessor;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;

/**
 * Report duration spent waiting at safepoints. This could indicate badness in terms of STW GCing, or biased locking
 * going badly. See https://stackoverflow.com/questions/29666057/analyzing-gc-logs/29673564#29673564 for details. This
 * essentially provides the information of '+PrintGCApplicationStoppedTime' programmatically.
 */
final class SafepointMetrics {
    private static final SafeLogger log = SafeLoggerFactory.get(SafepointMetrics.class);

    static void register(TaggedMetricRegistry registry) {
        Optional<SafepointTimeAccessor> safepointTimeAccessor = JvmDiagnostics.totalSafepointTime();
        if (safepointTimeAccessor.isPresent()) {
            InternalJvmMetrics.of(registry).safepointTime(safepointTimeAccessor.get()::safepointTimeMilliseconds);
        } else {
            log.info("Could not get the total safepoint time, these metrics will not be registered.");
        }
    }

    private SafepointMetrics() {
        throw new UnsupportedOperationException();
    }
}
