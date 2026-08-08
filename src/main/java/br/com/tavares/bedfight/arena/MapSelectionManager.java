package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.BedFightPermissions;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class MapSelectionManager {
	private static final Map<UUID, MapSelection> SELECTIONS = new ConcurrentHashMap<>();

	private MapSelectionManager() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register(MapSelectionManager::onAttackBlock);
		UseBlockCallback.EVENT.register(MapSelectionManager::onUseBlock);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SELECTIONS.remove(handler.getPlayer().getUUID()));
	}

	public static MapSelection get(UUID playerId) {
		return SELECTIONS.get(playerId);
	}

	private static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!WandItem.isWand(player.getItemInHand(hand)) || !BedFightPermissions.hasAdminPermission(serverPlayer.permissions())) {
			return InteractionResult.PASS;
		}
		MapSelection selection = selectionFor(serverPlayer, level);
		selection.pos1 = pos;
		serverPlayer.sendSystemMessage(Component.literal("Canto 1 definido: " + pos.toShortString()).withStyle(ChatFormatting.GREEN));
		return InteractionResult.FAIL;
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!WandItem.isWand(player.getItemInHand(hand)) || !BedFightPermissions.hasAdminPermission(serverPlayer.permissions())) {
			return InteractionResult.PASS;
		}
		MapSelection selection = selectionFor(serverPlayer, level);
		selection.pos2 = hitResult.getBlockPos();
		serverPlayer.sendSystemMessage(Component.literal("Canto 2 definido: " + hitResult.getBlockPos().toShortString()).withStyle(ChatFormatting.GREEN));
		return InteractionResult.FAIL;
	}

	/** Resets the selection if the player switched dimension since the last click - corners from different worlds make no sense together. */
	private static MapSelection selectionFor(ServerPlayer player, Level level) {
		MapSelection selection = SELECTIONS.computeIfAbsent(player.getUUID(), id -> new MapSelection());
		if (selection.dimension != null && selection.dimension != level.dimension()) {
			player.sendSystemMessage(Component.literal("Voce mudou de dimensao, a selecao foi reiniciada.").withStyle(ChatFormatting.YELLOW));
			selection.pos1 = null;
			selection.pos2 = null;
		}
		selection.dimension = level.dimension();
		return selection;
	}
}
