/*******************************************************************************
 * Copyright (c) 2017, 2020 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.demo.common;

import static io.vertx.core.json.Json.encode;

import java.util.Collections;

import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HttpHeaders;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegistrationV1 extends AbstractRegistration {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationV1.class);

    private final HttpUrl registrationUrl;
    private final HttpUrl credentialsUrl;

    private final String authzString;

    public RegistrationV1(final String tenantId, String token, final HttpUrl deviceRegistryUrl) {
        super(tenantId);

        if (token != null && !token.isBlank()) {
            this.authzString = String.format("Bearer %s", token);
        } else {
            this.authzString = null;
        }

        this.registrationUrl = deviceRegistryUrl
                .newBuilder()
                .addPathSegment("devices")
                .build();
        this.credentialsUrl = deviceRegistryUrl
                .newBuilder()
                .addPathSegment("credentials")
                .build();
    }

    @Override
    public void device(final String deviceId, final String username,
            final String password) throws Exception {

        if (this.registrationUrl == null) {
            throw new IllegalStateException("'DEVICE_REGISTRY_URL' is not set");
        }
        if (this.credentialsUrl == null) {
            throw new IllegalStateException("'DEVICE_REGISTRY_URL' is not set");
        }

        try (final Response getDevice = this.http.newCall(new Request.Builder()
                .url(
                        this.registrationUrl
                                .newBuilder()
                                .addPathSegment(this.tenantId)
                                .addPathSegment(deviceId)
                                .build())
                .addHeader(HttpHeaders.AUTHORIZATION, this.authzString)
                .get()
                .build()).execute()) {

            logger.debug("Registration URL - get: {}", getDevice.request().url());

            if (getDevice.isSuccessful()) {

                logger.debug("Device {} already registered", deviceId);

            } else {

                logger.debug("Failed to retrieve registration: {} {}", getDevice.code(), getDevice.message());

                try (final Response newDevice = this.http.newCall(new Request.Builder()
                        .url(
                                this.registrationUrl
                                        .newBuilder()
                                        .addPathSegment(this.tenantId)
                                        .addPathSegment(deviceId)
                                        .build())
                        .addHeader(HttpHeaders.AUTHORIZATION, this.authzString)
                        .post(RequestBody.create(MT_JSON, "{}" /* empty object */))
                        .build()).execute()) {

                    if (!newDevice.isSuccessful()) {

                        logger.info("Registration URL - post: {}", newDevice.request().url());

                        throw new RuntimeException(
                                "Unable to register device: " + deviceId + " -> " + newDevice.code() + ": "
                                        + newDevice.message());
                    }
                    logger.info("Registered device: {}", deviceId);
                }
            }
        }

        final PasswordCredential pc = new PasswordCredential();
        pc.setAuthId(username);
        pc.setSecrets(Collections.singletonList(AddCredentials.sha512(password)));

        try (Response putCredentials = this.http.newCall(new Request.Builder()
                .url(
                        this.credentialsUrl
                                .newBuilder()
                                .addPathSegment(this.tenantId)
                                .addPathSegment(deviceId)
                                .build())
                .addHeader(HttpHeaders.AUTHORIZATION, this.authzString)
                .put(RequestBody.create(MT_JSON, encode(new CommonCredential[] {pc})))
                .build())
                .execute()) {

            if (!putCredentials.isSuccessful()) {

                logger.info("Credentials URL - put: {}", putCredentials.request().url());

                throw new RuntimeException(
                        "Unable to register user: " + username + " -> " + putCredentials.code() + ": "
                                + putCredentials.message());
            }

            logger.info("Registered user {}", username);

        }

    }

}
