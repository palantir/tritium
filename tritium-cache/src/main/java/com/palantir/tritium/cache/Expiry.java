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

import java.time.Duration;
import java.util.function.BiFunction;

public interface Expiry<K, V> {

    /**
     * See {@link com.github.benmanes.caffeine.cache.Expiry#expireAfterCreate(Object, Object, long)}.
     */
    long expireAfterCreate(K key, V value, long currentTime);

    /**
     * See {@link com.github.benmanes.caffeine.cache.Expiry#expireAfterUpdate(Object, Object, long, long)}.
     */
    long expireAfterUpdate(K key, V value, long currentTime, long currentDuration);

    /**
     * See {@link com.github.benmanes.caffeine.cache.Expiry#expireAfterRead(Object, Object, long, long)}.
     */
    long expireAfterRead(K key, V value, long currentTime, long currentDuration);

    /**
     * See {@link com.github.benmanes.caffeine.cache.Expiry#accessing(BiFunction)}.
     */
    static <K, V> Expiry<K, V> afterAccess(Duration duration) {
        return new ExpiryAfterAccess<>(duration);
    }

    /**
     * See {@link com.github.benmanes.caffeine.cache.Expiry#writing(BiFunction)}.
     */
    static <K, V> Expiry<K, V> afterWrite(Duration duration) {
        return new ExpiryAfterWrite<>(duration);
    }
}
