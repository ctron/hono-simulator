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
package de.dentrassi.hono.simulator.mqtt;

import static io.micrometer.core.instrument.Tag.of;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import de.dentrassi.hono.demo.common.AppRuntime;
import de.dentrassi.hono.demo.common.ProducerConfig;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.demo.common.Tenant;
import de.dentrassi.hono.demo.common.Tls;
import io.glutamate.lang.Environment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.netty.handler.ssl.OpenSsl;
import okhttp3.OkHttpClient;

public class Application {

    private static final ProducerConfig config = ProducerConfig.fromEnv();

    public static <T> T envOrElse(final String name, final Function<String, T> converter, final T defaultValue) {
        final String value = System.getenv(name);

        if (value == null) {
            return defaultValue;
        }

        return converter.apply(value);
    }

    public static void main(final String[] args) throws Exception {

        try (final AppRuntime runtime = new AppRuntime(options -> {
            Environment.consumeAs("VERTX_EVENT_POOL_SIZE", Integer::parseInt, options::setEventLoopPoolSize);
        })) {
            run(runtime);
        }

    }

    private static void run(final AppRuntime runtime) throws InterruptedException {

        final int numberOfDevices = Environment.getAs("NUM_DEVICES", 10, Integer::parseInt);
        final int numberOfThreads = Environment.getAs("NUM_THREADS", 10, Integer::parseInt);

        final String deviceIdPrefix = Environment.get("HOSTNAME").orElse("");

        final OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
        if ( Tls.insecure()) {
            Tls.makeOkHttpInsecure(httpBuilder);
        }
        final OkHttpClient http = httpBuilder.build();

        final Register register = new Register(http, Tenant.TENANT);

        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfThreads);

        System.out.println("Vertx Native: " + runtime.getVertx().isNativeTransportEnabled());

        System.out.format("OpenSSL - available: %s -> %s%n", OpenSsl.isAvailable(), OpenSsl.versionString());
        if (OpenSsl.isAvailable()) {
            System.out.println("    Key Manager: " + OpenSsl.supportsKeyManagerFactory());
            System.out.println("    Host name validation: " + OpenSsl.supportsHostnameValidation());
        }

        System.out.format("MQTT Endpoint: %s:%s%n", Device.HONO_MQTT_HOST, Device.HONO_MQTT_PORT);

        final Random r = new Random();

        final Tags commonTags = Tags.of(
                of("tenant", Tenant.TENANT),
                of("protocol", "mqtt"),
                config.getType().asTag());

        final MeterRegistry metrics = runtime.getRegistry();
        final AtomicLong connected = metrics.gauge("connections", commonTags, new AtomicLong());
        final Statistics stats = new Statistics(metrics, commonTags);

        try {

            for (int i = 0; i < numberOfDevices; i++) {

                final String username = String.format("user-%s-%s", deviceIdPrefix, i);
                final String deviceId = String.format("%s-%s", deviceIdPrefix, i);

                System.out.format("New device - user: %s, clientId: %s%n", username, deviceId);

                final Device device = new Device(runtime.getVertx(), username, deviceId, Tenant.TENANT, "hono-secret",
                        register, connected, stats);

                final Runnable ticker;

                switch (config.getType()) {
                case EVENT:
                    ticker = () -> device.tickEvent();
                    break;
                default:
                    ticker = () -> device.tickTelemetry();
                    break;
                }

                executor.scheduleAtFixedRate(ticker,
                        r.nextInt((int) config.getPeriod().toMillis()), config.getPeriod().toMillis(),
                        TimeUnit.MILLISECONDS);

            }

            Thread.sleep(Long.MAX_VALUE);
        } finally {
            executor.shutdown();
        }

    }

}
