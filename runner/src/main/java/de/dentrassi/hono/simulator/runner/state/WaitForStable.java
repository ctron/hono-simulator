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
package de.dentrassi.hono.simulator.runner.state;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.simulator.runner.Metrics;

public class WaitForStable implements State {

    private static final Logger logger = LoggerFactory.getLogger(WaitForStable.class);

    private final Metrics metrics;
    private final double maxFailureRatio;
    private final Instant until;

    private final Duration sampleDuration;

    private State success;
    private State failure;

    private final Duration improveDuration;

    private Instant sampleEnd;

    private double bestFailureRatio;

    public WaitForStable(final Metrics metrics, final double maxFailureRatio, final Duration sampleDuration,
            final Duration waitDuration, final Duration improveDuration) {
        this.metrics = metrics;
        this.maxFailureRatio = maxFailureRatio;
        this.sampleDuration = sampleDuration;
        this.improveDuration = improveDuration;

        this.until = Instant.now().plus(waitDuration);
    }

    @Override
    public void check(final Context context) {
        final double currentFailureRatio = this.metrics.getFailureRate(this.sampleDuration);

        logger.info("Waiting for stable message flow");
        logger.info("           failure: {} % < {} %", currentFailureRatio * 100.0, this.maxFailureRatio * 100.0);
        logger.info("               RTT: {}", this.metrics.getRtt(this.sampleDuration));
        logger.info("       sent msgs/s: {}", this.metrics.getReceivedMessages(this.sampleDuration));
        logger.info("   received msgs/s: {}", this.metrics.getReceivedMessages(this.sampleDuration));

        if (this.sampleEnd != null) {

            // already detected acceptable failure ratio

            if (Instant.now().isAfter(this.sampleEnd)) {

                // end of sample period

                context.advance(this.success);

            } else if (currentFailureRatio < this.bestFailureRatio) {

                // value did improve in the accepted period of time

                this.bestFailureRatio = currentFailureRatio;

            }

        } else if (currentFailureRatio < this.maxFailureRatio) {

            // first time we detected an acceptable failure ratio

            this.sampleEnd = Instant.now().plus(this.improveDuration);
            this.bestFailureRatio = currentFailureRatio;

        } else if (Instant.now().isAfter(this.until)) {

            // no luck ... proceed with failure

            context.advance(this.failure);

        }
    }

    public void onSuccess(final State state) {
        this.success = state;
    }

    public void onFailure(final State state) {
        this.failure = state;
    }

}
