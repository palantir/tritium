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
import com.codahale.metrics.Timer;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class TaggedMetricsScheduledExecutorService extends AbstractExecutorService implements ScheduledExecutorService {
    private static final long MAX_NANOS = (Long.MAX_VALUE >>> 1) - 1;

    private final ScheduledExecutorService delegate;
    private final String name;

    private final Counter running;
    private final Timer duration;
    private final Timer queuedDuration;

    private final Counter scheduledOverrun;

    TaggedMetricsScheduledExecutorService(ScheduledExecutorService delegate, ExecutorMetrics metrics, String name) {
        this.delegate = delegate;
        this.name = name;

        this.running = metrics.running(name);
        this.duration = metrics.duration(name);
        this.queuedDuration = metrics.queuedDuration(name);

        this.scheduledOverrun = metrics.scheduledOverrun(name);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return delegate.schedule(new TaggedMetricsRunnable(task, 0, unit.toNanos(delay), Kind.SINGLE_RUN), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return delegate.schedule(
                new TaggedMetricsCallable<>(callable, 0, unit.toNanos(delay), Kind.SINGLE_RUN), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return delegate.scheduleAtFixedRate(
                new TaggedMetricsScheduledRunnable(task, unit.toNanos(initialDelay), unit.toNanos(period), Kind.RATE),
                initialDelay,
                period,
                unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return delegate.scheduleWithFixedDelay(
                new TaggedMetricsRunnable(task, unit.toNanos(initialDelay), unit.toNanos(delay), Kind.DELAY),
                initialDelay,
                delay,
                unit);
    }

    @Override
    public void execute(Runnable task) {
        delegate.execute(new TaggedMetricsRunnable(task, 0, 0, Kind.SINGLE_RUN));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(new TaggedMetricsCallable<>(task, 0, 0, Kind.SINGLE_RUN));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(new TaggedMetricsRunnable(task, 0, 0, Kind.SINGLE_RUN), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(new TaggedMetricsRunnable(task, 0, 0, Kind.SINGLE_RUN));
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
        return "TaggedMetricsScheduledExecutorService{name=" + name + ", delegate='" + delegate + "'}";
    }

    private static long triggerTime(long initialDelay, long periodInNanos) {
        return System.nanoTime() + Math.min(initialDelay, MAX_NANOS) + Math.min(periodInNanos, MAX_NANOS);
    }

    private enum Kind {
        SINGLE_RUN,
        RATE,
        DELAY
    }

    private final class TaggedMetricsRunnable implements Runnable {

        private final Runnable task;
        private final long periodInNanos;
        private final Kind kind;
        private long triggerTime;

        TaggedMetricsRunnable(Runnable task, long startDelayInNanos, long periodInNanos, Kind kind) {
            this.task = task;
            this.periodInNanos = periodInNanos;
            this.triggerTime = triggerTime(startDelayInNanos, periodInNanos);
            this.kind = kind;
        }

        @Override
        @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
        public void run() {
            running.inc();
            long startNanos = System.nanoTime();

            queuedDuration.update(startNanos - triggerTime, TimeUnit.NANOSECONDS);
            if (kind == Kind.RATE) {
                triggerTime = triggerTime(triggerTime, periodInNanos);
            }

            try {
                task.run();
            } finally {
                duration.update(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                running.dec();

                if (kind == Kind.DELAY) {
                    triggerTime = triggerTime(triggerTime, periodInNanos);
                }
            }
        }
    }

    private final class TaggedMetricsScheduledRunnable implements Runnable {

        private final Runnable task;
        private final long periodInNanos;
        private final Kind kind;
        private long triggerTime;

        TaggedMetricsScheduledRunnable(Runnable task, long startDelayInNanos, long periodInNanos, Kind kind) {
            this.task = task;
            this.periodInNanos = periodInNanos;
            this.triggerTime = triggerTime(startDelayInNanos, periodInNanos);
            this.kind = kind;
        }

        @Override
        @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
        public void run() {
            running.inc();

            long startNanos = System.nanoTime();
            queuedDuration.update(startNanos - triggerTime, TimeUnit.NANOSECONDS);
            if (kind == Kind.RATE) {
                triggerTime = triggerTime(triggerTime, periodInNanos);
            }

            try {
                task.run();
            } finally {
                long elapsed = System.nanoTime() - startNanos;
                duration.update(elapsed, TimeUnit.NANOSECONDS);
                running.dec();
                if (elapsed > periodInNanos) {
                    scheduledOverrun.inc();
                }
                if (kind == Kind.DELAY) {
                    triggerTime = triggerTime(triggerTime, periodInNanos);
                }
            }
        }
    }

    private final class TaggedMetricsCallable<T> implements Callable<T> {

        private final Callable<T> task;
        private final long periodInNanos;
        private final Kind kind;
        private long triggerTime;

        TaggedMetricsCallable(Callable<T> task, long startDelayInNanos, long periodInNanos, Kind kind) {
            this.task = task;
            this.periodInNanos = periodInNanos;
            this.kind = kind;
            this.triggerTime = triggerTime(startDelayInNanos, periodInNanos);
        }

        @Override
        @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
        public T call() throws Exception {
            running.inc();

            long startNanos = System.nanoTime();
            queuedDuration.update(startNanos - triggerTime, TimeUnit.NANOSECONDS);
            if (kind == Kind.RATE) {
                triggerTime = triggerTime(triggerTime, periodInNanos);
            }

            try {
                return task.call();
            } finally {
                duration.update(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                running.dec();
                if (kind == Kind.DELAY) {
                    triggerTime = triggerTime(triggerTime, periodInNanos);
                }
            }
        }
    }
}
