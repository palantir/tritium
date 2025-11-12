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

import com.github.benmanes.caffeine.cache.AsyncCache;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class AsyncBulkLoadingCacheImpl<K, V> extends AsyncLoadingCacheImpl<K, V> implements AsyncBulkLoadingCache<K, V> {

    private final Function<Set<? extends K>, Map<K, V>> bulkMappingFunction;

    AsyncBulkLoadingCacheImpl(String name, AsyncCache<K, V> cache, BulkCacheLoader<K, V> cacheLoader) {
        super(name, cache, cacheLoader);
        this.bulkMappingFunction = CacheLoaders.newBulkMappingFunction(cacheLoader);
    }

    @Override
    public final Map<K, V> getAll(Iterable<? extends K> keys) {
        return getAll(keys, bulkMappingFunction);
    }
}
