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

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.tritium.metrics.MetricRegistries;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

public class CaffeineCacheMetricSetTest {

    private MetricRegistry metrics = MetricRegistries.createWithHdrHistogramReservoirs();

    @After
    public void tearDown() throws Exception {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertDurationsTo(TimeUnit.MICROSECONDS)
                .convertRatesTo(TimeUnit.MICROSECONDS)
                .build();
        reporter.report();
        reporter.stop();
    }

    @Test
    public void testRegisterCache() throws InterruptedException {

        LoadingCache<Integer, String> cache = Caffeine.newBuilder()
                .maximumSize(1L)
                .recordStats()
                .build(String::valueOf);

        CaffeineCacheStats.registerCache(metrics, cache, "test1");

        assertThat(metrics.getGauges(metricsPrefixedBy("test1")).keySet()).containsExactly(
                "test1.cache.estimated.size",
                "test1.cache.eviction.count",
                "test1.cache.hit.count",
                "test1.cache.hit.ratio",
                "test1.cache.load.average.millis",
                "test1.cache.load.failure.count",
                "test1.cache.load.success.count",
                "test1.cache.miss.count",
                "test1.cache.miss.ratio",
                "test1.cache.request.count"
        );

        assertThat(cache.get(42)).isEqualTo("42");

        assertThat(metrics.getGauges().get("test1.cache.request.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.cache.hit.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.cache.hit.ratio").getValue()).isEqualTo(0.0d);
        assertThat(metrics.getGauges().get("test1.cache.miss.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.cache.miss.ratio").getValue()).isEqualTo(1.0d);
        assertThat(metrics.getGauges().get("test1.cache.estimated.size").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.cache.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.cache.load.average.millis").getValue()).isNotEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.cache.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.cache.load.success.count").getValue()).isEqualTo(1L);

        assertThat(cache.get(42)).isEqualTo("42");

        Thread.sleep(700); // let stats snapshot cache expire

        assertThat(metrics.getGauges().get("test1.cache.request.count").getValue()).isEqualTo(2L);
        assertThat(metrics.getGauges().get("test1.cache.hit.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.cache.miss.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test1.cache.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.cache.load.average.millis").getValue()).isNotEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.cache.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test1.cache.load.success.count").getValue()).isEqualTo(1L);

        cache.get(1);

        Thread.sleep(700); // let stats snapshot cache expire

        assertThat(metrics.getGauges().get("test1.cache.eviction.count").getValue()).isEqualTo(1L);
    }

    @Test
    public void testNoStats() throws Exception {
        LoadingCache<Integer, String> cache = Caffeine.newBuilder()
                .maximumSize(1L)
                .build(String::valueOf);

        CaffeineCacheStats.registerCache(metrics, cache, "test2");

        assertThat(metrics.getGauges(metricsPrefixedBy("test2")).keySet()).containsExactly(
                "test2.cache.estimated.size",
                "test2.cache.eviction.count",
                "test2.cache.hit.count",
                "test2.cache.hit.ratio",
                "test2.cache.load.average.millis",
                "test2.cache.load.failure.count",
                "test2.cache.load.success.count",
                "test2.cache.miss.count",
                "test2.cache.miss.ratio",
                "test2.cache.request.count");

        assertThat(cache.get(42)).isEqualTo("42");

        assertThat(metrics.getGauges().get("test2.cache.request.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.cache.hit.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.cache.hit.ratio").getValue()).isEqualTo(Double.NaN);
        assertThat(metrics.getGauges().get("test2.cache.miss.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.cache.miss.ratio").getValue()).isEqualTo(Double.NaN);
        assertThat(metrics.getGauges().get("test2.cache.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.cache.load.average.millis").getValue()).isEqualTo(0.0d);
        assertThat(metrics.getGauges().get("test2.cache.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test2.cache.load.success.count").getValue()).isEqualTo(0L);
    }

    private static MetricFilter metricsPrefixedBy(final String prefix) {
        return (name, metric) -> name.startsWith(prefix);
    }

}
