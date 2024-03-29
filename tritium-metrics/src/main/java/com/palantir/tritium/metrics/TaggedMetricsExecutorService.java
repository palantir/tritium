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

package com.palantir.tritium.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

final class TaggedMetricsExecutorService extends AbstractExecutorService {

    // 250ms minimum threshold is required to update the queued duration timer.
    // The queued duration is an estimate based on time between a task being submitted
    // and beginning to execute, there is always a delta between these operations, but
    // it doesn't necessarily mean there's a queue at all. We assume anything longer than
    // this threshold, which should be longer than pauses in most cases, is the result
    // of queueing.
    private static final long QUEUED_DURATION_MINIMUM_THRESHOLD_NANOS = 250_000_000L;

    private final ExecutorService delegate;
    private final String name;

    private final Meter submitted;
    private final Counter running;
    private final Timer duration;

    @Nullable
    private final Timer queuedDuration;

    TaggedMetricsExecutorService(
            ExecutorService delegate, ExecutorMetrics metrics, String name, boolean reportQueuedDuration) {
        this.delegate = delegate;
        this.name = name;
        this.submitted = metrics.submitted(name);
        this.running = metrics.running(name);
        this.duration = metrics.duration(name);
        this.queuedDuration = reportQueuedDuration ? metrics.queuedDuration(name) : null;
    }

    @Override
    public void execute(Runnable task) {
        delegate.execute(new TaggedMetricsRunnable(task));
        // RejectedExecutionException should prevent 'submitted' from being incremented.
        // This means a wrapped same-thread executor will produce delayed 'submitted' values,
        // however the results will work as expected for the more common cases in which
        // either a queue is full, or the delegate has shut down.
        submitted.mark();
    }

    @Override
    public Future<?> submit(Runnable task) {
        Future<?> future = delegate.submit(new TaggedMetricsRunnable(task));
        submitted.mark();
        return future;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        Future<T> future = delegate.submit(new TaggedMetricsRunnable(task), result);
        submitted.mark();
        return future;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        Future<T> future = delegate.submit(new TaggedMetricsCallable<>(task));
        submitted.mark();
        return future;
    }

    // n.b. We don't override invokeAny/invokeAll because the default AbstractExecutorService implementation will
    // produce more accurate metrics. When we call the delegate with N tasks, we don't know how many have been
    // submitted. It's difficult to tell if a task has been rejected as opposed to failing.

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public String toString() {
        return "TaggedMetricsExecutorService{name=" + name + ", delegate='" + delegate + "'}";
    }

    private final class TaggedMetricsRunnable implements Runnable {

        private final Runnable task;

        private final long created = queuedDuration == null ? 0L : System.nanoTime();

        TaggedMetricsRunnable(Runnable task) {
            this.task = task;
        }

        @Override
        @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
        public void run() {
            stopQueueTimer();
            running.inc();
            long startNanos = System.nanoTime();
            try {
                task.run();
            } finally {
                duration.update(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                running.dec();
            }
        }

        @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
        void stopQueueTimer() {
            Timer queuedDurationTimer = queuedDuration;
            if (queuedDurationTimer != null) {
                long queuedDurationNanos = System.nanoTime() - created;
                if (queuedDurationNanos > QUEUED_DURATION_MINIMUM_THRESHOLD_NANOS) {
                    queuedDurationTimer.update(queuedDurationNanos, TimeUnit.NANOSECONDS);
                }
            }
        }
    }

    private final class TaggedMetricsCallable<T> implements Callable<T> {

        private final Callable<T> task;

        private final long created = queuedDuration == null ? 0L : System.nanoTime();

        TaggedMetricsCallable(Callable<T> task) {
            this.task = task;
        }

        @Override
        @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
        public T call() throws Exception {
            stopQueueTimer();
            running.inc();
            long startNanos = System.nanoTime();
            try {
                return task.call();
            } finally {
                duration.update(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                running.dec();
            }
        }

        @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
        void stopQueueTimer() {
            Timer queuedDurationTimer = queuedDuration;
            if (queuedDurationTimer != null) {
                long queuedDurationNanos = System.nanoTime() - created;
                if (queuedDurationNanos > QUEUED_DURATION_MINIMUM_THRESHOLD_NANOS) {
                    queuedDurationTimer.update(queuedDurationNanos, TimeUnit.NANOSECONDS);
                }
            }
        }
    }
}
