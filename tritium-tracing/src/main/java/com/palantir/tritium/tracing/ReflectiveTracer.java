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

package com.palantir.tritium.tracing;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import java.lang.reflect.Method;

final class ReflectiveTracer implements Tracer {

    private final Method startSpanMethod;
    private final Method completeSpanMethod;

    ReflectiveTracer(Method startSpanMethod, Method completeSpanMethod) {
        this.startSpanMethod = Preconditions.checkNotNull(startSpanMethod, "startSpanMethod");
        this.completeSpanMethod = Preconditions.checkNotNull(completeSpanMethod, "completeSpanMethod");
    }

    @Override
    public void startSpan(String operationName) {
        try {
            startSpanMethod.invoke(null, operationName);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause();
            Throwables.throwIfUnchecked(cause);
            throw new RuntimeException(cause);
        }
    }

    @Override
    public void completeSpan() {
        try {
            completeSpanMethod.invoke(null);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause();
            Throwables.throwIfUnchecked(cause);
            throw new RuntimeException(cause);
        }
    }

}
