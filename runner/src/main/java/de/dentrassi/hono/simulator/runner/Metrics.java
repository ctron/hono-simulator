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
package de.dentrassi.hono.simulator.runner;

import static de.dentrassi.hono.demo.common.InfluxDbMetrics.createInstance;
import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.averagingLong;
import static java.util.stream.Collectors.summingLong;

import java.time.Duration;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.influxdb.dto.QueryResult;

import de.dentrassi.hono.demo.common.EventWriter;
import de.dentrassi.hono.demo.common.InfluxDbMetrics;

public class Metrics implements AutoCloseable {

    private final String type;
    private final InfluxDbMetrics metrics;

    public Metrics(final String type) {
        this.type = type;
        this.metrics = createInstance();
    }

    public EventWriter getEventWriter() {
        return this.metrics;
    }

    @Override
    public void close() {
        this.metrics.close();
    }

    private Stream<Number> singleQuery(final Duration duration, final String query) {

        final Duration offset = Duration.ofMinutes(1);
        final String timeRange = toTime(duration);
        final String timeOffset = toTime(offset);
        final String timeStart = toTime(duration.plus(offset));

        final String fullQuery = String.format(
                "SELECT %1$s WHERE (type = '%2$s') AND (time >= now() - %4$s) AND (time < now() - %3$s)  GROUP BY time(%5$s, %6$s)",
                query, this.type, timeOffset, timeStart, timeRange, "-" + timeOffset);

        // System.out.println(fullQuery);

        final QueryResult result = this.metrics.query(fullQuery);

        // System.out.println(result.getResults().get(0));

        return result.getResults().get(0).getSeries().get(0).getValues()
                .stream()
                .flatMap(l -> Stream.of(l.get(1)))
                .filter(value -> value != null)
                .map(e -> (Number) e);
    }

    private <T> T singleAggregatedQuery(final Duration duration, final String query,
            final Collector<Number, T, T> collector) {
        return singleQuery(duration, query).collect(collector);
    }

    private String toTime(final Duration duration) {
        return duration.toMillis() + "ms";
    }

    public double getFailureRate(final Duration duration) {
        return singleAggregatedQuery(
                duration,
                "mean(failureRatio) FROM autogen.\"http-publish\"",
                averagingDouble(Number::doubleValue));
    }

    public long getRtt(final Duration duration) {
        return singleAggregatedQuery(
                duration,
                "mean(avgDuration) FROM autogen.\"http-publish\"",
                averagingLong(Number::longValue)).longValue();
    }

    public long getReceivedMessages(final Duration duration) {
        return singleAggregatedQuery(
                duration,
                "sum(messageCount) FROM autogen.\"consumer\"",
                summingLong(Number::longValue)) / duration.getSeconds();
    }

    public long getSentMessages(final Duration duration) {
        return singleAggregatedQuery(
                duration,
                "sum(sent) FROM autogen.\"http-publish\"",
                summingLong(Number::longValue)) / duration.getSeconds();
    }

}
