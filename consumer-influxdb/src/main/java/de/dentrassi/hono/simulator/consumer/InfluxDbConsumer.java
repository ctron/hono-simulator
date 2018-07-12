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
package de.dentrassi.hono.simulator.consumer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.message.Message;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.Json;

public class InfluxDbConsumer {

    private static final Logger logger = LoggerFactory.getLogger(InfluxDbConsumer.class);

    private final InfluxDB db;

    private final int batchSize = Integer.parseInt(System.getenv().getOrDefault("INFLUXDB_BATCH_SIZE", "20"));

    public InfluxDbConsumer(final String uri, final String username, final String password,
            final String databaseName) {

        logger.info("InfluxDB - payload - URL: {}", uri);
        logger.info("           payload - batch size: {}", this.batchSize);

        this.db = InfluxDBFactory.connect(uri, username, password);

        if (!this.db.databaseExists(databaseName)) {
            this.db.createDatabase(databaseName);
        }

        this.db.setDatabase(databaseName);

        this.db.enableBatch(this.batchSize, 1000, TimeUnit.MILLISECONDS);
    }

    public void consume(final Message msg, final String string) {
        final Map<?, ?> values = Json.decodeValue(string, Map.class);

        final Point.Builder p = Point.measurement("P").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS);

        for (final Map.Entry<Symbol, ?> entry : msg.getMessageAnnotations().getValue().entrySet()) {
            if (entry.getValue() instanceof String) {
                p.tag(entry.getKey().toString(), "" + entry.getValue());
            }
        }

        for (final Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                continue;
            }

            final String key = (String) entry.getKey();
            final Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            if (value instanceof Number) {
                p.addField(key, (Number) value);
            } else if (value instanceof String) {
                try {
                    p.addField(key, Double.parseDouble((String) value));
                } catch (final Exception e) {
                    logger.debug("Failed to parse metric", e);
                }
            }
        }

        this.db.write(p.build());
    }

}
