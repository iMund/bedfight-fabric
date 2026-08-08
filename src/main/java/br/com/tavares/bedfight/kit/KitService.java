package br.com.tavares.bedfight.kit;

import br.com.tavares.bedfight.BedFight;
import br.com.tavares.bedfight.config.KitConfig;
import br.com.tavares.bedfight.config.KitItem;
import java.util.Map;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.Equippable;

public final class KitService {
	private KitService() {
	}

	/** Clears the player's inventory/armor/offhand and gives the fixed kit from kit.yml. */
	public static void giveKit(ServerPlayer player) {
		player.getInventory().clearContent();

		RegistryAccess registryAccess = player.registryAccess();
		for (KitItem kitItem : KitConfig.get().items) {
			ItemStack stack = createStack(kitItem, registryAccess);
			if (stack != null) {
				giveOrEquip(player, stack);
			}
		}
	}

	private static ItemStack createStack(KitItem kitItem, RegistryAccess registryAccess) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(kitItem.item));
		if (item == Items.AIR) {
			BedFight.LOGGER.warn("Item de kit desconhecido: {}", kitItem.item);
			return null;
		}
		ItemStack stack = new ItemStack(item, Math.max(1, kitItem.count));
		applyEnchantments(stack, kitItem.enchantments, registryAccess);
		return stack;
	}

	private static void applyEnchantments(ItemStack stack, Map<String, Integer> enchantments, RegistryAccess registryAccess) {
		if (enchantments == null || enchantments.isEmpty()) {
			return;
		}
		HolderGetter<Enchantment> enchantmentRegistry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
		for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
			ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(entry.getKey()));
			enchantmentRegistry.get(key).ifPresentOrElse(
				holder -> stack.enchant(holder, entry.getValue()),
				() -> BedFight.LOGGER.warn("Encantamento desconhecido: {}", entry.getKey()));
		}
	}

	private static void giveOrEquip(ServerPlayer player, ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			EquipmentSlot slot = equippable.slot();
			if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
				player.setItemSlot(slot, stack);
				return;
			}
		}
		player.getInventory().add(stack);
	}
}
