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

package com.palantir.tritium.proxy.subpackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.proxy.Instrumentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
class ByteBuddyInstrumentationPackageAccessTest {
    @SystemStub
    private SystemProperties systemProperties;

    @BeforeEach
    void before() {
        systemProperties.set("instrument.dynamic-proxy", "false");
        InstrumentationProperties.reload();
    }

    @Test
    void testPackagePrivateInterface() {
        PackagePrivateInterface raw = () -> "value";
        assertThat(Instrumentation.builder(PackagePrivateInterface.class, raw)
                        .withPerformanceTraceLogging()
                        .build())
                .describedAs("inaccessible interfaces cannot be implemented, the original should be returned")
                .isSameAs(raw);
    }

    @Test
    void testPublicInterfaceEnclosedInPackagePrivateClass() {
        PackagePrivateClass.PublicInterface raw = () -> "value";
        assertThat(Instrumentation.builder(PackagePrivateClass.PublicInterface.class, raw)
                        .withPerformanceTraceLogging()
                        .build())
                .describedAs("inaccessible interfaces cannot be implemented, the original should be returned")
                .isSameAs(raw);
    }

    @Test
    void testAlsoImplementsInaccessibleInterface() {
        AlsoImplementsInaccessibleInterface raw = new AlsoImplementsInaccessibleInterface();
        Runnable instrumented = Instrumentation.builder(Runnable.class, raw)
                .withPerformanceTraceLogging()
                .build();
        assertThat(raw)
                .isInstanceOf(PackagePrivateInterface.class)
                .isInstanceOf(PackagePrivateClass.PublicInterface.class);
        assertThat(instrumented)
                .isNotInstanceOfAny(PackagePrivateInterface.class, PackagePrivateClass.PublicInterface.class);
    }

    private static final class AlsoImplementsInaccessibleInterface
            implements Runnable, PackagePrivateInterface, PackagePrivateClass.PublicInterface {

        @Override
        public String getValue() {
            return "Hello, World";
        }

        @Override
        public void run() {
            // nop
        }
    }
}
