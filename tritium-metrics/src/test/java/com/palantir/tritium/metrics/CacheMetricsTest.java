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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Gauge;
import com.palantir.tritium.metrics.registry.MetricName;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CacheMetricsTest {

    @Mock CacheMetrics.Stats stats;

    @Test
    public void createMetrics() {
        Map<String, Gauge<?>> metrics = CacheMetrics.createMetrics(stats, Function.identity());
        assertThat(metrics)
                .containsOnlyKeys(
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
                        "cache.load.average.millis")
                .extracting(Map::values)
                .hasNoNullFieldsOrProperties();
    }

    @Test
    public void noRequests() {
        when(stats.requestCount()).thenReturn(0L);
        when(stats.hitRatio()).thenCallRealMethod();
        when(stats.missRatio()).thenCallRealMethod();

        assertThat(stats.hitRatio()).isEqualTo(0);
        assertThat(stats.missRatio()).isEqualTo(0);
    }

    @Test
    public void taggedMetricName() {
        Function<String, MetricName> function = CacheMetrics.taggedMetricName("test");
        assertThat(function.apply("a")).isEqualTo(MetricName.builder()
                .safeName("a")
                .putSafeTags("cache", "test")
                .build());
        assertThat(function.apply("a.b")).isEqualTo(MetricName.builder()
                .safeName("a.b")
                .putSafeTags("cache", "test")
                .build());
    }
}
