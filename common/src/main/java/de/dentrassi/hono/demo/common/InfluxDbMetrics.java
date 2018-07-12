/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.demo.common;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfluxDbMetrics {

    private static final Logger logger = LoggerFactory.getLogger(InfluxDbMetrics.class);

    private static final String HOSTNAME;

    static {
        String h = System.getenv("HOSTNAME");
        if (h == null) {
            h = UUID.randomUUID().toString();
        }
        HOSTNAME = h;
    }

    private final InfluxDB db;

    public InfluxDbMetrics(final String uri, final String username, final String password,
            final String databaseName) {

        logger.info("InfluxDB - metrics - URL: {}", uri);
        logger.info("InfluxDB -      Database: {}", databaseName);

        this.db = InfluxDBFactory.connect(uri, username, password);

        if (!this.db.databaseExists(databaseName)) {
            this.db.createDatabase(databaseName);
        }

        this.db.setDatabase(databaseName);
    }

    public void updateStats(final Instant timestamp, final String measurement, final String name, final Number value) {
        updateStats(timestamp, measurement, singletonMap(name, value), emptyMap());
    }

    public void updateStats(final Instant timestamp, final String measurement, final String name,
            final Map<String, String> tags, final Number value) {
        updateStats(timestamp, measurement, singletonMap(name, value), tags);
    }

    public void updateStats(final Instant timestamp, final String measurement, final Map<String, Number> values,
            final Map<String, String> tags) {

        final Point.Builder p = Point.measurement(measurement)
                .time(timestamp.toEpochMilli(), TimeUnit.MILLISECONDS);

        for (final Map.Entry<String, Number> entry : values.entrySet()) {
            p.addField(entry.getKey(), entry.getValue());
        }

        p.tag("host", HOSTNAME);
        tags.forEach(p::tag);

        this.db.write(p.build());
    }

}
