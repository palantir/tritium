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

package com.palantir.tritium.event;

import java.util.function.BooleanSupplier;

/**
 * Methods for interacting with instrumentation system properties.
 * @deprecated use {@link com.palantir.tritium.v1.core.event.InstrumentationProperties}
 */
@Deprecated // remove post 1.0
public final class InstrumentationProperties {
    private InstrumentationProperties() {}

    @SuppressWarnings({"deprecation", "UnnecessarilyFullyQualified"}) // backward-compatibility return type for now
    public static com.palantir.tritium.api.functions.BooleanSupplier getSystemPropertySupplier(String name) {
        BooleanSupplier systemPropertySupplier =
                com.palantir.tritium.v1.core.event.InstrumentationProperties.getSystemPropertySupplier(name);
        return systemPropertySupplier::getAsBoolean;
    }

    @SuppressWarnings("WeakerAccess") // public API
    public static boolean isSpecificEnabled(String name) {
        return com.palantir.tritium.v1.core.event.InstrumentationProperties.isSpecificallyEnabled(name);
    }

    @SuppressWarnings("WeakerAccess") // public API
    public static boolean isSpecificEnabled(String name, boolean defaultValue) {
        return com.palantir.tritium.v1.core.event.InstrumentationProperties.isSpecificallyEnabled(name, defaultValue);
    }

    @SuppressWarnings("WeakerAccess") // public API
    public static boolean isGloballyEnabled() {
        return com.palantir.tritium.v1.core.event.InstrumentationProperties.isGloballyEnabled();
    }

    /**
     * Reload the instrumentation properties.
     *
     * <p>Note this should only be used for testing purposes when manipulating system properties at runtime.
     */
    public static void reload() {
        com.palantir.tritium.v1.core.event.InstrumentationProperties.reload();
    }
}
