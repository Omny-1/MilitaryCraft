package me.bibo.militarycraft.vehicles.aircraft;

import me.bibo.militarycraft.core.model.Part;
import me.bibo.militarycraft.core.model.Transforms;
import me.bibo.militarycraft.core.vehicle.DisplayVehicle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/** DisplayVehicle with cached aircraft orientation and local/world helpers. */
public abstract class OrientedVehicle extends DisplayVehicle {

    protected double yaw;
    protected double pitch;
    protected double roll;

    private Quaternionf orientCache;
    private boolean orientDirty = true;

    protected OrientedVehicle(UUID id, World world, double x, double y, double z, double health,
                              double yaw, double pitch, double roll) {
        super(id, world, x, y, z, health);
        this.yaw = AircraftSafety.finiteOr(yaw, 0.0);
        this.pitch = AircraftSafety.finiteOr(pitch, 0.0);
        this.roll = AircraftSafety.finiteOr(roll, 0.0);
    }

    public Quaternionf orientation() {
        Quaternionf c = orientCache;
        if (c == null || orientDirty) {
            c = AircraftTransforms.orientation(yaw, pitch, roll);
            orientCache = c;
            orientDirty = false;
        }
        return c;
    }

    public Location localToWorld(Vector3f local) {
        Vector3f off = Transforms.localPointToWorld(local, orientation());
        return new Location(world, anchor.getX() + off.x, anchor.getY() + off.y, anchor.getZ() + off.z);
    }

    public Vector forward() {
        Vector3f f = AircraftTransforms.forward(orientation());
        return new Vector(f.x, f.y, f.z);
    }

    @Override
    public double facingYaw() {
        return yaw;
    }

    @Override
    public Transformation transformFor(Part part) {
        return AircraftTransforms.part(part, orientation());
    }

    @Override
    public Location hitboxLocation(int index) {
        float z = model().hitboxZOffsets()[index];
        Vector3f off = Transforms.localPointToWorld(new Vector3f(0f, 0f, z), orientation());
        return new Location(world, anchor.getX() + off.x, anchor.getY() + off.y, anchor.getZ() + off.z);
    }

    public double yaw() {
        return yaw;
    }

    public void setYaw(double yaw) {
        this.yaw = AircraftSafety.finiteOr(yaw, this.yaw);
        markAnglesDirty();
    }

    public double pitch() {
        return pitch;
    }

    public void setPitch(double pitch) {
        this.pitch = AircraftSafety.finiteOr(pitch, this.pitch);
        markAnglesDirty();
    }

    public double roll() {
        return roll;
    }

    public void setRoll(double roll) {
        this.roll = AircraftSafety.finiteOr(roll, this.roll);
        markAnglesDirty();
    }

    protected void markAnglesDirty() {
        yaw = AircraftSafety.finiteOr(yaw, 0.0);
        pitch = AircraftSafety.finiteOr(pitch, 0.0);
        roll = AircraftSafety.finiteOr(roll, 0.0);
        orientDirty = true;
        markStateDirty();
    }
}
