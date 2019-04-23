/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.tritium.api.functions.BooleanSupplier;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InstrumentationPropertiesTest {

    private ListeningExecutorService executorService =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

    @Before
    public void before() {
        System.clearProperty("instrument");
        System.getProperties().entrySet().removeIf(entry ->
                entry.getKey().toString().startsWith("instrument"));
        InstrumentationProperties.reload();
    }

    @After
    public void after() {
        executorService.shutdownNow();
    }

    @Test
    public void testSystemPropertySupplierEnabledByDefault() {
        BooleanSupplier supplier = InstrumentationProperties.getSystemPropertySupplier("test");
        assertThat(supplier.asBoolean()).isTrue();
    }

    @Test
    public void testSystemPropertySupplierInstrumentFalse() {
        System.setProperty("instrument", "false");
        BooleanSupplier supplier = InstrumentationProperties
                .getSystemPropertySupplier("test");
        assertThat(supplier.asBoolean()).isFalse();
    }

    @Test
    public void testSystemPropertySupplierInstrumentTrue() {
        System.setProperty("instrument", "true");
        BooleanSupplier supplier = InstrumentationProperties
                .getSystemPropertySupplier("test");
        assertThat(supplier.asBoolean()).isTrue();
    }

    @Test
    public void testSystemPropertySupplierInstrumentClassFalse() {
        System.setProperty("instrument.test", "false");
        BooleanSupplier supplier = InstrumentationProperties
                .getSystemPropertySupplier("test");
        assertThat(supplier.asBoolean()).isFalse();
    }

    @Test
    public void testSystemPropertySupplierInstrumentClassTrue() {
        System.clearProperty("instrument");
        System.setProperty("instrument.test", "true");
        BooleanSupplier supplier = InstrumentationProperties
                .getSystemPropertySupplier("test");
        assertThat(supplier.asBoolean()).isTrue();
    }

    @Test
    @SuppressWarnings("NullAway") // explicitly testing null
    public void invalid() {
        assertThatThrownBy(() -> InstrumentationProperties.getSystemPropertySupplier(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name cannot be null or empty: {name=null}");
        assertThatThrownBy(() -> InstrumentationProperties.getSystemPropertySupplier(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name cannot be null or empty: {name=}");
    }

    @Test
    public void racingSystemProperties() throws Exception {
        CountDownLatch latch = new CountDownLatch(8);
        List<Callable<Object>> tasks = ImmutableList.of(
                () -> {
                    latch.countDown();
                    latch.await();
                    return "getSystemPropertySupplier: " + InstrumentationProperties.getSystemPropertySupplier("test")
                            .asBoolean();
                },
                () -> {
                    latch.countDown();
                    latch.await();
                    return "getProperty: " + System.getProperty("instrument.test");
                },
                () -> {
                    latch.countDown();
                    latch.await();
                    return "setProperty: " + System.setProperty("instrument.test", "true");
                },
                () -> {
                    latch.countDown();
                    latch.await();
                    for (int i = 0; i < 1000; i++) {
                        System.setProperty("test" + i, "value" + i);
                    }
                    return "setProperties: " + System.getProperties();
                },
                () -> {
                    latch.countDown();
                    latch.await();
                    InstrumentationProperties.reload();
                    return "reload";
                },
                () -> {
                    latch.countDown();
                    latch.await();
                    return "getSystemPropertySupplier: "
                            + InstrumentationProperties.getSystemPropertySupplier("test").asBoolean();
                },
                () -> {
                    latch.countDown();
                    latch.await();
                    return "isSpecificEnabled: " + InstrumentationProperties.isSpecificEnabled("test");
                },
                () -> {
                    latch.countDown();
                    latch.await();
                    return "isGloballyEnabled: " + InstrumentationProperties.isGloballyEnabled();
                });

        List<ListenableFuture<Object>> futures = tasks.stream()
                .map(task -> executorService.submit(task))
                .map(task -> {
                    Futures.addCallback(task, new FutureCallback<Object>() {
                        @Override
                        public void onSuccess(@Nullable Object result) {
                            System.out.println("result: " + result);
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            System.err.println("error: " + throwable);
                            throwable.printStackTrace(System.err);
                            assertThat(throwable).isNull();
                        }
                    }, MoreExecutors.directExecutor());
                    return task;
                })
                .collect(Collectors.toList());

        await().atMost(Duration.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(Futures.allAsList(futures).get()).hasSize(futures.size());
        });
    }
}
