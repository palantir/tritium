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

package com.palantir.tritium.event.metrics;

import com.palantir.tritium.api.event.InvocationContext;
import java.util.Map;
import java.util.function.Function;

/**
 * Defines what tags are determined for the instrumented invocation context.
 */
@FunctionalInterface
public interface InvocationContextTagsFunction extends Function<InvocationContext, Map<String, String>> {

    /**
     * Return a non-null map of tags to include, possibly derived from the specified invocation context.
     *
     * @param context invocation context
     * @return map of tags
     */
    @Override
    Map<String, String> apply(InvocationContext context);

}
