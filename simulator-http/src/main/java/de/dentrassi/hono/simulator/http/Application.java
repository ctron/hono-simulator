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

import static io.glutamate.lang.Environment.getAs;
import static java.lang.System.getenv;

import java.util.Random;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.AppRuntime;
import de.dentrassi.hono.demo.common.ProducerConfig;
import de.dentrassi.hono.demo.common.DeadlockDetector;
import de.dentrassi.hono.demo.common.EventWriter;
import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.demo.common.Tenant;
import de.dentrassi.hono.demo.common.Tls;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private static final ProducerConfig config = ProducerConfig.fromEnv();

    private static final String DEVICE_PROVIDER = System.getenv().getOrDefault("DEVICE_PROVIDER", "VERTX");

    public static void main(final String[] args) throws Exception {

        try (
                DeadlockDetector detector = new DeadlockDetector();
                AppRuntime runtime = new AppRuntime();) {
            runSimulator(runtime);
        }

    }

    private static void runSimulator(final AppRuntime runtime) throws InterruptedException {
        logger.info("Using Device implementation: {}", DEVICE_PROVIDER);

        final DeviceProvider provider = locateProvider();

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

        final Register register = new Register(http, Tenant.TENANT);
        final MeterRegistry registry = runtime.getRegistry();

        final Tags commonTags = Tags.of(
                Tag.of("protocol", "http"),
                Tag.of("tenant", Tenant.TENANT)
                );
        final Statistics stats = new Statistics(registry, commonTags.and(config.getType().asTag()));

        final ScheduledExecutorService statsExecutor = Executors.newSingleThreadScheduledExecutor();
        // statsExecutor.scheduleAtFixedRate(Application::dumpStats, 1, 1, TimeUnit.SECONDS);

        final TickExecutor tickExecutor = new TickExecutor();

        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        final Random r = new Random();

        try {

            for (int i = 0; i < numberOfDevices; i++) {

                final String username = String.format("user-%s-%s", deviceIdPrefix, i);
                final String deviceId = String.format("%s-%s", deviceIdPrefix, i);

                final Device device = provider.createDevice(executor, username, deviceId, Tenant.TENANT,
                        "hono-secret", http, register, Payload.payload(), stats,
                        EventWriter.nullWriter());

                final Supplier<CompletableFuture<?>> ticker;
                switch (config.getType()) {
                case EVENT:
                    ticker = device::tickEvent;
                    break;
                default:
                    ticker = device::tickTelemetry;
                    break;
                }

                tickExecutor.scheduleAtFixedRate(ticker, r.nextInt((int) config.getPeriod().toMillis()),
                        config.getPeriod().toMillis());

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

}
