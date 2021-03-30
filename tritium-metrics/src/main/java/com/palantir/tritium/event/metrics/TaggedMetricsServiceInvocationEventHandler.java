/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.event.metrics;

import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.v1.core.event.InstrumentationProperties;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An implementation of {@link com.palantir.tritium.event.AbstractInvocationEventHandler}
 * whose purpose is to provide tagged metrics for classes which look like services.
 *
 * <p>Specifically, this class will generate metrics with the following parameters:
 *
 * <ul>
 *   <li>Metric Name: the service name supplied to the constructor
 *   <li>Tag - service-name: The simple name of the invoked class
 *   <li>Tag - endpoint: The name of the method that was invoked
 *   <li>Tag - cause: When an error is hit, this will be filled with the full class name of the cause.
 * </ul>
 * @deprecated use {@link com.palantir.tritium.v1.metrics.event.TaggedMetricsServiceInvocationEventHandler}
 */
@Deprecated // remove post 1.0
@SuppressWarnings("UnnecessarilyFullyQualified") // deprecated types
public class TaggedMetricsServiceInvocationEventHandler
        extends com.palantir.tritium.event.AbstractInvocationEventHandler<
                com.palantir.tritium.event.InvocationContext> {

    private final com.palantir.tritium.v1.metrics.event.TaggedMetricsServiceInvocationEventHandler delegate;

    public TaggedMetricsServiceInvocationEventHandler(TaggedMetricRegistry taggedMetricRegistry, String serviceName) {
        super(InstrumentationProperties.getSystemPropertySupplier(serviceName));
        delegate = new com.palantir.tritium.v1.metrics.event.TaggedMetricsServiceInvocationEventHandler(
                taggedMetricRegistry, serviceName);
    }

    @Override
    public final com.palantir.tritium.event.InvocationContext preInvocation(
            @Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        return com.palantir.tritium.event.DefaultInvocationContext.wrap(delegate.preInvocation(instance, method, args));
    }

    @Override
    public final void onSuccess(
            @Nullable com.palantir.tritium.v1.api.event.InvocationContext context, @Nullable Object result) {
        delegate.onSuccess(context, result);
    }

    @Override
    public final void onFailure(
            @Nullable com.palantir.tritium.v1.api.event.InvocationContext context, @Nonnull Throwable cause) {
        delegate.onFailure(context, cause);
    }
}
