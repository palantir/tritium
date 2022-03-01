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

import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
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
@SuppressWarnings("JdkObsolete")
final class TagMap implements SortedMap<String, String> {

    static final TagMap EMPTY = new TagMap(new String[0]);

    /**
     * Map entries arranged with keys on even indexes and values in odd indexes.
     * Keys are sorted alphabetically.
     * {@code ["a", "one", "b", "two"]} represents map {@code {"a"="one", "b"="two"}}.
     */
    private final String[] values;

    static TagMap of(Map<String, String> data) {
        if (data instanceof TagMap) {
            return (TagMap) data;
        }
        return data.isEmpty() ? EMPTY : new TagMap(toArray(data));
    }

    private TagMap(String[] values) {
        this.values = values;
    }

    private static String[] toArray(Map<String, String> data) {
        int size = data.size();
        String[] values = new String[size * 2];
        String[] keys = new String[size];
        int keysIndex = 0;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            keys[keysIndex++] = entry.getKey();
        }
        Arrays.sort(keys);
        for (int i = 0; i < keys.length; i++) {
            int valuesIndex = 2 * i;
            String key = keys[i];
            values[valuesIndex] = key;
            values[valuesIndex + 1] = data.get(key);
        }
        return values;
    }

    /** Returns a new {@link TagMap} with on additional entry. */
    TagMap withEntry(String key, String value) {
        String[] local = this.values;
        int newPosition = 0;
        for (; newPosition < local.length; newPosition += 2) {
            String current = local[newPosition];
            int comparisonResult = current.compareTo(key);
            if (comparisonResult == 0) {
                throw new SafeIllegalArgumentException("Base must not contain the extra key that is to be added");
            }
            if (comparisonResult > 0) {
                break;
            }
        }
        // current is greater than the new key
        String[] newArray = new String[local.length + 2];
        System.arraycopy(local, 0, newArray, 0, newPosition);
        newArray[newPosition] = key;
        newArray[newPosition + 1] = value;
        System.arraycopy(local, newPosition, newArray, newPosition + 2, local.length - newPosition);
        return new TagMap(newArray);
    }

    @Nullable
    @Override
    public String get(Object key) {
        int idx = indexOfKey(key);
        return idx >= 0 ? values[idx + 1] : null;
    }

    private int indexOfKey(Object key) {
        String[] local = values;
        for (int i = 0; i < local.length; i += 2) {
            if (Objects.equals(key, local[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super String> action) {
        String[] local = this.values;
        for (int i = 0; i < local.length; i += 2) {
            action.accept(local[i], local[i + 1]);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("{");
        String[] local = this.values;
        for (int i = 0; i < local.length; i += 2) {
            String key = local[i];
            String value = local[i + 1];
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
            return Arrays.equals(values, ((TagMap) other).values);
        }
        if (!(other instanceof Map)) {
            return false;
        }
        Map<?, ?> otherMap = (Map<?, ?>) other;
        if (otherMap.size() != size()) {
            return false;
        }
        for (int i = 0; i < values.length; i += 2) {
            String key = values[i];
            String value = values[i + 1];
            if (!Objects.equals(value, otherMap.get(key))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = 0;
        for (int i = 0; i < values.length; i += 2) {
            hashCode += Objects.hashCode(values[i]) ^ Objects.hashCode(values[i + 1]);
        }
        return hashCode;
    }

    /* Misc methods to support the SortedMap interface. */

    @Override
    @Nullable
    public Comparator<? super String> comparator() {
        return Comparator.naturalOrder();
    }

    @Override
    public SortedMap<String, String> subMap(String fromKey, String toKey) {
        int beginIndex = 0;
        for (int i = 0; i < values.length; i += 2) {
            String key = values[i];
            if (key.compareTo(fromKey) >= 0) {
                beginIndex = i;
                break;
            }
        }
        for (int i = values.length - 2; i >= beginIndex; i -= 2) {
            String key = values[i];
            if (key.compareTo(toKey) < 0) {
                return new TagMap(Arrays.copyOfRange(values, beginIndex, i + 2));
            }
        }
        return EMPTY;
    }

    @Override
    public SortedMap<String, String> headMap(String toKey) {
        for (int i = values.length - 2; i >= 0; i -= 2) {
            String key = values[i];
            if (key.compareTo(toKey) < 0) {
                return new TagMap(Arrays.copyOfRange(values, 0, i + 2));
            }
        }
        return EMPTY;
    }

    @Override
    public SortedMap<String, String> tailMap(String fromKey) {
        for (int i = 0; i < values.length; i += 2) {
            String key = values[i];
            if (key.compareTo(fromKey) >= 0) {
                return new TagMap(Arrays.copyOfRange(values, i, values.length));
            }
        }
        return EMPTY;
    }

    @Nullable
    @Override
    public String firstKey() {
        String[] local = this.values;
        if (local.length == 0) {
            throw new NoSuchElementException();
        }
        return local[0];
    }

    @Nullable
    @Override
    public String lastKey() {
        String[] local = this.values;
        if (local.length == 0) {
            throw new NoSuchElementException();
        }
        return local[local.length - 2];
    }

    @Override
    public int size() {
        return values.length / 2;
    }

    @Override
    public boolean isEmpty() {
        return values.length == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOfKey(key) >= 0;
    }

    @Override
    public boolean containsValue(Object value) {
        String[] local = this.values;
        for (int i = 1; i < local.length; i += 2) {
            if (Objects.equals(value, local[i])) {
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
        String[] local = this.values;
        Set<String> set = new LinkedHashSet<>(local.length / 2);
        for (int i = 0; i < local.length; i += 2) {
            set.add(local[i]);
        }
        return Collections.unmodifiableSet(set);
    }

    @Nonnull
    @Override
    public Collection<String> values() {
        String[] local = this.values;
        List<String> list = new ArrayList<>(local.length / 2);
        for (int i = 1; i < local.length; i += 2) {
            list.add(local[i]);
        }
        return Collections.unmodifiableCollection(list);
    }

    @Nonnull
    @Override
    public Set<Entry<String, String>> entrySet() {
        return new TagMapEntrySet(values);
    }

    private static final class TagMapEntrySet implements Set<Entry<String, String>> {

        private final String[] values;

        TagMapEntrySet(String[] values) {
            this.values = values;
        }

        @Override
        public int size() {
            return values.length / 2;
        }

        @Override
        public boolean isEmpty() {
            return values.length == 0;
        }

        @Override
        public boolean contains(Object object) {
            if (object instanceof Entry) {
                Entry<?, ?> entry = (Entry<?, ?>) object;
                for (int i = 0; i < values.length; i += 2) {
                    if (Objects.equals(entry.getKey(), values[i]) && Objects.equals(entry.getValue(), values[i + 1])) {
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
            String[] local = values;
            Object[] result = new Object[local.length / 2];
            for (int i = 0; i < local.length; i += 2) {
                result[i / 2] = new TagEntry(local[i], local[i + 1]);
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] array) {
            String[] local = values;
            int resultLength = local.length / 2;
            T[] result = resultLength > array.length
                    ? (T[]) Array.newInstance(array.getClass().getComponentType(), resultLength)
                    : array;
            for (int i = 0; i < local.length; i += 2) {
                result[i / 2] = (T) new TagEntry(local[i], local[i + 1]);
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

        @Override
        @SuppressWarnings("SuspiciousMethodCalls")
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Set)) {
                return false;
            }
            if (other instanceof TagMapEntrySet) {
                return Arrays.equals(values, ((TagMapEntrySet) other).values);
            }
            Set<?> otherSet = (Set<?>) other;
            return size() == otherSet.size() && containsAll(otherSet) && otherSet.containsAll(this);
        }

        @Override
        public int hashCode() {
            int hashCode = 0;
            for (int i = 0; i < values.length; i += 2) {
                hashCode += Objects.hashCode(values[i]) ^ Objects.hashCode(values[i + 1]);
            }
            return hashCode;
        }

        @Override
        public String toString() {
            return "TagMapEntrySet{" + Arrays.toString(values) + '}';
        }
    }

    private static final class TagMapEntrySetIterator implements Iterator<Entry<String, String>> {
        private final String[] values;
        private int current = -2;

        TagMapEntrySetIterator(String[] values) {
            this.values = values;
        }

        @Override
        public boolean hasNext() {
            return current + 2 < values.length;
        }

        @Override
        public Entry<String, String> next() {
            if (hasNext()) {
                current += 2;
                return new TagEntry(values[current], values[current + 1]);
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

        /** Matches AbstractMap#SimpleEntry in the standard library. */
        @Override
        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }
    }
}
