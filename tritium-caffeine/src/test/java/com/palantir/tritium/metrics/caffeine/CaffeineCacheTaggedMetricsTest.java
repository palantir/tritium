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

package com.palantir.tritium.metrics.caffeine;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Gauge;
import com.github.benmanes.caffeine.cache.Cache;
import com.palantir.tritium.metrics.registry.MetricName;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class CaffeineCacheTaggedMetricsTest {

    @Test
    void createMetrics(@Mock Cache<?, ?> cache) {
        CaffeineCacheTaggedMetrics cacheTaggedMetrics = CaffeineCacheTaggedMetrics.create(cache, "test");
        assertThat(cacheTaggedMetrics).isNotNull();
        Map<MetricName, Gauge<?>> metrics = cacheTaggedMetrics.getMetrics();
        assertThat(metrics).hasSize(12);
        assertThat(metrics.keySet())
                .extracting(MetricName::safeName)
                .containsOnly(
                        "cache.estimated.size",
                        "cache.maximum.size",
                        "cache.weighted.size",
                        "cache.request.count",
                        "cache.hit.count",
                        "cache.hit.ratio",
                        "cache.miss.count",
                        "cache.miss.ratio",
                        "cache.eviction.count",
                        "cache.load.success.count",
                        "cache.load.failure.count",
                        "cache.load.average.millis");
        metrics.keySet().forEach(metricName -> assertThat(metricName.safeTags())
                .containsOnlyKeys("cache")
                .containsValue("test"));
    }
}
