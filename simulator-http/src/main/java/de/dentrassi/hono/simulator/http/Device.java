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
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import io.glutamate.lang.Environment;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;

public class Device {

    private static final Logger logger = LoggerFactory.getLogger(Device.class);

    private static final boolean AUTO_REGISTER = Environment.getAs("AUTO_REGISTER", true, Boolean::parseBoolean);

    private final Register register;

    private final String user;

    private final String deviceId;

    private final String password;

    private final Statistics statistics;

    private final Supplier<HttpRequest<?>> requestProvider;

    private final Payload payload;

    public Device(final Supplier<HttpRequest<?>> requestProvider, final String user, final String deviceId,
            final String tenant, final String password, final Register register, final Payload payload,
            final Statistics statistics) {

        Objects.requireNonNull(requestProvider);
        Objects.requireNonNull(payload);

        this.register = register;
        this.user = user;
        this.deviceId = deviceId;
        this.password = password;
        this.statistics = statistics;

        this.requestProvider = requestProvider;
        this.payload = payload;

    }

    public void register() throws Exception {
        if (shouldRegister()) {
            this.register.device(this.deviceId, this.user, this.password);
        }
    }

    public CompletableFuture<?> tick() {

        this.statistics.scheduled();
        final Instant start = Instant.now();

        final CompletableFuture<?> future;

        try {
            future = process();
        } catch (final Exception e) {
            this.statistics.failed();
            return CompletableFuture.completedFuture(null);
        }

        return future.whenComplete((r, ex) -> {

            if (ex != null) {
                this.statistics.failed();
                logger.debug("Failed to publish", ex);
            }

            final Duration dur = Duration.between(start, Instant.now());
            this.statistics.duration(dur);
        });

    }

    protected void handleSuccess() {
        this.statistics.success();
    }

    protected void handleException(final Throwable e) {
        this.statistics.failed();
        this.statistics.error(0);
    }

    protected void handleFailure(final HttpResponse<?> response) {
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
                    register();
                }
                break;
            }
        } catch (final Exception e) {
            logger.warn("Failed to handle failure", e);
        }
    }

    protected void handleResponse(final HttpResponse<?> response) {

        final int code = response.statusCode();
        if (code < 200 || code > 299) {
            handleFailure(response);
        } else {
            handleSuccess();
        }

    }

    protected CompletableFuture<?> process() throws IOException {

        final CompletableFuture<?> result = new CompletableFuture<>();

        this.requestProvider
                .get()
                .sendBuffer(this.payload.getBuffer(), ar -> {

                    final HttpResponse<?> response = ar.result();

                    if (ar.succeeded()) {
                        handleResponse(response);
                        result.complete(null);
                    } else {
                        handleException(ar.cause());
                        result.completeExceptionally(ar.cause());
                    }

                });

        return result;
    }

}
