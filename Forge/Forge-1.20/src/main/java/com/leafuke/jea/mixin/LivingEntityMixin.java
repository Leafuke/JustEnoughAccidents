package com.leafuke.jea.mixin;

import com.leafuke.jea.runtime.JeaRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void jea$onTotemProtection(
            DamageSource source,
            CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && (Object) this instanceof ServerPlayer player) {
            JeaRuntime.signalTotem(player);
        }
    }
}
