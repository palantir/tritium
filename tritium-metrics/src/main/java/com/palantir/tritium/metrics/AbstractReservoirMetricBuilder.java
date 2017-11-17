/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.Metric;
import com.codahale.metrics.Reservoir;
import com.google.common.base.Supplier;

abstract class AbstractReservoirMetricBuilder<T extends Metric> extends AbstractMetricBuilder<T> {

    private final Supplier<Reservoir> reservoirSupplier;

    AbstractReservoirMetricBuilder(Class<T> metricType, Supplier<Reservoir> reservoirSupplier) {
        super(metricType);
        this.reservoirSupplier = checkNotNull(reservoirSupplier, "reservoirSupplier");
    }

    Supplier<Reservoir> getReservoirSupplier() {
        return reservoirSupplier;
    }

}
