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

import static de.dentrassi.hono.demo.common.Tags.EVENT;
import static de.dentrassi.hono.demo.common.Tags.TELEMETRY;
import static io.glutamate.lang.Environment.getAs;
import static java.lang.System.getenv;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.InfluxDbMetrics;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.demo.common.Tenant;
import de.dentrassi.hono.demo.common.Tls;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import okhttp3.OkHttpClient;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private static final int TELEMETRY_MS = getAs("TELEMETRY_MS", 0, Integer::parseInt);
    private static final int EVENT_MS = getAs("EVENT_MS", 0, Integer::parseInt);

    static final Statistics TELEMETRY_STATS = new Statistics();
    static final Statistics EVENT_STATS = new Statistics();
    static final AtomicLong CONNECTED = new AtomicLong();

    private static InfluxDbMetrics metrics;

    private static final boolean METRICS_ENABLED = Optional
            .ofNullable(System.getenv("ENABLE_METRICS"))
            .map(Boolean::parseBoolean)
            .orElse(true);

    private static String makeInfluxDbUrl() {
        final String url = getenv("INFLUXDB_URL");
        if (url != null && !url.isEmpty()) {
            return url;
        }

        return String.format("http://%s:%s", getenv("INFLUXDB_SERVICE_HOST"), getenv("INFLUXDB_SERVICE_PORT_API"));
    }

    public static <T> T envOrElse(final String name, final Function<String, T> converter, final T defaultValue) {
        final String value = System.getenv(name);

        if (value == null) {
            return defaultValue;
        }

        return converter.apply(value);
    }

    public static void main(final String[] args) throws Exception {

        if (METRICS_ENABLED) {
            logger.info("Recording metrics");
            metrics = new InfluxDbMetrics(makeInfluxDbUrl(),
                    getenv("INFLUXDB_USER"),
                    getenv("INFLUXDB_PASSWORD"),
                    getenv("INFLUXDB_NAME"));
        } else {
            metrics = null;
        }

        final int numberOfDevices = envOrElse("NUM_DEVICES", Integer::parseInt, 10);
        final int numberOfThreads = envOrElse("NUM_THREADS", Integer::parseInt, 10);
        final int eventLoopPoolSize = envOrElse("VERTX_EVENT_POOL_SIZE", Integer::parseInt, 10);

        final String deviceIdPrefix = System.getenv("HOSTNAME");

        final OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
        if ( Tls.insecure()) {
            Tls.makeOkHttpInsecure(httpBuilder);
        }
        final OkHttpClient http = httpBuilder.build();

        final Register register = new Register(http, Tenant.TENANT);

        final ScheduledExecutorService statsExecutor = Executors.newSingleThreadScheduledExecutor();
        statsExecutor.scheduleAtFixedRate(Application::dumpStats, 1, 1, TimeUnit.SECONDS);

        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfThreads);

        final VertxOptions options = new VertxOptions();
        options.setClustered(false);

        options.setEventLoopPoolSize(eventLoopPoolSize);
        options.setPreferNativeTransport(true);

        final Vertx vertx = Vertx.factory.vertx(options);

        System.out.println("Using native: " + vertx.isNativeTransportEnabled());

        final Random r = new Random();

        try {

            for (int i = 0; i < numberOfDevices; i++) {

                final String username = String.format("user-%s-%s", deviceIdPrefix, i);
                final String deviceId = String.format("%s-%s", deviceIdPrefix, i);

                final Device device = new Device(vertx, username, deviceId, Tenant.TENANT, "hono-secret", register);

                if (TELEMETRY_MS > 0) {
                    executor.scheduleAtFixedRate(device::tickTelemetry, r.nextInt(TELEMETRY_MS), TELEMETRY_MS,
                            TimeUnit.MILLISECONDS);
                }
                if (EVENT_MS > 0) {
                    executor.scheduleAtFixedRate(device::tickEvent, r.nextInt(EVENT_MS), EVENT_MS,
                            TimeUnit.MILLISECONDS);
                }
            }

            Thread.sleep(Long.MAX_VALUE);
        } finally {
            executor.shutdown();
        }

    }

    private static void dumpStats() {
        try {
            final long sentTelemetry = TELEMETRY_STATS.collectSent();
            final long sentEvent = EVENT_STATS.collectSent();
            final long connected = CONNECTED.get();

            final Instant now = Instant.now();

            System.out.format("%s: Connected: %8s, Sent/T: %8s, Sent/E: %8s", connected, sentTelemetry, sentEvent);
            System.out.println();
            System.out.flush();

            if (metrics != null) {
                final Map<String, Number> values = new HashMap<>(4);
                values.put("sent", sentTelemetry);
                metrics.updateStats(now, "mqtt-publish", values, TELEMETRY);

                values.put("sent", sentEvent);
                metrics.updateStats(now, "mqtt-publish", values, EVENT);

                values.clear();
                values.put("connected", connected);
                metrics.updateStats(now, "mqtt-device", values, Collections.emptyMap());
            }

        } catch (final Exception e) {
            logger.error("Failed to dump statistics", e);
        }
    }

}
