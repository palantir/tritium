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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Collections;
import org.junit.Test;

public class TaggedMetricTest {

    @Test
    public void simpleMetricName() {
        TaggedMetric metric = TaggedMetric.builder().name("test").build();
        assertThat(metric.toString()).isEqualTo("test");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void invalidTaggedMetricValues() {
        assertThatThrownBy(() -> TaggedMetric.builder().name(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaggedMetric.builder().tags(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaggedMetric.builder().putTags(null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaggedMetric.builder().putTags("key", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaggedMetric.builder().putTags(null, "value"))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> TaggedMetric.builder().name("colon:delimited").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain ':'");
        assertThatThrownBy(() -> TaggedMetric.builder().name("a,b").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain ','");
        assertThatThrownBy(() -> TaggedMetric.builder().name("a[").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain '['");
        assertThatThrownBy(() -> TaggedMetric.builder().name("a]").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain ']'");
        assertThatThrownBy(() -> TaggedMetric.builder().name("a").putTags("a]", "value").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain ']'");
        assertThatThrownBy(() -> TaggedMetric.builder().name("a").putTags("a", "value]").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" must not contain ']'");
    }

    @Test
    public void blankMetricName() {
        assertThatThrownBy(() ->
                TaggedMetric.builder().name(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("' ' must not be null or blank");

        assertThatThrownBy(() ->
                TaggedMetric.builder().name("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'' must not be null or blank");
    }

    @Test
    public void emptyTags() {
        TaggedMetric metric = TaggedMetric.builder()
                .name("test")
                .tags(Collections.emptyMap())
                .build();

        assertThat(metric.toString()).isEqualTo("test");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void singleTag() {
        TaggedMetric metric = TaggedMetric.builder()
                .name("test")
                .putTags("key", "value")
                .build();

        assertThat(metric.toString()).isEqualTo("test[key:value]");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void twoTags() {
        TaggedMetric metric = TaggedMetric.builder()
                .name("test")
                .putTags("key1", "value1")
                .putTags("key2", "value2")
                .build();

        assertThat(metric.toString()).isEqualTo("test[key1:value1,key2:value2]");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void fullMetricName() {
        TaggedMetric metric = TaggedMetric.builder()
                .name(TaggedMetric.class.getName())
                .putTags("ip", "127.0.0.1")
                .putTags("host", "localhost")
                .putTags("endpoint", "testEndpoint")
                .putTags("path", "/foo/{bar}")
                .build();

        assertThat(metric.toString()).isEqualTo("com.palantir.tritium.tags.TaggedMetric"
                + "["
                + "endpoint:testEndpoint,"
                + "host:localhost,"
                + "ip:127.0.0.1,"
                + "path:/foo/{bar}"
                + "]");
        assertThat(metric.canonicalName()).isSameAs(metric.toString());
    }

    @Test
    public void emptyTagName() {
        assertThatThrownBy(() -> TaggedMetric.builder()
                .name("test")
                .putTags("", "value")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'' must not be null or blank");
    }

    @Test
    public void emptyTagValue() {
        assertThatThrownBy(() -> TaggedMetric.builder()
                .name("test")
                .putTags("key", "")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'' must not be null or blank");
    }

    @Test
    public void blankTagValue() {
        assertThatThrownBy(() -> TaggedMetric.builder()
                .name("test")
                .putTags("key", " ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("' ' must not be null or blank");
    }

    @Test
    public void parseSimple() {
        TaggedMetric metric = TaggedMetric.from("server.response-time.p99");
        assertThat(metric.name())
                .isEqualTo("server.response-time.p99")
                .isSameAs(metric.canonicalName())
                .isSameAs(metric.toString());
        assertThat(metric.tags()).isEmpty();

    }

    @Test
    public void parseNoTags() {
        assertThat(TaggedMetric.from("test").tags()).isEmpty();
        assertThat(TaggedMetric.from("test[]").tags()).isEmpty();
        assertThat(TaggedMetric.from("test[ ]").tags()).isEmpty();
        assertThat(TaggedMetric.from("test[ , ]").tags()).isEmpty();
    }

    @Test
    public void parseSingleTag() {
        assertThat(TaggedMetric.from("test[ key: value ]").tags())
                .containsExactly(Maps.immutableEntry("key", "value"));
    }

    @Test
    public void parseTags() {
        TaggedMetric metric = TaggedMetric.from("server.response-time"
                + "["
                + "path:/foo/{bar},"
                + "endpoint:testEndpoint,"
                + "ip:127.0.0.1,"
                + "host:localhost"
                + "]");
        assertThat(metric.name()).isEqualTo("server.response-time");
        assertThat(metric.canonicalName())
                .isEqualTo("server.response-time[endpoint:testEndpoint,host:localhost,ip:127.0.0.1,path:/foo/{bar}]")
                .isSameAs(metric.toString());
        assertThat(metric.tags()).contains(
                Maps.immutableEntry("path", "/foo/{bar}"),
                Maps.immutableEntry("endpoint", "testEndpoint"),
                Maps.immutableEntry("ip", "127.0.0.1"),
                Maps.immutableEntry("host", "localhost"));
    }

    @Test
    public void parseInvalid() {
        assertThatThrownBy(() -> TaggedMetric.from(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("canonicalMetricName");

        assertThatThrownBy(() -> TaggedMetric.from("test:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'test:' must not contain ':'");

        assertThatThrownBy(() -> TaggedMetric.from("test:value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'test:value' must not contain ':'");

        assertThatThrownBy(() -> TaggedMetric.from("test,value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'test,value' must not contain ','");

        assertThatThrownBy(() -> TaggedMetric.from("test]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'test]' must not contain ']'");

        assertThatThrownBy(() -> TaggedMetric.from("test["))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid metric name 'test[', found trailing '['");

        assertThatThrownBy(() -> TaggedMetric.from("test[a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid metric name 'test[a', found trailing '[a'");
    }

    @Test
    public void generateParseLifecycle() {
        TaggedMetric metric1 = TaggedMetric.builder()
                .name("test")
                .putTags("alpha", "1")
                .putTags("beta", "2")
                .build();
        TaggedMetric metric2 = TaggedMetric.from(metric1.canonicalName());
        assertThat(metric1).isEqualTo(metric2);
        assertThat(metric1.canonicalName()).isEqualTo(metric2.canonicalName());
    }

    @Test
    public void generateSimple() {
        assertThat(TaggedMetric.toCanonicalName("test", Collections.emptyMap())).isEqualTo("test");
    }

    @Test
    public void generateSingleTag() {
        assertThat(TaggedMetric.toCanonicalName("test", Collections.singletonMap("key", "value")))
                .isEqualTo("test[key:value]");
    }

    @Test
    public void generateMultipleTagsOrdered() {
        assertThat(TaggedMetric.toCanonicalName("test", ImmutableMap.of(
                "beta", "b1",
                "alpha", "a1")))
                .isEqualTo("test[alpha:a1,beta:b1]");
    }

    @Test
    public void generateInvalid() {
        assertThatThrownBy(() -> TaggedMetric.toCanonicalName(null, Collections.emptyMap()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name");

        assertThatThrownBy(() -> TaggedMetric.toCanonicalName("test", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tags");
    }

    @Test
    public void normalizeTags() {
        assertThat(TaggedMetric.normalizeTags(ImmutableMap.of()))
                .isSameAs(Collections.emptySortedMap())
                .isEmpty();

        assertThat(TaggedMetric.normalizeTags(ImmutableMap.of(
                "key", "value")))
                .containsExactly(Maps.immutableEntry("key", "value"));

        assertThat(TaggedMetric.normalizeTags(ImmutableMap.of(
                "a2345678901234567890", "value")))
                .containsExactly(Maps.immutableEntry("a2345678901234567890", "value"));

        assertThat(TaggedMetric.normalizeTags(ImmutableMap.of(
                "key-name", "value")))
                .containsExactly(Maps.immutableEntry("key-name", "value"));

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "KEY", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid metric name 'KEY'");

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "a23456789012345678901", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid metric name 'a23456789012345678901'");

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "key", "value",
                " key ", "value2")))
                .hasMessageStartingWith("Invalid metric name ' key '");

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "a", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid metric name 'a'");

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "a.1", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid metric name 'a.1'");
    }
}
