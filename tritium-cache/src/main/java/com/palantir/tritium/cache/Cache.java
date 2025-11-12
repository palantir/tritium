/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.cache;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;

public enum Cache {
    ;

    @CheckReturnValue
    public static <K, V> NameBuilder<K, V> builder() {
        return new CacheBuilder<>();
    }

    @CheckReturnValue
    public interface NameBuilder<K, V> {

        SizeBuilder<K, V> name(@CompileTimeConstant String name);
    }

    @CheckReturnValue
    public interface SizeBuilder<K, V> {

        ExpiryBuilder<K, V> maximumSize(long maximumSize);

        ExpiryBuilder<K, V> maximumSize(long maximumSize, Weigher<? super K, ? super V> weigher);
    }

    @CheckReturnValue
    public interface ExpiryBuilder<K, V> {

        MetricsBuilder<K, V> expiry(Expiry<? super K, ? super V> expiry);

        MetricsBuilder<K, V> noExpiry();
    }

    @CheckReturnValue
    public interface MetricsBuilder<K, V> {

        ExecutorBuilder<K, V> metrics(TaggedMetricRegistry taggedMetrics);

        ExecutorBuilder<K, V> noMetrics();
    }

    @CheckReturnValue
    public interface ExecutorBuilder<K, V> {

        Builder<K, V> executor(ExecutorFactory executorFactory);
    }

    @CheckReturnValue
    public interface Builder<K, V> {

        Builder<K, V> ticker(Ticker ticker);

        SyncCache<K, V> buildSync();

        SyncLoadingCache<K, V> buildSyncWithLoader(CacheLoader<K, V> cacheLoader);

        AsyncCache<K, V> buildAsync();

        AsyncLoadingCache<K, V> buildAsyncWithLoader(CacheLoader<K, V> cacheLoader);

        AsyncBulkLoadingCache<K, V> buildAsyncWithBulkLoader(BulkCacheLoader<K, V> cacheLoader);
    }
}
