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
package de.dentrassi.hono.simulator.consumer;

import static io.vertx.core.CompositeFuture.join;
import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Environment;
import de.dentrassi.hono.demo.common.InfluxDbMetrics;
import de.dentrassi.hono.demo.common.Tags;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final Vertx vertx;
    private final HonoClient honoClient;
    private final CountDownLatch latch;
    private final String tenant;

    private final InfluxDbConsumer consumer;
    private final InfluxDbMetrics metrics;

    private final ScheduledExecutorService stats;

    private final Consumer telemetryConsumer;

    private final Consumer eventConsumer;

    private static final boolean PERSISTENCE_ENABLED = Optional
            .ofNullable(System.getenv("ENABLE_PERSISTENCE"))
            .map(Boolean::parseBoolean)
            .orElse(true);

    private static final boolean METRICS_ENABLED = Optional
            .ofNullable(System.getenv("ENABLE_METRICS"))
            .map(Boolean::parseBoolean)
            .orElse(true);

    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;

    public static void main(final String[] args) throws Exception {

        final Application app = new Application(
                getenv("HONO_TENANT"),
                getenv("MESSAGING_SERVICE_HOST"), // HONO_DISPATCH_ROUTER_EXT_SERVICE_HOST
                Integer.parseInt(getenv("MESSAGING_SERVICE_PORT_AMQP")), // HONO_DISPATCH_ROUTER_EXT_SERVICE_PORT
                getenv("HONO_USER"),
                getenv("HONO_PASSWORD"),
                ofNullable(getenv("HONO_TRUSTED_CERTS")));

        try {
            app.consumeTelemetryData();
            System.out.println("Exiting application ...");
        } finally {
            app.close();
        }
        System.out.println("Bye, bye!");

        Thread.sleep(1_000);

        for (final Thread t : Thread.getAllStackTraces().keySet()) {
            System.out.println(t.getName());
        }

        System.exit(-1);
    }

    public Application(final String tenant, final String host, final int port, final String user, final String password,
            final Optional<String> trustedCerts) {

        System.out.format("Hono Consumer - Server: %s:%s%n", host, port);

        if (PERSISTENCE_ENABLED) {
            logger.info("Recording payload");
            this.consumer = new InfluxDbConsumer(makeInfluxDbUrl(),
                    getenv("INFLUXDB_USER"),
                    getenv("INFLUXDB_PASSWORD"),
                    getenv("INFLUXDB_NAME"));
        } else {
            this.consumer = null;
        }

        if (METRICS_ENABLED) {
            logger.info("Recording metrics");
            this.metrics = new InfluxDbMetrics(makeInfluxDbUrl(),
                    getenv("INFLUXDB_USER"),
                    getenv("INFLUXDB_PASSWORD"),
                    getenv("INFLUXDB_NAME"));
        } else {
            this.metrics = null;
        }

        this.stats = Executors.newSingleThreadScheduledExecutor();
        this.stats.scheduleAtFixedRate(this::updateStats, 1, 1, TimeUnit.SECONDS);

        this.tenant = tenant;

        this.vertx = Vertx.vertx();

        final ClientConfigProperties config = new ClientConfigProperties();
        config.setHost(host);
        config.setPort(port);
        config.setUsername(user);
        config.setPassword(password);

        if (System.getenv("DISABLE_TLS") == null) {
            config.setTlsEnabled(true);
            config.setHostnameVerificationRequired(false);
        }

        trustedCerts.ifPresent(config::setTrustStorePath);

        Environment.getAs("HONO_INITIAL_CREDITS", Integer::parseInt).ifPresent(config::setInitialCredits);

        this.honoClient = HonoClient.newClient(this.vertx, config);

        this.latch = new CountDownLatch(1);

        this.telemetryConsumer = new Consumer(this.consumer);
        this.eventConsumer = new Consumer(this.consumer);
    }

    private void close() {
        this.stats.shutdown();
        this.honoClient.shutdown(done -> {
        });
        this.vertx.close();
    }

    public void updateStats() {

        final Instant now = Instant.now();

        final long n1 = this.telemetryConsumer.getCounter().getAndSet(0);
        final long n2 = this.eventConsumer.getCounter().getAndSet(0);

        System.out.format("%s: Processed %s telemetry, %s events%n", now, n1, n2);

        try {
            if (this.metrics != null) {
                this.metrics.updateStats(now, "consumer", "messageCount", Tags.TELEMETRY, n1);
                this.metrics.updateStats(now, "consumer", "messageCount", Tags.EVENT, n2);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static String makeInfluxDbUrl() {
        final String url = getenv("INFLUXDB_URL");
        if (url != null && !url.isEmpty()) {
            return url;
        }

        return String.format("http://%s:%s", getenv("INFLUXDB_SERVICE_HOST"), getenv("INFLUXDB_SERVICE_PORT_API"));
    }

    private ProtonClientOptions getOptions() {
        return new ProtonClientOptions();
    }

    private void consumeTelemetryData() throws Exception {
        connect()
                .setHandler(startup -> {
                    if (startup.failed()) {
                        logger.error("Error occurred during initialization of receiver", startup.cause());
                        this.latch.countDown();
                    }
                });

        // if everything went according to plan, the next step will block forever

        this.latch.await();
    }

    private Future<MessageConsumer> createTelemetryConsumer(final HonoClient connectedClient) {

        return connectedClient.createTelemetryConsumer(this.tenant,
                this.telemetryConsumer::handleMessage, closeHandler -> {
                    logger.info("close handler of telemetry consumer is called");
                    this.vertx.setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {
                        logger.info("attempting to re-open the TelemetryConsumer link ...");
                        createTelemetryConsumer(connectedClient);
                    });
                });
    }

    private Future<MessageConsumer> createEventConsumer(final HonoClient connectedClient) {

        return connectedClient.createEventConsumer(this.tenant,
                this.eventConsumer::handleMessage, closeHandler -> {
                    logger.info("close handler of event consumer is called");
                    this.vertx.setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {
                        logger.info("attempting to re-open the EventConsumer link ...");
                        createEventConsumer(connectedClient);
                    });
                });
    }

    private void onDisconnect(final ProtonConnection con) {
        this.vertx.setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {
            logger.info("attempting to re-connect to Hono ...");
            connect();
        });
    }

    private Future<?> connect() {

        return this.honoClient.connect(
                getOptions(),
                this::onDisconnect)

                .compose(connectedClient -> {
                    return join(
                            createTelemetryConsumer(connectedClient),
                            createEventConsumer(connectedClient));
                });

    }

}
