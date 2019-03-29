/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings({"BanGuavaCaches", // this implementation is explicitly for Guava caches
                   "NullAway"}) // IntelliJ warnings about injected fields
@RunWith(MockitoJUnitRunner.class)
public class CacheMetricSetTest {

    private final MetricRegistry metrics = new MetricRegistry();
    private final TestClock clock = new TestClock();

    @Mock
    private LoadingCache<Integer, String> cache;

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
    public void testDerivedGauge() {
        when(cache.stats()).thenReturn(new CacheStats(1L, 2L, 3L, 4L, 5L, 6L));
        Gauge<CacheStats> cachedCacheStats = CacheMetricSet.createCachedCacheStats(cache, clock,
                15, TimeUnit.SECONDS);
        CacheStats value1 = cachedCacheStats.getValue();
        CacheStats value2 = cachedCacheStats.getValue();
        assertThat(value1.requestCount()).isEqualTo(value2.requestCount());
        assertThat(value1).isSameAs(value2);
        verify(cache, times(1)).stats();

        Gauge<Long> requestGauge = CacheMetricSet.transformingGauge(cachedCacheStats, CacheStats::requestCount);
        assertThat(requestGauge.getValue()).isEqualTo(3);
        assertThat(requestGauge.getValue()).isEqualTo(3);
        verify(cache, times(1)).stats();

        clock.advance(1, TimeUnit.MINUTES);
        assertThat(requestGauge.getValue()).isEqualTo(3);
        verify(cache, times(2)).stats();
        verifyNoMoreInteractions(cache);
    }

}
