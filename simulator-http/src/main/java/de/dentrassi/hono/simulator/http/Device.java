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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Register;
import okhttp3.Credentials;
import okhttp3.HttpUrl;

public abstract class Device {

    private static final Logger logger = LoggerFactory.getLogger(Device.class);

    protected static final String HONO_HTTP_PROTO = System.getenv("HONO_HTTP_PROTO");
    protected static final String HONO_HTTP_HOST = System.getenv("HONO_HTTP_HOST");
    protected static final String HONO_HTTP_PORT = System.getenv("HONO_HTTP_PORT");
    protected static final HttpUrl HONO_HTTP_URL;

    private static final String METHOD = System.getenv().get("HTTP_METHOD");

    protected static final boolean AUTO_REGISTER = Boolean
            .parseBoolean(System.getenv().getOrDefault("AUTO_REGISTER", "true"));

    protected static final boolean NOAUTH = Boolean.parseBoolean(System.getenv().getOrDefault("HTTP_NOAUTH", "false"));

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
    }

    protected final String auth;

    protected final Register register;

    protected final String user;

    protected final String deviceId;

    protected final String password;

    protected final String tenant;

    protected final Statistics telemetryStatistics;

    protected final Statistics eventStatistics;

    protected final String method;

    public Device(final String user, final String deviceId, final String tenant, final String password,
            final Register register, final Statistics telemetryStatistics, final Statistics eventStatistics) {

        this.register = register;
        this.user = user;
        this.deviceId = deviceId;
        this.tenant = tenant;
        this.password = password;
        this.telemetryStatistics = telemetryStatistics;
        this.eventStatistics = eventStatistics;

        this.auth = Credentials.basic(user + "@" + tenant, password);

        this.method = METHOD != null ? METHOD : "PUT";
    }

    protected HttpUrl createUrl(final String type) {
        if ("POST".equals(this.method)) {
            return createPostUrl(type);
        } else {
            return createPutUrl(type);
        }
    }

    protected HttpUrl createPostUrl(final String type) {
        if (HONO_HTTP_URL == null) {
            return null;
        }

        return HONO_HTTP_URL.resolve("/" + type);
    }

    protected HttpUrl createPutUrl(final String type) {
        if (HONO_HTTP_URL == null) {
            return null;
        }

        return HONO_HTTP_URL.newBuilder()
                .addPathSegment(type)
                .addPathSegment(this.tenant)
                .addPathSegment(this.deviceId)
                .build();
    }

    public void register() throws Exception {
        if (shouldRegister()) {
            this.register.device(this.deviceId, this.user, this.password);
        }
    }

    protected abstract ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickTelemetryProvider();

    protected abstract ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickEventProvider();

    public CompletableFuture<?> tickTelemetry() {
        return tick(this.telemetryStatistics, () -> tickTelemetryProvider().apply(this.telemetryStatistics));
    }

    public CompletableFuture<?> tickEvent() {
        return tick(this.eventStatistics, () -> tickEventProvider().apply(this.eventStatistics));
    }

    protected CompletableFuture<?> tick(final Statistics statistics,
            final ThrowingSupplier<CompletableFuture<?>, Exception> runnable) {

        if (HONO_HTTP_URL == null) {
            return CompletableFuture.completedFuture(null);
        }

        statistics.sent();
        final Instant start = Instant.now();

        final CompletableFuture<?> future;

        try {
            future = runnable.get();
        } catch (final Exception e) {
            statistics.failed();
            return CompletableFuture.completedFuture(null);
        }

        return future.handle((r, ex) -> {

            if (ex != null) {
                statistics.failed();
                logger.debug("Failed to publish", ex);
            }

            final Duration dur = Duration.between(start, Instant.now());
            statistics.duration(dur);

            return null;
        });
    }

    protected void handleSuccess(final Statistics statistics) {
        statistics.success();
    }

    protected void handleException(final Throwable e, final Statistics statistics) {
        statistics.failed();
    }

    protected void handleFailure(final int code, final Statistics statistics) {
        statistics.failed();
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

    protected void handleResponse(final int code, final Statistics statistics) {
        if (code < 200 || code > 299) {
            handleFailure(code, statistics);
        } else {
            handleSuccess(statistics);
        }

    }
}
