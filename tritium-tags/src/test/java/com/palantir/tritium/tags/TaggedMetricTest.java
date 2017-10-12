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

package com.palantir.tritium.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import org.junit.Test;

public class TaggedMetricTest {

    @Test
    public void simpleMetricName() {
        TaggedMetric metric = TaggedMetric.builder().metricName("test").build();
        assertThat(metric.toString()).isEqualTo("test");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void invalidTaggedMetrics() {
        assertThatThrownBy(() -> TaggedMetric.builder().metricName(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaggedMetric.builder().tags(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaggedMetric.builder().putTags(null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaggedMetric.builder().putTags("key", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaggedMetric.builder().putTags(null, "value"))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> TaggedMetric.builder().metricName("colon:delimited").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain ':'");
        assertThatThrownBy(() -> TaggedMetric.builder().metricName("a,b").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain ','");
        assertThatThrownBy(() -> TaggedMetric.builder().metricName("a{").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain '{'");
        assertThatThrownBy(() -> TaggedMetric.builder().metricName("a}").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain '}'");
    }

    @Test
    public void blankMetricName() {
        assertThatThrownBy(() ->
                TaggedMetric.builder().metricName(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric name ' ' must not be empty");

        assertThatThrownBy(() ->
                TaggedMetric.builder().metricName("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric name '' must not be empty");
    }

    @Test
    public void emptyTags() {
        TaggedMetric metric = TaggedMetric.builder()
                .metricName("test")
                .tags(Collections.emptyMap())
                .build();

        assertThat(metric.toString()).isEqualTo("test");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void singleTag() {
        TaggedMetric metric = TaggedMetric.builder()
                .metricName("test")
                .putTags("key", "value")
                .build();

        assertThat(metric.toString()).isEqualTo("test[key:value]");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void twoTags() {
        TaggedMetric metric = TaggedMetric.builder()
                .metricName("test")
                .putTags("key1", "value1")
                .putTags("key2", "value2")
                .build();

        assertThat(metric.toString()).isEqualTo("test[key1:value1,key2:value2]");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void emptyTagName() {
        TaggedMetric metric = TaggedMetric.builder()
                .metricName("test")
                .putTags("", "value")
                .build();

        assertThat(metric.tags())
                .containsKey("")
                .containsValue("value");
        assertThat(metric.nonEmptyTags()).isEmpty();
        assertThat(metric.toString()).isEqualTo("test");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void emptyTagValue() {
        TaggedMetric metric = TaggedMetric.builder()
                .metricName("test")
                .putTags("key", "")
                .build();

        assertThat(metric.nonEmptyTags()).isEmpty();
        assertThat(metric.toString()).isEqualTo("test");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void blankTagValue() {
        TaggedMetric metric = TaggedMetric.builder()
                .metricName("test")
                .putTags("key", " ")
                .build();

        assertThat(metric.nonEmptyTags()).isEmpty();
        assertThat(metric.toString()).isEqualTo("test");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void notBlank() {
        assertThat(TaggedMetric.isNotBlank("test")).isTrue();
        assertThat(TaggedMetric.isNotBlank(" test")).isTrue();
        assertThat(TaggedMetric.isNotBlank("test ")).isTrue();
    }

    @Test
    public void blank() {
        assertThat(TaggedMetric.isNotBlank(null)).isFalse();
        assertThat(TaggedMetric.isNotBlank("")).isFalse();
        assertThat(TaggedMetric.isNotBlank(" ")).isFalse();
        assertThat(TaggedMetric.isNotBlank(" \t")).isFalse();
    }
}
