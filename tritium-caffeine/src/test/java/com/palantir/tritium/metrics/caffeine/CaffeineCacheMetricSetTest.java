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

package com.palantir.tritium.metrics.caffeine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.TestClock;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CaffeineCacheMetricSetTest {
    private static final long MAXIMUM_CACHE_SIZE = 1234L;
    private static final long WEIGHTED_CACHE_SIZE = 123L;

    private final MetricRegistry metrics = new MetricRegistry();
    private final TestClock clock = new TestClock();

    @Mock
    private LoadingCache<Integer, String> cache;

    @Mock
    private Policy<Integer, String> policy;

    @Mock
    private Policy.Eviction<Integer, String> evictionPolicy;

    @Before
    public void before() {
        when(cache.policy()).thenReturn(policy);
        when(policy.eviction()).thenReturn(Optional.of(evictionPolicy));
        when(evictionPolicy.getMaximum()).thenReturn(MAXIMUM_CACHE_SIZE);
        when(evictionPolicy.weightedSize()).thenReturn(OptionalLong.of(WEIGHTED_CACHE_SIZE));
    }

    @After
    public void after() {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertDurationsTo(TimeUnit.MICROSECONDS)
                .convertRatesTo(TimeUnit.MICROSECONDS)
                .build();
        reporter.report();
        reporter.stop();
    }

    @Test
    public void testRegisterCache() {
        CaffeineCacheStats.registerCache(metrics, cache, "test1", clock);

        assertThat(metrics.getGauges(MetricRegistries.metricsPrefixedBy("test1")).keySet()).containsExactlyInAnyOrder(
                "test1.cache.estimated.size",
                "test1.cache.eviction.count",
                "test1.cache.hit.count",
                "test1.cache.hit.ratio",
                "test1.cache.load.average.millis",
                "test1.cache.load.failure.count",
                "test1.cache.load.success.count",
                "test1.cache.maximum.size",
                "test1.cache.miss.count",
                "test1.cache.miss.ratio",
                "test1.cache.request.count",
                "test1.cache.weighted.size"
        );

        when(cache.stats()).thenReturn(new CacheStats(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        when(cache.estimatedSize()).thenReturn(42L);

        SortedMap<String, Gauge> gauges = metrics.getGauges();
        assertThat(gauges.get("test1.cache.request.count").getValue()).isEqualTo(3L);
        assertThat(gauges.get("test1.cache.hit.count").getValue()).isEqualTo(1L);
        assertThat(gauges.get("test1.cache.hit.ratio").getValue()).isEqualTo(1.0 / 3.0);
        assertThat(gauges.get("test1.cache.miss.count").getValue()).isEqualTo(2L);
        assertThat(gauges.get("test1.cache.miss.ratio").getValue()).isEqualTo(2.0 / 3.0);
        assertThat(gauges.get("test1.cache.maximum.size").getValue()).isEqualTo(MAXIMUM_CACHE_SIZE);
        assertThat(gauges.get("test1.cache.estimated.size").getValue()).isEqualTo(42L);
        assertThat(gauges.get("test1.cache.weighted.size").getValue()).isEqualTo(WEIGHTED_CACHE_SIZE);
        assertThat(gauges.get("test1.cache.eviction.count").getValue()).isEqualTo(6L);
        assertThat(gauges.get("test1.cache.load.average.millis").getValue()).isNotEqualTo(5.0 / 3.0);
        assertThat(gauges.get("test1.cache.load.failure.count").getValue()).isEqualTo(4L);
        assertThat(gauges.get("test1.cache.load.success.count").getValue()).isEqualTo(3L);
        verify(cache, times(1)).stats();

        clock.advance(1, TimeUnit.MINUTES); // let stats snapshot cache expire
        when(cache.stats()).thenReturn(new CacheStats(11L, 12L, 13L, 14L, 15L, 16L, 17L));
        when(cache.estimatedSize()).thenReturn(37L);

        gauges = metrics.getGauges();
        assertThat(gauges.get("test1.cache.request.count").getValue()).isEqualTo(23L);
        assertThat(gauges.get("test1.cache.hit.count").getValue()).isEqualTo(11L);
        assertThat(gauges.get("test1.cache.miss.count").getValue()).isEqualTo(12L);
        assertThat(gauges.get("test1.cache.eviction.count").getValue()).isEqualTo(16L);
        assertThat(gauges.get("test1.cache.load.average.millis").getValue()).isNotEqualTo(15.0 / 23.0);
        assertThat(gauges.get("test1.cache.load.failure.count").getValue()).isEqualTo(14L);
        assertThat(gauges.get("test1.cache.load.success.count").getValue()).isEqualTo(13L);
        verify(cache, times(2)).stats();
    }

    @Test
    public void testNoStats() {
        CaffeineCacheStats.registerCache(metrics, cache, "test2");

        assertThat(metrics.getGauges(MetricRegistries.metricsPrefixedBy("test2")).keySet()).containsExactlyInAnyOrder(
                "test2.cache.estimated.size",
                "test2.cache.eviction.count",
                "test2.cache.hit.count",
                "test2.cache.hit.ratio",
                "test2.cache.load.average.millis",
                "test2.cache.load.failure.count",
                "test2.cache.load.success.count",
                "test2.cache.maximum.size",
                "test2.cache.miss.count",
                "test2.cache.miss.ratio",
                "test2.cache.request.count",
                "test2.cache.weighted.size"
        );

        when(cache.stats()).thenReturn(new CacheStats(0L, 0L, 0L, 0L, 0L, 0L, 0L));
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

    @Test
    public void testDerivedGauge() {
        when(cache.stats()).thenReturn(new CacheStats(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        Gauge<CacheStats> cachedCacheStats = CaffeineCacheMetricSet.createCachedCacheStats(cache, clock,
                15, TimeUnit.SECONDS);
        CacheStats value1 = cachedCacheStats.getValue();
        CacheStats value2 = cachedCacheStats.getValue();
        assertThat(value1.requestCount()).isEqualTo(value2.requestCount());
        assertThat(value1).isSameAs(value2);
        verify(cache, times(1)).stats();

        Gauge<Long> requestGauge = CaffeineCacheMetricSet.transformingGauge(cachedCacheStats, CacheStats::requestCount);
        assertThat(requestGauge.getValue()).isEqualTo(3);
        assertThat(requestGauge.getValue()).isEqualTo(3);
        verify(cache, times(1)).stats();

        clock.advance(1, TimeUnit.MINUTES);
        assertThat(requestGauge.getValue()).isEqualTo(3);
        verify(cache, times(2)).stats();
        verifyNoMoreInteractions(cache);
    }

}
