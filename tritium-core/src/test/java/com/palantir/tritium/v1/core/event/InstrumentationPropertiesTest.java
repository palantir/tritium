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

package com.palantir.tritium.v1.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class InstrumentationPropertiesTest {

    private ListeningExecutorService executorService;

    @BeforeEach
    void before() {
        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        System.clearProperty("instrument");
        System.getProperties()
                .entrySet()
                .removeIf(entry -> entry.getKey().toString().startsWith("instrument"));
        InstrumentationProperties.reload();
    }

    @AfterEach
    void after() {
        executorService.shutdownNow();
    }

    @Test
    void testSystemPropertySupplierEnabledByDefault() {
        BooleanSupplier supplier = InstrumentationProperties.getSystemPropertySupplier("test");
        assertThat(supplier.getAsBoolean()).isTrue();
    }

    @Test
    void testSystemPropertySupplierInstrumentFalse() {
        System.setProperty("instrument", "false");
        BooleanSupplier supplier = InstrumentationProperties.getSystemPropertySupplier("test");
        assertThat(supplier.getAsBoolean()).isFalse();
    }

    @Test
    void testSystemPropertySupplierInstrumentTrue() {
        System.setProperty("instrument", "true");
        BooleanSupplier supplier = InstrumentationProperties.getSystemPropertySupplier("test");
        assertThat(supplier.getAsBoolean()).isTrue();
    }

    @Test
    void testSystemPropertySupplierInstrumentClassFalse() {
        System.setProperty("instrument.test", "false");
        BooleanSupplier supplier = InstrumentationProperties.getSystemPropertySupplier("test");
        assertThat(supplier.getAsBoolean()).isFalse();
    }

    @Test
    void testSystemPropertySupplierInstrumentClassTrue() {
        System.clearProperty("instrument");
        System.setProperty("instrument.test", "true");
        BooleanSupplier supplier = InstrumentationProperties.getSystemPropertySupplier("test");
        assertThat(supplier.getAsBoolean()).isTrue();
    }

    @Test
    @SuppressWarnings("NullAway") // explicitly testing null
    void invalid() {
        assertThatThrownBy(() -> InstrumentationProperties.getSystemPropertySupplier((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name cannot be null or empty: {name=null}");
        assertThatThrownBy(() -> InstrumentationProperties.getSystemPropertySupplier(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name cannot be null or empty: {name=}");
    }

    @Test
    void racingSystemProperties() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(8);
        List<Callable<Object>> tasks = ImmutableList.of(
                () -> {
                    barrier.await();
                    return "getSystemPropertySupplier: "
                            + InstrumentationProperties.getSystemPropertySupplier("test")
                                    .getAsBoolean();
                },
                () -> {
                    barrier.await();
                    return "getProperty: " + System.getProperty("instrument.test");
                },
                () -> {
                    barrier.await();
                    return "setProperty: " + System.setProperty("instrument.test", "true");
                },
                () -> {
                    barrier.await();
                    for (int i = 0; i < 1000; i++) {
                        System.setProperty("test" + i, "value" + i);
                    }
                    return "setProperties: " + System.getProperties();
                },
                () -> {
                    barrier.await();
                    InstrumentationProperties.reload();
                    return "reload";
                },
                () -> {
                    barrier.await();
                    return "getSystemPropertySupplier: "
                            + InstrumentationProperties.getSystemPropertySupplier("test")
                                    .getAsBoolean();
                },
                () -> {
                    barrier.await();
                    return "isSpecificallyEnabled: " + InstrumentationProperties.isSpecificallyEnabled("test");
                },
                () -> {
                    barrier.await();
                    return "isGloballyEnabled: " + InstrumentationProperties.isGloballyEnabled();
                });

        final int expectedTaskCount = tasks.size();
        assertThat(barrier.getParties()).isEqualTo(expectedTaskCount);
        assertThat(barrier.getNumberWaiting()).isZero();

        @SuppressWarnings({"unchecked", "rawtypes"}) // guaranteed by ListenableExecutorService
        List<ListenableFuture<Object>> futures = (List) executorService.invokeAll(tasks);

        ListenableFuture<List<Object>> successfulAsList = Futures.successfulAsList(futures);
        Futures.addCallback(
                successfulAsList,
                new FutureCallback<List<Object>>() {
                    @Override
                    public void onSuccess(@Nullable List<Object> result) {
                        assertThat(result).isNotNull().hasSize(expectedTaskCount);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        assertThat(throwable).describedAs("should not throw").isNull();
                    }
                },
                MoreExecutors.directExecutor());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(successfulAsList.get()).hasSize(expectedTaskCount);
            assertThat(barrier.getNumberWaiting()).isZero();
        });
    }

    @Test
    void testisSpecificallyEnabled_notSet() {
        assertThat(InstrumentationProperties.isSpecificallyEnabled("not_set")).isTrue();
    }

    @Test
    void testisSpecificallyEnabled_notSet_defaultTrue() {
        assertThat(InstrumentationProperties.isSpecificallyEnabled("not_set", true))
                .isTrue();
    }

    @Test
    void testisSpecificallyEnabled_notSet_defaultFalse() {
        assertThat(InstrumentationProperties.isSpecificallyEnabled("not_set", false))
                .isFalse();
    }

    @Test
    void testisSpecificallyEnabled_setGarbage() {
        System.setProperty("instrument.garbage", "garbage");
        InstrumentationProperties.reload();
        assertThat(InstrumentationProperties.isSpecificallyEnabled("garbage")).isFalse();
    }

    @Test
    void testisSpecificallyEnabled_setGarbage_defaultTrue() {
        System.setProperty("instrument.garbage", "garbage");
        InstrumentationProperties.reload();
        assertThat(InstrumentationProperties.isSpecificallyEnabled("garbage", true))
                .isFalse();
    }

    @Test
    void testisSpecificallyEnabled_setGarbage_defaultFalse() {
        System.setProperty("instrument.garbage", "garbage");
        InstrumentationProperties.reload();
        assertThat(InstrumentationProperties.isSpecificallyEnabled("garbage", false))
                .isFalse();
    }

    @Test
    void testisSpecificallyEnabled_setTrue() {
        System.setProperty("instrument.true", "true");
        InstrumentationProperties.reload();
        assertThat(InstrumentationProperties.isSpecificallyEnabled("true")).isTrue();
    }

    @Test
    void testisSpecificallyEnabled_setTrue_defaultTrue() {
        System.setProperty("instrument.true", "true");
        InstrumentationProperties.reload();
        assertThat(InstrumentationProperties.isSpecificallyEnabled("true", true))
                .isTrue();
    }

    @Test
    void testisSpecificallyEnabled_setTrue_defaultFalse() {
        System.setProperty("instrument.true", "true");
        InstrumentationProperties.reload();
        assertThat(InstrumentationProperties.isSpecificallyEnabled("true", false))
                .isTrue();
    }

    @Test
    void testisSpecificallyEnabled_setFalse() {
        System.setProperty("instrument.false", "false");
        InstrumentationProperties.reload();
        assertThat(InstrumentationProperties.isSpecificallyEnabled("false")).isFalse();
    }

    @Test
    void testisSpecificallyEnabled_setFalse_defaultTrue() {
        System.setProperty("instrument.false", "false");
        InstrumentationProperties.reload();
        assertThat(InstrumentationProperties.isSpecificallyEnabled("false", true))
                .isFalse();
    }

    @Test
    void testisSpecificallyEnabled_setFalse_defaultFalse() {
        System.setProperty("instrument.false", "false");
        InstrumentationProperties.reload();
        assertThat(InstrumentationProperties.isSpecificallyEnabled("false", false))
                .isFalse();
    }
}