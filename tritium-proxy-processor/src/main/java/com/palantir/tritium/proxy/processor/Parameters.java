/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.proxy.processor;

import java.util.Set;

final class Parameters {

    /**
     * Returns {@code requestedName} unless it's included in {@code parameterNames}, in which case the {@code
     * requestedName} is modified to avoid overlapping.
     */
    static String disambiguate(String requestedName, Set<String> parameterNames) {
        if (parameterNames.contains(requestedName)) {
            return disambiguate(requestedName + '_', parameterNames);
        }
        return requestedName;
    }

    private Parameters() {}
}
