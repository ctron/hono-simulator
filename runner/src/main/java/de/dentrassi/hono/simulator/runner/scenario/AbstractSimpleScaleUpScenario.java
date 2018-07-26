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
import static java.time.Instant.now;

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

/**
 * A basic scale-up scenario.
 * <p>
 * The scenario will scale up simulators one by one. After each scale-up it will wait for a bit and then start checking
 * for a failure rate to stay under a certain threshold. If it doesn't then it will try to compensate by scaling up
 * another protocol adapter instance.
 * </p>
 * <p>
 * The scenario will end once it cannot scale up, either the simulators or the adapters.
 * </p>
 */
public abstract class AbstractSimpleScaleUpScenario {

    private static final String DC_HONO_HTTP_ADAPTER = "hono-adapter-http-vertx";

    private static final String DC_SIMULATOR_HTTP = "simulator-http";

    private static final Logger logger = LoggerFactory.getLogger(AbstractSimpleScaleUpScenario.class);

    private final static DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    private final Metrics metrics;

    private final Path logFile;

    private final IClient sim;

    private final IClient iot;

    protected abstract int getMaximumAdapterInstances();

    protected abstract int getMaximumSimulatorInstances();

    public AbstractSimpleScaleUpScenario(final Metrics metrics, final String name) {

        this.metrics = metrics;
        this.logFile = Paths
                .get("logs/" + name + "_" + TIMESTAMP_FORMAT.format(now().atOffset(ZoneOffset.UTC)) + ".log");

        this.sim = createSimulationClient();
        this.iot = createIoTClient();

        logger.info("Prepare scenerio:");
        logger.info("\tMaximum adapter instances: {}", getMaximumAdapterInstances());
        logger.info("\tMaximum simulator instances: {}", getMaximumSimulatorInstances());
        logger.info("\tMaximum allowed failure ratio: {}", String.format("%.0f%%", 100.0 * getMaximumFailureRatio()));

    }

    protected Duration getSampleDuration() {
        return Duration.ofMinutes(3);
    }

    protected Duration getWaitForStableDuration() {
        return Duration.ofMinutes(15);
    }

    protected Duration getImproveDuration() {
        return Duration.ofMinutes(5);
    }

    protected double getMaximumFailureRatio() {
        return 0.02;
    }

    public void run() {
        final ScaleUp scaleUpSimulator = new ScaleUp(this.metrics.getEventWriter(), this.sim, "simulator",
                DEPLOYMENT_CONFIG, DC_SIMULATOR_HTTP, getMaximumSimulatorInstances());
        final ScaleUp scaleUpAdapter = new ScaleUp(this.metrics.getEventWriter(), this.iot, "hono",
                DEPLOYMENT_CONFIG, DC_HONO_HTTP_ADAPTER, getMaximumAdapterInstances());

        final WaitForStable verify = new WaitForStable(
                this.metrics, getMaximumFailureRatio(),
                getSampleDuration(), getWaitForStableDuration(), getImproveDuration(),
                this::logState);

        final Duration waitAfterScaleup = getSampleDuration().plus(this.metrics.getSingleQueryOffset());

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

        final Wait initState = new Wait(getSampleDuration().multipliedBy(2).plus(this.metrics.getSingleQueryOffset()));
        initState
                .then(new SimpleState(this::logState))
                .then(scaleUpSimulator);

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
            append(file, String.format("%s;%s;%.4f;%s;%s;%s%n", Instant.now(), received, failureRate, rtt,
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
        final double failureRate = this.metrics.getFailureRate(getSampleDuration());
        final long received = this.metrics.getReceivedMessages(getSampleDuration());
        final long rtt = this.metrics.getRtt(getSampleDuration());

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
