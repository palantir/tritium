/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.event.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.palantir.tritium.event.InstrumentationProperties;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
final class MetricsBooleanSupplierTest {

    private static final String METRICS_SYSTEM_PROPERTY_PREFIX = "instrument";

    @SystemStub
    private SystemProperties systemProperties;

    static Stream<Arguments> data() {
        return Stream.of(
                // disabled
                arguments(false, false, false, false),
                arguments(false, false, false, true),
                arguments(false, false, true, false),
                arguments(false, false, true, true),
                arguments(false, true, false, false),
                arguments(false, true, true, false),
                // enabled
                arguments(true, true, false, true),
                arguments(true, true, true, true));
    }

    @BeforeEach
    void before() {
        systemProperties.remove(METRICS_SYSTEM_PROPERTY_PREFIX);
        System.getProperties().entrySet().removeIf(entry -> String.valueOf(entry.getKey())
                .startsWith(METRICS_SYSTEM_PROPERTY_PREFIX));
        InstrumentationProperties.reload();
    }

    @AfterEach
    void after() {
        systemProperties.remove(METRICS_SYSTEM_PROPERTY_PREFIX);
        System.getProperties().entrySet().removeIf(entry -> String.valueOf(entry.getKey())
                .startsWith(METRICS_SYSTEM_PROPERTY_PREFIX));
        InstrumentationProperties.reload();
    }

    @ParameterizedTest
    @MethodSource("data")
    void testSupplier(boolean expected, boolean global, boolean handler, boolean service) {
        systemProperties.set(METRICS_SYSTEM_PROPERTY_PREFIX, String.valueOf(global));
        systemProperties.set(
                METRICS_SYSTEM_PROPERTY_PREFIX + ".com.palantir.tritium.event.metrics.MetricsInvocationEventHandler",
                String.valueOf(handler));
        systemProperties.set(METRICS_SYSTEM_PROPERTY_PREFIX + ".test", String.valueOf(service));
        InstrumentationProperties.reload();
        BooleanSupplier supplier = MetricsInvocationEventHandler.getEnabledSupplier("test");
        assertThat(supplier.getAsBoolean()).isEqualTo(expected);
    }
}
