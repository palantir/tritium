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

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public interface BulkCacheLoader<K, V> extends CacheLoader<K, V> {

    @Override
    @Nullable
    default V load(K key) {
        return loadAll(Set.of(key)).get(key);
    }

    /**
     * See {@link com.github.benmanes.caffeine.cache.CacheLoader#loadAll(Set)}.
     */
    Map<K, V> loadAll(Set<K> keys);
}
