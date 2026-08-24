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

package com.palantir.tritium.metrics.caffeine;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MovingAverages;

enum DisabledMovingAverages implements MovingAverages {
    INSTANCE;

    static Meter meter() {
        return new Meter(INSTANCE);
    }

    @Override
    public void tickIfNecessary() {}

    @Override
    public void update(long _events) {}

    @Override
    public double getM1Rate() {
        return 0.0d;
    }

    @Override
    public double getM5Rate() {
        return 0.0d;
    }

    @Override
    public double getM15Rate() {
        return 0.0d;
    }
}
