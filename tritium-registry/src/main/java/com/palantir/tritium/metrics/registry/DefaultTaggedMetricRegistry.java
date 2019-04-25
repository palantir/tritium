/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.google.auto.service.AutoService;

@AutoService(TaggedMetricRegistry.class)
public final class DefaultTaggedMetricRegistry extends AbstractTaggedMetricRegistry {

    public DefaultTaggedMetricRegistry() {
        super(ExponentiallyDecayingReservoir::new);
    }

    /**
     * Get the global default {@link TaggedMetricRegistry}.
     * @deprecated inline this method
     */
    @SuppressWarnings("unused") // public API
    @Deprecated
    public static TaggedMetricRegistry getDefault() {
        return SharedTaggedMetricRegistries.getDefault();
    }

}
