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

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Metric;
import org.checkerframework.checker.nullness.qual.Nullable;

abstract class AbstractMetricBuilder<T extends Metric> implements MetricBuilder<T> {

    private final Class<T> metricType;

    protected AbstractMetricBuilder(Class<T> metricType) {
        this.metricType = checkNotNull(metricType, "metricType");
    }

    @Override
    public boolean isInstance(@Nullable Metric metric) {
        //noinspection PointlessNullCheck
        return metric != null && metricType.isInstance(metric);
    }
}
