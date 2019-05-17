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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CaffeineStatsTest {

    @Mock Cache<?, ?> cache;
    @Mock Policy<?, ?> policy;
    @Mock Policy.Eviction<?, ?> eviction;

    private CaffeineStats stats;

    @Before
    public void before() {
        stats = new CaffeineStats(cache, () ->
                new CacheStats(1, 2, 3, 4, 5, 6, 7));
        when(cache.policy()).thenAnswer(ignored -> policy);
        when(policy.eviction()).thenAnswer(ignored -> Optional.of(eviction));
    }

    @Test
    public void estimatedSize() {
        when(cache.estimatedSize()).thenReturn(42L);
        assertThat(stats.estimatedSize()).isEqualTo(42);
        verify(cache).estimatedSize();
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void evictionCount() {
        assertThat(stats.evictionCount()).isEqualTo(6);
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void hitCount() {
        assertThat(stats.hitCount()).isEqualTo(1);
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void hitRatio() {
        assertThat(stats.hitRatio()).isEqualTo(1.0 / 3.0);
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void loadAverageMillis() {
        assertThat(stats.loadAverageMillis()).isEqualTo((5.0d / 7.0d) / 1_000_000.0d);
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void loadFailureCount() {
        assertThat(stats.loadFailureCount()).isEqualTo(4);
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void loadSuccessCount() {
        assertThat(stats.loadSuccessCount()).isEqualTo(3);
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void maximumSize() {
        when(eviction.getMaximum()).thenReturn(42L);
        assertThat(stats.maximumSize()).isEqualTo(42);
        verify(cache).policy();
        verify(policy).eviction();
        verify(eviction).getMaximum();
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void maximumSizeUnset() {
        when(policy.eviction()).thenReturn(Optional.empty());
        assertThat(stats.maximumSize()).isEqualTo(-1);
        verify(cache).policy();
        verify(policy).eviction();
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void missCount() {
        assertThat(stats.missCount()).isEqualTo(2);
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void missRatio() {
        assertThat(stats.missRatio()).isEqualTo(2.0 / 3.0);
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void weightedSize() {
        when(eviction.weightedSize()).thenReturn(OptionalLong.of(42L));
        assertThat(stats.weightedSize()).isEqualTo(42);
        verify(cache).policy();
        verify(policy).eviction();
        verify(eviction).weightedSize();
        verifyNoMoreInteractions(cache, policy, eviction);
    }

    @Test
    public void weightedSizeNoWeights() {
        when(policy.eviction()).thenReturn(Optional.empty());
        assertThat(stats.weightedSize()).isEqualTo(0);
        verify(cache).policy();
        verify(policy).eviction();
        verifyNoMoreInteractions(cache, policy, eviction);
    }
}
