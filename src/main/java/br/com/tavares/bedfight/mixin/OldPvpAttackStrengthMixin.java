package br.com.tavares.bedfight.mixin;

import br.com.tavares.bedfight.arena.ArenaInstancePool;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.8-style combat inside the arena: no attack-speed cooldown, every hit lands at full strength.
 * Forcing full strength alone would also make every hit qualify as a 1.9+ sweep attack (which
 * gates on strength &gt; 0.9), splashing damage onto anyone standing next to the target - 1.8 never
 * had sweep at all, so that's disabled too.
 */
@Mixin(Player.class)
public abstract class OldPvpAttackStrengthMixin {
	@Inject(method = "getAttackStrengthScale(F)F", at = @At("HEAD"), cancellable = true)
	private void bedfight$fullStrengthInArena(float adjustTicks, CallbackInfoReturnable<Float> cir) {
		if (inArena()) {
			cir.setReturnValue(1.0F);
		}
	}

	@Inject(method = "isSweepAttack(ZZZ)Z", at = @At("HEAD"), cancellable = true)
	private void bedfight$noSweepInArena(boolean fullyCharged, boolean criticalHit, boolean sprintingKnockback, CallbackInfoReturnable<Boolean> cir) {
		if (inArena()) {
			cir.setReturnValue(false);
		}
	}

	private boolean inArena() {
		Player player = (Player) (Object) this;
		return ArenaInstancePool.isAnyArenaInstance(player.level().dimension());
	}
}
