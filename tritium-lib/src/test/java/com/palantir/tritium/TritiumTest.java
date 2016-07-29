/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.util.SortedMap;
import org.junit.Test;

public final class TritiumTest {

    private static final String EXPECTED_METRIC_NAME = TestInterface.class.getName() + ".test";

    @Test
    public void testInstrument() {
        TestImplementation delegate = new TestImplementation();

        MetricRegistry metricRegistry = new MetricRegistry();

        TestInterface instrumentedService = Tritium.instrument(TestInterface.class, delegate, metricRegistry);

        assertThat(delegate.invocationCount(), equalTo(0));
        assertThat(metricRegistry.getTimers().get(Runnable.class.getName()), nullValue());

        instrumentedService.test();
        assertThat(delegate.invocationCount(), equalTo(1));


        SortedMap<String, Timer> timers = metricRegistry.getTimers();
        assertThat(timers.keySet(), hasSize(1));
        assertThat(timers.keySet(), equalTo(ImmutableSet.of(EXPECTED_METRIC_NAME)));
        assertThat(timers.get(EXPECTED_METRIC_NAME), notNullValue());
        assertThat(timers.get(EXPECTED_METRIC_NAME).getCount(), equalTo(1L));

        instrumentedService.test();

        assertThat(Long.valueOf(timers.get(EXPECTED_METRIC_NAME).getCount()).intValue(),
                equalTo(delegate.invocationCount()));
        assertTrue(timers.get(EXPECTED_METRIC_NAME).getSnapshot().getMax() >= 0L);

        Slf4jReporter.forRegistry(metricRegistry).withLoggingLevel(Slf4jReporter.LoggingLevel.INFO).build().report();
    }

}
