package net.niliota.fnfmod.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.niliota.fnfmod.FnfCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Moves the camera to FnfCamera's spot when the FNF camera is on.
// Runs at the end of Camera.setup(), after Minecraft placed the camera the normal way, and simply overrides it.
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void fnfmod$sideView(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!FnfCamera.active || !detached) return; // only in (our) third person mode

        FnfCamera.Placement placement = FnfCamera.place(level, entity, partialTick);
        setRotation(placement.yaw(), placement.pitch());
        setPosition(placement.position());
    }
}
