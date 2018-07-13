package de.dentrassi.hono.simulator.http;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Register;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpDevice extends Device {

    private static final Logger logger = LoggerFactory.getLogger(OkHttpDevice.class);

    private static final boolean ASYNC = Boolean.parseBoolean(System.getenv().getOrDefault("HTTP_ASYNC", "false"));

    private final OkHttpClient client;

    private final RequestBody body;
    private final Request telemetryRequest;
    private final Request eventRequest;

    static {
        System.out.println("Running Async: " + ASYNC);
    }

    public OkHttpDevice(final String user, final String deviceId, final String tenant, final String password,
            final OkHttpClient client, final Register register, final Statistics telemetryStatistics,
            final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);

        this.client = client;

        this.body = RequestBody.create(JSON, "{foo: 42}");

        if ("POST".equals(METHOD)) {
            this.telemetryRequest = createPostRequest("/telemetry");
            this.eventRequest = createPostRequest("/event");
        } else {
            this.telemetryRequest = createPutRequest("telemetry");
            this.eventRequest = createPutRequest("event");
        }
    }

    private Request createPostRequest(final String type) {

        if (HONO_HTTP_URL == null) {
            return null;
        }

        final Request.Builder builder = new Request.Builder()
                .url(HONO_HTTP_URL.resolve(type))
                .post(this.body);

        if (!NOAUTH) {
            builder.header("Authorization", this.auth);
        }

        return builder.build();
    }

    private Request createPutRequest(final String type) {
        final Request.Builder builder = new Request.Builder()
                .url(
                        HONO_HTTP_URL.newBuilder()
                                .addPathSegment(type)
                                .addPathSegment(this.tenant)
                                .addPathSegment(this.deviceId)
                                .build())
                .put(this.body);

        if (!NOAUTH) {
            builder.header("Authorization", this.auth);
        }

        return builder.build();
    }

    private Call createTelemetryCall() {
        return this.client.newCall(this.telemetryRequest);
    }

    private Call createEventCall() {
        return this.client.newCall(this.eventRequest);
    }

    @Override
    public void tickTelemetry() {
        doTick(this::createTelemetryCall, this.telemetryStatistics);
    }

    @Override
    public void tickEvent() {
        doTick(this::createEventCall, this.eventStatistics);
    }

    private void doTick(final Supplier<Call> c, final Statistics s) {
        if (HONO_HTTP_URL == null) {
            return;
        }

        try {
            process(s, c);
        } catch (final Exception e) {
            logger.warn("Failed to tick", e);
        }

    }

    private void process(final Statistics statistics, final Supplier<Call> call) {
        statistics.sent();

        final Instant start = Instant.now();

        try {
            if (ASYNC) {
                publishAsync(statistics, call);
            } else {
                publishSync(statistics, call);
            }

        } catch (final Exception e) {
            statistics.failed();
            logger.debug("Failed to publish", e);
        } finally {
            final Duration dur = Duration.between(start, Instant.now());
            statistics.duration(dur);
        }
    }

    private void publishSync(final Statistics statistics, final Supplier<Call> callSupplier) throws IOException {
        try (final Response response = callSupplier.get().execute()) {
            if (response.isSuccessful()) {
                statistics.success();
                handleSuccess(response, statistics);
            } else {
                logger.trace("Result code: {}", response.code());
                statistics.failed();
                handleFailure(response, statistics);
            }
        }
    }

    private void publishAsync(final Statistics statistics, final Supplier<Call> callSupplier) {
        statistics.backlog();
        callSupplier.get().enqueue(new Callback() {

            @Override
            public void onResponse(final Call call, final Response response) throws IOException {
                statistics.backlogSent();
                if (response.isSuccessful()) {
                    statistics.success();
                    handleSuccess(response, statistics);
                } else {
                    logger.trace("Result code: {}", response.code());
                    statistics.failed();
                    handleFailure(response, statistics);
                }
                response.close();
            }

            @Override
            public void onFailure(final Call call, final IOException e) {
                statistics.backlogSent();
                statistics.failed();
                logger.debug("Failed to tick", e);
            }
        });
    }

}
