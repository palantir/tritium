/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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

import static java.util.stream.Collectors.toMap;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import java.util.Map;

public final class DropwizardTaggedMetricSet implements TaggedMetricSet {
    private final MetricRegistry metricRegistry;

    public DropwizardTaggedMetricSet(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    public Map<MetricName, Metric> getMetrics() {
        return metricRegistry.getMetrics().entrySet().stream()
                .collect(toMap(entry -> MetricName.builder().safeName(entry.getKey()).build(), Map.Entry::getValue));
    }
}
