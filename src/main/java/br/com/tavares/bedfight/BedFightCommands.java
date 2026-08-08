package br.com.tavares.bedfight;

import br.com.tavares.bedfight.arena.ArenaDimension;
import br.com.tavares.bedfight.arena.ArenaInstance;
import br.com.tavares.bedfight.arena.ArenaInstancePool;
import br.com.tavares.bedfight.arena.ArenaInstanceService;
import br.com.tavares.bedfight.arena.ArenaSpawn;
import br.com.tavares.bedfight.arena.MapCaptureException;
import br.com.tavares.bedfight.arena.MapCaptureService;
import br.com.tavares.bedfight.arena.MapSelection;
import br.com.tavares.bedfight.arena.MapSelectionManager;
import br.com.tavares.bedfight.arena.Team;
import br.com.tavares.bedfight.arena.WandItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class BedFightCommands {
	private static final Pattern VALID_MAP_ID = Pattern.compile("[a-z0-9_-]{1,32}");
	private static final SimpleCommandExceptionType INVALID_MAP_ID = new SimpleCommandExceptionType(
		Component.literal("mapId invalido - use so letras minusculas, numeros, '-' e '_' (1 a 32 caracteres)."));

	private BedFightCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("bedfight")
			.then(literal("admin")
				.requires(source -> BedFightPermissions.hasAdminPermission(source.permissions()))
				.then(literal("wand")
					.executes(BedFightCommands::giveWand))
				.then(literal("setspawn")
					.then(argument("mapId", StringArgumentType.word())
						.then(literal("azul")
							.executes(context -> setSpawn(context, Team.AZUL)))
						.then(literal("vermelho")
							.executes(context -> setSpawn(context, Team.VERMELHO)))))
				.then(literal("capturar")
					.then(argument("mapId", StringArgumentType.word())
						.executes(BedFightCommands::capture)))
				.then(literal("testarena")
					.then(argument("instancia", IntegerArgumentType.integer(0))
						.then(argument("mapId", StringArgumentType.word())
							.executes(BedFightCommands::testArena))))));
	}

	private static int giveWand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		player.getInventory().add(WandItem.create());
		context.getSource().sendSuccess(() -> Component.literal("Varinha de selecao entregue.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int setSpawn(CommandContext<CommandSourceStack> context, Team team) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String mapId = requireValidMapId(context);
		MapSelection selection = MapSelectionManager.get(player.getUUID());
		if (selection == null || !selection.isComplete()) {
			context.getSource().sendFailure(Component.literal("Marque os dois cantos da regiao com a varinha antes.").withStyle(ChatFormatting.RED));
			return 0;
		}
		BlockPos pos = player.blockPosition();
		try {
			MapCaptureService.setSpawn(mapId, team, selection, pos, player.getYRot(), player.getXRot());
		} catch (MapCaptureException exception) {
			context.getSource().sendFailure(Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Spawn do time " + team.id() + " definido para o mapa " + mapId + ".").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int capture(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String mapId = requireValidMapId(context);
		MapSelection selection = MapSelectionManager.get(player.getUUID());
		if (selection == null || !selection.isComplete()) {
			context.getSource().sendFailure(Component.literal("Marque os dois cantos da regiao com a varinha antes.").withStyle(ChatFormatting.RED));
			return 0;
		}
		try {
			MapCaptureService.capture(mapId, context.getSource().getLevel(), selection);
		} catch (MapCaptureException exception) {
			context.getSource().sendFailure(Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
			return 0;
		} catch (IOException exception) {
			BedFight.LOGGER.error("Falha ao capturar o mapa {}.", mapId, exception);
			context.getSource().sendFailure(Component.literal("Falha ao capturar o mapa, veja o console.").withStyle(ChatFormatting.RED));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Mapa " + mapId + " capturado.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int testArena(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String mapId = requireValidMapId(context);
		int index = IntegerArgumentType.getInteger(context, "instancia");

		ServerLevel arenaLevel = ArenaDimension.get(context.getSource().getServer());
		if (arenaLevel == null) {
			context.getSource().sendFailure(Component.literal("Dimensao bedfight:arena nao carregou.").withStyle(ChatFormatting.RED));
			return 0;
		}
		Optional<ArenaInstance> instance = ArenaInstancePool.byIndex(index);
		if (instance.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Instancia " + index + " nao existe (pool tem " + ArenaInstancePool.all().size() + ").").withStyle(ChatFormatting.RED));
			return 0;
		}

		try {
			ArenaInstanceService.paste(arenaLevel, instance.get(), mapId);
		} catch (IOException exception) {
			BedFight.LOGGER.error("Falha ao colar o mapa {} na instancia {}.", mapId, index, exception);
			context.getSource().sendFailure(Component.literal("Falha ao colar o mapa, veja o console.").withStyle(ChatFormatting.RED));
			return 0;
		}

		ArenaSpawn spawn = ArenaInstanceService.teamSpawn(instance.get(), mapId, Team.AZUL);
		player.teleportTo(arenaLevel, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch(), false);
		context.getSource().sendSuccess(() -> Component.literal("Mapa " + mapId + " colado na instancia " + index + ".").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static String requireValidMapId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String mapId = StringArgumentType.getString(context, "mapId");
		if (!VALID_MAP_ID.matcher(mapId).matches()) {
			throw INVALID_MAP_ID.create();
		}
		return mapId;
	}
}
