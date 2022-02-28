/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.annotations.Immutable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@link TagMap} is a {@link SortedMap} implementation optimized for creation performance and memory overhead.
 * Primarily optimized to retain as little memory as possible, and create small short-lived intermediate objects.
 *
 * Note that we expect fairly small tag maps which are iterated over, not used for lookups. Most {@link Map}
 * methods are implemented, but use a naive linear search rather than a binary search.
 */
@Immutable
@SuppressWarnings("JdkObsolete")
final class TagMap implements SortedMap<String, String> {
    static final TagMap EMPTY = TagMap.of(ImmutableMap.of());

    private final ImmutableList<String> values;
    private final int hash;

    static TagMap of(Map<String, String> data) {
        if (data instanceof TagMap) {
            return (TagMap) data;
        }
        return new TagMap(toValues(data));
    }

    private TagMap(ImmutableList<String> values) {
        this.values = values;
        this.hash = values.hashCode();
    }

    private static ImmutableList<String> toValues(Map<String, String> data) {
        ImmutableList.Builder<String> values = ImmutableList.builderWithExpectedSize(data.size() * 2);
        List<String> keys = ImmutableSortedSet.copyOf(data.keySet()).asList();
        for (String key : keys) {
            if (key != null) {
                String value = data.get(key);
                if (value != null) {
                    values.add(key);
                    values.add(value);
                }
            }
        }
        return values.build();
    }

    @Nullable
    @Override
    public String get(Object key) {
        int idx = indexOfKey(key);
        return idx >= 0 ? values.get(idx + 1) : null;
    }

    private int indexOfKey(Object key) {
        for (int i = 0; i < values.size(); i += 2) {
            if (Objects.equals(key, values.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super String> action) {
        for (int i = 0; i < values.size(); i += 2) {
            action.accept(values.get(i), values.get(i + 1));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("{");
        for (int i = 0; i < values.size(); i += 2) {
            String key = values.get(i);
            String value = values.get(i + 1);
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(key);
            sb.append('=');
            sb.append(value);
        }
        return sb.append('}').toString();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof TagMap) {
            TagMap that = (TagMap) other;
            return this.hash == that.hash && values.equals(that.values);
        }
        if (!(other instanceof Map)) {
            return false;
        }
        Map<?, ?> otherMap = (Map<?, ?>) other;
        if (otherMap.size() == size()) {
            for (int i = 0; i < values.size(); i += 2) {
                String key = values.get(i);
                String value = values.get(i + 1);
                if (!Objects.equals(value, otherMap.get(key))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    /* Misc methods to support the SortedMap interface. */

    @Override
    @Nullable
    public Comparator<? super String> comparator() {
        return Comparator.naturalOrder();
    }

    @Override
    public SortedMap<String, String> subMap(String _fromKey, String _toKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SortedMap<String, String> headMap(String _toKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SortedMap<String, String> tailMap(String _fromKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Nullable
    @Override
    public String firstKey() {
        if (values.isEmpty()) {
            throw new NoSuchElementException();
        }
        return values.get(0);
    }

    @Nullable
    @Override
    public String lastKey() {
        if (values.isEmpty()) {
            throw new NoSuchElementException();
        }
        return values.get(values.size() - 2);
    }

    @Override
    public int size() {
        return values.size() / 2;
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOfKey(key) >= 0;
    }

    @Override
    public boolean containsValue(Object value) {
        for (int i = 1; i < values.size(); i += 2) {
            if (Objects.equals(value, values.get(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String put(String _key, String _value) {
        throw new UnsupportedOperationException("immutable");
    }

    @Override
    public void putAll(@Nonnull Map<? extends String, ? extends String> _map) {
        throw new UnsupportedOperationException("immutable");
    }

    @Override
    public String remove(Object _key) {
        throw new UnsupportedOperationException("immutable");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("immutable");
    }

    @Nonnull
    @Override
    public Set<String> keySet() {
        Set<String> set = new LinkedHashSet<>(values.size() / 2);
        for (int i = 0; i < values.size(); i += 2) {
            set.add(values.get(i));
        }
        return Collections.unmodifiableSet(set);
    }

    @Nonnull
    @Override
    public Collection<String> values() {
        List<String> list = new ArrayList<>(values.size() / 2);
        for (int i = 1; i < values.size(); i += 2) {
            list.add(values.get(i));
        }
        return Collections.unmodifiableCollection(list);
    }

    @Nonnull
    @Override
    public Set<Entry<String, String>> entrySet() {
        return new TagMapEntrySet(values);
    }

    private static final class TagMapEntrySet implements Set<Entry<String, String>> {

        private final ImmutableList<String> values;

        TagMapEntrySet(ImmutableList<String> values) {
            this.values = values;
        }

        @Override
        public int size() {
            return values.size() / 2;
        }

        @Override
        public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override
        public boolean contains(Object object) {
            if (object instanceof Entry) {
                Entry<?, ?> entry = (Entry<?, ?>) object;
                for (int i = 0; i < values.size(); i += 2) {
                    if (Objects.equals(entry.getKey(), values.get(i))
                            && Objects.equals(entry.getValue(), values.get(i + 1))) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Nonnull
        @Override
        public Iterator<Entry<String, String>> iterator() {
            return new TagMapEntrySetIterator(values);
        }

        @Nonnull
        @Override
        public Object[] toArray() {
            ImmutableList<String> local = values;
            Object[] result = new Object[local.size() / 2];
            for (int i = 0; i < local.size(); i += 2) {
                result[i / 2] = new TagEntry(local.get(i), local.get(i + 1));
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] array) {
            ImmutableList<String> local = values;
            int resultLength = local.size() / 2;
            T[] result = resultLength > array.length
                    ? (T[]) Array.newInstance(array.getClass().getComponentType(), resultLength)
                    : array;
            for (int i = 0; i < local.size(); i += 2) {
                result[i / 2] = (T) new TagEntry(local.get(i), local.get(i + 1));
            }
            Arrays.fill(result, resultLength, array.length, null);
            return result;
        }

        @Override
        public boolean add(Entry<String, String> _stringStringEntry) {
            throw new UnsupportedOperationException("immutable");
        }

        @Override
        public boolean remove(Object _object) {
            throw new UnsupportedOperationException("immutable");
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            for (Object object : collection) {
                if (!contains(object)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean addAll(@Nonnull Collection<? extends Entry<String, String>> _collection) {
            throw new UnsupportedOperationException("immutable");
        }

        @Override
        public boolean retainAll(@Nonnull Collection<?> _collection) {
            throw new UnsupportedOperationException("immutable");
        }

        @Override
        public boolean removeAll(@Nonnull Collection<?> _collection) {
            throw new UnsupportedOperationException("immutable");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("immutable");
        }
    }

    private static final class TagMapEntrySetIterator implements Iterator<Entry<String, String>> {
        private final ImmutableList<String> values;
        private int current = -2;

        TagMapEntrySetIterator(ImmutableList<String> values) {
            this.values = values;
        }

        @Override
        public boolean hasNext() {
            return current + 2 < values.size();
        }

        @Override
        public Entry<String, String> next() {
            if (hasNext()) {
                current += 2;
                return new TagEntry(values.get(current), values.get(current + 1));
            }
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("immutable");
        }
    }

    private static final class TagEntry implements Map.Entry<String, String> {

        private final String key;
        private final String value;

        TagEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String _value) {
            throw new UnsupportedOperationException("immutable");
        }

        @Override
        public String toString() {
            return '{' + key + '=' + value + '}';
        }

        @Override
        public boolean equals(@Nullable Object object) {
            if (this == object) {
                return true;
            }
            if (object instanceof Map.Entry) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) object;
                return Objects.equals(entry.getKey(), key) && Objects.equals(entry.getValue(), value);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return 31 * key.hashCode() + value.hashCode();
        }
    }
}
