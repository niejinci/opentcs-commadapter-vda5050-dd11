// SPDX-FileCopyrightText: The openTCS Authors
// SPDX-License-Identifier: MIT
package org.opentcs.commadapter.vehicle.vda5050.common.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.opentcs.data.model.Vehicle;

/**
 * Helper methods for broker-republished AGV client metadata.
 */
public final class ClientConnectionMetadata {

  /**
   * The fixed topic used by the broker to republish AGV MQTT client metadata.
   */
  public static final String TOPIC = "vda/client-meta";
  /**
   * Frontend-compatible vehicle property containing the current AGV IP.
   */
  public static final String PROPKEY_VEHICLE_IP = "ip";
  /**
   * Runtime metadata property containing the current AGV IP.
   */
  public static final String PROPKEY_RUNTIME_CLIENT_IP = "vda5050:runtimeClientIp";
  /**
   * Runtime metadata property containing the broker peer address.
   */
  public static final String PROPKEY_RUNTIME_CLIENT_PEER_NAME = "vda5050:runtimeClientPeerName";
  /**
   * Runtime metadata property containing the broker MQTT client id.
   */
  public static final String PROPKEY_RUNTIME_CLIENT_ID = "vda5050:runtimeClientId";
  /**
   * Runtime metadata property containing the last broker event name.
   */
  public static final String PROPKEY_RUNTIME_CLIENT_EVENT = "vda5050:runtimeClientEvent";
  /**
   * Runtime metadata property containing the last broker event timestamp.
   */
  public static final String PROPKEY_RUNTIME_CLIENT_EVENT_AT = "vda5050:runtimeClientEventAt";
  /**
   * Runtime metadata property containing the metadata source.
   */
  public static final String PROPKEY_RUNTIME_CLIENT_IP_SOURCE = "vda5050:runtimeClientIpSource";

  private ClientConnectionMetadata() {
  }

  public static boolean isConnectedEvent(
      @Nonnull
      ClientConnectionMeta meta
  ) {
    return "connected".equalsIgnoreCase(nullToEmpty(meta.getEvent()).trim());
  }

  public static boolean isDisconnectedEvent(
      @Nonnull
      ClientConnectionMeta meta
  ) {
    return "disconnected".equalsIgnoreCase(nullToEmpty(meta.getEvent()).trim());
  }

  public static Optional<String> extractIp(
      @Nonnull
      ClientConnectionMeta meta
  ) {
    String peerName = nullToEmpty(meta.getPeerName()).trim();
    if (peerName.isBlank()) {
      return Optional.empty();
    }

    String ip;
    if (peerName.startsWith("[")) {
      int end = peerName.indexOf(']');
      ip = end > 1 ? peerName.substring(1, end) : "";
    }
    else {
      int lastColon = peerName.lastIndexOf(':');
      ip = lastColon > 0 ? peerName.substring(0, lastColon) : peerName;
    }

    ip = ip.trim();
    return ip.isBlank() ? Optional.empty() : Optional.of(ip);
  }

  public static boolean matchesVehicle(
      @Nonnull
      ClientConnectionMeta meta,
      @Nonnull
      Vehicle vehicle,
      @Nonnull
      String vehicleManufacturer,
      @Nonnull
      String vehicleSerialNumber,
      @Nonnull
      String topicPrefix
  ) {
    String clientId = nullToEmpty(meta.getClientId()).trim();
    String username = nullToEmpty(meta.getUsername()).trim();

    return equalsAny(clientId, vehicle.getName(), vehicleSerialNumber, topicPrefix)
        || equalsAny(username, vehicle.getName(), vehicleSerialNumber, topicPrefix)
        || clientId.endsWith("_" + vehicleSerialNumber) // 当前上报的clientId格式: localIps_{serialNumber}
        || username.endsWith("/" + vehicleSerialNumber)
        || clientId.contains(vehicleManufacturer + "/" + vehicleSerialNumber)
        || username.contains(vehicleManufacturer + "/" + vehicleSerialNumber);
  }

  public static String eventTimestamp(
      @Nonnull
      ClientConnectionMeta meta
  ) {
    return timestampToString(firstPresent(meta.getTimestamp(), meta.getConnectedAt()));
  }

  private static Object firstPresent(Object first, Object second) {
    return first == null ? second : first;
  }

  private static String timestampToString(Object value) {
    if (value == null) {
      return Instant.now().toString();
    }
    if (value instanceof Number number) {
      return Instant.ofEpochMilli(number.longValue()).toString();
    }
    if (value instanceof JsonNode node) {
      if (node.isNumber()) {
        return Instant.ofEpochMilli(node.longValue()).toString();
      }
      if (node.isTextual()) {
        return timestampToString(node.asText());
      }
    }
    String text = value.toString().trim();
    if (text.isBlank()) {
      return Instant.now().toString();
    }
    try {
      return Instant.ofEpochMilli(Long.parseLong(text)).toString();
    }
    catch (NumberFormatException ignored) {
    }
    try {
      return Instant.parse(text).toString();
    }
    catch (DateTimeParseException ignored) {
      return text;
    }
  }

  private static boolean equalsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (!nullToEmpty(candidate).isBlank() && value.equals(candidate)) {
        return true;
      }
    }
    return false;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
