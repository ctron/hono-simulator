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

import static io.glutamate.lang.Environment.is;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Registration;
import de.dentrassi.hono.demo.common.Tls;
import de.dentrassi.hono.simulator.mqtt.vertx.MqttClientImpl;
import io.glutamate.lang.Environment;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttConnectionException;

public class Device {

    private final MqttClient client;

    private final Payload payload;

    private final Vertx vertx;

    private final Registration register;

    private final String deviceId;

    private final String username;

    private final String password;

    private boolean connected;

    public static final String HONO_MQTT_HOST = System.getenv().getOrDefault("HONO_MQTT_HOST", "localhost");
    public static final int HONO_MQTT_PORT = Application.envOrElse("HONO_MQTT_PORT", Integer::parseInt, 1883);

    private static final boolean AUTO_REGISTER = Environment.getAs("AUTO_REGISTER", true, Boolean::parseBoolean);

    private static final long RECONNECT_DELAY = Application.envOrElse("RECONNECT_DELAY", Long::parseLong, 2_000L);
    private static final int RECONNECT_JITTER = Application.envOrElse("RECONNECT_JITTER", Integer::parseInt, 2_000);

    private final Random random = new Random();

    private final AtomicLong connectedCount;

    private final Statistics stats;

    private long connectTimer = -1;

    public Device(final Vertx vertx, final String username, final String deviceId, final String tenant,
            final String password, final Optional<Registration> register, final AtomicLong connectedCount,
            final Statistics stats) {

        this.vertx = vertx;
        this.register = register.orElse(null);

        this.deviceId = deviceId;
        this.username = username;
        this.password = password;

        this.connectedCount = connectedCount;
        this.stats = stats;

        this.payload = Payload.payload();

        final MqttClientOptions options = new MqttClientOptions();

        options.setCleanSession(true);
        options.setConnectTimeout(10_000);
        options.setClientId(deviceId);
        options.setAutoKeepAlive(true);
        options.setKeepAliveTimeSeconds(10);

        if (Tls.insecure()) {
            options.setTrustAll(true);
        } else {
            options.setTrustAll(false);
            options.setHostnameVerificationAlgorithm("HTTPS");
        }

        options.setSsl(!Environment.getAs("DISABLE_TLS", false, Boolean::parseBoolean));
        options.setUsername(username + "@" + tenant);
        options.setPassword(password);

        is("WITH_OPENSSL", () -> {
            System.out.println("Using OpenSSL for MQTT");
            options.setSslEngineOptions(new OpenSSLEngineOptions());
        });

        this.client = new MqttClientImpl(vertx, options);

        this.client.publishCompletionHandler(this::publishComplete);
        this.client.closeHandler(v -> connectionLost(null));

        scheduleConnect();
    }

    private void scheduleConnect() {
        if (connectTimer >= 0) {
            // already scheduled
            return;
        }
        this.connectTimer = this.vertx.setTimer(getConnectDelay(), v -> startConnect());
    }

    private void startConnect() {

        this.connectTimer = -1;

        this.client.connect(HONO_MQTT_PORT, HONO_MQTT_HOST, HONO_MQTT_HOST, connected -> {
            if (connected.failed()) {
                connectionFailed(connected.cause());
            } else {
                connectionEstablished();
            }
        });

    }

    private long getConnectDelay() {

        final long delay = RECONNECT_DELAY + this.random.nextInt(RECONNECT_JITTER);
        if (delay <= 0) {
            return 1;
        }
        return delay;
    }

    public void tickTelemetry() {
        this.vertx.runOnContext(v -> {
            doPublish("telemetry", MqttQoS.AT_MOST_ONCE);
        });
    }

    public void tickEvent() {
        this.vertx.runOnContext(v -> {
            doPublish("event", MqttQoS.AT_LEAST_ONCE);
        });
    }


    private void doPublish( final String topic, final MqttQoS qos) {

        stats.scheduled();

        if (!this.client.isConnected()) {
            return;
        }

        this.client.publish(topic, this.payload.getBuffer(), qos, false, false);

        switch (qos) {
        case AT_MOST_ONCE:
            stats.sent();
            break;
        case AT_LEAST_ONCE:
            break;
        default:
            break;
        }
    }

    private void publishComplete(final Integer packetId) {
        stats.sent();
    }

    private void connectionEstablished() {
        if (!this.connected) {
            this.connected = true;
            this.connectedCount.incrementAndGet();
        }

        System.out.format("Connection established%n");

    }

    protected void connectionFailed(final Throwable throwable) {
        System.out.format("Connection failed: %s%n", throwable != null ? throwable.getMessage() : "<null>");

        if (throwable instanceof MqttConnectionException) {
            final MqttConnectReturnCode code = ((MqttConnectionException) throwable).code();

            switch (code) {
            case CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD:
                //$FALL-THROUGH$
            case CONNECTION_REFUSED_NOT_AUTHORIZED:
                //$FALL-THROUGH$
            case CONNECTION_REFUSED_IDENTIFIER_REJECTED:
                register();
                break;
            default:
                break;
            }
        } else {
            if (throwable != null) {
                throwable.printStackTrace();
            }
        }

        scheduleConnect();
    }

    protected void connectionLost(final Throwable throwable) {
        System.out.format("Connection lost: %s%n", throwable != null ? throwable.getMessage() : "<null>");

        if (this.connected) {
            this.connected = false;
            this.connectedCount.decrementAndGet();
        }

        scheduleConnect();
    }

    private void register() {
        if (this.register != null && AUTO_REGISTER) {
            System.out.println("Failed to connect ... try auto register");
            try {
                this.register.device(this.deviceId, this.username, this.password);
            } catch (final Exception e) {
            }
        }
    }

}
