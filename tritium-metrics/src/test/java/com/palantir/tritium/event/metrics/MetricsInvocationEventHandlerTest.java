/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.api.event.InvocationContext;
import org.junit.Test;

public class MetricsInvocationEventHandlerTest {

    @Test
    public void testFailure() throws Exception {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");

        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(String.class.getDeclaredMethod("length"));
        assertThat(metricRegistry.getMeters().get("failures")).isNull();

        handler.onFailure(context, new RuntimeException("unexpected"));

        assertThat(metricRegistry.getMeters().get("failures")).isNotNull();
        assertThat(metricRegistry.getMeters().get("failures").getCount()).isEqualTo(1L);
    }

    @Test
    public void testOnSuccessNullContext() {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");
        assertThat(metricRegistry.getMeters().get("failures")).isNull();

        handler.onSuccess(null, new Object());

        assertThat(metricRegistry.getMeters().get("failures")).isNull();
    }

    @Test
    public void testOnFailureNullContext() {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");
        assertThat(metricRegistry.getMeters().get("failures")).isNull();

        handler.onFailure(null, new RuntimeException("expected"));

        assertThat(metricRegistry.getMeters().get("failures")).isNotNull();
        assertThat(metricRegistry.getMeters().get("failures").getCount()).isEqualTo(1L);
    }

    @Test
    public void testSystemPropertySupplier_Handler_Enabled() throws Exception {
        assertThat(MetricsInvocationEventHandler.getEnabledSupplier("test").asBoolean()).isTrue();
    }

}
