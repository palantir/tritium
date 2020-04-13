/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.palantir.logsafe.Preconditions;
import java.util.concurrent.ThreadFactory;

/**
 * An instrumented {@link ThreadFactory} based on the codahale {@link com.codahale.metrics.InstrumentedThreadFactory}.
 */
final class TaggedMetricsThreadFactory implements ThreadFactory {

    private final ThreadFactory delegate;
    private final Meter created;
    private final Meter terminated;
    private final Counter running;

    TaggedMetricsThreadFactory(ThreadFactory delegate, ExecutorMetrics metrics, String name) {
        this.delegate = Preconditions.checkNotNull(delegate, "ThreadFactory is required");
        Preconditions.checkNotNull(name, "Name is required");
        Preconditions.checkNotNull(metrics, "ExecutorMetrics is required");
        this.created = metrics.threadsCreated(name);
        this.terminated = metrics.threadsTerminated(name);
        this.running = metrics.threadsRunning(name);
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread result = delegate.newThread(new InstrumentedTask(
                Preconditions.checkNotNull(runnable, "Runnable is required"), running, terminated));
        created.mark();
        return result;
    }

    @Override
    public String toString() {
        return "TaggedMetricsThreadFactory{delegate=" + delegate + '}';
    }

    private static final class InstrumentedTask implements Runnable {

        private final Runnable delegate;
        private final Meter terminated;
        private final Counter running;

        InstrumentedTask(Runnable delegate, Counter running, Meter terminated) {
            this.delegate = delegate;
            this.running = running;
            this.terminated = terminated;
        }

        @Override
        public void run() {
            running.inc();
            try {
                delegate.run();
            } finally {
                running.dec();
                terminated.mark();
            }
        }

        @Override
        public String toString() {
            return "InstrumentedTask{delegate=" + delegate + '}';
        }
    }
}
