package me.bibo.militarycraft.vehicles.moto.control;

/**
 * Side-effect-free driving math shared by the live Paper controller and unit tests.
 * All speeds are blocks/tick and all public angles are degrees.
 */
public final class DriveMath {

    private static final double EPSILON = 1.0e-9;
    private static final int MAX_SUBSTEPS = 64;

    private DriveMath() {
    }

    /** Returns +1, 0 or -1 and makes opposing keys cancel each other. */
    public static int signedInput(boolean positive, boolean negative) {
        return (positive ? 1 : 0) - (negative ? 1 : 0);
    }

    /** Selects forward/reverse target speed. A stalled engine or opposing keys mean neutral. */
    public static double throttleTarget(boolean forward, boolean backward, boolean stalled,
                                        double maxForwardSpeed, double maxReverseSpeed) {
        if (stalled) {
            return 0.0;
        }
        int input = signedInput(forward, backward);
        double forwardLimit = finiteNonNegative(maxForwardSpeed);
        double reverseLimit = finiteNonNegative(maxReverseSpeed);
        return input > 0 ? forwardLimit : input < 0 ? -reverseLimit : 0.0;
    }

    /**
     * Advances longitudinal speed with distinct throttle, direction-change brake and
     * neutral-friction rates. Invalid values collapse to a safe finite result.
     */
    public static double nextSpeed(double current, double target,
                                   double acceleration, double braking, double friction) {
        current = Double.isFinite(current) ? current : 0.0;
        target = Double.isFinite(target) ? target : 0.0;

        double rate;
        if (Math.abs(target) <= EPSILON) {
            rate = finiteNonNegative(friction);
        } else if (Math.abs(current) > EPSILON && Math.signum(current) != Math.signum(target)) {
            rate = finiteNonNegative(braking);
            // Braking consumes this tick. Do not cross straight through zero into
            // powered reverse/forward motion before the next input update.
            return approach(current, 0.0, rate);
        } else {
            rate = finiteNonNegative(acceleration);
        }
        return approach(current, target, rate);
    }

    /** Moves the visual handlebar toward A/D input and recentres it when released. */
    public static double nextHandlebar(double currentAngle, int steeringInput,
                                       double maxHandlebarAngle, double steeringSpeed) {
        double maxAngle = finiteNonNegative(maxHandlebarAngle);
        if (maxAngle <= EPSILON) {
            return 0.0;
        }
        currentAngle = Double.isFinite(currentAngle) ? currentAngle : 0.0;
        currentAngle = clamp(currentAngle, -maxAngle, maxAngle);
        int input = Math.max(-1, Math.min(1, steeringInput));
        double target = input * maxAngle;
        double result = approach(currentAngle, target, finiteNonNegative(steeringSpeed));
        return clamp(result, -maxAngle, maxAngle);
    }

    /**
     * Stable arcade bicycle approximation using the configured maximum turn rate.
     *
     * <p>Yaw grows linearly from zero with road speed, while a smooth high-speed
     * factor increases the turn radius near top speed. Multiplying by sign(speed)
     * gives physically correct reverse-steering inversion: the handlebar still
     * points with A/D, but the hull yaws the opposite way while backing up.</p>
     */
    public static double yawDeltaDegrees(double speed, double handlebarAngle,
                                         double maxHandlebarAngle, double maxForwardSpeed,
                                         double maxTurnSpeed, double highSpeedTurnFactor) {
        if (!Double.isFinite(speed) || !Double.isFinite(handlebarAngle)) {
            return 0.0;
        }
        double maxAngle = finiteNonNegative(maxHandlebarAngle);
        double forwardLimit = finiteNonNegative(maxForwardSpeed);
        double turnLimit = finiteNonNegative(maxTurnSpeed);
        if (Math.abs(speed) <= EPSILON || maxAngle <= EPSILON
                || forwardLimit <= EPSILON || turnLimit <= EPSILON) {
            return 0.0;
        }

        double steer = clamp(handlebarAngle / maxAngle, -1.0, 1.0);
        double speedRatio = clamp(Math.abs(speed) / forwardLimit, 0.0, 1.0);
        double smooth = speedRatio * speedRatio * (3.0 - 2.0 * speedRatio);
        double highFactor = Double.isFinite(highSpeedTurnFactor)
                ? clamp(highSpeedTurnFactor, 0.0, 1.0) : 1.0;
        double speedFactor = 1.0 + (highFactor - 1.0) * smooth;
        return Math.signum(speed) * steer * turnLimit * speedRatio * speedFactor;
    }

    /** Number of interpolation poses needed to sweep both translation and rotation. */
    public static int substepCount(double distance, double yawDelta,
                                   double maxTranslationStep, double maxYawStep) {
        double safeDistance = Double.isFinite(distance) ? Math.abs(distance) : 0.0;
        double safeYaw = Double.isFinite(yawDelta) ? Math.abs(yawDelta) : 0.0;
        int translationSteps = stepsFor(safeDistance, maxTranslationStep);
        int rotationSteps = stepsFor(safeYaw, maxYawStep);
        return Math.min(MAX_SUBSTEPS, Math.max(1, Math.max(translationSteps, rotationSteps)));
    }

    private static int stepsFor(double amount, double maximumStep) {
        if (amount <= EPSILON || !Double.isFinite(maximumStep) || maximumStep <= EPSILON) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(amount / maximumStep));
    }

    /** Interpolates across the shortest angular path. */
    public static double interpolateAngle(double start, double end, double fraction) {
        if (!Double.isFinite(start) || !Double.isFinite(end)) {
            return 0.0;
        }
        double t = Double.isFinite(fraction) ? clamp(fraction, 0.0, 1.0) : 0.0;
        return wrapDegrees(start + wrapDegrees(end - start) * t);
    }

    /** Horizontal X component for Minecraft yaw (0 degrees points toward +Z). */
    public static double forwardX(double yawDegrees) {
        return Double.isFinite(yawDegrees) ? -Math.sin(Math.toRadians(yawDegrees)) : 0.0;
    }

    /** Horizontal Z component for Minecraft yaw (0 degrees points toward +Z). */
    public static double forwardZ(double yawDegrees) {
        return Double.isFinite(yawDegrees) ? Math.cos(Math.toRadians(yawDegrees)) : 1.0;
    }

    /** Converts a local X/Z point to world X around the supplied anchor and yaw. */
    public static double localToWorldX(double anchorX, double yawDegrees, double localX, double localZ) {
        if (!allFinite(anchorX, yawDegrees, localX, localZ)) {
            return Double.NaN;
        }
        double yaw = Math.toRadians(yawDegrees);
        return anchorX + localX * Math.cos(yaw) - localZ * Math.sin(yaw);
    }

    /** Converts a local X/Z point to world Z around the supplied anchor and yaw. */
    public static double localToWorldZ(double anchorZ, double yawDegrees, double localX, double localZ) {
        if (!allFinite(anchorZ, yawDegrees, localX, localZ)) {
            return Double.NaN;
        }
        double yaw = Math.toRadians(yawDegrees);
        return anchorZ + localX * Math.sin(yaw) + localZ * Math.cos(yaw);
    }

    /**
     * Exact planar SAT test between a yaw-rotated motorcycle rectangle and an
     * axis-aligned block/entity rectangle. Vertical overlap is handled by caller.
     */
    public static boolean obbIntersectsAabb(double centreX, double centreZ, double yawDegrees,
                                            double halfWidth, double halfLength,
                                            double minX, double minZ, double maxX, double maxZ) {
        if (!allFinite(centreX, centreZ, yawDegrees, halfWidth, halfLength,
                minX, minZ, maxX, maxZ) || halfWidth < 0.0 || halfLength < 0.0) {
            return false;
        }
        if (minX > maxX) {
            double swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minZ > maxZ) {
            double swap = minZ;
            minZ = maxZ;
            maxZ = swap;
        }

        double boxCentreX = (minX + maxX) * 0.5;
        double boxCentreZ = (minZ + maxZ) * 0.5;
        double boxHalfX = (maxX - minX) * 0.5;
        double boxHalfZ = (maxZ - minZ) * 0.5;
        double dx = boxCentreX - centreX;
        double dz = boxCentreZ - centreZ;

        double yaw = Math.toRadians(yawDegrees);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        // World X and Z axes.
        double motorcycleOnX = halfWidth * Math.abs(rightX) + halfLength * Math.abs(forwardX);
        if (Math.abs(dx) > motorcycleOnX + boxHalfX + EPSILON) {
            return false;
        }
        double motorcycleOnZ = halfWidth * Math.abs(rightZ) + halfLength * Math.abs(forwardZ);
        if (Math.abs(dz) > motorcycleOnZ + boxHalfZ + EPSILON) {
            return false;
        }

        // Motorcycle local right and forward axes.
        double distanceOnRight = Math.abs(dx * rightX + dz * rightZ);
        double boxOnRight = boxHalfX * Math.abs(rightX) + boxHalfZ * Math.abs(rightZ);
        if (distanceOnRight > halfWidth + boxOnRight + EPSILON) {
            return false;
        }
        double distanceOnForward = Math.abs(dx * forwardX + dz * forwardZ);
        double boxOnForward = boxHalfX * Math.abs(forwardX) + boxHalfZ * Math.abs(forwardZ);
        return distanceOnForward <= halfLength + boxOnForward + EPSILON;
    }

    /** Applies gravity and caps only downward terminal velocity. */
    public static double nextVerticalVelocity(double current, double gravity, double terminalSpeed) {
        current = Double.isFinite(current) ? current : 0.0;
        gravity = finiteNonNegative(gravity);
        terminalSpeed = finiteNonNegative(terminalSpeed);
        double next = current - gravity;
        return terminalSpeed <= EPSILON ? Math.max(0.0, next) : Math.max(-terminalSpeed, next);
    }

    /** Caps a low-speed impact so it cannot reduce raw health below the configured floor. */
    public static double cappedNonLethalDamage(double health, double requestedDamage, double minimumHealth) {
        if (!Double.isFinite(health) || health <= 0.0
                || !Double.isFinite(requestedDamage) || requestedDamage <= 0.0) {
            return 0.0;
        }
        double floor = finiteNonNegative(minimumHealth);
        return Math.min(requestedDamage, Math.max(0.0, health - floor));
    }

    public static double approach(double current, double target, double maximumStep) {
        current = Double.isFinite(current) ? current : 0.0;
        target = Double.isFinite(target) ? target : 0.0;
        maximumStep = finiteNonNegative(maximumStep);
        double difference = target - current;
        if (Math.abs(difference) <= maximumStep) {
            return target;
        }
        return current + Math.signum(difference) * maximumStep;
    }

    public static double wrapDegrees(double degrees) {
        if (!Double.isFinite(degrees)) {
            return 0.0;
        }
        double wrapped = degrees % 360.0;
        if (wrapped <= -180.0) {
            wrapped += 360.0;
        } else if (wrapped > 180.0) {
            wrapped -= 360.0;
        }
        return wrapped;
    }

    public static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static boolean allFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
