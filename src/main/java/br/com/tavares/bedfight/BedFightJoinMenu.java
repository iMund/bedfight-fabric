package br.com.tavares.bedfight;

import br.com.tavares.bedfight.match.GameMode;
import br.com.tavares.bedfight.match.MatchManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A read-only chest GUI ("Jogar Bed Fight") standing in for a full custom screen - clicking a bed picks that mode's queue, nothing can actually be taken out. */
final class BedFightJoinMenu extends ChestMenu {
	private static final int SLOT_1V1 = 11;
	private static final int SLOT_2V2 = 15;

	private final ServerPlayer player;

	private BedFightJoinMenu(int containerId, Inventory playerInventory, ServerPlayer player, Container container) {
		super(MenuType.GENERIC_9x3, containerId, playerInventory, container, 3);
		this.player = player;
	}

	static void open(ServerPlayer player) {
		SimpleContainer container = new SimpleContainer(27);
		container.setItem(SLOT_1V1, modeItem(GameMode.ONE_V_ONE, DyeColor.RED));
		container.setItem(SLOT_2V2, modeItem(GameMode.TWO_V_TWO, DyeColor.BLUE));
		player.openMenu(new SimpleMenuProvider(
			(containerId, inventory, opener) -> new BedFightJoinMenu(containerId, inventory, player, container),
			Component.literal("Jogar Bed Fight")));
	}

	private static ItemStack modeItem(GameMode mode, DyeColor color) {
		ItemStack stack = new ItemStack(Items.BED.pick(color));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Bed Fight " + mode.id())
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GREEN).withBold(true)));
		return stack;
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
		if (slotId == SLOT_1V1) {
			select(GameMode.ONE_V_ONE);
		} else if (slotId == SLOT_2V2) {
			select(GameMode.TWO_V_TWO);
		}
		// Deliberately never calls super.clicked - this menu is a picker, not a real inventory,
		// nothing should ever actually move between its slots.
	}

	private void select(GameMode mode) {
		player.closeContainer();
		MatchManager.JoinResult result = MatchManager.join(player, mode, player.level().getServer());
		player.sendSystemMessage(Component.literal(result.message()).withStyle(result.joined() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
	}
}
