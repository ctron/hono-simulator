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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Statistics {
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong success = new AtomicLong();
    private final AtomicLong failure = new AtomicLong();
    private final AtomicLong backlog = new AtomicLong();
    private final AtomicLong durations = new AtomicLong();
    private final Map<Integer, AtomicLong> errors = new ConcurrentHashMap<>();

    public void sent() {
        this.sent.incrementAndGet();
    }

    public void failed() {
        this.failure.incrementAndGet();
    }

    public void success() {
        this.success.incrementAndGet();
    }

    public void backlog() {
        this.backlog.incrementAndGet();
    }

    public void backlogSent() {
        this.backlog.decrementAndGet();
    }

    public void error(final int code) {
        final AtomicLong counter = this.errors.computeIfAbsent(code, x -> new AtomicLong());
        counter.incrementAndGet();
    }

    public void duration(final Duration dur) {
        this.durations.addAndGet(dur.toMillis());
    }

    public AtomicLong getSent() {
        return this.sent;
    }

    public AtomicLong getSuccess() {
        return this.success;
    }

    public AtomicLong getFailure() {
        return this.failure;
    }

    public AtomicLong getBacklog() {
        return this.backlog;
    }

    public AtomicLong getDurations() {
        return this.durations;
    }

    public Map<Integer, AtomicLong> getErrors() {
        return this.errors;
    }

}
