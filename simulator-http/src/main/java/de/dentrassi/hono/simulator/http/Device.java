/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.http;

import static de.dentrassi.hono.demo.common.Register.shouldRegister;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Register;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Device {

    private static final Logger logger = LoggerFactory.getLogger(Device.class);

    private static final MediaType JSON = MediaType.parse("application/json");

    private static final String HONO_HTTP_PROTO = System.getenv("HONO_HTTP_PROTO");
    private static final String HONO_HTTP_HOST = System.getenv("HONO_HTTP_HOST");
    private static final String HONO_HTTP_PORT = System.getenv("HONO_HTTP_PORT");
    private static final HttpUrl HONO_HTTP_URL;

    private static final boolean ASYNC = Boolean.parseBoolean(System.getenv().getOrDefault("HTTP_ASYNC", "false"));
    private static final String METHOD = System.getenv().get("HTTP_METHOD");

    private static final boolean AUTO_REGISTER = Boolean
            .parseBoolean(System.getenv().getOrDefault("AUTO_REGISTER", "true"));

    private static final boolean NOAUTH = Boolean.parseBoolean(System.getenv().getOrDefault("HTTP_NOAUTH", "false"));

    static {
        String url = System.getenv("HONO_HTTP_URL");

        if (url == null && HONO_HTTP_HOST != null && HONO_HTTP_PORT != null) {
            final String proto = HONO_HTTP_PROTO != null ? HONO_HTTP_PROTO : "http";
            url = String.format("%s://%s:%s", proto, HONO_HTTP_HOST, HONO_HTTP_PORT);
        }

        if (url != null) {
            HONO_HTTP_URL = HttpUrl.parse(url);
        } else {
            HONO_HTTP_URL = null;
        }

        System.out.println("Running Async: " + ASYNC);
    }

    private final OkHttpClient client;

    private final String auth;

    private final RequestBody body;

    private final Request telemetryRequest;
    private final Request eventRequest;

    private final Register register;

    private final String user;

    private final String deviceId;

    private final String password;

    private final String tenant;

    private final Statistics telemetryStatistics;

    private final Statistics eventStatistics;

    public Device(final String user, final String deviceId, final String tenant, final String password,
            final OkHttpClient client, final Register register, final Statistics telemetryStatistics,
            final Statistics eventStatistics) {
        this.client = client;
        this.register = register;
        this.user = user;
        this.deviceId = deviceId;
        this.tenant = tenant;
        this.password = password;
        this.auth = Credentials.basic(user + "@" + tenant, password);
        this.body = RequestBody.create(JSON, "{foo: 42}");
        this.telemetryStatistics = telemetryStatistics;
        this.eventStatistics = eventStatistics;

        if ("POST".equals(METHOD)) {
            this.telemetryRequest = createPostRequest("/telemetry");
            this.eventRequest = createPostRequest("/event");
        } else {
            this.telemetryRequest = createPutRequest("telemetry");
            this.eventRequest = createPutRequest("event");
        }
    }

    private Request createPostRequest(final String type) {

        if (HONO_HTTP_URL == null) {
            return null;
        }

        final Request.Builder builder = new Request.Builder()
                .url(HONO_HTTP_URL.resolve(type))
                .post(this.body);

        if (!NOAUTH) {
            builder.header("Authorization", this.auth);
        }

        return builder.build();
    }

    private Request createPutRequest(final String type) {
        final Request.Builder builder = new Request.Builder()
                .url(
                        HONO_HTTP_URL.newBuilder()
                                .addPathSegment(type)
                                .addPathSegment(this.tenant)
                                .addPathSegment(this.deviceId)
                                .build())
                .put(this.body);

        if (!NOAUTH) {
            builder.header("Authorization", this.auth);
        }

        return builder.build();
    }

    public void register() throws Exception {
        if (shouldRegister()) {
            this.register.device(this.deviceId, this.user, this.password);
        }
    }

    public void tickTelemetry() {
        doTick(this::createTelemetryCall, this.telemetryStatistics);
    }

    public void tickEvent() {
        doTick(this::createEventCall, this.eventStatistics);
    }

    private void doTick(final Supplier<Call> c, final Statistics s) {
        if (HONO_HTTP_URL == null) {
            return;
        }

        try {
            process(s, c);
        } catch (final Exception e) {
            logger.warn("Failed to tick", e);
        }

    }

    private void process(final Statistics statistics, final Supplier<Call> call) {
        statistics.sent();

        final Instant start = Instant.now();

        try {
            if (ASYNC) {
                publishAsync(statistics, call);
            } else {
                publishSync(statistics, call);
            }

        } catch (final Exception e) {
            statistics.failed();
            logger.debug("Failed to publish", e);
        } finally {
            final Duration dur = Duration.between(start, Instant.now());
            statistics.duration(dur);
        }
    }

    private void publishSync(final Statistics statistics, final Supplier<Call> callSupplier) throws IOException {
        try (final Response response = callSupplier.get().execute()) {
            if (response.isSuccessful()) {
                statistics.success();
                handleSuccess(response, statistics);
            } else {
                logger.trace("Result code: {}", response.code());
                statistics.failed();
                handleFailure(response, statistics);
            }
        }
    }

    private void publishAsync(final Statistics statistics, final Supplier<Call> callSupplier) {
        statistics.backlog();
        callSupplier.get().enqueue(new Callback() {

            @Override
            public void onResponse(final Call call, final Response response) throws IOException {
                statistics.backlogSent();
                if (response.isSuccessful()) {
                    statistics.success();
                    handleSuccess(response, statistics);
                } else {
                    logger.trace("Result code: {}", response.code());
                    statistics.failed();
                    handleFailure(response, statistics);
                }
                response.close();
            }

            @Override
            public void onFailure(final Call call, final IOException e) {
                statistics.backlogSent();
                statistics.failed();
                logger.debug("Failed to tick", e);
            }
        });
    }

    private Call createTelemetryCall() {
        return this.client.newCall(this.telemetryRequest);
    }

    private Call createEventCall() {
        return this.client.newCall(this.eventRequest);
    }

    protected void handleSuccess(final Response response, final Statistics statistics) {
    }

    protected void handleFailure(final Response response, final Statistics statistics) {
        final int code = response.code();

        statistics.error(code);

        try {
            switch (code) {
            case 401:
            case 403: //$FALL-THROUGH$
                if (AUTO_REGISTER && shouldRegister()) {
                    register();
                }
                break;
            }
        } catch (final Exception e) {
            logger.warn("Failed to handle failure", e);
        }
    }
}
