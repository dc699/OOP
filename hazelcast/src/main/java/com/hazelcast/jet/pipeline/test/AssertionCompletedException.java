/*
 * Copyright (c) 2008-2024, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.pipeline.test;

import com.hazelcast.jet.Job;
import com.hazelcast.jet.annotation.EvolvingApi;

import java.io.Serial;

/**
 * An exception which indicates that an assertion passed successfully. It is
 * used to terminate the streaming job. If caught from the {@link Job#join()}
 * method, it can be ignored.
 *
 * @since Jet 3.2
 */
@EvolvingApi
public final class AssertionCompletedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception
     */
    public AssertionCompletedException() {
    }

    /**
     * Creates the exception with the given message
     */
    public AssertionCompletedException(String message) {
        super(message);
    }

    /**
     * Creates the exception with the given message and cause
     */
    public AssertionCompletedException(String message, Throwable cause) {
        super(message, cause);
    }
}
