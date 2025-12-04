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

package com.palantir.tritium.metrics.jvm;

import com.palantir.jvm.diagnostics.JvmDiagnostics;
import com.palantir.jvm.diagnostics.VirtualThreadSchedulerAccessor;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;

final class VirtualThreadsMetrics {
    private static final SafeLogger log = SafeLoggerFactory.get(VirtualThreadsMetrics.class);

    static void register(TaggedMetricRegistry registry) {
        Optional<VirtualThreadSchedulerAccessor> accessor = JvmDiagnostics.virtualThreadScheduler();
        if (accessor.isPresent()) {
            InternalJvmMetrics internalJvmMetrics = InternalJvmMetrics.of(registry);
            VirtualThreadSchedulerAccessor virtualThreads = accessor.get();
            internalJvmMetrics.threadsVirtualParallelism(virtualThreads::getParallelism);
            internalJvmMetrics.threadsVirtualPoolSize(virtualThreads::getPoolSize);
            internalJvmMetrics.threadsVirtualMounted(virtualThreads::getMountedVirtualThreadCount);
            internalJvmMetrics.threadsVirtualQueued(virtualThreads::getQueuedVirtualThreadCount);
        } else {
            // logged at debug to avoid spamming, many services are still using jdk21 which does not support
            // this metric reporting
            log.debug("Could not get virtual thread metrics from the JVM, these metrics will not be registered.");
        }
    }

    private VirtualThreadsMetrics() {
        throw new UnsupportedOperationException();
    }
}
