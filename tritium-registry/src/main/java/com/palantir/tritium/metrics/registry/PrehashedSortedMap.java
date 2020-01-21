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

package com.palantir.tritium.metrics.registry;

import com.google.common.collect.ForwardingSortedMap;
import com.google.common.collect.ImmutableSortedMap;

/**
 * A sorted map implementation which prehashes for faster usage in hashmaps. This is only safe for immutable underlying
 * maps (both the map implementation and the entries must be immutable).
 */
final class PrehashedSortedMap<K, V> extends ForwardingSortedMap<K, V> {
    private final ImmutableSortedMap<K, V> delegate;
    private final int hashCode;

    PrehashedSortedMap(ImmutableSortedMap<K, V> delegate) {
        this.delegate = delegate;
        this.hashCode = this.delegate.hashCode();
    }

    @Override
    protected ImmutableSortedMap<K, V> delegate() {
        return delegate;
    }

    @SuppressWarnings("checkstyle:EqualsHashCode")
    @Override
    public int hashCode() {
        return hashCode;
    }
}
