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

import static com.palantir.logsafe.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.api.functions.BooleanSupplier;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstrumentationProperties {
    private static final Logger log = LoggerFactory.getLogger(InstrumentationProperties.class);

    private InstrumentationProperties() {}

    private static final String INSTRUMENT_PREFIX = "instrument";

    private static volatile Supplier<Map<String, String>> instrumentationProperties = createSupplier();

    public static BooleanSupplier getSystemPropertySupplier(String name) {
        checkArgument(!Strings.isNullOrEmpty(name), "name cannot be null or empty", SafeArg.of("name", name));
        boolean instrumentationEnabled = isGloballyEnabled() && isSpecificEnabled(name);
        return () -> instrumentationEnabled;
    }

    @SuppressWarnings("WeakerAccess") // public API
    public static boolean isSpecificEnabled(String name) {
        String qualifiedValue = instrumentationProperties().get(INSTRUMENT_PREFIX + "." + name);
        return "true".equalsIgnoreCase(qualifiedValue) || qualifiedValue == null;
    }

    @SuppressWarnings("WeakerAccess") // public API
    public static boolean isGloballyEnabled() {
        return !isGloballyDisabled();
    }

    private static boolean isGloballyDisabled() {
        return "false".equalsIgnoreCase(instrumentationProperties().get(INSTRUMENT_PREFIX));
    }

    /**
     * Reload the instrumentation properties.
     * <p>
     * Note this should only be used for testing purposes when manipulating system properties at runtime.
     */
    public static void reload() {
        instrumentationProperties = createSupplier();
    }

    private static Supplier<Map<String, String>> createSupplier() {
        return Suppliers.memoizeWithExpiration(
                InstrumentationProperties::createInstrumentationSystemProperties,
                1L, TimeUnit.MINUTES);
    }

    private static Map<String, String> instrumentationProperties() {
        return instrumentationProperties.get();
    }

    private static Map<String, String> createInstrumentationSystemProperties() {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        copySystemProperties().forEach((key, value) -> {
            if (String.valueOf(key).startsWith(INSTRUMENT_PREFIX)) {
                builder.put(String.valueOf(key), String.valueOf(value));
            }
        });
        Map<String, String> map = builder.build();
        log.debug("Reloaded instrumentation properties {}", map);
        return map;
    }

    private static Map<Object, Object> copySystemProperties() {
        /*
         * Since system properties are a hash table, they can be a point of contention
         * as all access is synchronized. We therefore take an approach of copying all the
         * keys at once, but must hold the lock on the entire Hashtable.
         */
        Properties properties = System.getProperties();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (properties) {
            return ImmutableMap.copyOf(properties);
        }
    }

}
