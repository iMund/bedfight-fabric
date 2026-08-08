package br.com.tavares.bedfight.arena;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class MapSelectionManager {
	private static final Map<UUID, MapSelection> SELECTIONS = new HashMap<>();

	private MapSelectionManager() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register(MapSelectionManager::onAttackBlock);
		UseBlockCallback.EVENT.register(MapSelectionManager::onUseBlock);
	}

	public static MapSelection get(UUID playerId) {
		return SELECTIONS.get(playerId);
	}

	private static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!WandItem.isWand(player.getMainHandItem())) {
			return InteractionResult.PASS;
		}
		MapSelection selection = SELECTIONS.computeIfAbsent(player.getUUID(), id -> new MapSelection());
		selection.pos1 = pos;
		serverPlayer.sendSystemMessage(Component.literal("Canto 1 definido: " + pos.toShortString()).withStyle(ChatFormatting.GREEN));
		return InteractionResult.FAIL;
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!WandItem.isWand(player.getMainHandItem())) {
			return InteractionResult.PASS;
		}
		MapSelection selection = SELECTIONS.computeIfAbsent(player.getUUID(), id -> new MapSelection());
		selection.pos2 = hitResult.getBlockPos();
		serverPlayer.sendSystemMessage(Component.literal("Canto 2 definido: " + hitResult.getBlockPos().toShortString()).withStyle(ChatFormatting.GREEN));
		return InteractionResult.FAIL;
	}
}
