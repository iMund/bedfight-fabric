package br.com.tavares.bedfight.mixin;

import br.com.tavares.bedfight.arena.ArenaDimension;
import br.com.tavares.bedfight.arena.ArenaInstance;
import br.com.tavares.bedfight.arena.ArenaInstancePool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A block a player places in the arena stays breakable by anyone - bridging is core to the mode.
 * Injects into placeBlock (not the outer place) because that's the method that receives the
 * context already updated by BlockItem#updatePlacementContext (e.g. scaffolding walking down a
 * column places somewhere other than the originally clicked position).
 */
@Mixin(BlockItem.class)
public abstract class BlockPlaceTrackingMixin {
	@Inject(method = "placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("RETURN"))
	private void bedfight$trackPlacement(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) {
			return;
		}
		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel) || serverLevel.dimension() != ArenaDimension.KEY) {
			return;
		}
		ArenaInstancePool.findByPosition(context.getClickedPos())
			.filter(ArenaInstance::isInUse)
			.ifPresent(instance -> instance.markPlaced(context.getClickedPos()));
	}
}
