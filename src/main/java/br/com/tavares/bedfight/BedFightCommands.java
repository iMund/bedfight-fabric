package br.com.tavares.bedfight;

import br.com.tavares.bedfight.arena.ArenaDimension;
import br.com.tavares.bedfight.arena.ArenaInstance;
import br.com.tavares.bedfight.arena.ArenaInstancePool;
import br.com.tavares.bedfight.arena.ArenaInstanceService;
import br.com.tavares.bedfight.arena.ArenaSpawn;
import br.com.tavares.bedfight.arena.BedProtectionScanner;
import br.com.tavares.bedfight.arena.MapCaptureException;
import br.com.tavares.bedfight.arena.MapCaptureService;
import br.com.tavares.bedfight.arena.MapSelection;
import br.com.tavares.bedfight.arena.MapSelectionManager;
import br.com.tavares.bedfight.arena.Team;
import br.com.tavares.bedfight.arena.WandItem;
import br.com.tavares.bedfight.config.ArenaConfig;
import br.com.tavares.bedfight.config.KitConfig;
import br.com.tavares.bedfight.config.MatchConfig;
import br.com.tavares.bedfight.kit.KitService;
import br.com.tavares.bedfight.match.GameMode;
import br.com.tavares.bedfight.match.MatchManager;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class BedFightCommands {
	private static final Pattern VALID_MAP_ID = Pattern.compile("[a-z0-9_-]{1,32}");
	private static final SimpleCommandExceptionType INVALID_MAP_ID = new SimpleCommandExceptionType(
		Component.literal("mapId invalido - use so letras minusculas, numeros, '-' e '_' (1 a 32 caracteres)."));
	/**
	 * Well outside any instance grid slot (instances only ever sit at x = index * gridSpacingBlocks,
	 * always >= 0, z = 0) - a safe place to build/capture a map without ever touching survival or
	 * overlapping a live match. Numbered so each map gets its own spot instead of every build landing
	 * on top of the last one - zone N is 500 blocks further along Z than zone N-1.
	 */
	private static final int BUILD_ZONE_BASE_X = -2000;
	private static final int BUILD_ZONE_Y = 100;
	private static final int BUILD_ZONE_SPACING = 500;
	private static final int BUILD_ZONE_PLATFORM_RADIUS = 20;

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
							.executes(BedFightCommands::testArena))))
				.then(literal("freearena")
					.then(argument("instancia", IntegerArgumentType.integer(0))
						.executes(BedFightCommands::freeArena)))
				.then(literal("testkit")
					.executes(context -> testKit(context, Team.AZUL))
					.then(literal("azul")
						.executes(context -> testKit(context, Team.AZUL)))
					.then(literal("vermelho")
						.executes(context -> testKit(context, Team.VERMELHO))))
				.then(literal("reload")
					.executes(BedFightCommands::reload))
				.then(literal("buildzone")
					.then(argument("area", IntegerArgumentType.integer(0))
						.executes(BedFightCommands::buildZone))))
			.then(literal("join")
				.then(literal("1v1")
					.executes(context -> join(context, GameMode.ONE_V_ONE)))
				.then(literal("2v2")
					.executes(context -> join(context, GameMode.TWO_V_TWO)))));
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
		} catch (IOException exception) {
			BedFight.LOGGER.error("Falha ao ler map.yml existente do mapa {}.", mapId, exception);
			context.getSource().sendFailure(Component.literal("Mapa " + mapId + " ja tem um map.yml, mas ele nao pode ser lido - veja o console antes de continuar (senao o proximo salvamento apaga o que ja tinha).").withStyle(ChatFormatting.RED));
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

		BedProtectionScanner.Result scan;
		try {
			scan = ArenaInstanceService.paste(arenaLevel, instance.get(), mapId);
		} catch (MapCaptureException exception) {
			context.getSource().sendFailure(Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
			return 0;
		} catch (IOException exception) {
			BedFight.LOGGER.error("Falha ao colar o mapa {} na instancia {}.", mapId, index, exception);
			context.getSource().sendFailure(Component.literal("Falha ao colar o mapa, veja o console.").withStyle(ChatFormatting.RED));
			return 0;
		}

		Optional<ArenaSpawn> spawn = ArenaInstanceService.teamSpawn(instance.get(), mapId, Team.AZUL);
		if (spawn.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Mapa " + mapId + " colado, mas nao tem spawn do time azul definido.").withStyle(ChatFormatting.RED));
			return 0;
		}
		player.teleportTo(arenaLevel, spawn.get().x(), spawn.get().y(), spawn.get().z(), Set.of(), spawn.get().yaw(), spawn.get().pitch(), false);
		context.getSource().sendSuccess(() -> Component.literal("Mapa " + mapId + " colado na instancia " + index + " ("
			+ scan.bedCount() + " cama(s), " + scan.woodCount() + " madeira, " + scan.endStoneCount() + " end stone).").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int freeArena(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		int index = IntegerArgumentType.getInteger(context, "instancia");
		Optional<ArenaInstance> instance = ArenaInstancePool.byIndex(index);
		if (instance.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Instancia " + index + " nao existe.").withStyle(ChatFormatting.RED));
			return 0;
		}
		ArenaInstanceService.free(instance.get());
		context.getSource().sendSuccess(() -> Component.literal("Instancia " + index + " liberada.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int testKit(CommandContext<CommandSourceStack> context, Team team) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		KitService.Result result = KitService.giveKit(player, team);
		if (!result.isComplete()) {
			context.getSource().sendFailure(Component.literal("Kit entregue com problemas: " + String.join("; ", result.failures())).withStyle(ChatFormatting.RED));
			return result.given();
		}
		context.getSource().sendSuccess(() -> Component.literal("Kit entregue (" + result.given() + " itens, inventario resetado).").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		int activeMatches = MatchManager.activeMatchCount();
		if (activeMatches > 0) {
			context.getSource().sendFailure(Component.literal("Tem " + activeMatches + " partida(s) em andamento - reconstruir o pool agora quebraria elas. Espere terminar.").withStyle(ChatFormatting.RED));
			return 0;
		}
		ArenaConfig.load();
		KitConfig.load();
		MatchConfig.load();
		ArenaInstancePool.init();
		context.getSource().sendSuccess(() -> Component.literal("Configs recarregadas e pool de instancias reconstruido.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	/** Teleports the admin to build zone number "area" inside bedfight:arena - each number is its own isolated platform, 500 blocks apart, so building map 2 never lands on top of map 1. Build the map "molde" here instead of the survival overworld, then select/setspawn/capturar from here same as always. */
	private static int buildZone(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int area = IntegerArgumentType.getInteger(context, "area");
		ServerLevel arenaLevel = ArenaDimension.get(context.getSource().getServer());
		if (arenaLevel == null) {
			context.getSource().sendFailure(Component.literal("Dimensao bedfight:arena nao carregou.").withStyle(ChatFormatting.RED));
			return 0;
		}
		BlockPos origin = new BlockPos(BUILD_ZONE_BASE_X, BUILD_ZONE_Y, area * BUILD_ZONE_SPACING);
		if (arenaLevel.getBlockState(origin).isAir()) {
			for (int dx = -BUILD_ZONE_PLATFORM_RADIUS; dx <= BUILD_ZONE_PLATFORM_RADIUS; dx++) {
				for (int dz = -BUILD_ZONE_PLATFORM_RADIUS; dz <= BUILD_ZONE_PLATFORM_RADIUS; dz++) {
					arenaLevel.setBlock(origin.offset(dx, 0, dz), Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		player.teleportTo(arenaLevel, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), false);
		context.getSource().sendSuccess(() -> Component.literal("Teleportado pra area de construcao " + area + ". Construa o mapa aqui e capture normalmente.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int join(CommandContext<CommandSourceStack> context, GameMode mode) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		MatchManager.JoinResult result = MatchManager.join(player, mode, context.getSource().getServer());
		if (result.joined()) {
			context.getSource().sendSuccess(() -> Component.literal(result.message()).withStyle(ChatFormatting.GREEN), false);
			return 1;
		}
		context.getSource().sendFailure(Component.literal(result.message()).withStyle(ChatFormatting.YELLOW));
		return 0;
	}

	private static String requireValidMapId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String mapId = StringArgumentType.getString(context, "mapId");
		if (!VALID_MAP_ID.matcher(mapId).matches()) {
			throw INVALID_MAP_ID.create();
		}
		return mapId;
	}
}
