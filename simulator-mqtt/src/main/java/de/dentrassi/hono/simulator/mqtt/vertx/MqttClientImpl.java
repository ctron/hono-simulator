/*
 * Copyright 2016 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dentrassi.hono.simulator.mqtt.vertx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribePayload;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubscribePayload;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.NetSocketInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttConnectionException;
import io.vertx.mqtt.MqttException;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.netty.handler.codec.mqtt.MqttQoS.*;

/**
 * MQTT client implementation <br>
 * Vertx broke the failure on connect issue again. So this is a fork that works. And should get broken again by the next
 * vertx version.
 */
public class MqttClientImpl implements MqttClient {

  // patterns for topics validation
  private static final Pattern validTopicNamePattern = Pattern.compile("^[^#+\\u0000]+$");
  private static final Pattern validTopicFilterPattern = Pattern.compile("^(#|((\\+(?![^/]))?([^#+]*(/\\+(?![^/]))?)*(/#)?))$");
  private static final Logger log = LoggerFactory.getLogger(MqttClientImpl.class);

  private static final int MAX_MESSAGE_ID = 65535;
  private static final int MAX_TOPIC_LEN = 65535;
  private static final int MIN_TOPIC_LEN = 1;
  private static final String PROTOCOL_NAME = "MQTT";
  private static final int PROTOCOL_VERSION = 4;
  private static final int DEFAULT_IDLE_TIMEOUT = 0;

  private final MqttClientOptions options;
  private final NetClient client;
  private NetSocketInternal connection;
  private Context ctx;

  // handler to call when a publish is complete
  private Handler<Integer> publishCompletionHandler;
  // handler to call when a unsubscribe request is completed
  private Handler<Integer> unsubscribeCompletionHandler;
  // handler to call when a publish message comes in
  private Handler<MqttPublishMessage> publishHandler;
  // handler to call when a subscribe request is completed
  private Handler<MqttSubAckMessage> subscribeCompletionHandler;
  // handler to call when a connection request is completed
  private Handler<AsyncResult<MqttConnAckMessage>> connectHandler;
  // handler to call when a pingresp is received
  private Handler<Void> pingrespHandler;
  // handler to call when a problem at protocol level happens
  private Handler<Throwable> exceptionHandler;
  //handler to call when the remote MQTT server closes the connection
  private Handler<Void> closeHandler;

  // storage of PUBLISH QoS=1 messages which was not responded with PUBACK
  private final LinkedHashMap<Integer, io.netty.handler.codec.mqtt.MqttMessage> qos1outbound = new LinkedHashMap<>();

  // storage of PUBLISH QoS=2 messages which was not responded with PUBREC
  // and PUBREL messages which was not responded with PUBCOMP
  private final LinkedHashMap<Integer, io.netty.handler.codec.mqtt.MqttMessage> qos2outbound = new LinkedHashMap<>();

  // storage of PUBLISH messages which was responded with PUBREC
  private final LinkedHashMap<Integer, MqttMessage> qos2inbound = new LinkedHashMap<>();

  // counter for the message identifier
  private int messageIdCounter;

  // total amount of unacknowledged packets
  private int countInflightQueue;

  private boolean isConnected;

  /**
   * Constructor
   *
   * @param vertx Vert.x instance
   * @param options MQTT client options
   */
  public MqttClientImpl(final Vertx vertx, final MqttClientOptions options) {

    // copy given options
    final NetClientOptions netClientOptions = new NetClientOptions(options);
    netClientOptions.setIdleTimeout(DEFAULT_IDLE_TIMEOUT);

    this.client = vertx.createNetClient(netClientOptions);
    this.options = options;
  }

  /**
   * See {@link MqttClient#connect(int, String, Handler)} for more details
   */
  @Override
  public MqttClient connect(final int port, final String host, final Handler<AsyncResult<MqttConnAckMessage>> connectHandler) {

    this.doConnect(port, host, null, connectHandler);
    return this;
  }

  /**
   * See {@link MqttClient#connect(int, String, String, Handler)} for more details
   */
  @Override
  public MqttClient connect(final int port, final String host, final String serverName, final Handler<AsyncResult<MqttConnAckMessage>> connectHandler) {

    this.doConnect(port, host, serverName, connectHandler);
    return this;
  }

  private void doConnect(final int port, final String host, final String serverName, final Handler<AsyncResult<MqttConnAckMessage>> connectHandler) {

    log.debug(String.format("Trying to connect with %s:%d", host, port));
    this.client.connect(port, host, serverName, done -> {

      // the TCP connection fails
      if (done.failed()) {
        log.error(String.format("Can't connect to %s:%d", host, port), done.cause());
        if (connectHandler != null) {
          connectHandler.handle(Future.failedFuture(done.cause()));
        }
      } else {
        log.info(String.format("Connection with %s:%d established successfully", host, port));

        final NetSocketInternal soi = (NetSocketInternal) done.result();
        final ChannelPipeline pipeline = soi.channelHandlerContext().pipeline();
        this.connectHandler = connectHandler;

        if (options.isAutoGeneratedClientId() && (options.getClientId() == null || options.getClientId().isEmpty())) {
          options.setClientId(generateRandomClientId());
        }

        initChannel(pipeline);
        this.connection = soi;
        this.ctx = Vertx.currentContext();

        soi.messageHandler(msg -> this.handleMessage(soi.channelHandlerContext(), msg));
                soi.closeHandler(v -> handleClosed());

        // an exception at connection level
        soi.exceptionHandler(this::handleException);

        final MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.CONNECT,
          false,
          AT_MOST_ONCE,
          false,
          0);

        final MqttConnectVariableHeader variableHeader = new MqttConnectVariableHeader(
          PROTOCOL_NAME,
          PROTOCOL_VERSION,
          options.hasUsername(),
          options.hasPassword(),
          options.isWillRetain(),
          options.getWillQoS(),
          options.isWillFlag(),
          options.isCleanSession(),
          options.getKeepAliveTimeSeconds()
        );

        final MqttConnectPayload payload = new MqttConnectPayload(
          options.getClientId() == null ? "" : options.getClientId(),
          options.getWillTopic(),
          options.getWillMessage() != null ? options.getWillMessage().getBytes(StandardCharsets.UTF_8) : null,
          options.hasUsername() ? options.getUsername() : null,
          options.hasPassword() ? options.getPassword().getBytes() : null
        );

        final io.netty.handler.codec.mqtt.MqttMessage connect = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

        this.write(connect);
      }

    });
  }

  /**
   * See {@link MqttClient#disconnect()} for more details
   */
  @Override
  public MqttClient disconnect() {
    return disconnect(null);
  }

  /**
   * See {@link MqttClient#disconnect(Handler)} for more details
   */
  @Override
  public MqttClient disconnect(final Handler<AsyncResult<Void>> disconnectHandler) {

        log.info("Request to close connection: " + this.connection.channelHandlerContext());

    final MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.DISCONNECT,
      false,
      AT_MOST_ONCE,
      false,
      0
    );

    final io.netty.handler.codec.mqtt.MqttMessage disconnect = MqttMessageFactory.newMessage(fixedHeader, null, null);

    this.write(disconnect);

    if (disconnectHandler != null) {
      disconnectHandler.handle(Future.succeededFuture());
    }
    connection().close();
    return this;
  }

  /**
   * See {@link MqttClient#publish(String, Buffer, MqttQoS, boolean, boolean)} for more details
   */
  @Override
  public MqttClient publish(final String topic, final Buffer payload, final MqttQoS qosLevel, final boolean isDup, final boolean isRetain) {
    return publish(topic, payload, qosLevel, isDup, isRetain, null);
  }

  /**
   * See {@link MqttClient#publish(String, Buffer, MqttQoS, boolean, boolean, Handler)} for more details
   */
  @Override
  public MqttClient publish(final String topic, final Buffer payload, final MqttQoS qosLevel, final boolean isDup, final boolean isRetain, final Handler<AsyncResult<Integer>> publishSentHandler) {

    io.netty.handler.codec.mqtt.MqttMessage publish;
    MqttPublishVariableHeader variableHeader;
    synchronized (this) {
      if (countInflightQueue >= options.getMaxInflightQueue()) {
        final String msg = String.format("Attempt to exceed the limit of %d inflight messages", options.getMaxInflightQueue());
        log.error(msg);
        final MqttException exception = new MqttException(MqttException.MQTT_INFLIGHT_QUEUE_FULL, msg);
        if (publishSentHandler != null) {
          ctx.runOnContext(v -> publishSentHandler.handle(Future.failedFuture(exception)));
        }
        return this;
      }

      if (!isValidTopicName(topic)) {
        final String msg = String.format("Invalid Topic Name - %s. It mustn't contains wildcards: # and +. Also it can't contains U+0000(NULL) chars", topic);
        log.error(msg);
        final MqttException exception = new MqttException(MqttException.MQTT_INVALID_TOPIC_NAME, msg);
        if (publishSentHandler != null) {
          ctx.runOnContext(v -> publishSentHandler.handle(Future.failedFuture(exception)));
        }
        return this;
      }

      final MqttFixedHeader fixedHeader = new MqttFixedHeader(
        MqttMessageType.PUBLISH,
        isDup,
        qosLevel,
        isRetain,
        0
      );
      final ByteBuf buf = Unpooled.copiedBuffer(payload.getBytes());
      variableHeader = new MqttPublishVariableHeader(topic, nextMessageId());
      publish = MqttMessageFactory.newMessage(fixedHeader, variableHeader, buf);
      switch (qosLevel) {
        case AT_LEAST_ONCE:
          qos1outbound.put(variableHeader.messageId(), publish);
          countInflightQueue++;
          break;
        case EXACTLY_ONCE:
          qos2outbound.put(variableHeader.messageId(), publish);
          countInflightQueue++;
          break;
      }
    }

    this.write(publish);
    if (publishSentHandler != null) {
      publishSentHandler.handle(Future.succeededFuture(variableHeader.messageId()));
    }

    return this;
  }

  /**
   * See {@link MqttClient#publishCompletionHandler(Handler)} for more details
   */
  @Override
  public MqttClient publishCompletionHandler(final Handler<Integer> publishCompletionHandler) {

    this.publishCompletionHandler = publishCompletionHandler;
    return this;
  }

  private synchronized Handler<Integer> publishCompletionHandler() {
    return this.publishCompletionHandler;
  }

  /**
   * See {@link MqttClient#publishHandler(Handler)} for more details
   */
  @Override
  public MqttClient publishHandler(final Handler<MqttPublishMessage> publishHandler) {

    this.publishHandler = publishHandler;
    return this;
  }

  private synchronized Handler<MqttPublishMessage> publishHandler() {
    return this.publishHandler;
  }

  /**
   * See {@link MqttClient#subscribeCompletionHandler(Handler)} for more details
   */
  @Override
  public MqttClient subscribeCompletionHandler(final Handler<MqttSubAckMessage> subscribeCompletionHandler) {

    this.subscribeCompletionHandler = subscribeCompletionHandler;
    return this;
  }

  private synchronized Handler<MqttSubAckMessage> subscribeCompletionHandler() {
    return this.subscribeCompletionHandler;
  }

  /**
   * See {@link MqttClient#subscribe(String, int)} for more details
   */
  @Override
  public MqttClient subscribe(final String topic, final int qos) {
    return subscribe(topic, qos, null);
  }

  /**
   * See {@link MqttClient#subscribe(String, int, Handler)} for more details
   */
  @Override
  public MqttClient subscribe(final String topic, final int qos, final Handler<AsyncResult<Integer>> subscribeSentHandler) {
    return subscribe(Collections.singletonMap(topic, qos), subscribeSentHandler);
  }

  /**
   * See {@link MqttClient#subscribe(Map)} for more details
   */
  @Override
  public MqttClient subscribe(final Map<String, Integer> topics) {
    return subscribe(topics, null);
  }

  /**
   * See {@link MqttClient#subscribe(Map, Handler)} for more details
   */
  @Override
  public MqttClient subscribe(final Map<String, Integer> topics, final Handler<AsyncResult<Integer>> subscribeSentHandler) {

    final Map<String, Integer> invalidTopics = topics.entrySet()
      .stream()
      .filter(e -> !isValidTopicFilter(e.getKey()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (invalidTopics.size() > 0) {
      final String msg = String.format("Invalid Topic Filters: %s", invalidTopics);
      log.error(msg);
      final MqttException exception = new MqttException(MqttException.MQTT_INVALID_TOPIC_FILTER, msg);
      if (subscribeSentHandler != null) {
        subscribeSentHandler.handle(Future.failedFuture(exception));
      }
      return this;
    }

    final MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.SUBSCRIBE,
      false,
      AT_LEAST_ONCE,
      false,
      0);

    final MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(nextMessageId());
    final List<MqttTopicSubscription> subscriptions = topics.entrySet()
      .stream()
      .map(e -> new MqttTopicSubscription(e.getKey(), valueOf(e.getValue())))
      .collect(Collectors.toList());

    final MqttSubscribePayload payload = new MqttSubscribePayload(subscriptions);

    final io.netty.handler.codec.mqtt.MqttMessage subscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

    this.write(subscribe);

    if (subscribeSentHandler != null) {
      subscribeSentHandler.handle(Future.succeededFuture(variableHeader.messageId()));
    }
    return this;
  }

  /**
   * See {@link MqttClient#unsubscribeCompletionHandler(Handler)} for more details
   */
  @Override
  public MqttClient unsubscribeCompletionHandler(final Handler<Integer> unsubscribeCompletionHandler) {

    this.unsubscribeCompletionHandler = unsubscribeCompletionHandler;
    return this;
  }

  private synchronized Handler<Integer> unsubscribeCompletionHandler() {

    return this.unsubscribeCompletionHandler;
  }

  /**
   * See {@link MqttClient#unsubscribe(String, Handler)} )} for more details
   */
  @Override
  public MqttClient unsubscribe(final String topic, final Handler<AsyncResult<Integer>> unsubscribeSentHandler) {

    final MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.UNSUBSCRIBE,
      false,
      AT_LEAST_ONCE,
      false,
      0);

    final MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(nextMessageId());

    final MqttUnsubscribePayload payload = new MqttUnsubscribePayload(Stream.of(topic).collect(Collectors.toList()));

    final io.netty.handler.codec.mqtt.MqttMessage unsubscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

    this.write(unsubscribe);

    if (unsubscribeSentHandler != null) {
      unsubscribeSentHandler.handle(Future.succeededFuture(variableHeader.messageId()));
    }
    return this;
  }

  private synchronized Handler<AsyncResult<MqttConnAckMessage>> connectHandler() {
    return this.connectHandler;
  }

  /**
   * See {@link MqttClient#unsubscribe(String)} )} for more details
   */
  @Override
  public MqttClient unsubscribe(final String topic) {
    return this.unsubscribe(topic, null);
  }

  /**
   * See {@link MqttClient#pingResponseHandler(Handler)} for more details
   */
  @Override
  public synchronized MqttClient pingResponseHandler(final Handler<Void> pingResponseHandler) {
    this.pingrespHandler = pingResponseHandler;
    return this;
  }

  private synchronized Handler<Void> pingResponseHandler() {
    return this.pingrespHandler;
  }

  /**
   * See {@link MqttClient#exceptionHandler(Handler)} for more details
   */
  @Override
  public synchronized MqttClient exceptionHandler(final Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  private synchronized Handler<Throwable> exceptionHandler() {
    return this.exceptionHandler;
  }

  /**
   * See {@link MqttClient#closeHandler(Handler)} for more details
   */
  @Override
  public synchronized MqttClient closeHandler(final Handler<Void> closeHandler) {
    this.closeHandler = closeHandler;
    return this;
  }

  private synchronized Handler<Void> closeHandler() {
    return this.closeHandler;
  }

  /**
   * See {@link MqttClient#ping()} for more details
   */
  @Override
  public MqttClient ping() {

    final MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0);

    final io.netty.handler.codec.mqtt.MqttMessage pingreq = MqttMessageFactory.newMessage(fixedHeader, null, null);

    this.write(pingreq);

    return this;
  }

  @Override
  public synchronized String clientId() {
    return this.options.getClientId();
  }

  @Override
  public synchronized boolean isConnected() {
    return this.isConnected;
  }

  /**
   * Sends PUBACK packet to server
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   */
  private void publishAcknowledge(final int publishMessageId) {

    final MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBACK, false, AT_MOST_ONCE, false, 0);

    final MqttMessageIdVariableHeader variableHeader =
      MqttMessageIdVariableHeader.from(publishMessageId);

    final io.netty.handler.codec.mqtt.MqttMessage puback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    this.write(puback);
  }

  /**
   * Sends PUBREC packet to server
   *
   * @param publishMessage a PUBLISH message to acknowledge
   */
  private void publishReceived(final MqttPublishMessage publishMessage) {

    final MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBREC, false, AT_MOST_ONCE, false, 0);

    final MqttMessageIdVariableHeader variableHeader =
      MqttMessageIdVariableHeader.from(publishMessage.messageId());

    final io.netty.handler.codec.mqtt.MqttMessage pubrec = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    synchronized (this) {
      qos2inbound.put(publishMessage.messageId(), publishMessage);
    }
    this.write(pubrec);
  }

  /**
   * Sends PUBCOMP packet to server
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   */
  private void publishComplete(final int publishMessageId) {

    final MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBCOMP, false, AT_MOST_ONCE, false, 0);

    final MqttMessageIdVariableHeader variableHeader =
      MqttMessageIdVariableHeader.from(publishMessageId);

    final io.netty.handler.codec.mqtt.MqttMessage pubcomp = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    this.write(pubcomp);
  }

  /**
   * Sends the PUBREL message to server
   *
   * @param publishMessageId  identifier of the PUBLISH message to acknowledge
   */
  private void publishRelease(final int publishMessageId) {

    final MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);

    final MqttMessageIdVariableHeader variableHeader =
      MqttMessageIdVariableHeader.from(publishMessageId);

    final io.netty.handler.codec.mqtt.MqttMessage pubrel = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    synchronized (this) {
      qos2outbound.put(publishMessageId, pubrel);
    }
    this.write(pubrel);
  }

  private void initChannel(final ChannelPipeline pipeline) {

    // add into pipeline netty's (en/de)coder
    pipeline.addBefore("handler", "mqttEncoder", MqttEncoder.INSTANCE);

    if (this.options.getMaxMessageSize() > 0) {
      pipeline.addBefore("handler", "mqttDecoder", new MqttDecoder(this.options.getMaxMessageSize()));
    } else {
      // max message size not set, so the default from Netty MQTT codec is used
      pipeline.addBefore("handler", "mqttDecoder", new MqttDecoder());
    }

    if (this.options.isAutoKeepAlive() &&
      this.options.getKeepAliveTimeSeconds() != 0) {

      pipeline.addBefore("handler", "idle",
                    new IdleStateHandler(this.options.getKeepAliveTimeSeconds(), 0, 0));
      pipeline.addBefore("handler", "keepAliveHandler", new ChannelDuplexHandler() {

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {

          if (evt instanceof IdleStateEvent) {
            final IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                log.info("Sending ping: " + ctx);
                ping();
            }
          }
        }
      });
    }
  }

    /**
     * Update and return the next message identifier
     *
     * @return message identifier
     */
  private synchronized int nextMessageId() {

    // if 0 or MAX_MESSAGE_ID, it becomes 1 (first valid messageId)
    this.messageIdCounter = ((this.messageIdCounter % MAX_MESSAGE_ID) != 0) ? this.messageIdCounter + 1 : 1;
    return this.messageIdCounter;
  }

  private synchronized NetSocketInternal connection() {
    return connection;
  }

  void write(final io.netty.handler.codec.mqtt.MqttMessage mqttMessage) {
    log.debug(String.format("Sending packet %s", mqttMessage));
    this.connection().writeMessage(mqttMessage);
  }

    /**
     * Used for calling the close handler when the remote MQTT server closes the connection
     */
    private void handleClosed() {
        log.info("Connection closed: " + this.connection.channelHandlerContext());
    synchronized (this) {
      final boolean isConnected = this.isConnected;
      this.isConnected = false;
      // ctron: always call close handler,
      /*
      if (!isConnected) {
        return;
      }
      */
    }
    final Handler<Void> handler = closeHandler();
    if (handler != null) {
      handler.handle(null);
    }
  }

  /**
   * Handle the MQTT message received from the remote MQTT server
   *
   * @param msg Incoming Packet
   */
  private void handleMessage(final ChannelHandlerContext chctx, final Object msg) {

    // handling directly native Netty MQTT messages, some of them are translated
    // to the related Vert.x ones for polyglotization
    if (msg instanceof io.netty.handler.codec.mqtt.MqttMessage) {

      final io.netty.handler.codec.mqtt.MqttMessage mqttMessage = (io.netty.handler.codec.mqtt.MqttMessage) msg;

      final DecoderResult result = mqttMessage.decoderResult();
      if (result.isFailure()) {
        chctx.pipeline().fireExceptionCaught(result.cause());
        return;
      }
      if (!result.isFinished()) {
        chctx.pipeline().fireExceptionCaught(new Exception("Unfinished message"));
        return;
      }

      log.debug(String.format("Incoming packet %s", msg));
      switch (mqttMessage.fixedHeader().messageType()) {

        case CONNACK:

          final io.netty.handler.codec.mqtt.MqttConnAckMessage connack = (io.netty.handler.codec.mqtt.MqttConnAckMessage) mqttMessage;

          final MqttConnAckMessage mqttConnAckMessage = MqttConnAckMessage.create(
            connack.variableHeader().connectReturnCode(),
            connack.variableHeader().isSessionPresent());
          handleConnack(mqttConnAckMessage);
          break;

        case PUBLISH:

          final io.netty.handler.codec.mqtt.MqttPublishMessage publish = (io.netty.handler.codec.mqtt.MqttPublishMessage) mqttMessage;
          final ByteBuf newBuf = VertxHandler.safeBuffer(publish.payload(), chctx.alloc());

          final MqttPublishMessage mqttPublishMessage = MqttPublishMessage.create(
            publish.variableHeader().messageId(),
            publish.fixedHeader().qosLevel(),
            publish.fixedHeader().isDup(),
            publish.fixedHeader().isRetain(),
            publish.variableHeader().topicName(),
            newBuf);
          handlePublish(mqttPublishMessage);
          break;

        case PUBACK:
          handlePuback(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
          break;

        case PUBREC:
          handlePubrec(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
          break;

        case PUBREL:
          handlePubrel(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
          break;

        case PUBCOMP:
          handlePubcomp(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
          break;

        case SUBACK:

          final io.netty.handler.codec.mqtt.MqttSubAckMessage unsuback = (io.netty.handler.codec.mqtt.MqttSubAckMessage) mqttMessage;

          final MqttSubAckMessage mqttSubAckMessage = MqttSubAckMessage.create(
            unsuback.variableHeader().messageId(),
            unsuback.payload().grantedQoSLevels());
          handleSuback(mqttSubAckMessage);
          break;

        case UNSUBACK:
          handleUnsuback(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
          break;

        case PINGRESP:
          handlePingresp();
          break;

        default:

          chctx.pipeline().fireExceptionCaught(new Exception("Wrong message type " + msg.getClass().getName()));
          break;
      }

    } else {

      chctx.pipeline().fireExceptionCaught(new Exception("Wrong message type"));
    }
  }

  /**
   * Used for calling the pingresp handler when the server replies to the ping
   */
  private void handlePingresp() {
        log.debug("Received pingresp: " + this.connection.channelHandlerContext());
    final Handler<Void> handler = pingResponseHandler();
    if (handler != null) {
      handler.handle(null);
    }
  }

  /**
   * Used for calling the unsuback handler when the server acks an unsubscribe
   *
   * @param unsubackMessageId identifier of the subscribe acknowledged by the server
   */
  private void handleUnsuback(final int unsubackMessageId) {

    final Handler<Integer> handler = unsubscribeCompletionHandler();
    if (handler != null) {
      handler.handle(unsubackMessageId);
    }
  }

  /**
   * Used for calling the puback handler when the server acknowledge a QoS 1 message with puback
   *
   * @param pubackMessageId identifier of the message acknowledged by the server
   */
  private void handlePuback(final int pubackMessageId) {

    synchronized (this) {

      final io.netty.handler.codec.mqtt.MqttMessage removedPacket = qos1outbound.remove(pubackMessageId);

      if (removedPacket == null) {
        log.warn("Received PUBACK packet without having related PUBLISH packet in storage");
        return;
      }

      countInflightQueue--;
    }
    final Handler<Integer> handler = publishCompletionHandler();
    if (handler != null) {
      handler.handle(pubackMessageId);
    }
  }

  /**
   * Used for calling the pubcomp handler when the server client acknowledge a QoS 2 message with pubcomp
   *
   * @param pubcompMessageId identifier of the message acknowledged by the server
   */
  private void handlePubcomp(final int pubcompMessageId) {

    synchronized (this) {
      final io.netty.handler.codec.mqtt.MqttMessage removedPacket = qos2outbound.remove(pubcompMessageId);

      if (removedPacket == null) {
        log.warn("Received PUBCOMP packet without having related PUBREL packet in storage");
        return;
      }

      countInflightQueue--;
    }
    final Handler<Integer> handler = publishCompletionHandler();
    if (handler != null) {
      handler.handle(pubcompMessageId);
    }
  }

  /**
   * Used for sending the pubrel when a pubrec is received from the server
   *
   * @param pubrecMessageId identifier of the message acknowledged by server
   */
  private void handlePubrec(final int pubrecMessageId) {

    this.publishRelease(pubrecMessageId);
  }

  /**
   * Used for calling the suback handler when the server acknoweldge subscribe to topics
   *
   * @param msg message with suback information
   */
  private void handleSuback(final MqttSubAckMessage msg) {

    final Handler<MqttSubAckMessage> handler = subscribeCompletionHandler();
    if (handler != null) {
      handler.handle(msg);
    }
  }

  /**
   * Used for calling the publish handler when the server publishes a message
   *
   * @param msg published message
   */
  private void handlePublish(final MqttPublishMessage msg) {

    Handler<MqttPublishMessage> handler;
    switch (msg.qosLevel()) {

      case AT_MOST_ONCE:
        handler = this.publishHandler();
        if (handler != null) {
          handler.handle(msg);
        }
        break;

      case AT_LEAST_ONCE:
        this.publishAcknowledge(msg.messageId());
        handler = this.publishHandler();
        if (handler != null) {
          handler.handle(msg);
        }
        break;

      case EXACTLY_ONCE:
        this.publishReceived(msg);
        // we will handle the PUBLISH when a PUBREL comes
        break;
    }
  }

  /**
   * Used for calling the pubrel handler when the server acknowledge a QoS 2 message with pubrel
   *
   * @param pubrelMessageId identifier of the message acknowledged by the server
   */
  private void handlePubrel(final int pubrelMessageId) {
    MqttMessage message;
    synchronized (this) {
      message = qos2inbound.get(pubrelMessageId);

      if (message == null) {
        log.warn("Received PUBREL packet without having related PUBREC packet in storage");
        return;
      }
      this.publishComplete(pubrelMessageId);
    }
    final Handler<MqttPublishMessage> handler = this.publishHandler();
    if (handler != null) {
      handler.handle((MqttPublishMessage) message);
    }
  }

  /**
   * Used for calling the connect handler when the server replies to the request
   *
   * @param msg  connection response message
   */
  private void handleConnack(final MqttConnAckMessage msg) {

    synchronized (this) {
      this.isConnected = msg.code() == MqttConnectReturnCode.CONNECTION_ACCEPTED;
    }

    final Handler<AsyncResult<MqttConnAckMessage>> handler = connectHandler();
    if (handler != null) {

      if (msg.code() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
        handler.handle(Future.succeededFuture(msg));
      } else {
        final MqttConnectionException exception = new MqttConnectionException(msg.code());
        log.error(String.format("Connection refused by the server - code: %s", msg.code()));
        handler.handle(Future.failedFuture(exception));
      }
    }
  }

  /**
   * Used for calling the exception handler when an error at connection level
   *
   * @param t exception raised
   */
  private void handleException(final Throwable t) {

    final Handler<Throwable> handler = exceptionHandler();
    if (handler != null) {
      handler.handle(t);
    }
  }

  /**
   * @return Randomly-generated ClientId
   */
  private String generateRandomClientId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Check either given Topic Name valid of not
   *
   * @param topicName given Topic Name
   * @return true - valid, otherwise - false
   */
  private boolean isValidTopicName(final String topicName) {
    if(!isValidStringSizeInUTF8(topicName)){
      return false;
    }

    final Matcher matcher = validTopicNamePattern.matcher(topicName);
    return matcher.find();
  }

  /**
   * Check either given Topic Filter valid of not
   *
   * @param topicFilter given Topic Filter
   * @return true - valid, otherwise - false
   */
  private boolean isValidTopicFilter(final String topicFilter) {
    if(!isValidStringSizeInUTF8(topicFilter)){
      return false;
    }

    final Matcher matcher = validTopicFilterPattern.matcher(topicFilter);
    return matcher.find();
  }

  /**
   * Check either given string has size more then 65535 bytes in UTF-8 Encoding
   * @param string given string
   * @return true - size is lower or equal than 65535, otherwise - false
   */
  private boolean isValidStringSizeInUTF8(final String string) {
    try {
      final int length = string.getBytes("UTF-8").length;
      return length >= MIN_TOPIC_LEN && length <= MAX_TOPIC_LEN;
    } catch (final UnsupportedEncodingException e) {
      log.error("UTF-8 charset is not supported", e);
    }

    return false;
  }
}
