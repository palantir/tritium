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
import com.google.errorprone.annotations.MustBeClosed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

final class TaggedMetricsExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    private final Meter submitted;
    private final Counter running;
    private final Meter completed;
    private final Timer duration;

    @Nullable
    private final Timer queuedDuration;

    TaggedMetricsExecutorService(
            ExecutorService delegate, ExecutorMetrics metrics, String name, boolean reportQueuedDuration) {
        this.delegate = delegate;

        this.submitted = metrics.submitted(name);
        this.running = metrics.running(name);
        this.completed = metrics.completed(name);
        this.duration = metrics.duration(name);
        this.queuedDuration = reportQueuedDuration ? metrics.queuedDuration(name) : null;
    }

    @Override
    public void execute(Runnable task) {
        submitted.mark();
        delegate.execute(new TaggedMetricsRunnable(task));
    }

    @Override
    public Future<?> submit(Runnable task) {
        submitted.mark();
        return delegate.submit(new TaggedMetricsRunnable(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        submitted.mark();
        return delegate.submit(new TaggedMetricsRunnable(task), result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        submitted.mark();
        return delegate.submit(new TaggedMetricsCallable<>(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        submitted.mark(tasks.size());
        Collection<TaggedMetricsCallable<T>> instrumented = instrument(tasks);
        return delegate.invokeAll(instrumented);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        submitted.mark(tasks.size());
        Collection<TaggedMetricsCallable<T>> instrumented = instrument(tasks);
        return delegate.invokeAll(instrumented, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException, InterruptedException {
        submitted.mark(tasks.size());
        Collection<TaggedMetricsCallable<T>> instrumented = instrument(tasks);
        return delegate.invokeAny(instrumented);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        submitted.mark(tasks.size());
        Collection<TaggedMetricsCallable<T>> instrumented = instrument(tasks);
        return delegate.invokeAny(instrumented, timeout, unit);
    }

    private <T> Collection<TaggedMetricsCallable<T>> instrument(Collection<? extends Callable<T>> tasks) {
        List<TaggedMetricsCallable<T>> instrumented = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            instrumented.add(new TaggedMetricsCallable<>(task));
        }
        return instrumented;
    }

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

    @Nullable
    @MustBeClosed
    private Timer.Context startQueueTimerIfEnabled() {
        Timer queuedDurationTimer = queuedDuration;
        return queuedDurationTimer == null ? null : queuedDurationTimer.time();
    }

    private final class TaggedMetricsRunnable implements Runnable {

        private final Runnable task;

        @Nullable
        private final Timer.Context queuedContext = startQueueTimerIfEnabled();

        TaggedMetricsRunnable(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            stopQueueTimer();
            running.inc();
            try (Timer.Context ignored = duration.time()) {
                task.run();
            } finally {
                running.dec();
                completed.mark();
            }
        }

        void stopQueueTimer() {
            Timer.Context queuedContextSnapshot = queuedContext;
            if (queuedContextSnapshot != null) {
                queuedContextSnapshot.stop();
            }
        }
    }

    private final class TaggedMetricsCallable<T> implements Callable<T> {

        private final Callable<T> task;

        @Nullable
        private final Timer.Context queuedContext = startQueueTimerIfEnabled();

        TaggedMetricsCallable(Callable<T> task) {
            this.task = task;
        }

        @Override
        public T call() throws Exception {
            stopQueueTimer();
            running.inc();
            try (Timer.Context ignored = duration.time()) {
                return task.call();
            } finally {
                running.dec();
                completed.mark();
            }
        }

        void stopQueueTimer() {
            Timer.Context queuedContextSnapshot = queuedContext;
            if (queuedContextSnapshot != null) {
                queuedContextSnapshot.stop();
            }
        }
    }
}
