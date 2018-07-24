/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.runner.scenario;

import static com.openshift.restclient.ResourceKind.DEPLOYMENT_CONFIG;
import static de.dentrassi.hono.simulator.runner.OpenShift.createIoTClient;
import static de.dentrassi.hono.simulator.runner.OpenShift.createSimulationClient;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.time.Duration.ofMinutes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.restclient.IClient;

import de.dentrassi.hono.simulator.runner.Metrics;
import de.dentrassi.hono.simulator.runner.Runner;
import de.dentrassi.hono.simulator.runner.state.ScaleUp;
import de.dentrassi.hono.simulator.runner.state.SimpleState;
import de.dentrassi.hono.simulator.runner.state.Wait;
import de.dentrassi.hono.simulator.runner.state.WaitForStable;
import io.glutamate.util.concurrent.Await;

public class Scenario1 {

    private static final String DC_HONO_HTTP_ADAPTER = "hono-adapter-http-vertx";

    private static final String DC_SIMULATOR_HTTP = "simulator-http";

    private static final int MAX_ADAPTER_INSTANCES = 16;

    private static final int MAX_SIMULATOR_INSTANCES = 48;

    private static final double MAX_FAILURE_RATE = 0.02;

    private static final Logger logger = LoggerFactory.getLogger(Scenario1.class);

    private final Duration sampleDuration = Duration.ofMinutes(3);

    private final Metrics metrics;

    private final static String TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
            .format(Instant.now().atOffset(ZoneOffset.UTC));

    private final Path logFile = Paths
            .get("logs/scenario1_" + TIMESTAMP + ".log");

    private final IClient sim;

    private final IClient iot;

    public Scenario1(final Metrics metrics) {

        this.metrics = metrics;

        this.sim = createSimulationClient();
        this.iot = createIoTClient();

        final ScaleUp scaleUpSimulator = new ScaleUp(this.sim, "simulator",
                DEPLOYMENT_CONFIG, DC_SIMULATOR_HTTP, MAX_SIMULATOR_INSTANCES);
        final ScaleUp scaleUpAdapter = new ScaleUp(this.iot, "hono",
                DEPLOYMENT_CONFIG, DC_HONO_HTTP_ADAPTER, MAX_ADAPTER_INSTANCES);

        final WaitForStable verify = new WaitForStable(metrics, MAX_FAILURE_RATE,
                this.sampleDuration, Duration.ofMinutes(15), Duration.ofMinutes(5), this::logState);

        final Duration waitAfterScaleup = this.sampleDuration.plus(Duration.ofMinutes(1));

        scaleUpAdapter
                .then(new Wait(waitAfterScaleup))
                .then(verify);

        scaleUpSimulator
                .then(new Wait(waitAfterScaleup))
                .then(verify);

        verify
                .onSuccess(scaleUpSimulator);
        verify
                .onFailure(scaleUpAdapter);

        final Wait initState = new Wait(ofMinutes(7));
        initState
                .then(new SimpleState(this::logState))
                .then(scaleUpSimulator);

        // start

        logger.info("Reset system...");

        final DeploymentConfig dcSimulator = this.sim.getResourceFactory().stub(DEPLOYMENT_CONFIG, DC_SIMULATOR_HTTP,
                "simulator");
        dcSimulator.refresh();
        dcSimulator.setReplicas(1);
        this.sim.update(dcSimulator);
        dcSimulator.refresh();

        final DeploymentConfig dcAdapter = this.iot.getResourceFactory().stub(DEPLOYMENT_CONFIG,
                DC_HONO_HTTP_ADAPTER, "hono");
        dcAdapter.refresh();
        dcAdapter.setReplicas(1);
        this.iot.update(dcAdapter);
        dcAdapter.refresh();

        try {
            logger.info("Waiting for reset...");
            while (dcSimulator.getCurrentReplicaCount() > 1 || dcAdapter.getCurrentReplicaCount() > 1) {
                Thread.sleep(1_000);

                dcSimulator.refresh();
                dcAdapter.refresh();
            }
        } catch (final InterruptedException e) {
        }

        logger.info("System reset");

        // run

        logger.info("Starting runner...");

        try (final Runner runner = new Runner(initState, Duration.ofSeconds(10))) {
            Await.await(runner.complete(), Long.MAX_VALUE);
        }

        logger.info("Runner completed");

        logState();

        System.err.println("Runner completed");

    }

    protected static void logState(final Path file, final double failureRate, final long received, final long rtt,
            final int simulators, final int adapters) {

        try {
            append(file, String.format("%s;%s;%s;%s;%s;%s%n", Instant.now(), received, failureRate, rtt,
                    simulators, adapters));
        } catch (final IOException e) {
            logger.warn("Failed to log output", e);
        }
    }

    protected void logState(final double failureRate, final long received, final long rtt) {
        final DeploymentConfig dcSim = (DeploymentConfig) this.sim.getResourceFactory().stub(DEPLOYMENT_CONFIG,
                DC_SIMULATOR_HTTP, "simulator");
        dcSim.refresh();

        final int simulators = dcSim.getCurrentReplicaCount();

        final DeploymentConfig dcAdapter = (DeploymentConfig) this.iot.getResourceFactory().stub(DEPLOYMENT_CONFIG,
                DC_HONO_HTTP_ADAPTER, "hono");
        dcAdapter.refresh();
        final int adapters = dcAdapter.getCurrentReplicaCount();

        logState(this.logFile, failureRate, received, rtt, simulators, adapters);
    }

    protected void logState() {
        final double failureRate = this.metrics.getFailureRate(this.sampleDuration);
        final long received = this.metrics.getReceivedMessages(this.sampleDuration);
        final long rtt = this.metrics.getRtt(this.sampleDuration);

        logState(failureRate, received, rtt);
    }

    private static void append(final Path path, final String string) throws IOException {
        try (
                final OutputStream stream = Files.newOutputStream(path, APPEND, CREATE, WRITE);
                final Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {

            writer.write(string);
        }
    }
}
