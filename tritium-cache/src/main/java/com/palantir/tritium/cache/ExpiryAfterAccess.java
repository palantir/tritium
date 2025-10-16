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

final class ExpiryAfterAccess<K, V> implements Expiry<K, V> {

    private final long expireAfterNanos;

    ExpiryAfterAccess(Duration duration) {
        this.expireAfterNanos = duration.toNanos();
    }

    @Override
    public long expireAfterCreate(K _key, V _value, long _currentTime) {
        return expireAfterNanos;
    }

    @Override
    public long expireAfterUpdate(K _key, V _value, long _currentTime, long _currentDuration) {
        return expireAfterNanos;
    }

    @Override
    public long expireAfterRead(K _key, V _value, long _currentTime, long _currentDuration) {
        return expireAfterNanos;
    }
}
