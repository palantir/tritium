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

package com.palantir.tritium.api.event;

/**
 * Filter predicate to determine whether a method invocation should be instrumented.
 * @deprecated use #com.palantir.tritium.v1.api.event.InstrumentationFilter
 */
@Deprecated // remove post 1.0
@FunctionalInterface
public interface InstrumentationFilter extends com.palantir.tritium.v1.api.event.InstrumentationFilter {}
