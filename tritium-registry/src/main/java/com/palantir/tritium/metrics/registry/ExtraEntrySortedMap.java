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

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.PeekingIterator;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

final class ExtraEntrySortedMap<K, V> extends AbstractMap<K, V> implements SortedMap<K, V> {
    private final Ordering<? super K> ordering;
    private final SortedMap<K, V> left;
    private final K extraKey;
    private final V extraValue;
    private final int extraEntryHashCode;

    ExtraEntrySortedMap(SortedMap<K, V> left, K extraKey, V extraValue) {
        this.left = left;
        this.extraKey = extraKey;
        this.extraValue = extraValue;
        this.ordering = Ordering.from(left.comparator());
        this.extraEntryHashCode = Maps.immutableEntry(extraKey, extraValue).hashCode();
    }

    @Override
    public Comparator<? super K> comparator() {
        return left.comparator();
    }

    @Override
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        SortedMap<K, V> delegate = left.subMap(fromKey, toKey);
        if (ordering.compare(fromKey, extraKey) <= 0 && ordering.compare(toKey, extraKey) > 0) {
            return new ExtraEntrySortedMap<>(delegate, extraKey, extraValue);
        }
        return delegate;
    }

    @Override
    public SortedMap<K, V> headMap(K toKey) {
        SortedMap<K, V> delegate = left.headMap(toKey);
        if (ordering.compare(toKey, extraKey) > 0) {
            return new ExtraEntrySortedMap<>(delegate, extraKey, extraValue);
        }
        return delegate;
    }

    @Override
    public SortedMap<K, V> tailMap(K fromKey) {
        SortedMap<K, V> delegate = left.tailMap(fromKey);
        if (ordering.compare(fromKey, extraKey) <= 0) {
            return new ExtraEntrySortedMap<>(delegate, extraKey, extraValue);
        }
        return delegate;
    }

    @Override
    public K firstKey() {
        if (left.isEmpty()) {
            return extraKey;
        }
        return ordering.min(left.firstKey(), extraKey);
    }

    @Override
    public K lastKey() {
        if (left.isEmpty()) {
            return extraKey;
        }
        return ordering.max(left.lastKey(), extraKey);
    }

    @Override
    public int size() {
        return left.size() + 1;
    }

    @Override
    public boolean isEmpty() {
        return left.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return extraKey.equals(key) || left.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return extraValue.equals(value) || left.containsValue(value);
    }

    @Override
    public V get(Object key) {
        if (extraKey.equals(key)) {
            return extraValue;
        }
        return left.get(key);
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<K> keySet() {
        return new AbstractSet<K>() {
            @Override
            public Iterator<K> iterator() {
                return new MergeIterator<>(left.keySet().iterator(), extraKey, ordering);
            }

            @Override
            public int size() {
                return left.size() + 1;
            }
        };
    }

    @Override
    public Collection<V> values() {
        return new AbstractCollection<V>() {
            @Override
            public Iterator<V> iterator() {
                return keySet().stream().map(ExtraEntrySortedMap.this::get).iterator();
            }

            @Override
            public int size() {
                return left.values().size() + 1;
            }
        };
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Map.Entry<K, V>>() {
            @Override
            public Iterator<Map.Entry<K, V>> iterator() {
                return new MergeIterator<>(
                        left.entrySet().iterator(),
                        Maps.immutableEntry(extraKey, extraValue),
                        ordering.onResultOf(Map.Entry::getKey));
            }

            @Override
            public int size() {
                return left.size() + 1;
            }
        };
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ExtraEntrySortedMap) {
            ExtraEntrySortedMap otherMap = (ExtraEntrySortedMap) other;
            if (extraKey.equals(otherMap.extraKey) && extraValue.equals(otherMap.extraValue)) {
                return left.equals(otherMap.left);
            }
        }
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return left.hashCode() + extraEntryHashCode;
    }

    private static final class MergeIterator<T> extends AbstractIterator<T> {
        private final PeekingIterator<T> delegate;
        private final T option;
        private final Comparator<? super T> comparator;
        private boolean usedOption = false;

        private MergeIterator(Iterator<T> delegate, T option, Comparator<? super T> comparator) {
            this.delegate = Iterators.peekingIterator(delegate);
            this.option = option;
            this.comparator = comparator;
        }

        @Override
        protected T computeNext() {
            if (!delegate.hasNext() && usedOption) {
                return endOfData();
            } else if (!delegate.hasNext()) {
                usedOption = true;
                return option;
            } else if (usedOption) {
                return delegate.next();
            }
            T element = delegate.peek();
            if (comparator.compare(element, option) >= 0) {
                usedOption = true;
                return option;
            }
            return delegate.next();
        }
    }
}
