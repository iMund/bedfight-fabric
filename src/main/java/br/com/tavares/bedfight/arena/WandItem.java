package br.com.tavares.bedfight.arena;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WandItem {
	private static final String WAND_NAME = "Bed Fight - Varinha de Selecao";

	private WandItem() {
	}

	public static boolean isWand(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.getItem() != Items.GOLDEN_AXE) {
			return false;
		}
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name != null && name.getString().equalsIgnoreCase(WAND_NAME);
	}

	public static ItemStack create() {
		ItemStack stack = new ItemStack(Items.GOLDEN_AXE);
		stack.set(DataComponents.CUSTOM_NAME, notItalic(Component.literal(WAND_NAME).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));

		List<Component> lore = new ArrayList<>();
		lore.add(notItalic(Component.literal("Clique esquerdo").withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" marca o canto 1.").withStyle(ChatFormatting.GRAY))));
		lore.add(notItalic(Component.literal("Clique direito").withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" marca o canto 2.").withStyle(ChatFormatting.GRAY))));
		stack.set(DataComponents.LORE, new ItemLore(lore));

		return stack;
	}

	private static Component notItalic(Component text) {
		return text.copy().withStyle(style -> style.withItalic(false));
	}
}
