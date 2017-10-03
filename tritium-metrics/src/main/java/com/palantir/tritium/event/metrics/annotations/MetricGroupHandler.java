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

package com.palantir.tritium.event.metrics.annotations;

import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.api.event.InvocationContext;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

public final class MetricGroupHandler {

    private final Map<AnnotationHelper.MethodSignature, String> metricGroups;
    private final MetricRegistry metricRegistry;
    private final String serviceMetricName;
    @Nullable private final String globalPrefix;

    private MetricGroupHandler(MetricRegistry metricRegistry,
            Map<AnnotationHelper.MethodSignature, String> metricGroups, String serviceMetricName, String globalPrefix) {
        this.metricRegistry = checkNotNull(metricRegistry);
        this.metricGroups = checkNotNull(metricGroups);
        this.serviceMetricName = checkNotNull(serviceMetricName);
        this.globalPrefix = globalPrefix;
    }

    public void onSuccess(InvocationContext context) {
        long nanos = context.markCompleteTimeNanos() - context.getStartTimeNanos();

        String metricName = metricGroups.get(AnnotationHelper.MethodSignature.of(context.getMethod()));
        if (metricName != null) {
            metricRegistry.timer(MetricRegistry.name(serviceMetricName, metricName))
                    .update(nanos, TimeUnit.NANOSECONDS);

            if (globalPrefix != null) {
                metricRegistry.timer(MetricRegistry.name(globalPrefix, metricName))
                        .update(nanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    public void onFailure(InvocationContext context) {
        long nanos = context.markCompleteTimeNanos() - context.getStartTimeNanos();
        String metricName = metricGroups.get(AnnotationHelper.MethodSignature.of(context.getMethod()));

        if (metricName != null) {
            metricRegistry.timer(MetricRegistry.name(serviceMetricName, metricName, "failures"))
                    .update(nanos, TimeUnit.NANOSECONDS);

            if (globalPrefix != null) {
                metricRegistry.timer(MetricRegistry.name(globalPrefix, metricName, "failures"))
                        .update(nanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    public static MetricGroupHandler of(MetricRegistry metricRegistry,
            Class<?> serviceClass, String serviceMetricName, @Nullable String globalPrefix) {

        ImmutableMap.Builder<AnnotationHelper.MethodSignature, String> builder = ImmutableMap.builder();

        MetricGroup classGroup = AnnotationHelper.getSuperTypeAnnotation(serviceClass, MetricGroup.class);

        for (Method method : serviceClass.getMethods()) {
            AnnotationHelper.MethodSignature sig = AnnotationHelper.MethodSignature.of(method);
            MetricGroup methodGroup = AnnotationHelper.getMethodAnnotation(MetricGroup.class, serviceClass, sig);

            if (methodGroup != null) {
                builder.put(sig, methodGroup.value());
            } else if (classGroup != null) {
                builder.put(sig, classGroup.value());
            }
        }

        return new MetricGroupHandler(metricRegistry, builder.build(), serviceMetricName, globalPrefix);
    }

}
