package br.com.tavares.bedfight.mixin;

import br.com.tavares.bedfight.arena.ArenaDimension;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.8-style combat inside the arena: no attack-speed cooldown, every hit lands at full strength. */
@Mixin(Player.class)
public abstract class OldPvpAttackStrengthMixin {
	@Inject(method = "getAttackStrengthScale(F)F", at = @At("HEAD"), cancellable = true)
	private void bedfight$fullStrengthInArena(float adjustTicks, CallbackInfoReturnable<Float> cir) {
		Player player = (Player) (Object) this;
		if (player.level().dimension() == ArenaDimension.KEY) {
			cir.setReturnValue(1.0F);
		}
	}
}
