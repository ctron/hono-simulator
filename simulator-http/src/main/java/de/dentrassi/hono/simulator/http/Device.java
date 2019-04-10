/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat Inc and others.
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
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.ProducerConfig;
import de.dentrassi.hono.demo.common.Register;
import io.glutamate.lang.Environment;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;

public class Device {

    private static final Random JITTER = new Random();

    private static final Logger logger = LoggerFactory.getLogger(Device.class);

    private static final boolean AUTO_REGISTER = Environment.getAs("AUTO_REGISTER", true, Boolean::parseBoolean);

    private final Vertx vertx;
    private final ProducerConfig config;

    private final Register register;

    private final String user;

    private final String deviceId;

    private final String password;

    private final Statistics statistics;

    private final Supplier<HttpRequest<?>> requestProvider;

    private final Payload payload;

    public Device(final Vertx vertx, final Supplier<HttpRequest<?>> requestProvider, final ProducerConfig config,
            final String user, final String deviceId, final String tenant, final String password,
            final Register register, final Payload payload, final Statistics statistics) {

        Objects.requireNonNull(requestProvider);
        Objects.requireNonNull(payload);

        this.vertx = vertx;
        this.requestProvider = requestProvider;
        this.config = config;

        this.user = user;
        this.deviceId = deviceId;
        this.password = password;
        this.statistics = statistics;
        this.register = register;
        this.payload = payload;

    }

    public void start() {
        final var initialDelay = JITTER.nextInt((int) config.getPeriod().toMillis());
        schedule(initialDelay);
    }

    private void schedule(final long delay) {
        this.vertx.setTimer(delay, v -> tick());
    }

    protected Future<?> register() throws Exception {

        if (shouldRegister()) {

            final Future<?> f = Future.future();
            this.vertx.executeBlocking(future -> {
                try {
                    this.register.device(this.deviceId, this.user, this.password);
                    future.complete();
                } catch (final Exception e) {
                    future.fail(e);
                }
            }, f);
            return f;

        } else {
            return Future.succeededFuture();
        }
    }

    protected void tick() {

        final var start = Instant.now();

        this.statistics.scheduled();

        this.requestProvider
                .get()
                .sendBuffer(this.payload.getBuffer(), ar -> {

                    response(start, ar)
                            .setHandler(v -> scheduleNext(start.plus(config.getPeriod())));

                });

    }

    private <T> Future<?> response(final Instant start, final AsyncResult<HttpResponse<T>> result) {
        if (result.succeeded()) {
            return handleResponse(start, result.result());
        } else {
            handleException(result.cause());
            return Future.succeededFuture();
        }
    }

    private void scheduleNext(final Instant next) {
        var delay = Duration.between(Instant.now(), next).toMillis();
        if (delay <= 0) {
            delay = 1;
        }
        schedule(delay);
    }

    protected void handleSuccess(final Instant start) {
        this.statistics.success();
        this.statistics.duration(Duration.between(start, Instant.now()));
    }

    protected void handleException(final Throwable e) {
        this.statistics.failed();
        this.statistics.error(0);
    }

    protected Future<?> handleFailure(final HttpResponse<?> response) {
        this.statistics.failed();
        this.statistics.error(response.statusCode());

        if (logger.isDebugEnabled()) {
            logger.debug("handleFailure - code: {}, body: {}", response.statusCode(), response.bodyAsString());
        }

        try {
            switch (response.statusCode()) {
            case 401:
            case 403: //$FALL-THROUGH$
                if (AUTO_REGISTER && shouldRegister()) {
                    return register();
                }
                break;
            }
        } catch (final Exception e) {
            logger.warn("Failed to handle failure", e);
        }

        return Future.succeededFuture();
    }

    protected Future<?> handleResponse(final Instant start, final HttpResponse<?> response) {

        final int code = response.statusCode();
        if (code < 200 || code > 299) {
            return handleFailure(response);
        } else {
            handleSuccess(start);
            return Future.succeededFuture();
        }

    }

}
