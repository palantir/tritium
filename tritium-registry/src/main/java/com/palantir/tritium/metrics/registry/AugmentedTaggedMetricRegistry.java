/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Any metric created on this registry will be saved into the delegate {@link TaggedMetricRegistry} with an extra tag
 * added. This ensures any reads from the underlying delegate will have the desired 'augmented' tag.
 */
public final class AugmentedTaggedMetricRegistry implements TaggedMetricRegistry {
    private final TaggedMetricRegistry delegate;
    private final String tagName;
    private final String tagValue;

    private AugmentedTaggedMetricRegistry(TaggedMetricRegistry delegate, String tagName, String tagValue) {
        this.delegate = Preconditions.checkNotNull(delegate, "delegate");
        this.tagName = Preconditions.checkNotNull(tagName, "tagName");
        this.tagValue = Preconditions.checkNotNull(tagValue, "tagValue");
    }

    public static AugmentedTaggedMetricRegistry create(
            TaggedMetricRegistry delegate, @Safe String tagName, @Safe String tagValue) {
        if (delegate instanceof AugmentedTaggedMetricRegistry) {
            AugmentedTaggedMetricRegistry other = (AugmentedTaggedMetricRegistry) delegate;
            if (Objects.equals(tagName, other.tagName)) {
                if (Objects.equals(tagValue, other.tagValue)) {
                    if (Objects.equals(delegate, other.delegate)) {
                        return other;
                    }
                } else {
                    throw new SafeIllegalArgumentException(
                            "Tag is already defined with a different value",
                            SafeArg.of("tagName", tagName),
                            SafeArg.of("existing", other.tagValue),
                            SafeArg.of("new", tagValue));
                }
            }
        }

        return new AugmentedTaggedMetricRegistry(delegate, tagName, tagValue);
    }

    private MetricName augment(MetricName existing) {
        return RealMetricName.create(existing, tagName, tagValue);
    }

    @Override
    public Map<MetricName, Metric> getMetrics() {
        return delegate.getMetrics();
    }

    @Override
    public void forEachMetric(BiConsumer<MetricName, Metric> consumer) {
        delegate.forEachMetric(consumer);
    }

    @Override
    public <T> Optional<Gauge<T>> gauge(MetricName metricName) {
        return delegate.gauge(augment(metricName));
    }

    @Override
    public <T> Gauge<T> gauge(MetricName metricName, Gauge<T> gauge) {
        return delegate.gauge(augment(metricName), gauge);
    }

    @Override
    public void registerWithReplacement(MetricName metricName, Gauge<?> gauge) {
        delegate.registerWithReplacement(augment(metricName), gauge);
    }

    @Override
    public Timer timer(MetricName metricName) {
        return delegate.timer(augment(metricName));
    }

    @Override
    public Timer timer(MetricName metricName, Supplier<Timer> timerSupplier) {
        return delegate.timer(augment(metricName), timerSupplier);
    }

    @Override
    public Meter meter(MetricName metricName) {
        return delegate.meter(augment(metricName));
    }

    @Override
    public Meter meter(MetricName metricName, Supplier<Meter> meterSupplier) {
        return delegate.meter(augment(metricName), meterSupplier);
    }

    @Override
    public Histogram histogram(MetricName metricName) {
        return delegate.histogram(augment(metricName));
    }

    @Override
    public Histogram histogram(MetricName metricName, Supplier<Histogram> histogramSupplier) {
        return delegate.histogram(augment(metricName), histogramSupplier);
    }

    @Override
    public Counter counter(MetricName metricName) {
        return delegate.counter(augment(metricName));
    }

    @Override
    public Counter counter(MetricName metricName, Supplier<Counter> counterSupplier) {
        return delegate.counter(augment(metricName), counterSupplier);
    }

    @Override
    public Optional<Metric> remove(MetricName metricName) {
        return delegate.remove(augment(metricName));
    }

    /**
     * .
     * @deprecated not implemented
     */
    @Deprecated
    @Override
    public void addMetrics(String _safeTagName, String _safeTagValue, TaggedMetricSet _metrics) {
        throw new UnsupportedOperationException(
                "Operations involving transforming TaggedMetricSet are not supported, please interact with the "
                        + "delegate directly");
    }

    /**
     * .
     * @deprecated not implemented
     */
    @Deprecated
    @Override
    public Optional<TaggedMetricSet> removeMetrics(String _safeTagName, String _safeTagValue) {
        throw new UnsupportedOperationException(
                "Removal of an entire TaggedMetricSet is not supported, please interact with the delegate directly");
    }

    /**
     * .
     * @deprecated not implemented
     */
    @Deprecated
    @Override
    public boolean removeMetrics(String _safeTagName, String _safeTagValue, TaggedMetricSet _metrics) {
        throw new UnsupportedOperationException(
                "Removal of an entire TaggedMetricSet is not supported, please interact with the delegate directly");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AugmentedTaggedMetricRegistry that = (AugmentedTaggedMetricRegistry) obj;
        return delegate.equals(that.delegate) && tagName.equals(that.tagName) && tagValue.equals(that.tagValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, tagName, tagValue);
    }

    @Override
    public String toString() {
        return "AugmentedTaggedMetricRegistry{tagName='" + tagName + "', tagValue='" + tagValue + "', delegate="
                + delegate + '}';
    }
}
