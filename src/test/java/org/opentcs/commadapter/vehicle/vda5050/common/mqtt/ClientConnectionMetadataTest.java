// SPDX-FileCopyrightText: The openTCS Authors
// SPDX-License-Identifier: MIT
package org.opentcs.commadapter.vehicle.vda5050.common.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.opentcs.data.model.Vehicle;

/**
 * Tests for {@link ClientConnectionMetadata}.
 */
public class ClientConnectionMetadataTest {

  @Test
  void extractsIpv4FromPeerName() {
    ClientConnectionMeta meta = new ClientConnectionMeta()
        .setPeerName("192.168.192.123:58987");

    assertThat(ClientConnectionMetadata.extractIp(meta)).contains("192.168.192.123");
  }

  @Test
  void extractsIpv6FromPeerName() {
    ClientConnectionMeta meta = new ClientConnectionMeta()
        .setPeerName("[fe80::1]:58987");

    assertThat(ClientConnectionMetadata.extractIp(meta)).contains("fe80::1");
  }

  @Test
  void formatsEmqxEpochMillisTimestamp() {
    ClientConnectionMeta meta = new ClientConnectionMeta()
        .setTimestamp(1782883304790L);

    assertThat(ClientConnectionMetadata.eventTimestamp(meta))
        .isEqualTo("2026-07-01T05:21:44.790Z");
  }

  @Test
  void matchesVehicleByNameSerialNumberOrTopicPrefix() {
    Vehicle vehicle = new Vehicle("Vehicle-1");

    assertThat(
        ClientConnectionMetadata.matchesVehicle(
            new ClientConnectionMeta().setClientId("Vehicle-1"),
            vehicle,
            "BYD_11",
            "DP0055",
            "VDA/V2.0.0/BYD_11/DP0055"
        )
    ).isTrue();

    assertThat(
        ClientConnectionMetadata.matchesVehicle(
            new ClientConnectionMeta().setClientId("DP0055"),
            vehicle,
            "BYD_11",
            "DP0055",
            "VDA/V2.0.0/BYD_11/DP0055"
        )
    ).isTrue();

    assertThat(
        ClientConnectionMetadata.matchesVehicle(
            new ClientConnectionMeta().setClientId("VDA/V2.0.0/BYD_11/DP0055"),
            vehicle,
            "BYD_11",
            "DP0055",
            "VDA/V2.0.0/BYD_11/DP0055"
        )
    ).isTrue();

    assertThat(
        ClientConnectionMetadata.matchesVehicle(
            new ClientConnectionMeta().setClientId("other"),
            vehicle,
            "BYD_11",
            "DP0055",
            "VDA/V2.0.0/BYD_11/DP0055"
        )
    ).isFalse();
  }
}
