package br.com.tavares.bedfight.kit;

import br.com.tavares.bedfight.arena.Team;
import br.com.tavares.bedfight.config.KitConfig;
import br.com.tavares.bedfight.config.KitItem;
import java.util.ArrayList;
import java.util.List;
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
	public record Result(int given, List<String> failures) {
		public boolean isComplete() {
			return failures.isEmpty();
		}
	}

	private KitService() {
	}

	private static final String TEAM_WOOL_ID = "bedfight:team_wool";

	/** Clears the player's inventory (including crafting grid and cursor) and gives the fixed kit from kit.yml, team-only items (team_wool) resolved to that team's color. Pass null team to get a neutral kit (e.g. an admin test command with no match context) - team_wool falls back to white in that case. */
	public static Result giveKit(ServerPlayer player, Team team) {
		player.getInventory().clearContent();
		player.inventoryMenu.getCraftSlots().clearContent();
		player.containerMenu.setCarried(ItemStack.EMPTY);

		List<KitItem> items = KitConfig.get().items;
		List<String> failures = new ArrayList<>();
		if (items.isEmpty()) {
			failures.add("kit.yml nao tem nenhum item (config vazia ou falhou ao carregar)");
		}

		int given = 0;
		boolean[] armorFilled = new boolean[EquipmentSlot.values().length];
		RegistryAccess registryAccess = player.registryAccess();
		for (KitItem kitItem : items) {
			ItemStack stack = createStack(kitItem, team, registryAccess, failures);
			if (stack == null) {
				continue;
			}
			if (giveOrEquip(player, stack, armorFilled, failures)) {
				given++;
			}
		}

		player.containerMenu.broadcastChanges();
		player.inventoryMenu.slotsChanged(player.getInventory());
		return new Result(given, failures);
	}

	private static ItemStack createStack(KitItem kitItem, Team team, RegistryAccess registryAccess, List<String> failures) {
		Item item;
		if (TEAM_WOOL_ID.equals(kitItem.item)) {
			item = team == Team.VERMELHO ? Items.WOOL.red() : Items.WOOL.blue();
		} else {
			Identifier id = Identifier.tryParse(kitItem.item);
			item = id != null ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
		}
		if (item == Items.AIR) {
			failures.add("item de kit desconhecido: " + kitItem.item);
			return null;
		}
		int count = Math.min(Math.max(kitItem.count, 1), item.getDefaultMaxStackSize());
		if (kitItem.count != count) {
			failures.add("quantidade invalida (" + kitItem.count + ") pra " + kitItem.item + ", usando " + count);
		}
		ItemStack stack = new ItemStack(item, count);
		applyEnchantments(stack, kitItem.enchantments, registryAccess, failures);
		return stack;
	}

	private static void applyEnchantments(ItemStack stack, Map<String, Integer> enchantments, RegistryAccess registryAccess, List<String> failures) {
		if (enchantments == null || enchantments.isEmpty()) {
			return;
		}
		HolderGetter<Enchantment> enchantmentRegistry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
		for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id == null) {
				failures.add("id de encantamento invalido: " + entry.getKey());
				continue;
			}
			int level = entry.getValue();
			if (level < 1) {
				failures.add("nivel de encantamento invalido (" + level + ") pra " + entry.getKey());
				continue;
			}
			enchantmentRegistry.get(ResourceKey.create(Registries.ENCHANTMENT, id)).ifPresentOrElse(holder -> {
				int max = holder.value().getMaxLevel();
				if (level > max) {
					failures.add("nivel " + level + " de " + entry.getKey() + " acima do maximo (" + max + "), aplicando mesmo assim");
				}
				stack.enchant(holder, level);
			}, () -> failures.add("encantamento desconhecido: " + entry.getKey()));
		}
	}

	private static boolean giveOrEquip(ServerPlayer player, ItemStack stack, boolean[] armorFilled, List<String> failures) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			EquipmentSlot slot = equippable.slot();
			if (armorFilled[slot.getIndex()]) {
				failures.add("kit.yml tem mais de um item pro slot " + slot + ", ficou so o ultimo");
			}
			armorFilled[slot.getIndex()] = true;
			player.setItemSlot(slot, stack);
			return true;
		}
		boolean added = player.getInventory().add(stack);
		if (!added) {
			failures.add("inventario cheio, item perdido: " + stack.getItem());
		}
		return added;
	}
}
