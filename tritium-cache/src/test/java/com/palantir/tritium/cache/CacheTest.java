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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.tracing.Observability;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.Tracers;
import com.palantir.tracing.api.OpenSpan;
import com.palantir.tracing.api.Span;
import com.palantir.tracing.api.SpanType;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class CacheTest {

    private ExecutorService executor;

    @BeforeEach
    void before() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void after() {
        Tracer.getAndClearTrace();
        executor.shutdownNow();
    }

    @Test
    void maximumSize_sync() throws Exception {
        SyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(1)
                .noExpiry()
                .noMetrics()
                .executor(_name -> executor)
                .buildSync();

        cache.put("key1", "value1");

        assertThat(cache.getAllPresent(Set.of("key1"))).hasSize(1);

        cache.put("key2", "value2");

        // maximumSize is enforced by a background task
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

        assertThat(cache.getAllPresent(Set.of("key1", "key2"))).hasSize(1);
    }

    @Test
    void maximumSize_async() throws Exception {
        AsyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(1)
                .noExpiry()
                .noMetrics()
                .executor(_name -> executor)
                .buildAsync();

        cache.put("key1", "value1");

        assertThat(cache.getAllPresent(Set.of("key1"))).hasSize(1);

        cache.put("key2", "value2");

        // maximumSize is enforced by a background task
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

        assertThat(cache.getAllPresent(Set.of("key1", "key2"))).hasSize(1);
    }

    @Test
    void expiry_afterCreate_sync() {
        Expiry<String, String> expiry = new DefaultExpiry<>() {
            @Override
            public long expireAfterCreate(String _key, String _value, long _currentTime) {
                return 1;
            }
        };
        FakeTicker ticker = new FakeTicker();

        SyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(1)
                .expiry(expiry)
                .noMetrics()
                .executor(_name -> executor)
                .ticker(ticker)
                .buildSync();

        cache.put("key", "value");

        assertThat(cache.getIfPresent("key")).isEqualTo("value");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key")).isNull();
    }

    @Test
    void expiry_afterCreate_async() {
        Expiry<String, String> expiry = new DefaultExpiry<>() {
            @Override
            public long expireAfterCreate(String _key, String _value, long _currentTime) {
                return 1;
            }
        };
        FakeTicker ticker = new FakeTicker();

        AsyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(1)
                .expiry(expiry)
                .noMetrics()
                .executor(_name -> executor)
                .ticker(ticker)
                .buildAsync();

        cache.put("key", "value");

        assertThat(cache.getIfPresent("key")).isEqualTo("value");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key1")).isNull();
    }

    @Test
    void expiry_afterUpdate_sync() {
        Expiry<String, String> expiry = new DefaultExpiry<>() {
            @Override
            public long expireAfterUpdate(String _key, String _value, long _currentTime, long _currentDuration) {
                return 1;
            }
        };
        FakeTicker ticker = new FakeTicker();

        SyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(1)
                .expiry(expiry)
                .noMetrics()
                .executor(_name -> executor)
                .ticker(ticker)
                .buildSync();

        cache.put("key", "value1");

        assertThat(cache.getIfPresent("key")).isEqualTo("value1");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key")).isEqualTo("value1");

        cache.put("key", "value2");

        assertThat(cache.getIfPresent("key")).isEqualTo("value2");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key")).isNull();
    }

    @Test
    void expiry_afterUpdate_async() {
        Expiry<String, String> expiry = new DefaultExpiry<>() {
            @Override
            public long expireAfterUpdate(String _key, String _value, long _currentTime, long _currentDuration) {
                return 1;
            }
        };
        FakeTicker ticker = new FakeTicker();

        AsyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(1)
                .expiry(expiry)
                .noMetrics()
                .executor(_name -> executor)
                .ticker(ticker)
                .buildAsync();

        cache.put("key", "value1");

        assertThat(cache.getIfPresent("key")).isEqualTo("value1");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key")).isEqualTo("value1");

        cache.put("key", "value2");

        assertThat(cache.getIfPresent("key")).isEqualTo("value2");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key")).isNull();
    }

    @Test
    void expiry_afterRead_sync() {
        Expiry<String, String> expiry = new DefaultExpiry<>() {
            @Override
            public long expireAfterRead(String _key, String _value, long _currentTime, long _currentDuration) {
                return 1;
            }
        };
        FakeTicker ticker = new FakeTicker();

        SyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(1)
                .expiry(expiry)
                .noMetrics()
                .executor(_name -> executor)
                .ticker(ticker)
                .buildSync();

        cache.put("key", "value1");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key")).isEqualTo("value1");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key")).isNull();
    }

    @Test
    void expiry_afterRead_async() {
        Expiry<String, String> expiry = new DefaultExpiry<>() {
            @Override
            public long expireAfterRead(String _key, String _value, long _currentTime, long _currentDuration) {
                return 1;
            }
        };
        FakeTicker ticker = new FakeTicker();

        AsyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(1)
                .expiry(expiry)
                .noMetrics()
                .executor(_name -> executor)
                .ticker(ticker)
                .buildAsync();

        cache.put("key", "value1");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key")).isEqualTo("value1");

        ticker.plus(Duration.ofNanos(1));

        assertThat(cache.getIfPresent("key")).isNull();
    }

    @Test
    void entries_sync() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);

        SyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(10)
                .noExpiry()
                .noMetrics()
                .executor(_name -> executor)
                .buildSync();

        cache.put("key1", "value1");

        Future<String> future = executor.submit(() -> {
            return cache.get("key2", _key -> {
                startLatch.countDown();
                Uninterruptibles.awaitUninterruptibly(finishLatch);
                return "value2";
            });
        });

        startLatch.await();

        assertThat(cache.entries())
                .isUnmodifiable()
                .toIterable()
                .containsExactlyInAnyOrder(Map.entry("key1", "value1"));

        finishLatch.countDown();

        assertThat(future).succeedsWithin(1, TimeUnit.SECONDS).isEqualTo("value2");
    }

    @Test
    void entries_async() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);

        AsyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(10)
                .noExpiry()
                .noMetrics()
                .executor(_name -> executor)
                .buildAsync();

        cache.put("key1", "value1");

        Future<String> future = executor.submit(() -> {
            return cache.get("key2", _key -> {
                startLatch.countDown();
                Uninterruptibles.awaitUninterruptibly(finishLatch);
                return "value2";
            });
        });

        startLatch.await();

        assertThat(cache.entries())
                .isUnmodifiable()
                .toIterable()
                .containsExactlyInAnyOrder(Map.entry("key1", "value1"));

        finishLatch.countDown();

        assertThat(future).succeedsWithin(1, TimeUnit.SECONDS).isEqualTo("value2");
    }

    @Test
    void tracing_sync() throws Exception {
        SyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(10)
                .noExpiry()
                .noMetrics()
                .executor(_name -> executor)
                .buildSync();

        String traceId = Tracers.randomId();
        Tracer.initTraceWithSpan(Observability.SAMPLE, traceId, "root", SpanType.LOCAL);

        OpenSpan parentSpan = Tracer.startSpan("parent");

        cache.get("key", _key -> {
            Span span = Tracer.completeSpan().orElseThrow();
            assertThat(span.getTraceId()).isEqualTo(traceId);
            assertThat(span.getSpanId()).isEqualTo(parentSpan.getSpanId());
            assertThat(span.getOperation()).isEqualTo("parent");

            return "value";
        });
    }

    @Test
    void tracing_async() throws Exception {
        AsyncCache<String, String> cache = Cache.<String, String>builder()
                .name("test")
                .maximumSize(10)
                .noExpiry()
                .noMetrics()
                .executor(_name -> executor)
                .buildAsync();

        String traceId = Tracers.randomId();
        Tracer.initTraceWithSpan(Observability.SAMPLE, traceId, "root", SpanType.LOCAL);

        OpenSpan parentSpan = Tracer.startSpan("parent");

        cache.get("key", _key -> {
            Span span = Tracer.completeSpan().orElseThrow();
            assertThat(span.getTraceId()).isEqualTo(traceId);
            assertThat(span.getParentSpanId()).contains(parentSpan.getSpanId());
            assertThat(span.getOperation()).isEqualTo("test cache load");

            return "value";
        });
    }

    private interface DefaultExpiry<K, V> extends Expiry<K, V> {

        @Override
        default long expireAfterCreate(K _key, V _value, long _currentTime) {
            return Long.MAX_VALUE;
        }

        @Override
        default long expireAfterUpdate(K _key, V _value, long _currentTime, long currentDuration) {
            return currentDuration;
        }

        @Override
        default long expireAfterRead(K _key, V _value, long _currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
