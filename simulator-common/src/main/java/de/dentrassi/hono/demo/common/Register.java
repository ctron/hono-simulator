/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.demo.common;

import static java.lang.System.getenv;
import static java.util.Collections.singletonMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dentrassi.hono.demo.common.AddCredentials.Secret;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Register {

    private static final HttpUrl REGISTRATION_URL;
    private static final HttpUrl CREDENTIALS_URL;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static boolean shouldRegister() {
        return REGISTRATION_URL != null;
    }

    static {

        String devRegUrl = getenv("DEVICE_REGISTRY_URL");
        if (devRegUrl == null) {
            final String regHost = getenv("HONO_SERVICE_DEVICE_REGISTRY_SERVICE_HOST");
            final String regPort = getenv("HONO_SERVICE_DEVICE_REGISTRY_SERVICE_PORT_HTTP");
            if (regHost != null) {
                devRegUrl = "http://" + regHost;
                if (regPort != null && !regPort.isEmpty()) {
                    devRegUrl += ":" + regPort;
                }
            }
        }

        if (devRegUrl != null && !devRegUrl.isEmpty()) {
            final HttpUrl devReg = HttpUrl.parse(devRegUrl);
            REGISTRATION_URL = devReg.resolve("/registration/");
            CREDENTIALS_URL = devReg.resolve("/credentials/");
        } else {
            REGISTRATION_URL = null;
            CREDENTIALS_URL = null;
        }

        System.out.format("Registration: %s%n", REGISTRATION_URL);
        System.out.format("Credentials: %s%n", CREDENTIALS_URL);
    }

    private static final Logger logger = LoggerFactory.getLogger(Register.class);

    private static final MediaType MT_JSON = MediaType.parse("application/json");

    private final OkHttpClient http;
    private final String tenantId;

    public Register(final OkHttpClient http, final String tenantId) {
        this.http = http;
        this.tenantId = tenantId;
    }

    public void device(final String deviceId, final String username,
            final String password)
            throws Exception {

        try (final Response getDevice = this.http.newCall(new Request.Builder()
                .url(
                        REGISTRATION_URL
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
                                REGISTRATION_URL
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
                        CREDENTIALS_URL
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
                add.getSecrets().add(Secret.sha512(password));

                try (final Response newUser = this.http.newCall(new Request.Builder()
                        .url(
                                CREDENTIALS_URL
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

    private static String encode(final Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
