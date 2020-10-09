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

import com.codahale.metrics.Counter;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public final class HasBeenUpdatedCounter extends Counter {
    private volatile boolean hasBeenUpdated = false;

    HasBeenUpdatedCounter() {}

    /**
     * Returns false if this counter has never been interacted with, allowing users to differentiate whether
     * {#getCount} returns 0 because the Counter has never been touched from the case where it has been incremented
     * and then decremented.
     */
    public boolean hasBeenUpdated() {
        return hasBeenUpdated;
    }

    @Override
    public void inc() {
        inc(1);
    }

    @Override
    public void inc(long num) {
        hasBeenUpdated = true;
        super.inc(num);
    }

    @Override
    public void dec() {
        dec(1);
    }

    @Override
    public void dec(long num) {
        hasBeenUpdated = true;
        super.dec(num);
    }
}
