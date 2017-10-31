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

package com.palantir.tritium.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import org.junit.Before;
import org.junit.Test;

public final class TaggedMetricRegistryTest {

    private static final MetricName METRIC_NO_TAGS = MetricName.builder().safeName("name").build();
    private static final MetricName METRIC_WITH_TAG = MetricName.builder().safeName("name")
            .putSafeTags("key", "val").build();

    private TaggedMetricRegistry registry;

    @Before
    public void before() {
        registry = new TaggedMetricRegistry();
    }

    @Test
    public void testCounter() {
        Counter counter1 = registry.counter(METRIC_NO_TAGS);
        Counter counter2 = registry.counter(METRIC_WITH_TAG);

        // ensure new counters are created
        assertThat(counter1.getCount()).isEqualTo(0);
        assertThat(counter2.getCount()).isEqualTo(0);

        // ensure they're not the same and can be retrieved using the same name
        assertThat(counter1).isNotSameAs(counter2);
        assertThat(registry.counter(METRIC_NO_TAGS)).isSameAs(counter1);
        assertThat(registry.counter(METRIC_WITH_TAG)).isSameAs(counter2);
    }

    @Test
    public void testGauge() {
        Gauge gauge1 = registry.gauge(METRIC_NO_TAGS, () -> 1);
        Gauge gauge2 = registry.gauge(METRIC_WITH_TAG, () -> 2);

        assertThat(gauge1.getValue()).isEqualTo(1);
        assertThat(gauge2.getValue()).isEqualTo(2);

        assertThat(gauge1).isNotSameAs(gauge2);
        assertThat(registry.gauge(METRIC_NO_TAGS, () -> 3)).isSameAs(gauge1);
        assertThat(registry.gauge(METRIC_WITH_TAG, () -> 4)).isSameAs(gauge2);
    }

    @Test
    public void testReplaceGauge() {
        Gauge gauge1 = registry.replaceGauge(METRIC_NO_TAGS, () -> 1);
        assertThat(gauge1.getValue()).isEqualTo(1);

        Gauge gauge2 = registry.replaceGauge(METRIC_NO_TAGS, () -> 2);
        assertThat(gauge1).isNotSameAs(gauge2);

        assertThat(gauge2.getValue()).isEqualTo(2);

        assertThat(registry.gauge(METRIC_NO_TAGS, () -> 3)).isSameAs(gauge2);
        assertThat(registry.gauge(METRIC_NO_TAGS, () -> 1).getValue()).isEqualTo(2);
    }

    @Test
    public void testHistogram() {
        Histogram histogram1 = registry.histogram(METRIC_NO_TAGS);
        Histogram histogram2 = registry.histogram(METRIC_WITH_TAG);

        assertThat(histogram1.getCount()).isEqualTo(0);
        assertThat(histogram2.getCount()).isEqualTo(0);

        assertThat(histogram1).isNotSameAs(histogram2);
        assertThat(registry.histogram(METRIC_NO_TAGS)).isSameAs(histogram1);
        assertThat(registry.histogram(METRIC_WITH_TAG)).isSameAs(histogram2);
    }

    @Test
    public void testMeter() {
        Meter meter1 = registry.meter(METRIC_NO_TAGS);
        Meter meter2 = registry.meter(METRIC_WITH_TAG);

        assertThat(meter1.getCount()).isEqualTo(0);
        assertThat(meter2.getCount()).isEqualTo(0);

        assertThat(meter1).isNotSameAs(meter2);
        assertThat(registry.meter(METRIC_NO_TAGS)).isSameAs(meter1);
        assertThat(registry.meter(METRIC_WITH_TAG)).isSameAs(meter2);
    }

    @Test
    public void testTimer() {
        Timer timer1 = registry.timer(METRIC_NO_TAGS);
        Timer timer2 = registry.timer(METRIC_WITH_TAG);

        assertThat(timer1.getCount()).isEqualTo(0);
        assertThat(timer2.getCount()).isEqualTo(0);

        assertThat(timer1).isNotSameAs(timer2);
        assertThat(registry.timer(METRIC_NO_TAGS)).isSameAs(timer1);
        assertThat(registry.timer(METRIC_WITH_TAG)).isSameAs(timer2);
    }

    @Test
    public void testExistingMetric() {
        registry.counter(METRIC_NO_TAGS);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registry.timer(METRIC_NO_TAGS))
                .withMessage("'name' already used for a metric of type 'Counter' but wanted type 'Timer'. tags: {}");
    }
}
