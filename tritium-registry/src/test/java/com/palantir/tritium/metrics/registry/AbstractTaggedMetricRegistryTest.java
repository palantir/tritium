/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.metrics.registry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.codahale.metrics.Gauge;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

// @RunWith(Parameterized.class)
public class AbstractTaggedMetricRegistryTest {
    private final TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();

    @Mock
    private TaggedMetricRegistryListener listener;
    @Mock
    private TaggedMetricRegistryListener listener2;
    @Mock
    private MetricName name;

    private static final Gauge<Integer> GAUGE = () -> 1;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    private void addMetric() {
        registry.gauge(name, GAUGE);
    }

    private void verifyMetricAdded(TaggedMetricRegistryListener param) {
        verify(param).onGaugeAdded(name, GAUGE);
    }

    private void verifyMetricRemoved(TaggedMetricRegistryListener param) {
        verify(param).onGaugeRemoved(name);
    }

    private void stubMetricAdded(TaggedMetricRegistryListener listener, Runnable action) {
        doAnswer(invocation -> {
            action.run();
            return null;
        }).when(listener).onGaugeAdded(any(), any());
    }

    @Test
    public void adding_new_metric_triggers_listeners() {
        registry.addListener(listener);
        addMetric();
        verifyMetricAdded(listener);
    }

    @Test
    public void removing_metric_triggers_listeners() {
        registry.addListener(listener);
        addMetric();
        reset(listener);

        registry.remove(name);
        verifyMetricRemoved(listener);
    }

    @Test
    public void removed_listener_does_not_get_notified() {
        registry.addListener(listener);
        registry.removeListener(listener);
        addMetric();
    }

    @Test
    public void removing_listener_that_has_not_been_added_throws() {
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registry.removeListener(listener));
    }

    @Test
    public void self_removing_listener_does_not_break_other_listeners() {
        registry.addListener(listener);
        registry.addListener(listener2);
        stubMetricAdded(listener, () -> registry.removeListener(this.listener));

        addMetric();

        verifyMetricAdded(listener);
        verifyMetricAdded(listener2);
    }

    @After
    public void after() {
        verifyNoMoreInteractions(listener, listener2);
    }
}
