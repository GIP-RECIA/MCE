/*
 * Copyright (C) 2023 GIP-RECIA, Inc.
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
package fr.recia.mce.api.escomceapi.services.exception;

import java.util.List;

public class ErrorResponse {

    private final String code;
    private final String message;
    private final List<String> details;
    private final String charteUrl;
    private final Long retryAfterSeconds;

    public ErrorResponse(String code, String message, List<String> details) {
        this(code, message, details, null, null);
    }

    public ErrorResponse(String code, String message) {
        this(code, message, null, null, null);
    }

    public ErrorResponse(String code, String message, List<String> details, String charteUrl) {
        this(code, message, details, charteUrl, null);
    }

    public ErrorResponse(String code, String message, Long retryAfterSeconds) {
        this(code, message, null, null, retryAfterSeconds);
    }

    public ErrorResponse(String code, String message, List<String> details, String charteUrl, Long retryAfterSeconds) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.charteUrl = charteUrl;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getDetails() {
        return details;
    }

    public String getCharteUrl() {
        return charteUrl;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
