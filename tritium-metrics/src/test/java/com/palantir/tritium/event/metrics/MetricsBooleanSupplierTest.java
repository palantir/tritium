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

import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.InstrumentationProperties;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetricsBooleanSupplierTest {

    private static final String METRICS_SYSTEM_PROPERTY_PREFIX = "instrument";

    @Parameterized.Parameters(name = "{index}: expected {0}: global={1}, handler={2}, service={3}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                // disabled
                {false, false, false, false},
                {false, false, false, true},
                {false, false, true, false},
                {false, false, true, true},
                {false, true, false, false},
                {false, true, true, false},
                // enabled
                {true, true, false, true},
                {true, true, true, true},
                });
    }

    @SuppressWarnings({"WeakerAccess", "checkstyle:VisibilityModifier"})
    @Parameterized.Parameter(value = 0)
    public boolean expected;

    @SuppressWarnings({"WeakerAccess", "checkstyle:VisibilityModifier"})
    @Parameterized.Parameter(value = 1)
    public boolean global;

    @SuppressWarnings({"WeakerAccess", "checkstyle:VisibilityModifier"})
    @Parameterized.Parameter(value = 2)
    public boolean handler;

    @SuppressWarnings({"WeakerAccess", "checkstyle:VisibilityModifier"})
    @Parameterized.Parameter(value = 3)
    public boolean service;

    @Before
    public void before() {
        System.clearProperty("instrument");
        System.getProperties().entrySet().removeIf(entry ->
                entry.getKey().toString().startsWith("instrument"));
        InstrumentationProperties.reload();
    }

    @After
    public void after() {
        Set<Map.Entry<Object, Object>> entries = System.getProperties().entrySet();
        entries.removeIf(objectObjectEntry ->
                String.valueOf(objectObjectEntry.getKey()).startsWith(METRICS_SYSTEM_PROPERTY_PREFIX));
        InstrumentationProperties.reload();
    }

    @Test
    public void testSupplier() {
        System.setProperty("instrument", String.valueOf(global));
        System.setProperty("instrument.com.palantir.tritium.event.metrics.MetricsInvocationEventHandler",
                String.valueOf(handler));
        System.setProperty("instrument.test", String.valueOf(service));
        InstrumentationProperties.reload();
        BooleanSupplier supplier = MetricsInvocationEventHandler.getEnabledSupplier("test");
        assertThat(supplier.asBoolean()).isEqualTo(expected);
    }

}
