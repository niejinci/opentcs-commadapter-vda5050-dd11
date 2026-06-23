// SPDX-FileCopyrightText: The openTCS Authors
// SPDX-License-Identifier: MIT
package org.opentcs.commadapter.vehicle.vda5050.v2_0;

import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.annotation.Nonnull;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.action.CancelOrder;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.common.Action;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.instantactions.InstantActions;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.order.Order;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.state.ActionStatus;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.state.OperatingMode;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.state.State;
import org.opentcs.drivers.vehicle.MovementCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches a state messages with sent order messages to confirm their delivery.
 */
public class MessageResponseMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(MessageResponseMatcher.class);
  /**
   * The comm adapter.
   */
  private final String commAdapterName;
  /**
   * Queue for requests that need to be sent to the vehicle.
   */
  private final Queue<Object> requests = new ArrayDeque<>();
  /**
   * The callback for sending the next order.
   */
  private final Consumer<Order> sendOrderCallback;
  /**
   * The callback for sending the next instant actions.
   */
  private final Consumer<InstantActions> sendInstantActionsCallback;
  /**
   * The callback for when an order is accepted by the vehicle.
   */
  private final Consumer<OrderAssociation> orderAcceptedCallback;
  /**
   * The maximum number of consecutive state messages that indicate a rejection of the current
   * order/message before we consider the rejection to be permanent and stop retrying.
   */
  private final int maxIgnoredRejectionsCount;
  /**
   * Minimum time to wait before resending an unacknowledged order, in milliseconds.
   */
  private final long orderResendTimeoutMs;
  /**
   * Whether instant actions may be resent while waiting for acknowledgement.
   */
  private final boolean instantActionsResendEnabled;
  /**
   * Minimum time to wait before resending unacknowledged instant actions, in milliseconds.
   */
  private final long instantActionsResendIntervalMs;
  /**
   * Maximum number of times an instant action request may be sent.
   */
  private final int instantActionsMaxSendAttempts;
  /**
   * Maximum time to wait for acknowledgement of instant actions, in milliseconds.
   */
  private final long instantActionsAckTimeoutMs;
  /**
   * Provides the current time in milliseconds.
   */
  private final LongSupplier currentTimeMillis;
  /**
   * The number of consecutive state messages that indicate a rejection of the current order/message
   * we have received so far.
   */
  private int consecutiveRejectionsCount;
  /**
   * The request most recently sent to the vehicle.
   */
  private Object lastSentRequest;
  /**
   * The timestamp at which {@link #lastSentRequest} was sent.
   */
  private long lastSentRequestTimestamp;
  /**
   * The timestamp at which the current instant actions request was sent for the first time.
   */
  private long firstInstantActionsSentTimestamp = -1;
  /**
   * The number of times the current instant actions request was sent.
   */
  private int instantActionsSendAttempts;
  /**
   * Flag indicating whether this comm adapter may currently send requests to the vehicle.
   * If false, all enqueued requests will stay in the queue until the flag becomes true.
   */
  private boolean sendingAllowed;

  /**
   * Creates a new OrderResponseMatcher.
   *
   * @param commAdapterName The name of the comm adapter
   * @param sendOrderCallback The callback for sending the next order.
   * @param sendInstantActionsCallback The callback for sending instant actions.
   * @param orderAcceptedCallback The callback for when the order is accepted by the vehicle.
   * @param maxIgnoredRejectionsCount The maximum number of consecutive state messages that
   * indicate a rejection of the current order/message before we consider the rejection to be
   * permanent and stop retrying.
   */
  public MessageResponseMatcher(
      @Nonnull
      String commAdapterName,
      @Nonnull
      Consumer<Order> sendOrderCallback,
      @Nonnull
      Consumer<InstantActions> sendInstantActionsCallback,
      @Nonnull
      Consumer<OrderAssociation> orderAcceptedCallback,
      int maxIgnoredRejectionsCount
  ) {
    this(
        commAdapterName,
        sendOrderCallback,
        sendInstantActionsCallback,
        orderAcceptedCallback,
        maxIgnoredRejectionsCount,
        0
    );
  }

  /**
   * Creates a new OrderResponseMatcher.
   *
   * @param commAdapterName The name of the comm adapter
   * @param sendOrderCallback The callback for sending the next order.
   * @param sendInstantActionsCallback The callback for sending instant actions.
   * @param orderAcceptedCallback The callback for when the order is accepted by the vehicle.
   * @param maxIgnoredRejectionsCount The maximum number of consecutive state messages that
   * indicate a rejection of the current order/message before we consider the rejection to be
   * permanent and stop retrying.
   * @param orderResendTimeoutMs Minimum interval before resending an unacknowledged order.
   */
  public MessageResponseMatcher(
      @Nonnull
      String commAdapterName,
      @Nonnull
      Consumer<Order> sendOrderCallback,
      @Nonnull
      Consumer<InstantActions> sendInstantActionsCallback,
      @Nonnull
      Consumer<OrderAssociation> orderAcceptedCallback,
      int maxIgnoredRejectionsCount,
      long orderResendTimeoutMs
  ) {
    this(
        commAdapterName,
        sendOrderCallback,
        sendInstantActionsCallback,
        orderAcceptedCallback,
        maxIgnoredRejectionsCount,
        orderResendTimeoutMs,
        false,
        5000,
        1,
        30000,
        System::currentTimeMillis
    );
  }

  /**
   * Creates a new OrderResponseMatcher.
   *
   * @param commAdapterName The name of the comm adapter
   * @param sendOrderCallback The callback for sending the next order.
   * @param sendInstantActionsCallback The callback for sending instant actions.
   * @param orderAcceptedCallback The callback for when the order is accepted by the vehicle.
   * @param maxIgnoredRejectionsCount The maximum number of consecutive state messages that
   * indicate a rejection of the current order/message before we consider the rejection to be
   * permanent and stop retrying.
   * @param orderResendTimeoutMs Minimum interval before resending an unacknowledged order.
   * @param instantActionsResendEnabled Whether instant actions may be resent.
   * @param instantActionsResendIntervalMs Minimum interval before resending instant actions.
   * @param instantActionsMaxSendAttempts Maximum number of send attempts for instant actions.
   * @param instantActionsAckTimeoutMs Maximum time to wait for instant actions acknowledgement.
   */
  public MessageResponseMatcher(
      @Nonnull
      String commAdapterName,
      @Nonnull
      Consumer<Order> sendOrderCallback,
      @Nonnull
      Consumer<InstantActions> sendInstantActionsCallback,
      @Nonnull
      Consumer<OrderAssociation> orderAcceptedCallback,
      int maxIgnoredRejectionsCount,
      long orderResendTimeoutMs,
      boolean instantActionsResendEnabled,
      long instantActionsResendIntervalMs,
      int instantActionsMaxSendAttempts,
      long instantActionsAckTimeoutMs
  ) {
    this(
        commAdapterName,
        sendOrderCallback,
        sendInstantActionsCallback,
        orderAcceptedCallback,
        maxIgnoredRejectionsCount,
        orderResendTimeoutMs,
        instantActionsResendEnabled,
        instantActionsResendIntervalMs,
        instantActionsMaxSendAttempts,
        instantActionsAckTimeoutMs,
        System::currentTimeMillis
    );
  }

  MessageResponseMatcher(
      @Nonnull
      String commAdapterName,
      @Nonnull
      Consumer<Order> sendOrderCallback,
      @Nonnull
      Consumer<InstantActions> sendInstantActionsCallback,
      @Nonnull
      Consumer<OrderAssociation> orderAcceptedCallback,
      int maxIgnoredRejectionsCount,
      long orderResendTimeoutMs,
      @Nonnull
      LongSupplier currentTimeMillis
  ) {
    this(
        commAdapterName,
        sendOrderCallback,
        sendInstantActionsCallback,
        orderAcceptedCallback,
        maxIgnoredRejectionsCount,
        orderResendTimeoutMs,
        false,
        5000,
        1,
        30000,
        currentTimeMillis
    );
  }

  MessageResponseMatcher(
      @Nonnull
      String commAdapterName,
      @Nonnull
      Consumer<Order> sendOrderCallback,
      @Nonnull
      Consumer<InstantActions> sendInstantActionsCallback,
      @Nonnull
      Consumer<OrderAssociation> orderAcceptedCallback,
      int maxIgnoredRejectionsCount,
      long orderResendTimeoutMs,
      boolean instantActionsResendEnabled,
      long instantActionsResendIntervalMs,
      int instantActionsMaxSendAttempts,
      long instantActionsAckTimeoutMs,
      @Nonnull
      LongSupplier currentTimeMillis
  ) {
    this.commAdapterName = requireNonNull(commAdapterName, "commAdapterName");
    this.sendOrderCallback = requireNonNull(sendOrderCallback, "sendOrderCallback");
    this.sendInstantActionsCallback
        = requireNonNull(sendInstantActionsCallback, "sendInstantActionsCallback");
    this.orderAcceptedCallback = requireNonNull(orderAcceptedCallback, "orderAcceptedCallback");
    this.maxIgnoredRejectionsCount = maxIgnoredRejectionsCount;
    this.orderResendTimeoutMs = Math.max(orderResendTimeoutMs, 0);
    this.instantActionsResendEnabled = instantActionsResendEnabled;
    this.instantActionsResendIntervalMs = Math.max(instantActionsResendIntervalMs, 0);
    this.instantActionsMaxSendAttempts = Math.max(instantActionsMaxSendAttempts, 1);
    this.instantActionsAckTimeoutMs = Math.max(instantActionsAckTimeoutMs, 0);
    this.currentTimeMillis = requireNonNull(currentTimeMillis, "currentTimeMillis");
  }

  public void enqueueCommand(Order order, MovementCommand command) {
    LOG.debug("{}: Enqueuing order: {}", commAdapterName, order);
    enqueueRequest(new OrderAssociation(order, command));
  }

  public void enqueueAction(InstantActions action) {
    LOG.debug("{}: Enqueuing instant action: {}", commAdapterName, action);
    enqueueRequest(action);
  }

  private void enqueueRequest(Object request) {
    requests.add(request);

    if (requests.size() > 1) {
      LOG.debug(
          "{}: Not sending enqueued request yet, due to unacknowledged previous request.",
          commAdapterName
      );
      return;
    }

    sendNextOrder();
  }

  /**
   * Clears all orders for which the {@link MessageResponseMatcher} is waiting for acknowledgement
   * from the vehicle.
   */
  public void clear() {
    requests.clear();
    consecutiveRejectionsCount = 0;
    lastSentRequest = null;
    lastSentRequestTimestamp = 0;
    resetInstantActionsTracking();
  }

  public void onStateMessage(
      @Nonnull
      State state
  ) {
    requireNonNull(state, "state");

    sendingAllowed = state.getOperatingMode() == OperatingMode.AUTOMATIC
        || state.getOperatingMode() == OperatingMode.SEMIAUTOMATIC;

    Object currentRequest = requests.peek();  // 获取但不移除头部 元素
    if (currentRequest == null) {
      return;
    }

    if (StateMappings.vehicleRejectsOrder(state)) {
      consecutiveRejectionsCount++;
    }
    else {
      consecutiveRejectionsCount = 0;
    }
    if (consecutiveRejectionsCount > maxIgnoredRejectionsCount) {
      // Don't do anything - the vehicle cannot continue processing the drive order. We will wait
      // for this to be resolved via order withdrawal and a new initial order message.
      return;
    }

    if (requestAcknowledged(currentRequest, state)) {
      requests.poll();  // 获取并移除头部 元素
      if (currentRequest instanceof OrderAssociation) {
        OrderAssociation order = (OrderAssociation) currentRequest;
        LOG.debug("{}: Vehicle acknowledged order: {}", commAdapterName, order);
        orderAcceptedCallback.accept(order);
      }
      else if (currentRequest instanceof InstantActions) {
        InstantActions actions = (InstantActions) currentRequest;
        LOG.debug("{}: Vehicle acknowledged instant actions: {}", commAdapterName, actions);
        resetInstantActionsTracking();
      }
      sendNextOrder();
    }
    else if (instantActionsTimedOut(currentRequest)) {
      LOG.warn(
          "{}: Dropping unacknowledged instant actions after {} ms: {}",
          commAdapterName,
          instantActionsAckTimeoutMs,
          currentRequest
      );
      requests.poll();
      resetInstantActionsTracking();
      sendNextOrder();
    }
    else {
      sendNextOrder();
    }
  }

  private boolean requestAcknowledged(Object request, State state) {
    if (request instanceof OrderAssociation) {
      return orderAccepted(((OrderAssociation) request).getOrder(), state);
    }
    else if (request instanceof InstantActions) {
      return instantActionsAcknowledged((InstantActions) request, state);
    }
    else {
      LOG.warn(
          "{}: Unrecognized request of type {}.",
          commAdapterName,
          request.getClass().getName()
      );
      return false;
    }
  }

  /**
   * Send the first request in the queue to the vehicle.
   */
  private void sendNextOrder() {
    if (requests.isEmpty()) {
      LOG.debug("{}: Cannot send next order. No request to send", commAdapterName);
      return;
    }

    Object request = requests.peek();
    if (request instanceof OrderAssociation && !sendingAllowed) {
      LOG.debug("{}: Cannot send next order. Sending is currently disallowed", commAdapterName);
      return;
    }

    if (!requestResendAllowed(request)) {
      return;
    }

    LOG.debug("{}: Sending order to comm adapter: {}", commAdapterName, request);
    if (request instanceof OrderAssociation) {
      sendOrderCallback.accept(((OrderAssociation) request).getOrder());
    }
    else if (request instanceof InstantActions) {
      sendInstantActionsCallback.accept((InstantActions) request);
    }
    else {
      LOG.warn(
          "{}: Cannot send request. Unrecognized request of type {}.",
          commAdapterName,
          request.getClass().getName()
      );
    }
    onRequestSent(request);
  }

  private boolean requestResendAllowed(Object request) {
    if (request instanceof OrderAssociation) {
      return orderResendAllowed(request);
    }
    else if (request instanceof InstantActions) {
      return instantActionsResendAllowed(request);
    }
    else {
      return false;
    }
  }

  private boolean orderResendAllowed(Object request) {
    if (orderResendTimeoutMs == 0 || !Objects.equals(request, lastSentRequest)) {
      return true;
    }

    long elapsedMs = currentTimeMillis.getAsLong() - lastSentRequestTimestamp;
    if (elapsedMs >= orderResendTimeoutMs) {
      return true;
    }

    LOG.trace(
        "{}: Not resending order yet, last send was {} ms ago. Configured timeout: {} ms.",
        commAdapterName,
        elapsedMs,
        orderResendTimeoutMs
    );
    return false;
  }

  private boolean instantActionsResendAllowed(Object request) {
    if (!Objects.equals(request, lastSentRequest)) {
      return true;
    }

    if (!instantActionsResendEnabled) {
      LOG.trace("{}: Not resending instant actions. Resending is disabled.", commAdapterName);
      return false;
    }

    if (instantActionsSendAttempts >= instantActionsMaxSendAttempts) {
      LOG.trace(
          "{}: Not resending instant actions. Maximum send attempts reached: {}.",
          commAdapterName,
          instantActionsMaxSendAttempts
      );
      return false;
    }

    long elapsedMs = currentTimeMillis.getAsLong() - lastSentRequestTimestamp;
    if (elapsedMs >= instantActionsResendIntervalMs) {
      return true;
    }

    LOG.trace(
        "{}: Not resending instant actions yet, last send was {} ms ago. Configured timeout: {} ms.",
        commAdapterName,
        elapsedMs,
        instantActionsResendIntervalMs
    );
    return false;
  }

  private boolean instantActionsTimedOut(Object request) {
    if (!(request instanceof InstantActions)
        || instantActionsAckTimeoutMs == 0
        || firstInstantActionsSentTimestamp < 0) {
      return false;
    }

    return currentTimeMillis.getAsLong() - firstInstantActionsSentTimestamp
        >= instantActionsAckTimeoutMs;
  }

  private void onRequestSent(Object request) {
    long now = currentTimeMillis.getAsLong();

    if (request instanceof InstantActions) {
      if (!Objects.equals(request, lastSentRequest) || firstInstantActionsSentTimestamp < 0) {
        firstInstantActionsSentTimestamp = now;
        instantActionsSendAttempts = 0;
      }
      instantActionsSendAttempts++;
    }

    lastSentRequest = request;
    lastSentRequestTimestamp = now;
  }

  private void resetInstantActionsTracking() {
    firstInstantActionsSentTimestamp = -1;
    instantActionsSendAttempts = 0;
  }

  private boolean orderAccepted(Order order, State state) {
    return Objects.equals(state.getOrderId(), order.getOrderId())
        && Objects.equals(state.getOrderUpdateId(), order.getOrderUpdateId());
  }

  private boolean instantActionsAcknowledged(InstantActions instantAction, State state) {
    return instantAction.getActions().stream()
        .allMatch(action -> {
          // In case of a cancelOrder action, we actually wait for the vehicle to accept AND
          // COMPLETE the action. Not doing this can lead to situations in which we send another
          // order while the vehicle is still processing the cancelOrder, and the vehicle then
          // immediately cancelling that new order.
          if (Objects.equals(action.getActionType(), CancelOrder.ACTION_TYPE)) {
            return cancelOrderAcceptedAndCompleted(action, state);
          }
          else {
            return actionAccepted(action, state);
          }
        });
  }

  private boolean actionAccepted(Action action, State state) {
    return state.getActionStates().stream()
        .anyMatch(actionState -> actionState.getActionId().equals(action.getActionId()));
  }

  private boolean cancelOrderAcceptedAndCompleted(Action action, State state) {
    return state.getActionStates().stream()
        .filter(actionState -> actionState.getActionId().equals(action.getActionId()))
        .anyMatch(
            actionState -> actionState.getActionStatus() == ActionStatus.FINISHED
                || actionState.getActionStatus() == ActionStatus.FAILED
        );
  }
}
