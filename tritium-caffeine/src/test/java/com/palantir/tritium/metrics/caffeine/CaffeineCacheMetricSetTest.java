/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

import static com.google.common.truth.Truth.assertThat;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.tritium.metrics.MetricRegistries;
import org.junit.Test;

public class CaffeineCacheMetricSetTest {

    private static final String RESERVOIR_TYPE_GAUGE_NAME = MetricRegistry.name(MetricRegistry.class, "reservoirType");


    private MetricRegistry createMetricRegistry() {
        MetricRegistry metrics = MetricRegistries.createWithHdrHistogramReservoirs();
        assertThat(metrics.getGauges().size()).isEqualTo(1);
        assertThat(metrics.getGauges()).containsKey(RESERVOIR_TYPE_GAUGE_NAME);
        assertThat(metrics.getGauges().get(RESERVOIR_TYPE_GAUGE_NAME).getValue()).isEqualTo("HDR Histogram");
        return metrics;
    }

    @Test
    public void testRegisterCache() throws InterruptedException {
        MetricRegistry metrics = createMetricRegistry();

        LoadingCache<Integer, String> cache = Caffeine.newBuilder()
                .maximumSize(1L)
                .recordStats()
                .build(String::valueOf);

        CaffeineCacheStats.registerCache(metrics, cache, "test1");

        assertThat(metrics.getGauges().keySet()).containsExactly(
                RESERVOIR_TYPE_GAUGE_NAME,
                "test1.estimated.size",
                "test1.eviction.count",
                "test1.hit.count",
                "test1.hit.ratio",
                "test1.load.average.millis",
                "test1.load.failure.count",
                "test1.load.success.count",
                "test1.miss.count",
                "test1.miss.ratio",
                "test1.request.count"
        );

        assertThat(cache.get(42)).isEqualTo("42");

        assertThat(metrics.getGauges().get("test1.request.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.hit.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.hit.ratio").getValue()).isEqualTo(0.0d);
        assertThat(metrics.getGauges().get("test1.miss.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.miss.ratio").getValue()).isEqualTo(1.0d);
        assertThat(metrics.getGauges().get("test1.estimated.size").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.load.average.millis").getValue()).isNotEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.load.success.count").getValue()).isEqualTo(1L);

        assertThat(cache.get(42)).isEqualTo("42");

        Thread.sleep(700); // let stats snapshot cache expire

        assertThat(metrics.getGauges().get("test1.request.count").getValue()).isEqualTo(2L);
        assertThat(metrics.getGauges().get("test1.hit.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.miss.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.load.average.millis").getValue()).isNotEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.load.success.count").getValue()).isEqualTo(1L);

        cache.get(1);

        Thread.sleep(700); // let stats snapshot cache expire

        assertThat(metrics.getGauges().get("test1.eviction.count").getValue()).isEqualTo(1L);
    }

    @Test
    public void testNoStats() throws Exception {
        MetricRegistry metrics = MetricRegistries.createWithHdrHistogramReservoirs();
        assertThat(metrics.getGauges().size()).isEqualTo(1);
        assertThat(metrics.getGauges()).containsKey(RESERVOIR_TYPE_GAUGE_NAME);
        assertThat(metrics.getGauges().get(RESERVOIR_TYPE_GAUGE_NAME).getValue()).isEqualTo("HDR Histogram");

        LoadingCache<Integer, String> cache = Caffeine.newBuilder()
                .maximumSize(1L)
                .build(String::valueOf);

        CaffeineCacheStats.registerCache(metrics, cache, "test2");

        assertThat(metrics.getGauges().keySet()).containsExactly(
                "com.codahale.metrics.MetricRegistry.reservoirType",
                "test2.estimated.size",
                "test2.eviction.count",
                "test2.hit.count",
                "test2.hit.ratio",
                "test2.load.average.millis",
                "test2.load.failure.count",
                "test2.load.success.count",
                "test2.miss.count",
                "test2.miss.ratio",
                "test2.request.count");

        assertThat(cache.get(42)).isEqualTo("42");

        assertThat(metrics.getGauges().get("test2.request.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.hit.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.hit.ratio").getValue()).isEqualTo(Double.NaN);
        assertThat(metrics.getGauges().get("test2.miss.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.miss.ratio").getValue()).isEqualTo(Double.NaN);
        assertThat(metrics.getGauges().get("test2.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.load.average.millis").getValue()).isEqualTo(0.0d);
        assertThat(metrics.getGauges().get("test2.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.load.success.count").getValue()).isEqualTo(0L);
    }

}
