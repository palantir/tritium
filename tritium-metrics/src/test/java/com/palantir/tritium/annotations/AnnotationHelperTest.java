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

package com.palantir.tritium.annotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

@SuppressWarnings({
    "deprecation", // explicitly testing deprecated types
    "NullAway", // implicitly testing null handling
    "UnnecessarilyFullyQualified" // deprecated types
})
public class AnnotationHelperTest {

    @com.palantir.tritium.event.metrics.annotations.MetricGroup("DEFAULT")
    public interface TestSuperInterface {

        @com.palantir.tritium.event.metrics.annotations.MetricGroup("ONE")
        void method();

        @com.palantir.tritium.event.metrics.annotations.MetricGroup("OVERLOAD")
        void method(String arg);

        @com.palantir.tritium.event.metrics.annotations.MetricGroup("TWO")
        void hasParams(String arg);

        @com.palantir.tritium.event.metrics.annotations.MetricGroup("VARGS")
        void vargMethod(String... vargs);

        void otherMethod();
    }

    public interface TestOverrideInterface extends TestSuperInterface {
        @Override
        @com.palantir.tritium.event.metrics.annotations.MetricGroup("OVERRIDE")
        void method();
    }

    @Test
    public void testParentInterfaceAnnotations() throws NoSuchMethodException {
        TestSuperInterface impl = mock(TestSuperInterface.class);

        // discovery annotation on parent class
        com.palantir.tritium.event.metrics.annotations.MetricGroup cls =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getSuperTypeAnnotation(
                        impl.getClass(), com.palantir.tritium.event.metrics.annotations.MetricGroup.class);
        assertThat(cls)
                .isNotNull()
                .extracting(com.palantir.tritium.event.metrics.annotations.MetricGroup::value)
                .isEqualTo("DEFAULT");

        // find annotation by class method
        com.palantir.tritium.event.metrics.annotations.MetricGroup met =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getMethodAnnotation(
                        com.palantir.tritium.event.metrics.annotations.MetricGroup.class,
                        impl.getClass(),
                        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                                impl.getClass().getMethod("method")));
        assertThat(met)
                .isNotNull()
                .extracting(com.palantir.tritium.event.metrics.annotations.MetricGroup::value)
                .isEqualTo("ONE");

        // find annotation by string descriptor
        com.palantir.tritium.event.metrics.annotations.MetricGroup descriptor =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getMethodAnnotation(
                        com.palantir.tritium.event.metrics.annotations.MetricGroup.class,
                        impl.getClass(),
                        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of("method"));
        assertThat(descriptor)
                .isNotNull()
                .extracting(com.palantir.tritium.event.metrics.annotations.MetricGroup::value)
                .isEqualTo("ONE");

        // validate overloaded methods
        com.palantir.tritium.event.metrics.annotations.MetricGroup overload =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getMethodAnnotation(
                        com.palantir.tritium.event.metrics.annotations.MetricGroup.class,
                        impl.getClass(),
                        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                                "method", String.class));
        assertThat(overload)
                .isNotNull()
                .extracting(com.palantir.tritium.event.metrics.annotations.MetricGroup::value)
                .isEqualTo("OVERLOAD");

        // return null if annotation does not exist
        com.palantir.tritium.event.metrics.annotations.MetricGroup noAnnotation =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getMethodAnnotation(
                        com.palantir.tritium.event.metrics.annotations.MetricGroup.class,
                        impl.getClass(),
                        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                                impl.getClass().getMethod("otherMethod")));
        assertThat(noAnnotation).isNull();

        // validate method matching with parameters
        com.palantir.tritium.event.metrics.annotations.MetricGroup clsParams =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getMethodAnnotation(
                        com.palantir.tritium.event.metrics.annotations.MetricGroup.class,
                        impl.getClass(),
                        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                                impl.getClass().getMethod("hasParams", String.class)));
        assertThat(clsParams)
                .isNotNull()
                .extracting(com.palantir.tritium.event.metrics.annotations.MetricGroup::value)
                .isEqualTo("TWO");

        // validate signature matching with parameters
        com.palantir.tritium.event.metrics.annotations.MetricGroup sigParams =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getMethodAnnotation(
                        com.palantir.tritium.event.metrics.annotations.MetricGroup.class,
                        impl.getClass(),
                        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                                "hasParams", String.class));
        assertThat(sigParams)
                .isNotNull()
                .extracting(com.palantir.tritium.event.metrics.annotations.MetricGroup::value)
                .isEqualTo("TWO");

        // return null if method does not exist
        com.palantir.tritium.event.metrics.annotations.MetricGroup noMethod =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getMethodAnnotation(
                        com.palantir.tritium.event.metrics.annotations.MetricGroup.class,
                        impl.getClass(),
                        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of("noMethod"));
        assertThat(noMethod).isNull();
    }

    @Test
    public void testMethodSignatureEquality() throws NoSuchMethodException {
        assertThat(com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                        TestSuperInterface.class.getMethod("method")))
                .isEqualTo(
                        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of("method"));

        assertThat(com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                        TestSuperInterface.class.getMethod("hasParams", String.class)))
                .isEqualTo(com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                        "hasParams", String.class));
    }

    @Test
    public void testVargVariants() throws NoSuchMethodException {
        TestSuperInterface impl = mock(TestSuperInterface.class);
        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature vargSig =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                        "vargMethod", String[].class);

        // validate signature matching with vargs
        com.palantir.tritium.event.metrics.annotations.MetricGroup vargParams =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getMethodAnnotation(
                        com.palantir.tritium.event.metrics.annotations.MetricGroup.class, impl.getClass(), vargSig);

        assertThat(vargParams)
                .isNotNull()
                .extracting(com.palantir.tritium.event.metrics.annotations.MetricGroup::value)
                .isEqualTo("VARGS");

        assertThat(vargSig)
                .isEqualTo(com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of(
                        impl.getClass().getMethod("vargMethod", String[].class)));
    }

    @Test
    public void testOverrideInterface() {
        TestOverrideInterface impl = mock(TestOverrideInterface.class);

        // validate signature matching with vargs
        com.palantir.tritium.event.metrics.annotations.MetricGroup override =
                com.palantir.tritium.event.metrics.annotations.AnnotationHelper.getMethodAnnotation(
                        com.palantir.tritium.event.metrics.annotations.MetricGroup.class,
                        impl.getClass(),
                        com.palantir.tritium.event.metrics.annotations.AnnotationHelper.MethodSignature.of("method"));

        assertThat(override)
                .isNotNull()
                .extracting(com.palantir.tritium.event.metrics.annotations.MetricGroup::value)
                .isEqualTo("OVERRIDE");
    }
}
