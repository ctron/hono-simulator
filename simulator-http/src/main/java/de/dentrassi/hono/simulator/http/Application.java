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
package de.dentrassi.hono.simulator.http;

import static de.dentrassi.hono.demo.common.Tags.EVENT;
import static de.dentrassi.hono.demo.common.Tags.TELEMETRY;
import static io.glutamate.lang.Environment.getAs;
import static java.lang.System.getenv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.DeadlockDetector;
import de.dentrassi.hono.demo.common.InfluxDbMetrics;
import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.demo.common.Tls;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    private static InfluxDbMetrics metrics;

    private static final Statistics TELEMETRY_STATS = new Statistics();
    private static final Statistics EVENT_STATS = new Statistics();

    private static final int TELEMETRY_MS = getAs("TELEMETRY_MS", 0, Integer::parseInt);
    private static final int EVENT_MS = getAs("EVENT_MS", 0, Integer::parseInt);

    private static final String DEVICE_PROVIDER = System.getenv().getOrDefault("DEVICE_PROVIDER", "VERTX");

    public static void main(final String[] args) throws Exception {

        try (DeadlockDetector detector = new DeadlockDetector()) {
            runSimulator();
        }

    }

    private static void runSimulator() throws InterruptedException {
        logger.info("Using Device implementation: {}", DEVICE_PROVIDER);

        final DeviceProvider provider = locateProvider();

        metrics = InfluxDbMetrics.createInstance().orElse(null);
        if (metrics != null) {
            logger.info("Recording metrics");
        }

        final int numberOfDevices = getAs("NUM_DEVICES", 10, Integer::parseInt);
        final int numberOfThreads = getAs("NUM_THREADS", 10, Integer::parseInt);

        System.out.format("#devices: %s, #threads: %s%n", numberOfDevices, numberOfThreads);
        System.out.format("TLS insecure: %s%n", Tls.insecure());

        final OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();

        // "minimalistic" setting

        if (getAs("OKHTTP_MINIMALISTIC_CONNECTION_POOL", false, Boolean::parseBoolean)) {
            System.out.println("Using minimalistic OkHttp connection pool");
            httpBuilder.connectionPool(new ConnectionPool(0, 1, TimeUnit.MILLISECONDS));
        }

        // explicit connection pool setting

        final String poolSize = getenv("OKHTTP_CONNECTION_POOL_SIZE");

        if (poolSize != null) {
            System.out.println("Setting connection pool to: " + Integer.parseInt(poolSize));
            final ConnectionPool connectionPool = new ConnectionPool(Integer.parseInt(poolSize), 1, TimeUnit.MINUTES);
            httpBuilder.connectionPool(connectionPool);
        }

        // disable TLS validation

        if (Tls.insecure()) {
            Tls.makeOkHttpInsecure(httpBuilder);
        }

        // create new client

        final OkHttpClient http = httpBuilder.build();

        getAs("OKHTTP_DISPATCHER_MAX_REQUESTS", Integer::parseInt).ifPresent(http.dispatcher()::setMaxRequests);
        getAs("OKHTTP_DISPATCHER_MAX_REQUESTS_PER_HOST", Integer::parseInt)
                .ifPresent(http.dispatcher()::setMaxRequestsPerHost);

        final String deviceIdPrefix = System.getenv("HOSTNAME");

        final Register register = new Register(http, DEFAULT_TENANT);

        final ScheduledExecutorService statsExecutor = Executors.newSingleThreadScheduledExecutor();
        statsExecutor.scheduleAtFixedRate(Application::dumpStats, 1, 1, TimeUnit.SECONDS);

        final TickExecutor tickExecutor = new TickExecutor();

        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        final Random r = new Random();

        try {

            for (int i = 0; i < numberOfDevices; i++) {

                final String username = String.format("user-%s-%s", deviceIdPrefix, i);
                final String deviceId = String.format("%s-%s", deviceIdPrefix, i);

                final Device device = provider.createDevice(executor, username, deviceId, DEFAULT_TENANT,
                        "hono-secret", http, register, Payload.payload(), TELEMETRY_STATS, EVENT_STATS, metrics);

                if (TELEMETRY_MS > 0) {
                    tickExecutor.scheduleAtFixedRate(device::tickTelemetry, r.nextInt(TELEMETRY_MS), TELEMETRY_MS);
                }

                if (EVENT_MS > 0) {
                    tickExecutor.scheduleAtFixedRate(device::tickEvent, r.nextInt(EVENT_MS), EVENT_MS);
                }
            }

            Thread.sleep(Long.MAX_VALUE);
        } finally {
            executor.shutdown();
            statsExecutor.shutdown();
        }
    }

    private static DeviceProvider locateProvider() {
        for (final DeviceProvider provider : ServiceLoader.load(DeviceProvider.class)) {
            if (provider.getName().equals(DEVICE_PROVIDER)) {
                return provider;
            }
        }
        throw new IllegalArgumentException(String.format("Unable to find device provider: '%s'", DEVICE_PROVIDER));
    }

    private static void dumpStats() {
        dumpStatistics("Telemetry", TELEMETRY, TELEMETRY_STATS);
        dumpStatistics("    Event", EVENT, EVENT_STATS);
    }

    private static void dumpStatistics(final String name, final Map<String, String> tags, final Statistics statistics) {
        try {
            final long sent = statistics.getSent().getAndSet(0);
            final long success = statistics.getSuccess().getAndSet(0);
            final long failure = statistics.getFailure().getAndSet(0);
            final long durations = statistics.getDurations().getAndSet(0);
            final long backlog = statistics.getBacklog().get();

            final double failureRatio;
            if (sent > 0) {
                failureRatio = BigDecimal
                        .valueOf(failure)
                        .divide(BigDecimal.valueOf(sent), 2, RoundingMode.HALF_UP)
                        .doubleValue();
            } else {
                failureRatio = 0.0;
            }

            final Map<Integer, Long> counts = new TreeMap<>();

            for (final Map.Entry<Integer, AtomicLong> entry : statistics.getErrors().entrySet()) {
                final int code = entry.getKey();
                final long value = entry.getValue().getAndSet(0);
                counts.put(code, value);
            }

            final Instant now = Instant.now();

            if (metrics != null) {
                final Map<String, Number> values = new HashMap<>();
                values.put("sent", sent);
                values.put("success", success);
                values.put("failure", failure);
                values.put("backlog", backlog);
                values.put("durations", durations);
                values.put("failureRatio", failureRatio);
                final double numSent = success + failure;
                if (numSent > 0) {
                    values.put("avgDuration", durations / numSent);
                }
                metrics.updateStats(now, "http-publish", values, tags);

                if (!counts.isEmpty()) {
                    final Map<String, Number> errors = new HashMap<>();
                    counts.forEach((code, num) -> {
                        errors.put("" + code, num);
                    });
                    metrics.updateStats(now, "http-errors", errors, tags);
                }
            }

            System.out.format("%s - Sent: %8d, Success: %8d, Failure: %8d, Backlog: %8d, FRatio: %.2f",
                    name, sent, success, failure, backlog, failureRatio);
            counts.forEach((code, num) -> {
                System.out.format(", %03d: %8d", code, num);
            });
            System.out.format(", %10d ms", durations);
            System.out.println();
        } catch (final Exception e) {
            logger.error("Failed to dump statistics", e);
        }
    }
}
