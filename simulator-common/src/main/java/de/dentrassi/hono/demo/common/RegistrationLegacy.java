/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat Inc and others.
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
import static java.util.Collections.singletonMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegistrationLegacy extends AbstractRegistration {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationLegacy.class);

    private final HttpUrl registrationUrl;
    private final HttpUrl credentialsUrl;

    public RegistrationLegacy(final String tenantId, final HttpUrl deviceRegistryUrl) {
        super(tenantId);

        this.registrationUrl = deviceRegistryUrl.resolve("registration/");
        this.credentialsUrl = deviceRegistryUrl.resolve("credentials/");
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
                                .resolve(this.tenantId + "/")
                                .resolve(deviceId))
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
                                        .resolve(this.tenantId))
                        .post(RequestBody.create(MT_JSON, encode(singletonMap("device-id", deviceId))))
                        .build()).execute()) {

                    logger.debug("Registration URL - post: {}", newDevice.request().url());

                    if (!newDevice.isSuccessful()) {
                        throw new RuntimeException(
                                "Unable to register device: " + deviceId + " -> " + newDevice.code() + ": "
                                        + newDevice.message());
                    }
                    logger.info("Registered device: {}", deviceId);
                }
            }
        }

        try (Response getCredentials = this.http.newCall(new Request.Builder()
                .url(
                        this.credentialsUrl
                                .resolve(this.tenantId + "/")
                                .resolve(username + "/")
                                .resolve("hashed-password"))
                .get()
                .build())
                .execute()) {

            logger.debug("Credentials URL - get: {}", getCredentials.request().url());

            if (getCredentials.isSuccessful()) {

                logger.debug("User {} already registered", username);

            } else {

                final AddCredentials add = new AddCredentials();
                add.setAuthId(username);
                add.setDeviceId(deviceId);
                add.setType("hashed-password");
                add.getSecrets().add(AddCredentials.sha512(password));

                try (final Response newUser = this.http.newCall(new Request.Builder()
                        .url(
                                this.credentialsUrl
                                        .resolve(this.tenantId))
                        .post(RequestBody.create(MT_JSON, encode(add)))
                        .build()).execute()) {

                    logger.debug("Credentials URL - get: {}", newUser.request().url());

                    if (!newUser.isSuccessful()) {
                        throw new RuntimeException(
                                "Unable to register user: " + username + " -> " + newUser.code() + ": "
                                        + newUser.message());
                    }

                    logger.info("Registered user {}", username);

                }
            }
        }

    }

}
