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

import com.palantir.tritium.event.metrics.annotations.AnnotationHelper;
import com.palantir.tritium.event.metrics.annotations.MetricGroup;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway") // implicitly testing null handling
public class AnnotationHelperTest {

    @MetricGroup("DEFAULT")
    public interface TestSuperInterface {

        @MetricGroup("ONE")
        void method();

        @MetricGroup("OVERLOAD")
        void method(String arg);

        @MetricGroup("TWO")
        void hasParams(String arg);

        @MetricGroup("VARGS")
        void vargMethod(String... vargs);

        void otherMethod();
    }

    public interface TestOverrideInterface extends TestSuperInterface {
        @Override
        @MetricGroup("OVERRIDE")
        void method();
    }

    @Test
    public void testParentInterfaceAnnotations() throws NoSuchMethodException {
        TestSuperInterface impl = mock(TestSuperInterface.class);

        //discovery annotation on parent class
        MetricGroup cls = AnnotationHelper.getSuperTypeAnnotation(impl.getClass(), MetricGroup.class);
        assertThat(cls).isNotNull().extracting(MetricGroup::value).isEqualTo("DEFAULT");

        //find annotation by class method
        MetricGroup met = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class,
                impl.getClass(),
                AnnotationHelper.MethodSignature.of(impl.getClass().getMethod("method")));
        assertThat(met).isNotNull().extracting(MetricGroup::value).isEqualTo("ONE");

        //find annotation by string descriptor
        MetricGroup descriptor = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class,
                impl.getClass(),
                AnnotationHelper.MethodSignature.of("method"));
        assertThat(descriptor).isNotNull().extracting(MetricGroup::value).isEqualTo("ONE");

        //validate overloaded methods
        MetricGroup overload = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class,
                impl.getClass(),
                AnnotationHelper.MethodSignature.of("method", String.class));
        assertThat(overload).isNotNull().extracting(MetricGroup::value).isEqualTo("OVERLOAD");

        //return null if annotation does not exist
        MetricGroup noAnnotation = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class,
                impl.getClass(),
                AnnotationHelper.MethodSignature.of(impl.getClass().getMethod("otherMethod")));
        assertThat(noAnnotation).isNull();

        //validate method matching with parameters
        MetricGroup clsParams = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class,
                impl.getClass(),
                AnnotationHelper.MethodSignature.of(impl.getClass().getMethod("hasParams", String.class)));
        assertThat(clsParams).isNotNull().extracting(MetricGroup::value).isEqualTo("TWO");

        //validate signature matching with parameters
        MetricGroup sigParams = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class,
                impl.getClass(),
                AnnotationHelper.MethodSignature.of("hasParams", String.class));
        assertThat(sigParams).isNotNull().extracting(MetricGroup::value).isEqualTo("TWO");

        //return null if method does not exist
        MetricGroup noMethod = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class,
                impl.getClass(),
                AnnotationHelper.MethodSignature.of("noMethod"));
        assertThat(noMethod).isNull();
    }

    @Test
    public void testMethodSignatureEquality() throws NoSuchMethodException {
        assertThat(AnnotationHelper.MethodSignature.of(
                TestSuperInterface.class.getMethod("method")))
                .isEqualTo(AnnotationHelper.MethodSignature.of("method"));

        assertThat(AnnotationHelper.MethodSignature.of(
                TestSuperInterface.class.getMethod("hasParams", String.class)))
                .isEqualTo(AnnotationHelper.MethodSignature.of("hasParams", String.class));
    }

    @Test
    public void testVargVariants() throws NoSuchMethodException {
        TestSuperInterface impl = mock(TestSuperInterface.class);
        AnnotationHelper.MethodSignature vargSig = AnnotationHelper.MethodSignature.of("vargMethod", String[].class);

        //validate signature matching with vargs
        MetricGroup vargParams = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class,
                impl.getClass(),
                vargSig);

        assertThat(vargParams).isNotNull().extracting(MetricGroup::value).isEqualTo("VARGS");

        assertThat(vargSig).isEqualTo(
                AnnotationHelper.MethodSignature.of(impl.getClass().getMethod("vargMethod", String[].class)));
    }

    @Test
    public void testOverrideInterface() {
        TestOverrideInterface impl = mock(TestOverrideInterface.class);

        //validate signature matching with vargs
        MetricGroup override = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class,
                impl.getClass(),
                AnnotationHelper.MethodSignature.of("method"));

        assertThat(override).isNotNull().extracting(MetricGroup::value).isEqualTo("OVERRIDE");
    }
}
