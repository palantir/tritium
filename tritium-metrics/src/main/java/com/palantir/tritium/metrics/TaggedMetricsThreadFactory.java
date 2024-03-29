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
import com.palantir.logsafe.Safe;
import com.palantir.nylon.threads.VirtualThreads;
import com.palantir.tritium.metrics.ExecutorMetrics.ThreadsCreated_ThreadType;
import com.palantir.tritium.metrics.ExecutorMetrics.ThreadsRunning_ThreadType;
import java.util.concurrent.ThreadFactory;

/**
 * An instrumented {@link ThreadFactory} based on the codahale {@link com.codahale.metrics.InstrumentedThreadFactory}.
 */
final class TaggedMetricsThreadFactory implements ThreadFactory {

    private final ThreadFactory delegate;
    // Note that there's no guarantee a given ThreadFactory implementation
    // will always produce the same kind of thread for every invocation, so
    // we must track both variants.
    private final Meter createdPlatform;
    private final Meter createdVirtual;
    private final Counter runningPlatform;
    private final Counter runningVirtual;

    TaggedMetricsThreadFactory(ThreadFactory delegate, ExecutorMetrics metrics, @Safe String name) {
        this.delegate = Preconditions.checkNotNull(delegate, "ThreadFactory is required");
        Preconditions.checkNotNull(name, "Name is required");
        Preconditions.checkNotNull(metrics, "ExecutorMetrics is required");
        this.createdPlatform = metrics.threadsCreated()
                .executor(name)
                .threadType(ThreadsCreated_ThreadType.PLATFORM)
                .build();
        this.createdVirtual = metrics.threadsCreated()
                .executor(name)
                .threadType(ThreadsCreated_ThreadType.VIRTUAL)
                .build();
        this.runningPlatform = metrics.threadsRunning()
                .executor(name)
                .threadType(ThreadsRunning_ThreadType.PLATFORM)
                .build();
        this.runningVirtual = metrics.threadsRunning()
                .executor(name)
                .threadType(ThreadsRunning_ThreadType.VIRTUAL)
                .build();
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread result =
                delegate.newThread(new InstrumentedTask(Preconditions.checkNotNull(runnable, "Runnable is required")));
        createdMeterFor(result).mark();
        return result;
    }

    private Meter createdMeterFor(Thread thread) {
        return VirtualThreads.isVirtual(thread) ? createdVirtual : createdPlatform;
    }

    @Override
    public String toString() {
        return "TaggedMetricsThreadFactory{delegate=" + delegate + '}';
    }

    private final class InstrumentedTask implements Runnable {

        private final Runnable delegate;

        InstrumentedTask(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            Counter running = runningCounterFor(Thread.currentThread());
            running.inc();
            try {
                delegate.run();
            } finally {
                running.dec();
            }
        }

        private Counter runningCounterFor(Thread thread) {
            return VirtualThreads.isVirtual(thread) ? runningVirtual : runningPlatform;
        }

        @Override
        public String toString() {
            return "InstrumentedTask{delegate=" + delegate + '}';
        }
    }
}
