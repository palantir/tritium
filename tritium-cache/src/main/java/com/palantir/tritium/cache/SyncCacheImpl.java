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

import com.google.common.collect.Iterators;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

class SyncCacheImpl<K, V> implements SyncCache<K, V> {

    private final com.github.benmanes.caffeine.cache.Cache<K, V> cache;

    SyncCacheImpl(com.github.benmanes.caffeine.cache.Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    @Nullable
    public V getIfPresent(K key) {
        return cache.getIfPresent(key);
    }

    @Override
    @Nullable
    public V get(K key, Function<? super K, ? extends V> mappingFunction) {
        return cache.get(key, mappingFunction);
    }

    @Override
    public Map<K, V> getAllPresent(Iterable<? extends K> keys) {
        return cache.getAllPresent(keys);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        cache.putAll(map);
    }

    @Override
    public void invalidate(K key) {
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll(Iterable<? extends K> keys) {
        cache.invalidateAll(keys);
    }

    @Override
    public Iterator<Entry<K, V>> entries() {
        return Iterators.unmodifiableIterator(cache.asMap().entrySet().iterator());
    }
}
