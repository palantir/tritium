/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event.metrics;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.event.InvocationContext;
import org.junit.Test;

public final class MetricsInvocationEventHandlerTest {

    @Test
    public void testFailure() throws Exception {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");

        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(String.class.getDeclaredMethod("length"));
        assertThat(metricRegistry.getMeters().get("failures"), equalTo(null));

        handler.onFailure(context, new RuntimeException("unexpected"));

        assertThat(metricRegistry.getMeters().get("failures").getCount(), equalTo(1L));
    }

    @Test
    public void testOnSuccessNullContext() {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");
        assertThat(metricRegistry.getMeters().get("failures"), equalTo(null));

        handler.onSuccess(null, new Object());

        assertThat(metricRegistry.getMeters().get("failures"), equalTo(null));
    }

    @Test
    public void testOnFailureNullContext() {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");
        assertThat(metricRegistry.getMeters().get("failures"), equalTo(null));

        handler.onFailure(null, new RuntimeException("expected"));

        assertThat(metricRegistry.getMeters().get("failures").getCount(), equalTo(1L));
    }

}
