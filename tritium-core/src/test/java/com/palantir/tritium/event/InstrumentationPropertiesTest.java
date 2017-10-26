/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

import com.palantir.tritium.api.functions.BooleanSupplier;
import org.junit.Before;
import org.junit.Test;

public class InstrumentationPropertiesTest {

    @Before
    public void before() {
        System.clearProperty("instrument");
        System.getProperties().entrySet().removeIf(entry ->
                entry.getKey().toString().startsWith("instrument"));
        InstrumentationProperties.reload();
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
    public void invalid() {
        assertThatThrownBy(() -> InstrumentationProperties.getSystemPropertySupplier(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("name cannot be null or empty");
        assertThatThrownBy(() -> InstrumentationProperties.getSystemPropertySupplier(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("name cannot be null or empty");
    }
}
