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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.palantir.tritium.metrics.TaggedMetricRegistry;
import com.palantir.tritium.metrics.TestClock;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class CaffeineCacheMetricSetTest {

    private final TaggedMetricRegistry metrics = new TaggedMetricRegistry();
    private final TestClock clock = new TestClock();

    @Mock
    private LoadingCache<Integer, String> cache;

    @After
    public void after() {
        System.out.println(metrics.getMetrics());
    }

    Map<String, Metric> getCacheGauges(TaggedMetricRegistry taggedMetricRegistry, String cacheName) {
        return taggedMetricRegistry.getMetrics().entrySet().stream()
                .filter(e -> e.getValue() instanceof Gauge)
                .filter(e -> cacheName.equals(e.getKey().tags().get("name")))
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
    }

    @Test
    public void testRegisterCache() {
        CaffeineCacheStats.registerCache(metrics, cache, "test1", clock);
        Map<String, Metric> cacheGauges = getCacheGauges(metrics, "test1");
        assertThat(cacheGauges).containsOnlyKeys(
                "cache.estimated.size",
                "cache.eviction.count",
                "cache.hit.count",
                "cache.hit.ratio",
                "cache.load.average.millis",
                "cache.load.failure.count",
                "cache.load.success.count",
                "cache.miss.count",
                "cache.miss.ratio",
                "cache.request.count"
        );

        when(cache.stats()).thenReturn(new CacheStats(1L, 2L, 3L, 4L, 5L, 6L));
        when(cache.estimatedSize()).thenReturn(42L);

        assertThat(cacheGauges).extracting("cache.request.count").extracting("value").contains(3L);
        assertThat(cacheGauges).extracting("cache.hit.count").extracting("value").contains(1L);
        assertThat(cacheGauges).extracting("cache.hit.ratio").extracting("value").contains(1.0 / 3.0);
        assertThat(cacheGauges).extracting("cache.miss.count").extracting("value").contains(2L);
        assertThat(cacheGauges).extracting("cache.miss.ratio").extracting("value").contains(2.0 / 3.0);
        assertThat(cacheGauges).extracting("cache.estimated.size").extracting("value").contains(42L);
        assertThat(cacheGauges).extracting("cache.eviction.count").extracting("value").contains(6L);
        assertThat(cacheGauges).extracting("cache.load.average.millis").extracting("value").isNotNull();
        assertThat(cacheGauges).extracting("cache.load.failure.count").extracting("value").contains(4L);
        assertThat(cacheGauges).extracting("cache.load.success.count").extracting("value").contains(3L);
        verify(cache, times(1)).stats();

        clock.advance(1, TimeUnit.MINUTES); // let stats snapshot cache expire
        when(cache.stats()).thenReturn(new CacheStats(11L, 12L, 13L, 14L, 15L, 16L));
        when(cache.estimatedSize()).thenReturn(37L);

        assertThat(cacheGauges).extracting("cache.request.count").extracting("value").contains(23L);
        assertThat(cacheGauges).extracting("cache.hit.count").extracting("value").contains(11L);
        assertThat(cacheGauges).extracting("cache.miss.count").extracting("value").contains(12L);
        assertThat(cacheGauges).extracting("cache.eviction.count").extracting("value").contains(16L);
        assertThat(cacheGauges).extracting("cache.load.average.millis").extracting("value").isNotNull();
        assertThat(cacheGauges).extracting("cache.load.failure.count").extracting("value").contains(14L);
        assertThat(cacheGauges).extracting("cache.load.success.count").extracting("value").contains(13L);
        verify(cache, times(2)).stats();
    }

    @Test
    public void testNoStats() {
        CaffeineCacheStats.registerCache(metrics, cache, "test2");
        Map<String, Metric> cacheGauges = getCacheGauges(metrics, "test2");
        assertThat(cacheGauges).containsOnlyKeys(
                "cache.estimated.size",
                "cache.eviction.count",
                "cache.hit.count",
                "cache.hit.ratio",
                "cache.load.average.millis",
                "cache.load.failure.count",
                "cache.load.success.count",
                "cache.miss.count",
                "cache.miss.ratio",
                "cache.request.count"
        );

        when(cache.stats()).thenReturn(new CacheStats(0L, 0L, 0L, 0L, 0L, 0L));
        assertThat(cacheGauges).extracting("cache.request.count").extracting("value").contains(0L);
        assertThat(cacheGauges).extracting("cache.hit.count").extracting("value").contains(0L);
        assertThat(cacheGauges).extracting("cache.hit.ratio").extracting("value").contains(Double.NaN);
        assertThat(cacheGauges).extracting("cache.miss.count").extracting("value").contains(0L);
        assertThat(cacheGauges).extracting("cache.miss.ratio").extracting("value").contains(Double.NaN);
        assertThat(cacheGauges).extracting("cache.eviction.count").extracting("value").contains(0L);
        assertThat(cacheGauges).extracting("cache.load.average.millis").extracting("value").contains(0.0d);
        assertThat(cacheGauges).extracting("cache.load.failure.count").extracting("value").contains(0L);
        assertThat(cacheGauges).extracting("cache.load.success.count").extracting("value").contains(0L);
    }

    @Test
    public void testDerivedGauge() {
        when(cache.stats()).thenReturn(new CacheStats(1L, 2L, 3L, 4L, 5L, 6L));
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
