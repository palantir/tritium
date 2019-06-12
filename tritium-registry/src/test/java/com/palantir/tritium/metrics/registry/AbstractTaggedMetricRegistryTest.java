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

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.immutables.value.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(Parameterized.class)
public class AbstractTaggedMetricRegistryTest {
    @Value.Immutable
    interface TestCase<T> {
        Consumer<TaggedMetricRegistry> addMetric();
        Consumer<TaggedMetricRegistryListener> stubOrVerifyMetricAdded();
        Consumer<TaggedMetricRegistryListener> stubOrVerifyMetricRemoved();

    }

    private static final MetricName NAME = MetricName.builder()
            .safeName("name")
            .build();

    @Parameterized.Parameters
    public static Iterable<TestCase<?>> data() {
        Gauge<Integer> gauge = () -> 1;
        Meter meter = mock(Meter.class, "meter");

        return ImmutableList.of(
                ImmutableTestCase.<Gauge>builder()
                        .addMetric(registry -> registry.gauge(NAME, gauge))
                        .stubOrVerifyMetricAdded(listener -> listener.onGaugeAdded(NAME, gauge))
                        .stubOrVerifyMetricRemoved(listener -> listener.onGaugeRemoved(NAME))
                        .build(),
                ImmutableTestCase.<Gauge>builder()
                        .addMetric(registry -> registry.meter(NAME, () -> meter))
                        .stubOrVerifyMetricAdded(listener -> listener.onMeterAdded(NAME, meter))
                        .stubOrVerifyMetricRemoved(listener -> listener.onMeterRemoved(NAME))
                        .build()
        );
    }

    @Parameterized.Parameter
    public TestCase<?> testCase;

    private final TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();

    @Mock
    private TaggedMetricRegistryListener listener;
    @Mock
    private TaggedMetricRegistryListener listener2;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    private void addMetric() {
        testCase.addMetric().accept(registry);
    }

    private void verifyMetricAdded(TaggedMetricRegistryListener param) {
        testCase.stubOrVerifyMetricAdded().accept(verify(param));
    }

    private void verifyMetricRemoved(TaggedMetricRegistryListener param) {
        testCase.stubOrVerifyMetricRemoved().accept(verify(param));
    }

    private void stubMetricAdded(TaggedMetricRegistryListener param, Runnable action) {
        testCase.stubOrVerifyMetricAdded().accept(doAnswer(invocation -> {
            action.run();
            return null;
        }).when(param));
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

        registry.remove(NAME);
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
