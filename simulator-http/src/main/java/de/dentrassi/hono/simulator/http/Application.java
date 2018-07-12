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

import static de.dentrassi.hono.demo.common.Tags.EVENT;
import static de.dentrassi.hono.demo.common.Tags.TELEMETRY;
import static java.lang.System.getenv;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.InfluxDbMetrics;
import de.dentrassi.hono.demo.common.Register;
import okhttp3.ConnectionPool;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    private static final boolean COOKIES = Boolean.parseBoolean(System.getenv().getOrDefault("HTTP_COOKIES", "false"));

    private static InfluxDbMetrics metrics;

    private static final Statistics TELEMETRY_STATS = new Statistics();
    private static final Statistics EVENT_STATS = new Statistics();

    private static final int TELEMETRY_MS = Integer.parseInt(System.getenv().getOrDefault("TELEMETRY_MS", "0"));
    private static final int EVENT_MS = Integer.parseInt(System.getenv().getOrDefault("EVENT_MS", "0"));

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

        final OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();

        final String poolSize = getenv("CONNECTION_POOL_SIZE");

        if (poolSize != null) {
            System.out.println("Enabling connection pool");
            final ConnectionPool connectionPool = new ConnectionPool(Integer.parseInt(poolSize), 1, TimeUnit.MINUTES);
            httpBuilder.connectionPool(connectionPool);
        }

        if (COOKIES) {
            /**
             * Cookies don't really work as we would need a cookie per device and not per
             * URL
             */
            System.out.println("Enabling cookies");
            final CookieJar cookieJar = new CookieJar() {
                private final Map<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>();

                @Override
                public void saveFromResponse(final HttpUrl url, final List<Cookie> cookies) {
                    this.cookieStore.put(url.host(), cookies);
                }

                @Override
                public List<Cookie> loadForRequest(final HttpUrl url) {
                    final List<Cookie> cookies = this.cookieStore.get(url.host());
                    return cookies != null ? cookies : new ArrayList<>();
                }
            };
            httpBuilder.cookieJar(cookieJar);
        }

        final OkHttpClient http = httpBuilder.build();

        final String deviceIdPrefix = System.getenv("HOSTNAME");

        final Register register = new Register(http, DEFAULT_TENANT);

        final ScheduledExecutorService deadlockExecutor = Executors.newSingleThreadScheduledExecutor();
        deadlockExecutor.scheduleAtFixedRate(Application::detectDeadlock, 1, 1, TimeUnit.SECONDS);

        final ScheduledExecutorService statsExecutor = Executors.newSingleThreadScheduledExecutor();
        statsExecutor.scheduleAtFixedRate(Application::dumpStats, 1, 1, TimeUnit.SECONDS);

        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfThreads);

        final Random r = new Random();

        try {

            for (int i = 0; i < numberOfDevices; i++) {

                final String username = String.format("user-%s-%s", deviceIdPrefix, i);
                final String deviceId = String.format("%s-%s", deviceIdPrefix, i);

                final Device device = new Device(username, deviceId, DEFAULT_TENANT, "hono-secret", http, register,
                        TELEMETRY_STATS, EVENT_STATS);

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

    private static void detectDeadlock() {
        final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        final long[] threadIds = threadBean.findDeadlockedThreads();

        if (threadIds != null) {
            System.out.format("Threads in deadlock: %s%n", threadIds.length);
        }
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

            final Map<Integer, Long> counts = new TreeMap<>();

            for (final Map.Entry<Integer, AtomicLong> entry : statistics.getErrors().entrySet()) {
                final int code = entry.getKey();
                final long value = entry.getValue().getAndSet(0);
                counts.put(code, value);
            }

            final Instant now = Instant.now();

            if (metrics != null) {
                final Map<String, Number> values = new HashMap<>(4);
                values.put("sent", sent);
                values.put("success", success);
                values.put("failure", failure);
                values.put("backlog", backlog);
                values.put("durations", durations);
                if (sent > 0) {
                    values.put("avgDuration", (double) durations / (double) sent);
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

            System.out.format("%s - Sent: %8d, Success: %8d, Failure: %8d, Backlog: %8d", name, sent, success, failure,
                    backlog);
            counts.forEach((code, num) -> {
                System.out.format(", %03d: %8d", code, num);
            });
            System.out.format(", %10d ms", durations);
            System.out.println();
            System.out.flush();
        } catch (final Exception e) {
            logger.error("Failed to dump statistics", e);
        }
    }

    private static <T> T envOrElse(final String name, final Function<String, T> converter, final T defaultValue) {
        final String value = System.getenv(name);

        if (value == null) {
            return defaultValue;
        }

        return converter.apply(value);
    }
}
