/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.demo.common;

import java.util.EnumSet;
import java.util.function.Consumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.micrometer.MetricsDomain;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

public class AppRuntime implements AutoCloseable {

    private final Vertx vertx;
    private final HealthCheckHandler healthCheckHandler;
    private final MeterRegistry registry;

    public AppRuntime() {
        this(null);
    }

    public AppRuntime(final Consumer<VertxOptions> customizer) {

        final VertxOptions options = new VertxOptions();

        options.setPreferNativeTransport(true);
        options.setMetricsOptions(
                new MicrometerMetricsOptions()
                        .setEnabled(true)
                        .setDisabledMetricsCategories(EnumSet.allOf(MetricsDomain.class))
                        .setPrometheusOptions(
                                new VertxPrometheusOptions()
                                        .setEnabled(true)));

        if (customizer != null) {
            customizer.accept(options);
        }

        this.vertx = Vertx.vertx(options);

        final Router router = Router.router(this.vertx);

        this.vertx.createHttpServer()
                .requestHandler(router)
                .listen(8081);

        router.route("/metrics").handler(PrometheusScrapingHandler.create());

        this.healthCheckHandler = HealthCheckHandler.create(this.vertx);
        router.get("/health").handler(this.healthCheckHandler);

        this.registry = BackendRegistries.getDefaultNow();
    }

    @Override
    public void close() {
        vertx.close();
    }

    public void register(final String name, final Handler<Future<Status>> procedure) {
        this.healthCheckHandler.register(name, procedure);
    }

    public MeterRegistry getRegistry() {
        return this.registry;
    }

    public Vertx getVertx() {
        return this.vertx;
    }
}
