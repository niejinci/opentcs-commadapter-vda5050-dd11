// SPDX-FileCopyrightText: The openTCS Authors
// SPDX-License-Identifier: MIT
package org.opentcs.commadapter.vehicle.vda5050.v2_0.ordermapping;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ORIENTATION_FORWARD;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ORIENTATION_REVERSE;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ORIENTATION_TYPE_FORWARD;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ORIENTATION_TYPE_REVERSE;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ROTATION_ALLOWED_FORWARD;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ROTATION_ALLOWED_REVERSE;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_VEHICLE_ORIENTATION;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.order.Edge;
import org.opentcs.data.model.Path;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Triple;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.Route;
import org.opentcs.data.order.Route.Step;

/**
 * Tests the edge mapping functions.
 */
public class EdgeMappingTest {

  private Vehicle vehicle;
  private Point source;
  private Point dest;
  private Path path;

  @BeforeEach
  public void setup() {
    vehicle = new Vehicle("vehicle-0001");
    source = new Point("Point-0001");
    dest = new Point("Point-0002");
    path = new Path("Path-001", source.getReference(), dest.getReference());
  }

  @Test
  public void shouldGenerateEdgeCorrectly() {
    Step step = new Route.Step(
        path,
        source,
        dest,
        Vehicle.Orientation.FORWARD,
        0,
        1
    );

    Edge edge = EdgeMapping.toBaseEdge(step, vehicle, List.of());

    assertThat(edge.getEdgeId(), is(path.getName()));
    assertThat(edge.getSequenceId(), is(1L));
    assertThat(edge.isReleased(), is(true));
    assertThat(edge.getStartNodeId(), is(source.getName()));
    assertThat(edge.getEndNodeId(), is(dest.getName()));
  }

  @Test
  public void shouldGenerateEdgeCorrectlyWithOrientationForForwardVehicle() {
    path = path
        .withMaxVelocity(700)
        .withMaxReverseVelocity(350)
        .withProperties(
            Map.of(
                PROPKEY_PATH_ORIENTATION_FORWARD, String.valueOf(12.34),
                PROPKEY_PATH_ORIENTATION_TYPE_FORWARD, "GLOBAL",
                PROPKEY_PATH_ROTATION_ALLOWED_FORWARD, String.valueOf(true)
            )
        );

    Step step = new Route.Step(
        path,
        source,
        dest,
        Vehicle.Orientation.FORWARD,
        0,
        1
    );

    Edge edge = EdgeMapping.toBaseEdge(step, vehicle, List.of());
    assertThat(edge.getMaxSpeed(), is(0.7));
    assertThat(edge.getOrientation(), is(Math.toRadians(12.34)));
    assertThat(edge.getOrientationType(), is("GLOBAL"));
    assertThat(edge.getRotationAllowed(), is(true));
  }

  @Test
  public void shouldGenerateEdgeCorrectlyWithOrientationForBackwardsVehicle() {
    path = path
        .withMaxVelocity(700)
        .withMaxReverseVelocity(350)
        .withProperties(
            Map.of(
                PROPKEY_PATH_ORIENTATION_REVERSE, String.valueOf(12.34),
                PROPKEY_PATH_ORIENTATION_TYPE_REVERSE, "GLOBAL",
                PROPKEY_PATH_ROTATION_ALLOWED_REVERSE, String.valueOf(true)
            )
        );
    Step step = new Route.Step(
        path,
        source,
        dest,
        Vehicle.Orientation.BACKWARD,
        0,
        1
    );

    Edge edge = EdgeMapping.toBaseEdge(step, vehicle, List.of());
    assertThat(edge.getMaxSpeed(), is(0.35));
    assertThat(edge.getOrientation(), is(Math.toRadians(12.34)));
    assertThat(edge.getOrientationType(), is("GLOBAL"));
    assertThat(edge.getRotationAllowed(), is(true));
  }

  @Test
  public void shouldUseConfiguredPathVehicleOrientation() {
    path = path
        .withMaxVelocity(700)
        .withMaxReverseVelocity(350)
        .withProperties(
            Map.of(
                PROPKEY_PATH_VEHICLE_ORIENTATION, "BACKWARD",
                PROPKEY_PATH_ORIENTATION_REVERSE, String.valueOf(180.0),
                PROPKEY_PATH_ORIENTATION_TYPE_REVERSE, "GLOBAL",
                PROPKEY_PATH_ROTATION_ALLOWED_REVERSE, String.valueOf(false)
            )
        );

    Step step = new Route.Step(
        path,
        source,
        dest,
        Vehicle.Orientation.FORWARD,
        0,
        1
    );

    Edge edge = EdgeMapping.toBaseEdge(step, vehicle, List.of());

    assertThat(edge.getMaxSpeed(), is(0.35));
    assertThat(edge.getOrientation(), is(Math.toRadians(180.0)));
    assertThat(edge.getOrientationType(), is("GLOBAL"));
    assertThat(edge.getRotationAllowed(), is(false));
  }

  @Test
  public void shouldCalculateReverseOrientationForGlobalOrientationType() {
    source = source.withPose(source.getPose().withPosition(new Triple(9395, -7174, 0)));
    dest = dest.withPose(dest.getPose().withPosition(new Triple(11400, -7200, 0)));
    path = path
        .withMaxVelocity(700)
        .withMaxReverseVelocity(350)
        .withProperties(
            Map.of(
                PROPKEY_PATH_VEHICLE_ORIENTATION, "BACKWARD",
                PROPKEY_PATH_ORIENTATION_TYPE_REVERSE, "GLOBAL"
            )
        );

    Step step = new Route.Step(
        path,
        source,
        dest,
        Vehicle.Orientation.FORWARD,
        0,
        1
    );

    Edge edge = EdgeMapping.toBaseEdge(step, vehicle, List.of());

    assertThat(
        edge.getOrientation(),
        is(Math.atan2(-7200.0 - -7174.0, 11400.0 - 9395.0) + Math.PI)
    );
  }

  @Test
  public void shouldPreferConfiguredReverseOrientationOverCalculatedOrientation() {
    source = source.withPose(source.getPose().withPosition(new Triple(9395, -7174, 0)));
    dest = dest.withPose(dest.getPose().withPosition(new Triple(11400, -7200, 0)));
    path = path
        .withMaxVelocity(700)
        .withMaxReverseVelocity(350)
        .withProperties(
            Map.of(
                PROPKEY_PATH_VEHICLE_ORIENTATION, "BACKWARD",
                PROPKEY_PATH_ORIENTATION_REVERSE, String.valueOf(12.34),
                PROPKEY_PATH_ORIENTATION_TYPE_REVERSE, "GLOBAL"
            )
        );

    Step step = new Route.Step(
        path,
        source,
        dest,
        Vehicle.Orientation.FORWARD,
        0,
        1
    );

    Edge edge = EdgeMapping.toBaseEdge(step, vehicle, List.of());

    assertThat(edge.getOrientation(), is(Math.toRadians(12.34)));
  }

  @Test
  public void shouldNotCalculateReverseOrientationForNonGlobalOrientationType() {
    source = source.withPose(source.getPose().withPosition(new Triple(9395, -7174, 0)));
    dest = dest.withPose(dest.getPose().withPosition(new Triple(11400, -7200, 0)));
    path = path
        .withMaxVelocity(700)
        .withMaxReverseVelocity(350)
        .withProperties(
            Map.of(
                PROPKEY_PATH_VEHICLE_ORIENTATION, "BACKWARD",
                PROPKEY_PATH_ORIENTATION_TYPE_REVERSE, "TANGENTIAL"
            )
        );

    Step step = new Route.Step(
        path,
        source,
        dest,
        Vehicle.Orientation.FORWARD,
        0,
        1
    );

    Edge edge = EdgeMapping.toBaseEdge(step, vehicle, List.of());

    assertThat(edge.getOrientation(), is((Double) null));
  }

  @Test
  public void shouldGenerateHorizonAsUnreleased() {
    Step step = new Route.Step(
        path,
        source,
        dest,
        Vehicle.Orientation.FORWARD,
        0,
        1
    );

    Edge edge = EdgeMapping.toHorizonEdge(step, List.of());

    assertThat(edge.getEdgeId(), is(path.getName()));
    assertThat(edge.getSequenceId(), is(1L));
    assertThat(edge.isReleased(), is(false));
    assertThat(edge.getStartNodeId(), is(source.getName()));
    assertThat(edge.getEndNodeId(), is(dest.getName()));
  }
}
