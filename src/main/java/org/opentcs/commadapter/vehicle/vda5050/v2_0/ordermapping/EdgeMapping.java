// SPDX-FileCopyrightText: The openTCS Authors
// SPDX-License-Identifier: MIT
package org.opentcs.commadapter.vehicle.vda5050.v2_0.ordermapping;

import static java.lang.Math.toRadians;
import static java.util.Objects.requireNonNull;
import static org.opentcs.commadapter.vehicle.vda5050.common.PropertyExtractions.getProperty;
import static org.opentcs.commadapter.vehicle.vda5050.common.PropertyExtractions.getPropertyDouble;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ORIENTATION_FORWARD;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ORIENTATION_REVERSE;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ORIENTATION_TYPE_FORWARD;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ORIENTATION_TYPE_REVERSE;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_VEHICLE_ORIENTATION;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ROTATION_ALLOWED_FORWARD;
import static org.opentcs.commadapter.vehicle.vda5050.v2_0.ObjectProperties.PROPKEY_PATH_ROTATION_ALLOWED_REVERSE;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.common.Action;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.order.Edge;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.Route;

/**
 * Functions to map a {@link Route.Step} from openTCS movement commands to a VDA5050 edge.
 */
public class EdgeMapping {

  /**
   * Prevents unwanted instantiation.
   */
  private EdgeMapping() {
  }

  /**
   * Maps a route step to a base edge.
   * The edge has the release flag set to true.
   *
   * @param step The step to map.
   * @param vehicle the vehicle to map the for.
   * @param actions The actions for this edge.
   * @return The mapped edge
   */
  public static Edge toBaseEdge(
      Route.Step step,
      Vehicle vehicle,
      List<Action> actions
  ) {
    requireNonNull(step, "step");
    requireNonNull(vehicle, "vehicle");
    requireNonNull(actions, "actions");

    Edge edge = new Edge(
        step.getPath().getName(),
        step.getRouteIndex() * 2L + 1,
        true,
        step.getSourcePoint().getName(),
        step.getDestinationPoint().getName(),
        actions
    );

    edge.setMaxSpeed(maxSpeed(step));
    edge.setOrientation(edgeOrientation(step));
    edge.setRotationAllowed(rotationAllowed(step));
    edge.setOrientationType(edgeOrientationType(step));

    return edge;
  }

  /**
   * Maps a route step to a horizon edge.
   * The edge has the release flag set to false.
   *
   * @param step The step to map.
   * @param actions The actions for this edge.
   * @return The mapped edge
   */
  public static Edge toHorizonEdge(Route.Step step, List<Action> actions) {
    requireNonNull(step, "step");

    Edge edge = new Edge(
        step.getPath().getName(),
        step.getRouteIndex() * 2L + 1,
        false,
        step.getSourcePoint().getName(),
        step.getDestinationPoint().getName(),
        actions
    );

    edge.setMaxSpeed(maxSpeed(step));
    edge.setOrientation(edgeOrientation(step));
    edge.setRotationAllowed(rotationAllowed(step));
    edge.setOrientationType(edgeOrientationType(step));

    return edge;
  }

  @Nonnull
  private static Double maxSpeed(Route.Step step) {
    if (vehicleOrientation(step) == Vehicle.Orientation.BACKWARD) {
      return step.getPath().getMaxReverseVelocity() / 1000.0;
    }
    else {
      return step.getPath().getMaxVelocity() / 1000.0;
    }
  }

  @Nullable
  private static Double edgeOrientation(Route.Step step) {
    if (vehicleOrientation(step) == Vehicle.Orientation.BACKWARD) {
      return getPropertyDouble(PROPKEY_PATH_ORIENTATION_REVERSE, step.getPath())
          .map(value -> toRadians(value))
          .orElseGet(() -> calculatedReverseOrientation(step).orElse(null));
    }
    else {
      return getPropertyDouble(PROPKEY_PATH_ORIENTATION_FORWARD, step.getPath())
          .map(value -> toRadians(value))
          .orElse(null);
    }
  }

  @Nullable
  private static Boolean rotationAllowed(Route.Step step) {
    if (vehicleOrientation(step) == Vehicle.Orientation.BACKWARD) {
      return getProperty(PROPKEY_PATH_ROTATION_ALLOWED_REVERSE, step.getPath())
          .map(value -> Boolean.valueOf(value))
          .orElse(null);
    }
    else {
      return getProperty(PROPKEY_PATH_ROTATION_ALLOWED_FORWARD, step.getPath())
          .map(value -> Boolean.valueOf(value))
          .orElse(null);
    }
  }

  @Nullable
  private static String edgeOrientationType(Route.Step step) {
    if (vehicleOrientation(step) == Vehicle.Orientation.BACKWARD) {
      return getProperty(PROPKEY_PATH_ORIENTATION_TYPE_REVERSE, step.getPath()).orElse(null);
    }
    else {
      return getProperty(PROPKEY_PATH_ORIENTATION_TYPE_FORWARD, step.getPath()).orElse(null);
    }
  }

  private static Vehicle.Orientation vehicleOrientation(Route.Step step) {
    return getProperty(PROPKEY_PATH_VEHICLE_ORIENTATION, step.getPath())
        .flatMap(EdgeMapping::vehicleOrientation)
        .orElse(step.getVehicleOrientation());
  }

  private static Optional<Vehicle.Orientation> vehicleOrientation(String value) {
    String normalizedValue = value.trim().toUpperCase(Locale.ROOT);

    if (normalizedValue.isEmpty() || normalizedValue.equals("AUTO")) {
      return Optional.empty();
    }

    if (normalizedValue.equals("FORWARD")) {
      return Optional.of(Vehicle.Orientation.FORWARD);
    }

    if (normalizedValue.equals("BACKWARD") || normalizedValue.equals("REVERSE")) {
      return Optional.of(Vehicle.Orientation.BACKWARD);
    }

    throw new IllegalArgumentException(
        "Unsupported path property "
            + PROPKEY_PATH_VEHICLE_ORIENTATION
            + " value: "
            + value
    );
  }

  private static Optional<Double> calculatedReverseOrientation(Route.Step step) {
    if (!usesGlobalReverseOrientation(step)) {
      return Optional.empty();
    }

    double deltaX = step.getDestinationPoint().getPose().getPosition().getX()
        - step.getSourcePoint().getPose().getPosition().getX();
    double deltaY = step.getDestinationPoint().getPose().getPosition().getY()
        - step.getSourcePoint().getPose().getPosition().getY();

    if (deltaX == 0.0 && deltaY == 0.0) {
      return Optional.empty();
    }

    return Optional.of(normalizeAngle(Math.atan2(deltaY, deltaX) + Math.PI));
  }

  private static boolean usesGlobalReverseOrientation(Route.Step step) {
    return getProperty(PROPKEY_PATH_ORIENTATION_TYPE_REVERSE, step.getPath())
        .map(value -> value.trim().equalsIgnoreCase("GLOBAL"))
        .orElse(false);
  }

  private static double normalizeAngle(double angle) {
    double normalizedAngle = angle;

    while (normalizedAngle > Math.PI) {
      normalizedAngle -= 2.0 * Math.PI;
    }

    while (normalizedAngle <= -Math.PI) {
      normalizedAngle += 2.0 * Math.PI;
    }

    return normalizedAngle;
  }
}
