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

import static com.palantir.logsafe.Preconditions.checkArgument;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.palantir.logsafe.SafeArg;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MultiTaggedMetricRegistryListener implements TaggedMetricRegistryListener {
    private static final Logger log = LoggerFactory.getLogger(MultiTaggedMetricRegistryListener.class);

    private final List<TaggedMetricRegistryListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(TaggedMetricRegistryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TaggedMetricRegistryListener listener) {
        checkArgument(
                listeners.remove(listener),
                "Listener wasn't registered on the metric registry",
                SafeArg.of("listener", listener));
    }

    @Override
    public void onGaugeAdded(MetricName name, Gauge<?> gauge) {
        notifyAll(listener -> listener.onGaugeAdded(name, gauge));
    }

    @Override
    public void onGaugeRemoved(MetricName name) {
        notifyAll(listener -> listener.onGaugeRemoved(name));
    }

    @Override
    public void onCounterAdded(MetricName name, Counter counter) {
        notifyAll(listener -> listener.onCounterAdded(name, counter));
    }

    @Override
    public void onCounterRemoved(MetricName name) {
        notifyAll(listener -> listener.onCounterRemoved(name));
    }

    @Override
    public void onHistogramAdded(MetricName name, Histogram histogram) {
        notifyAll(listener -> listener.onHistogramAdded(name, histogram));
    }

    @Override
    public void onHistogramRemoved(MetricName name) {
        notifyAll(listener -> listener.onHistogramRemoved(name));
    }

    @Override
    public void onMeterAdded(MetricName name, Meter meter) {
        notifyAll(listener -> listener.onMeterAdded(name, meter));
    }

    @Override
    public void onMeterRemoved(MetricName name) {
        notifyAll(listener -> listener.onMeterRemoved(name));
    }

    @Override
    public void onTimerAdded(MetricName name, Timer timer) {
        notifyAll(listener -> listener.onTimerAdded(name, timer));

    }

    @Override
    public void onTimerRemoved(MetricName name) {
        notifyAll(listener -> listener.onTimerRemoved(name));
    }

    private void notifyAll(Consumer<TaggedMetricRegistryListener> notifier) {
        listeners.forEach(listener -> {
            try {
                notifier.accept(listener);
            } catch (Exception e) {
                log.error("Failed to notify listener metric change", e);
            }
        });
    }
}
