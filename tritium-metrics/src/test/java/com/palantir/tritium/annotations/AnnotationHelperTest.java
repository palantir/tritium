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

package com.palantir.tritium.annotations;

import com.palantir.tritium.event.metrics.annotations.AnnotationHelper;
import com.palantir.tritium.event.metrics.annotations.MetricGroup;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class AnnotationHelperTest {

    public interface TestSuperInterface {

        @MetricGroup("ONE")
        void method();
    }

    @Test
    public void testParentInterfaceAnnotation() throws NoSuchMethodException {
        TestSuperInterface impl = mock(TestSuperInterface.class);

        MetricGroup met = AnnotationHelper.getMethodAnnotation(
                MetricGroup.class, impl.getClass(), impl.getClass().getMethod("method"));

        assertThat(met.value()).isEqualTo("ONE");
    }
}
