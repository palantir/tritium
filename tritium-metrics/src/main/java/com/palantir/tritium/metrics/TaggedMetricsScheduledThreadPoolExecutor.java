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

package com.palantir.tritium.metrics;

import com.codahale.metrics.Timer;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Instruments an {@link ScheduledThreadPoolExecutor} that monitors the delay between when a task was scheduled
 * to be executed and when it actually got executed.
 * Use in conjunction with {@link MetricRegistries#instrument(TaggedMetricRegistry, ScheduledExecutorService, String)}
 * to fully instrument an {@link ScheduledExecutorService}.
 */
public class TaggedMetricsScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
    private final Timer delay;

    public TaggedMetricsScheduledThreadPoolExecutor(
            int corePoolSize, @NotNull ThreadFactory threadFactory, ExecutorMetrics metrics, String name) {
        super(corePoolSize, threadFactory);
        this.delay = metrics.queuedDuration(name);
    }

    @Override
    @SuppressWarnings("checkstyle:DesignForExtension")
    protected void beforeExecute(Thread thread, Runnable runnable) {
        if (runnable instanceof ScheduledFuture<?> sf) {
            long futureDelay = sf.getDelay(TimeUnit.MILLISECONDS);
            if (futureDelay < 0) {
                this.delay.update(-futureDelay, TimeUnit.MILLISECONDS);
            }
        }

        super.beforeExecute(thread, runnable);
    }
}
