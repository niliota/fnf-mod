package space.niliota.fnfmod;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import space.niliota.fnfmod.network.ClientVs;

// The FNF-style camera: looks at the player from the side, like an FNF stage.
// The player stands on the right of the screen facing left (like boyfriend), with room on the left for the opponent.
// The camera itself is moved by CameraMixin, this class decides where it goes and when it's on.
//
// It uses Minecraft's third person mode underneath, so the player's model gets drawn and the hand/crosshair don't.
// The direction is locked when it's turned on, so looking around during a song doesn't swing the camera around.
public class FnfCamera {
    public static final double MIN_DISTANCE = 2, MAX_DISTANCE = 10;
    private static final double FOCUS_AHEAD = 1.5;  // the camera looks at a point this far in front of the player, so they end up on the right
    private static final double FOCUS_HEIGHT = 1.2; // blocks above the player's feet
    private static final float PITCH = 6;           // looking down a little

    public static boolean active = false;
    private static CameraType previousCameraType;
    private static float lockedYaw;                // the player's facing direction when the camera was turned on

    public record Placement(Vec3 position, float yaw, float pitch) {}

    public static void toggle() {
        if (active) turnOff();
        else turnOn();
    }

    public static void turnOn() {
        Minecraft minecraft = Minecraft.getInstance();
        if (active || minecraft.player == null) return;

        previousCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        lockedYaw = minecraft.player.getYRot();
        active = true;
    }

    public static void turnOff() {
        if (!active) return;
        Minecraft.getInstance().options.setCameraType(previousCameraType != null ? previousCameraType : CameraType.FIRST_PERSON);
        active = false;
    }

    // Song started with the "FNF camera" option on
    public static void songStarted() {
        turnOn();
    }

    // When a song ends (or is stopped) the FNF camera always goes back to first person
    public static void songEnded() {
        if (!active) return;
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        active = false;
    }

    // Every tick: pressing F5 (changing the camera type) or leaving the world turns the FNF camera off
    public static void tick(Minecraft minecraft) {
        if (!active) return;
        if (minecraft.player == null) {
            active = false;
            return;
        }
        if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            active = false; // the player switched the camera themselves, keep their choice
        }
    }

    // Where the camera goes this frame. Called by CameraMixin at the end of Minecraft's own camera setup.
    public static Placement place(BlockGetter level, Entity entity, float partialTick) {
        Vec3 feet = entity.getPosition(partialTick); // smooth position between ticks

        // VS: frame both players, with me on the right facing my rival on the left
        if (Gameplay.vs && ClientVs.rivalId != null && Minecraft.getInstance().level != null) {
            Player rival = Minecraft.getInstance().level.getPlayerByUUID(ClientVs.rivalId);
            if (rival != null && rival != entity && rival.distanceTo(entity) < 48) {
                Vec3 rivalFeet = rival.getPosition(partialTick);
                double dx = rivalFeet.x - feet.x, dz = rivalFeet.z - feet.z;
                double separation = Math.sqrt(dx * dx + dz * dz);
                if (separation > 0.5) {
                    float facingRival = (float) Math.toDegrees(Math.atan2(-dx, dz)); // the yaw that looks from me to them
                    Vec3 middle = feet.add(rivalFeet).scale(0.5).add(0, FOCUS_HEIGHT, 0);
                    // far enough back to fit both, at least the chosen distance
                    double distance = Math.max(FnfOptions.cameraDistance, separation * 0.6 + 2);
                    // True POV on the opponent side: the camera looks from my right, so I'm on the left facing right
                    return placeAround(level, entity, middle, facingRival, distance, Gameplay.sidesSwapped());
                }
            }
        }

        Vec3 playerForward = Vec3.directionFromRotation(0, lockedYaw);
        // the point the camera looks at: a bit in front of the player, where the opponent would be
        Vec3 focus = feet.add(playerForward.scale(FOCUS_AHEAD)).add(0, FOCUS_HEIGHT, 0);
        return placeAround(level, entity, focus, lockedYaw, FnfOptions.cameraDistance, false);
    }

    // The camera looks at "focus" from the side of someone facing "facingYaw", from "distance" blocks away.
    // meOnLeft = look from the other side, so the player is on the left of the screen facing right (True POV)
    private static Placement placeAround(BlockGetter level, Entity entity, Vec3 focus, float facingYaw, double distance, boolean meOnLeft) {
        // Looking at the player's side: turned 90 degrees from where they face.
        // (+90 puts the player's front towards the left of the screen, so they face left like boyfriend)
        float yaw = facingYaw + (meOnLeft ? -90 : 90);
        Vec3 cameraForward = Vec3.directionFromRotation(PITCH, yaw);
        Vec3 wanted = focus.subtract(cameraForward.scale(distance));

        // if a wall is in the way, come closer instead of looking through it (like vanilla third person)
        HitResult hit = level.clip(new ClipContext(focus, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        Vec3 position = hit.getType() == HitResult.Type.MISS ? wanted : hit.getLocation().add(cameraForward.scale(0.2));

        return new Placement(position, yaw, PITCH);
    }
}
