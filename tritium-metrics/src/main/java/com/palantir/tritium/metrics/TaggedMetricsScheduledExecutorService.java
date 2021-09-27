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
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class TaggedMetricsScheduledExecutorService extends AbstractExecutorService implements ScheduledExecutorService {

    private final ScheduledExecutorService delegate;
    private final String name;

    private final Meter submitted;
    private final Counter running;
    private final Timer duration;

    private final Counter scheduledOverrun;

    TaggedMetricsScheduledExecutorService(ScheduledExecutorService delegate, ExecutorMetrics metrics, String name) {
        this.delegate = delegate;
        this.name = name;

        this.submitted = metrics.submitted(name);
        this.running = metrics.running(name);
        this.duration = metrics.duration(name);

        this.scheduledOverrun = metrics.scheduledOverrun(name);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        ScheduledFuture<?> future = delegate.schedule(new TaggedMetricsRunnable(task), delay, unit);
        // RejectedExecutionException should prevent 'submitted' from being incremented.
        // This means a wrapped same-thread executor will produce delayed 'submitted' values,
        // however the results will work as expected for the more common cases in which
        // either a queue is full, or the delegate has shut down.
        submitted.mark();
        return future;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ScheduledFuture<V> future = delegate.schedule(new TaggedMetricsCallable<>(callable), delay, unit);
        // RejectedExecutionException should prevent 'submitted' from being incremented.
        // This means a wrapped same-thread executor will produce delayed 'submitted' values,
        // however the results will work as expected for the more common cases in which
        // either a queue is full, or the delegate has shut down.
        submitted.mark();
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        ScheduledFuture<?> future = delegate.scheduleAtFixedRate(
                new TaggedMetricsScheduledRunnable(task, period, unit), initialDelay, period, unit);
        // RejectedExecutionException should prevent 'submitted' from being incremented.
        // This means a wrapped same-thread executor will produce delayed 'submitted' values,
        // however the results will work as expected for the more common cases in which
        // either a queue is full, or the delegate has shut down.
        submitted.mark();
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        ScheduledFuture<?> future =
                delegate.scheduleWithFixedDelay(new TaggedMetricsRunnable(task), initialDelay, delay, unit);
        // RejectedExecutionException should prevent 'submitted' from being incremented.
        // This means a wrapped same-thread executor will produce delayed 'submitted' values,
        // however the results will work as expected for the more common cases in which
        // either a queue is full, or the delegate has shut down.
        submitted.mark();
        return future;
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
    public <T> Future<T> submit(Callable<T> task) {
        Future<T> future = delegate.submit(new TaggedMetricsCallable<>(task));
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
    public Future<?> submit(Runnable task) {
        Future<?> future = delegate.submit(new TaggedMetricsRunnable(task));
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
        return "TaggedMetricsScheduledExecutorService{name=" + name + ", delegate='" + delegate + "'}";
    }

    private final class TaggedMetricsRunnable implements Runnable {

        private final Runnable task;

        TaggedMetricsRunnable(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            running.inc();
            try (Timer.Context ignored = duration.time()) {
                task.run();
            } finally {
                running.dec();
            }
        }
    }

    private final class TaggedMetricsScheduledRunnable implements Runnable {

        private final Runnable task;
        private final long periodInNanos;

        TaggedMetricsScheduledRunnable(Runnable task, long period, TimeUnit unit) {
            this.task = task;
            this.periodInNanos = unit.toNanos(period);
        }

        @Override
        public void run() {
            running.inc();
            Timer.Context context = duration.time();
            try {
                task.run();
            } finally {
                long elapsed = context.stop();
                running.dec();
                if (elapsed > periodInNanos) {
                    scheduledOverrun.inc();
                }
            }
        }
    }

    private final class TaggedMetricsCallable<T> implements Callable<T> {

        private final Callable<T> task;

        TaggedMetricsCallable(Callable<T> task) {
            this.task = task;
        }

        @Override
        public T call() throws Exception {
            running.inc();
            try (Timer.Context ignored = duration.time()) {
                return task.call();
            } finally {
                running.dec();
            }
        }
    }
}
