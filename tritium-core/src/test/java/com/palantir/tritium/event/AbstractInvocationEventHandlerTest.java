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

package com.palantir.tritium.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
final class AbstractInvocationEventHandlerTest {

    @SystemStub
    private SystemProperties systemProperties;

    @BeforeEach
    void before() {
        systemProperties.remove("instrument");
        System.getProperties()
                .entrySet()
                .removeIf(entry -> entry.getKey().toString().startsWith("instrument"));
        InstrumentationProperties.reload();
    }

    @Test
    void testSystemPropertySupplierEnabledByDefault() {
        BooleanSupplier supplier =
                AbstractInvocationEventHandler.getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isTrue();
    }

    @Test
    void testSystemPropertySupplierInstrumentFalse() {
        systemProperties.set("instrument", "false");
        BooleanSupplier supplier =
                AbstractInvocationEventHandler.getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isFalse();
    }

    @Test
    void testSystemPropertySupplierInstrumentTrue() {
        systemProperties.set("instrument", "true");
        BooleanSupplier supplier =
                AbstractInvocationEventHandler.getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isTrue();
    }

    @Test
    void testSystemPropertySupplierInstrumentClassFalse() {
        systemProperties.set("instrument." + CompositeInvocationEventHandler.class.getName(), "false");
        BooleanSupplier supplier =
                AbstractInvocationEventHandler.getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isFalse();
    }

    @Test
    void testSystemPropertySupplierInstrumentClassTrue() {
        systemProperties.remove("instrument");
        systemProperties.set("instrument." + CompositeInvocationEventHandler.class.getName(), "true");
        BooleanSupplier supplier =
                AbstractInvocationEventHandler.getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isTrue();
    }
}
