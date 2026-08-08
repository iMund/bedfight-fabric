package br.com.tavares.bedfight;

import br.com.tavares.bedfight.arena.MapCaptureService;
import br.com.tavares.bedfight.arena.MapSelection;
import br.com.tavares.bedfight.arena.MapSelectionManager;
import br.com.tavares.bedfight.arena.WandItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class BedFightCommands {
	private static final String ADMIN_PERMISSION_NODE = "bedfight.admin.use";
	private static final Permission ADMIN_PERMISSION =
		Permission.Atom.create(Identifier.fromNamespaceAndPath(BedFight.MOD_ID, ADMIN_PERMISSION_NODE));

	private BedFightCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("bedfight")
			.then(literal("admin")
				.requires(BedFightCommands::hasAdminPermission)
				.then(literal("wand")
					.executes(BedFightCommands::giveWand))
				.then(literal("setspawn")
					.then(argument("mapId", StringArgumentType.word())
						.then(literal("azul")
							.executes(context -> setSpawn(context, "azul")))
						.then(literal("vermelho")
							.executes(context -> setSpawn(context, "vermelho")))))
				.then(literal("capturar")
					.then(argument("mapId", StringArgumentType.word())
						.executes(BedFightCommands::capture)))));
	}

	private static int giveWand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		player.getInventory().add(WandItem.create());
		context.getSource().sendSuccess(() -> Component.literal("Varinha de selecao entregue.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int setSpawn(CommandContext<CommandSourceStack> context, String team) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String mapId = StringArgumentType.getString(context, "mapId");
		MapSelection selection = MapSelectionManager.get(player.getUUID());
		if (selection == null || !selection.isComplete()) {
			context.getSource().sendFailure(Component.literal("Marque os dois cantos da regiao com a varinha antes.").withStyle(ChatFormatting.RED));
			return 0;
		}
		BlockPos pos = player.blockPosition();
		MapCaptureService.setSpawn(mapId, team, selection, pos, player.getYRot());
		context.getSource().sendSuccess(() -> Component.literal("Spawn do time " + team + " definido para o mapa " + mapId + ".").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int capture(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String mapId = StringArgumentType.getString(context, "mapId");
		MapSelection selection = MapSelectionManager.get(player.getUUID());
		if (selection == null || !selection.isComplete()) {
			context.getSource().sendFailure(Component.literal("Marque os dois cantos da regiao com a varinha antes.").withStyle(ChatFormatting.RED));
			return 0;
		}
		try {
			MapCaptureService.capture(mapId, context.getSource().getLevel(), selection);
		} catch (IOException exception) {
			BedFight.LOGGER.error("Falha ao capturar o mapa {}.", mapId, exception);
			context.getSource().sendFailure(Component.literal("Falha ao capturar o mapa, veja o console.").withStyle(ChatFormatting.RED));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Mapa " + mapId + " capturado.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static boolean hasAdminPermission(CommandSourceStack source) {
		if (source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			return true;
		}
		return source.permissions().hasPermission(ADMIN_PERMISSION);
	}
}
