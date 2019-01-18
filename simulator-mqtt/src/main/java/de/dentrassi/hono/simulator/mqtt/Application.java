/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.mqtt;

import static io.glutamate.lang.Environment.getAs;
import static io.micrometer.core.instrument.Tag.of;

import java.util.EnumSet;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.demo.common.Tenant;
import de.dentrassi.hono.demo.common.Tls;
import io.glutamate.lang.Environment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.micrometer.MetricsDomain;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import okhttp3.OkHttpClient;

public class Application {

    private static final int TELEMETRY_MS = getAs("TELEMETRY_MS", 0, Integer::parseInt);
    private static final int EVENT_MS = getAs("EVENT_MS", 0, Integer::parseInt);

    public static <T> T envOrElse(final String name, final Function<String, T> converter, final T defaultValue) {
        final String value = System.getenv(name);

        if (value == null) {
            return defaultValue;
        }

        return converter.apply(value);
    }

    public static void main(final String[] args) throws Exception {

        final int numberOfDevices = Environment.getAs("NUM_DEVICES", 10, Integer::parseInt);
        final int numberOfThreads = Environment.getAs("NUM_THREADS", 10, Integer::parseInt);
        final int eventLoopPoolSize = Environment.getAs("VERTX_EVENT_POOL_SIZE", 10, Integer::parseInt);

        final String deviceIdPrefix = Environment.get("HOSTNAME").orElse("");

        final OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
        if ( Tls.insecure()) {
            Tls.makeOkHttpInsecure(httpBuilder);
        }
        final OkHttpClient http = httpBuilder.build();

        final Register register = new Register(http, Tenant.TENANT);

        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfThreads);

        final VertxOptions options = new VertxOptions();
        options.setClustered(false);

        options.setEventLoopPoolSize(eventLoopPoolSize);
        options.setPreferNativeTransport(true);

        options.setMetricsOptions(
                new MicrometerMetricsOptions()
                        .setEnabled(true)
                        .setDisabledMetricsCategories(EnumSet.allOf(MetricsDomain.class))
                        .setPrometheusOptions(
                                new VertxPrometheusOptions()
                                        .setEmbeddedServerOptions(
                                                new HttpServerOptions()
                                                        .setPort(8081))
                                        .setEnabled(true)
                                        .setStartEmbeddedServer(true)));

        final Vertx vertx = Vertx.factory.vertx(options);

        System.out.println("Using native: " + vertx.isNativeTransportEnabled());

        final Random r = new Random();

        final Tags commonTags = Tags.of(
                of("tenant", Tenant.TENANT),
                of("protocol", "mqtt"));

        final MeterRegistry metrics = BackendRegistries.getDefaultNow();
        final AtomicLong connected = metrics.gauge("connections", commonTags, new AtomicLong());
        final Statistics telemetryStats = new Statistics(metrics, commonTags.and(of("type", "telemetry")));
        final Statistics eventStats = new Statistics(metrics, commonTags.and(of("type", "event")));

        try {

            for (int i = 0; i < numberOfDevices; i++) {

                final String username = String.format("user-%s-%s", deviceIdPrefix, i);
                final String deviceId = String.format("%s-%s", deviceIdPrefix, i);

                final Device device = new Device(vertx, username, deviceId, Tenant.TENANT, "hono-secret", register,
                        connected);

                if (TELEMETRY_MS > 0) {
                    executor.scheduleAtFixedRate(() -> device.tickTelemetry(telemetryStats), r.nextInt(TELEMETRY_MS),
                            TELEMETRY_MS, TimeUnit.MILLISECONDS);
                }
                if (EVENT_MS > 0) {
                    executor.scheduleAtFixedRate(() -> device.tickEvent(eventStats), r.nextInt(EVENT_MS),
                            EVENT_MS, TimeUnit.MILLISECONDS);
                }
            }

            Thread.sleep(Long.MAX_VALUE);
        } finally {
            executor.shutdown();
        }

    }

}
