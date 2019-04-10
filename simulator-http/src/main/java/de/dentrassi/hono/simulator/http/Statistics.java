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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

public class Statistics {

    private final MeterRegistry registry;
    private final Tags commonTags;

    private final Counter scheduled;
    private final Counter failure;
    private final Counter sent;
    private final AtomicLong backlog;
    private final Timer durations;

    public Statistics(final MeterRegistry registry, final Tags commonTags) {
        this.registry = registry;
        this.commonTags = commonTags;

        this.scheduled = registry.counter("messages_scheduled", commonTags);
        this.failure = registry.counter("messages_failure", commonTags);
        this.sent = registry.counter("messages_sent", commonTags);
        this.backlog = registry.gauge("messages_backlog", commonTags, new AtomicLong());
        this.durations = registry.timer("messages_duration", commonTags);
    }

    public void scheduled() {
        this.scheduled.increment();
    }

    public void failed() {
        this.failure.increment();
    }

    public void success() {
        this.sent.increment();
    }

    public void backlog() {
        this.backlog.incrementAndGet();
    }

    public void backlogSent() {
        this.backlog.decrementAndGet();
    }

    public void error(final int code) {
        registry.counter("messages_error", commonTags.and("code", Integer.toString(code))).increment();
    }

    public void duration(final Duration duration) {
        this.durations.record(duration);
    }


}
