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

package com.palantir.tritium.metrics.tags;

/**
 * The standard set of metric tags as well as constant tag values.
 */
public final class MetricTags {

    private MetricTags() {}

    public static final String SERVICE_NAME = "service-name";
    public static final String ENDPOINT = "endpoint";
    public static final String USER_AGENT = "user-agent";

    /**
     * Tags for response code families.
     */
    public static final String RESPONSE_FAMILY = "family";
    public static final String RESPONSE_1XX = "1xx";
    public static final String RESPONSE_2XX = "2xx";
    public static final String RESPONSE_3XX = "3xx";
    public static final String RESPONSE_4XX = "4xx";
    public static final String RESPONSE_5XX = "5xx";
    public static final String RESPONSE_OTHER = "other";
}
