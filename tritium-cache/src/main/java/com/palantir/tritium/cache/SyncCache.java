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

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public interface SyncCache<K, V> {

    /**
     * See {@link com.github.benmanes.caffeine.cache.Cache#getIfPresent(Object)}.
     */
    @Nullable
    V getIfPresent(K key);

    /**
     * See {@link com.github.benmanes.caffeine.cache.Cache#get(Object, Function)}.
     */
    @Nullable
    V get(K key, Function<? super K, ? extends V> mappingFunction);

    /**
     * See {@link com.github.benmanes.caffeine.cache.Cache#getAllPresent(Iterable)}.
     */
    Map<K, V> getAllPresent(Iterable<? extends K> keys);

    // This interface intentionally does not support getAll() because it is does not provide the desired linearizability
    // properties. The loads in getAll() are performed outside of the backing concurrent map locks and thus will not be
    // removed by a concurrent invalidate() request. This can lead to surprising behavior where outdated entries are
    // added to the cache after an invalidation has occurred.
    //
    // See https://github.com/ben-manes/caffeine/issues/1739#issuecomment-2200862708.

    /**
     * See {@link com.github.benmanes.caffeine.cache.Cache#put(Object, Object)}.
     */
    void put(K key, V value);

    /**
     * See {@link com.github.benmanes.caffeine.cache.Cache#putAll(Map)}.
     */
    void putAll(Map<? extends K, ? extends V> map);

    /**
     * See {@link com.github.benmanes.caffeine.cache.Cache#invalidate(Object)}.
     */
    void invalidate(K key);

    /**
     * See {@link com.github.benmanes.caffeine.cache.Cache#invalidateAll(Iterable)}.
     */
    void invalidateAll(Iterable<? extends K> keys);

    // This interface intentionally does not support invalidateAll() because it is does not provide the desired
    // linearizability properties. The cache does not have visibility into in-flight computations (because the backing
    // concurrent map does not expose them) and thus cannot invalidate these in-flight computations. This can lead to
    // surprising behavior where outdated entries are added to the cache after an invalidation has occurred.
    //
    // See https://github.com/ben-manes/caffeine/issues/1739#issuecomment-2200862708.

    // This interface intentionally does not support asMap() because we do not want to support cache mutations outside
    // of the methods explicitly provide as part of this interface. We cannot guarantee the map implementation returned
    // by Caffeine has the desired behavior. For example, it is impractical to provide a sensible, performant
    // implementation of methods like Map.compute() for an async cache with in-flight computations.
    //
    // It is reasonable for callers to want to obtain snapshots of cache entries, so we provide this method to allow
    // callers to iterate over the realized cache entries. This method intentionally returns an Iterator to avoid
    // providing Collection methods which may have surprising behavior.
    Iterator<Entry<K, V>> entries();
}
