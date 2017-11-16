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

package com.palantir.tritium.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Timer;
import com.google.common.base.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@SuppressWarnings("NullableProblems") // IntelliJ warnings about injected fields
@RunWith(MockitoJUnitRunner.class)
public class MetricRegistryWithReservoirsTest {

    @Mock
    private Supplier<Reservoir> mockSupplier;

    @Mock
    private Reservoir mockReservoir;

    private MetricRegistryWithReservoirs metrics;

    @Before
    public void before() {
        when(mockSupplier.get()).thenReturn(mockReservoir);
        metrics = new MetricRegistryWithReservoirs(mockSupplier);
    }

    @Test
    public void getReservoirSupplier() {
        assertThat(metrics.getReservoirSupplier()).isSameAs(mockSupplier);
        assertThat(metrics.getReservoirSupplier().get()).isSameAs(mockReservoir);
        assertThat(metrics.getReservoirSupplier().get()).isSameAs(mockReservoir);

        verify(mockSupplier, times(2)).get();
        verifyNoMoreInteractions(mockReservoir, mockSupplier);
    }

    @Test
    public void histogram() {
        Histogram histogram = metrics.histogram("test");
        assertThat(histogram).isNotNull();
        assertThat(metrics.histogram("test")).isSameAs(histogram);

        verify(mockSupplier, times(1)).get();
        verifyNoMoreInteractions(mockReservoir, mockSupplier);
    }

    @Test
    public void timer() {
        Timer timer = metrics.timer("test");
        assertThat(timer).isNotNull();
        assertThat(metrics.timer("test")).isSameAs(timer);

        verify(mockSupplier, times(1)).get();
        verifyNoMoreInteractions(mockReservoir, mockSupplier);
    }

}
