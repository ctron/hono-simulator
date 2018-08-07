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

import static de.dentrassi.hono.demo.common.Environment.get;
import static de.dentrassi.hono.demo.common.Environment.getAs;
import static java.lang.System.getenv;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfluxDbMetrics implements EventWriter, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(InfluxDbMetrics.class);

    private static final String HOSTNAME;

    private static final int BATCH_SIZE = getAs("INFLUXDB_METRICS_BATCH_SIZE", 10, Integer::parseInt);
    private static final int FLUSH_DURATION_SECONDS = getAs("INFLUXDB_METRICS_FLUSH_DURATION_SECONDS", 1,
            Integer::parseInt);

    static {
        String h = System.getenv("HOSTNAME");
        if (h == null) {
            h = UUID.randomUUID().toString();
        }
        HOSTNAME = h;
    }

    public static String makeInfluxDbUrl() {
        final String url = getenv("INFLUXDB_URL");
        if (url != null && !url.isEmpty()) {
            return url;
        }

        final String host = getenv("INFLUXDB_SERVICE_HOST");
        final String port = getenv("INFLUXDB_SERVICE_PORT_API");
        if (host != null && port != null) {
            return String.format("http://%s:%s", host, getenv("INFLUXDB_SERVICE_PORT_API"));
        } else {
            return null;
        }

    }

    public static Optional<InfluxDbMetrics> createInstance() {

        final String uri = makeInfluxDbUrl();

        if (uri == null) {
            return Optional.empty();
        }

        return Optional.of(new InfluxDbMetrics(uri,
                get("INFLUXDB_USER").orElse("user"),
                get("INFLUXDB_PASSWORD").orElse("password"),
                get("INFLUXDB_NAME").orElse("metrics")));
    }

    private final InfluxDB db;

    private final String databaseName;

    @SuppressWarnings("deprecation")
    public InfluxDbMetrics(final String uri, final String username, final String password,
            final String databaseName) {

        logger.info("InfluxDB - metrics - URL: {}", uri);
        logger.info("InfluxDB -      Database: {}", databaseName);

        this.db = InfluxDBFactory.connect(uri, username, password);
        this.db.enableBatch(BATCH_SIZE, FLUSH_DURATION_SECONDS, TimeUnit.SECONDS);
        logger.info("InfluxDB -      Batching: {} items, {} seconds", BATCH_SIZE, FLUSH_DURATION_SECONDS);

        this.databaseName = databaseName;

        if (!this.db.databaseExists(databaseName)) {
            this.db.createDatabase(databaseName);
        }

        this.db.setDatabase(databaseName);
    }

    @Override
    public void writeEvent(final Instant timestamp, final String table, final String title, final String description,
            final Map<String, String> tags) {

        final Builder p = Point.measurement(table);

        p.addField("title", title);
        if (description != null) {
            p.addField("description", description);
        }

        if (tags != null) {
            p.tag(tags);
        }

        this.db.write(p.build());

    }

    public QueryResult query(final String query) {
        return this.db.query(new Query(query, this.databaseName));
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

    @Override
    public void close() {
        this.db.close();
    }

}
