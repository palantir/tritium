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

package com.palantir.tritium.metrics.registry;

import com.codahale.metrics.MovingAverages;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Lazy {@link MovingAverages} implementation which only tracks rates after they've first been requested.
 * This allows the majority of metrics to avoid timer syscalls without breaking existing consumers that
 * depend upon meter and timer rate APIs.
 */
final class LazilyInitializedMovingAverages implements MovingAverages {

    private static final AtomicReferenceFieldUpdater<LazilyInitializedMovingAverages, MovingAverages> delegateUpdater =
            AtomicReferenceFieldUpdater.newUpdater(
                    LazilyInitializedMovingAverages.class, MovingAverages.class, "delegate");
    private final Supplier<MovingAverages> delegateFactory;

    @Nullable
    private volatile MovingAverages delegate;

    LazilyInitializedMovingAverages(Supplier<MovingAverages> delegateFactory) {
        this.delegateFactory = delegateFactory;
    }

    @Override
    public void tickIfNecessary() {
        MovingAverages snapshot = delegate;
        if (snapshot != null) {
            snapshot.tickIfNecessary();
        }
    }

    @Override
    public void update(long value) {
        MovingAverages snapshot = delegate;
        if (snapshot != null) {
            snapshot.update(value);
        }
    }

    @Override
    public double getM1Rate() {
        return getOrCreateDelegate().getM1Rate();
    }

    @Override
    public double getM5Rate() {
        return getOrCreateDelegate().getM5Rate();
    }

    @Override
    public double getM15Rate() {
        return getOrCreateDelegate().getM15Rate();
    }

    private MovingAverages getOrCreateDelegate() {
        MovingAverages snapshot = delegate;
        return snapshot == null ? tryCreateDelegate() : snapshot;
    }

    // After a cas from null -> non-null has failed, the result cannot be null.
    // No path updates from non-null to null.
    @SuppressWarnings("NullAway")
    private MovingAverages tryCreateDelegate() {
        MovingAverages created = delegateFactory.get();
        if (delegateUpdater.compareAndSet(this, null, created)) {
            return created;
        }
        // Another thread has initialized the delegate, return the value it created
        return this.delegate;
    }

    @Override
    public String toString() {
        MovingAverages snapshot = delegate;
        return "LazilyInitializedMovingAverages{" + (snapshot == null ? delegateFactory : snapshot) + '}';
    }
}
