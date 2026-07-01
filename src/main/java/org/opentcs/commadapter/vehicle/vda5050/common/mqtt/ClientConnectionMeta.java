// SPDX-FileCopyrightText: The openTCS Authors
// SPDX-License-Identifier: MIT
package org.opentcs.commadapter.vehicle.vda5050.common.mqtt;

/**
 * Metadata republished by the MQTT broker for AGV client connect/disconnect events.
 */
public class ClientConnectionMeta {

  /**
   * Broker event name, e.g. "connected" or "disconnected".
   */
  private String event = "";
  /**
   * MQTT client id.
   */
  private String clientId = "";
  /**
   * Authenticated MQTT user name.
   */
  private String username = "";
  /**
   * Peer socket address as reported by the broker, e.g. "10.136.73.33:58987".
   */
  private String peerName = "";
  /**
   * Timestamp reported by the broker for the connection. EMQX commonly sends epoch millis.
   */
  private Object connectedAt;
  /**
   * Event timestamp reported by the broker. EMQX commonly sends epoch millis.
   */
  private Object timestamp;
  /**
   * Broker node that produced this event.
   */
  private String node = "";

  public String getEvent() {
    return event;
  }

  public ClientConnectionMeta setEvent(String event) {
    this.event = event;
    return this;
  }

  public String getClientId() {
    return clientId;
  }

  public ClientConnectionMeta setClientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public ClientConnectionMeta setUsername(String username) {
    this.username = username;
    return this;
  }

  public String getPeerName() {
    return peerName;
  }

  public ClientConnectionMeta setPeerName(String peerName) {
    this.peerName = peerName;
    return this;
  }

  public Object getConnectedAt() {
    return connectedAt;
  }

  public ClientConnectionMeta setConnectedAt(Object connectedAt) {
    this.connectedAt = connectedAt;
    return this;
  }

  public Object getTimestamp() {
    return timestamp;
  }

  public ClientConnectionMeta setTimestamp(Object timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public String getNode() {
    return node;
  }

  public ClientConnectionMeta setNode(String node) {
    this.node = node;
    return this;
  }

  @Override
  public String toString() {
    return "ClientConnectionMeta{"
        + "event=" + event
        + ", clientId=" + clientId
        + ", username=" + username
        + ", peerName=" + peerName
        + ", connectedAt=" + connectedAt
        + ", timestamp=" + timestamp
        + ", node=" + node
        + '}';
  }
}
