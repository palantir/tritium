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

import static com.palantir.logsafe.Preconditions.checkArgument;
import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.CharMatcher;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.tritium.metrics.caffeine.CacheStats;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("HiddenField")
final class CacheBuilder<K, V>
        implements Cache.NameBuilder<K, V>,
                Cache.SizeBuilder<K, V>,
                Cache.ExpiryBuilder<K, V>,
                Cache.MetricsBuilder<K, V>,
                Cache.ExecutorBuilder<K, V>,
                Cache.Builder<K, V> {

    private static final CharMatcher NAME_MATCHER = CharMatcher.inRange('a', 'z')
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.is('-'))
            .precomputed();

    @Nullable
    private String name;

    private long maximumSize;

    @Nullable
    private Weigher<? super K, ? super V> weigher;

    @Nullable
    private Expiry<? super K, ? super V> expiry;

    @Nullable
    private ExecutorFactory executorFactory;

    @Nullable
    private TaggedMetricRegistry taggedMetrics;

    @Nullable
    private Ticker ticker;

    @Override
    public Cache.SizeBuilder<K, V> name(@CompileTimeConstant String name) {
        checkArgument(NAME_MATCHER.matchesAllOf(name));
        this.name = checkNotNull(name, "name");
        return this;
    }

    @Override
    public Cache.ExpiryBuilder<K, V> maximumSize(long maximumSize) {
        this.maximumSize = checkNotNull(maximumSize, "maximumSize");
        return this;
    }

    @Override
    public Cache.ExpiryBuilder<K, V> maximumSize(long maximumSize, Weigher<? super K, ? super V> weigher) {
        this.maximumSize = checkNotNull(maximumSize, "maximumSize");
        this.weigher = checkNotNull(weigher, "weigher");
        return this;
    }

    @Override
    public Cache.MetricsBuilder<K, V> expiry(Expiry<? super K, ? super V> expiry) {
        this.expiry = checkNotNull(expiry, "expiry");
        return this;
    }

    @Override
    public Cache.MetricsBuilder<K, V> noExpiry() {
        this.expiry = null;
        return this;
    }

    @Override
    public Cache.ExecutorBuilder<K, V> metrics(TaggedMetricRegistry taggedMetrics) {
        this.taggedMetrics = checkNotNull(taggedMetrics, "taggedMetrics");
        return this;
    }

    @Override
    public Cache.ExecutorBuilder<K, V> noMetrics() {
        this.taggedMetrics = null;
        return this;
    }

    @Override
    public Cache.Builder<K, V> executor(ExecutorFactory executorFactory) {
        this.executorFactory = checkNotNull(executorFactory, "executorFactory");
        return this;
    }

    @Override
    public Cache.Builder<K, V> ticker(Ticker ticker) {
        this.ticker = checkNotNull(ticker, "ticker");
        return this;
    }

    @Override
    public SyncCache<K, V> buildSync() {
        return new SyncCacheImpl<>(buildSyncCache());
    }

    @Override
    public SyncLoadingCache<K, V> buildSyncWithLoader(CacheLoader<K, V> cacheLoader) {
        return new SyncLoadingCacheImpl<>(buildSyncCache(), cacheLoader);
    }

    @Override
    public AsyncCache<K, V> buildAsync() {
        return new AsyncCacheImpl<>(checkNotNull(name), buildAsyncCache());
    }

    @Override
    public AsyncLoadingCache<K, V> buildAsyncWithLoader(CacheLoader<K, V> cacheLoader) {
        return new AsyncLoadingCacheImpl<>(checkNotNull(name), buildAsyncCache(), cacheLoader);
    }

    @Override
    public AsyncBulkLoadingCache<K, V> buildAsyncWithBulkLoader(BulkCacheLoader<K, V> cacheLoader) {
        return new AsyncBulkLoadingCacheImpl<>(checkNotNull(name), buildAsyncCache(), cacheLoader);
    }

    private com.github.benmanes.caffeine.cache.Cache<K, V> buildSyncCache() {
        return buildCache(Caffeine::build, Function.identity());
    }

    private com.github.benmanes.caffeine.cache.AsyncCache<K, V> buildAsyncCache() {
        return buildCache(Caffeine::buildAsync, com.github.benmanes.caffeine.cache.AsyncCache::synchronous);
    }

    @SuppressWarnings("unchecked")
    private <C> C buildCache(
            Function<Caffeine<K, V>, C> build,
            Function<C, com.github.benmanes.caffeine.cache.Cache<K, V>> synchronous) {
        CacheStats cacheStats;
        if (taggedMetrics != null) {
            cacheStats = CacheStats.of(taggedMetrics, checkNotNull(name));
        } else {
            cacheStats = null;
        }

        Caffeine<K, V> builder = ((Caffeine<K, V>) Caffeine.newBuilder())
                .maximumSize(maximumSize)
                .executor(checkNotNull(executorFactory).create(name + "-cache"));
        if (weigher != null) {
            builder = builder.weigher(new WeigherAdapter<>(weigher));
        }
        if (expiry != null) {
            builder = builder.expireAfter(new ExpiryAdapter<>(expiry));
        }
        if (ticker != null) {
            builder = builder.ticker(new TickerAdapter(ticker));
        }
        if (cacheStats != null) {
            builder = builder.recordStats(cacheStats);
        }

        C cache = build.apply(builder);

        com.github.benmanes.caffeine.cache.Cache<K, V> synchronousCache = synchronous.apply(cache);

        if (cacheStats != null) {
            cacheStats.register(synchronousCache);
        }

        return cache;
    }

    private static final class ExpiryAdapter<K, V> implements com.github.benmanes.caffeine.cache.Expiry<K, V> {

        private final Expiry<? super K, ? super V> expiry;

        private ExpiryAdapter(Expiry<? super K, ? super V> expiry) {
            this.expiry = expiry;
        }

        @Override
        public long expireAfterCreate(K key, V value, long currentTime) {
            return expiry.expireAfterCreate(key, value, currentTime);
        }

        @Override
        public long expireAfterUpdate(K key, V value, long currentTime, long currentDuration) {
            return expiry.expireAfterUpdate(key, value, currentTime, currentDuration);
        }

        @Override
        public long expireAfterRead(K key, V value, long currentTime, long currentDuration) {
            return expiry.expireAfterRead(key, value, currentTime, currentDuration);
        }
    }

    private static final class WeigherAdapter<K, V> implements com.github.benmanes.caffeine.cache.Weigher<K, V> {

        private final Weigher<? super K, ? super V> weigher;

        private WeigherAdapter(Weigher<? super K, ? super V> weigher) {
            this.weigher = weigher;
        }

        @Override
        public int weigh(K key, V value) {
            return weigher.weigh(key, value);
        }
    }

    private static final class TickerAdapter implements com.github.benmanes.caffeine.cache.Ticker {

        private final Ticker ticker;

        private TickerAdapter(Ticker ticker) {
            this.ticker = ticker;
        }

        @Override
        public long read() {
            return ticker.read();
        }
    }
}
