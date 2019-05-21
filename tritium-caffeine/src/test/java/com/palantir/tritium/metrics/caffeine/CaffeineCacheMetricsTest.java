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
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CaffeineCacheMetricsTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) Cache<?, ?> cache;

    private Map<String, Metric> metrics;

    @Before
    public void before() {
        MetricSet cacheMetricSet = CaffeineCacheMetrics.create(cache, "test");
        assertThat(cacheMetricSet).isNotNull();
        metrics = cacheMetricSet.getMetrics();
    }

    @Test
    public void createMetrics() {
        assertThat(metrics)
                .containsOnlyKeys(
                        "test.cache.estimated.size",
                        "test.cache.maximum.size",
                        "test.cache.weighted.size",
                        "test.cache.request.count",
                        "test.cache.hit.count",
                        "test.cache.hit.ratio",
                        "test.cache.miss.count",
                        "test.cache.miss.ratio",
                        "test.cache.eviction.count",
                        "test.cache.load.success.count",
                        "test.cache.load.failure.count",
                        "test.cache.load.average.millis")
                .extracting(Map::values)
                .hasNoNullFieldsOrProperties();
    }

    @Test
    public void estimatedSize() {
        assertThat(metrics.get("test.cache.estimated.size")).isInstanceOf(Gauge.class)
                .returns(0L, metric -> ((Gauge) metric).getValue());
    }

    @Test
    public void maximumSize() {
        assertThat(metrics.get("test.cache.maximum.size")).isInstanceOf(Gauge.class)
                .returns(-1L, metric -> ((Gauge) metric).getValue());
    }

    @Test
    public void weightedSize() {
        assertThat(metrics.get("test.cache.weighted.size")).isInstanceOf(Gauge.class)
                .returns(0L, metric -> ((Gauge) metric).getValue());
    }
}
