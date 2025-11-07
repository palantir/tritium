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

import java.util.function.Function;
import org.jspecify.annotations.Nullable;

class AsyncLoadingCacheImpl<K, V> extends AsyncCacheImpl<K, V> implements AsyncLoadingCache<K, V> {

    private final Function<K, V> mappingFunction;

    AsyncLoadingCacheImpl(
            String name, com.github.benmanes.caffeine.cache.AsyncCache<K, V> cache, CacheLoader<K, V> cacheLoader) {
        super(name, cache);
        this.mappingFunction = CacheLoaders.newMappingFunction(cacheLoader);
    }

    @Override
    @Nullable
    public final V get(K key) {
        return get(key, mappingFunction);
    }
}
