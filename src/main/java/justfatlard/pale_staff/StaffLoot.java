package justfatlard.pale_staff;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

import java.util.Optional;

/**
 * Where a staff is found: the creaking, and, very rarely, the leaves of the trees it lives among.
 *
 * <p>A creaking bound to a heart cannot be killed, only undone by breaking the heart, and the game
 * counts that as the player killing it and rolls its loot table. So "killed by a player" covers both
 * a heart broken and a stray creaking fought down, and Looting on what broke the heart helps.
 *
 * <p>The leaves give one up however they come down, broken or decayed, but only inside a pale
 * garden: pale oak planted elsewhere does not count.
 */
public final class StaffLoot {
	private static final Optional<ResourceKey<LootTable>> LEAVES = Blocks.PALE_OAK_LEAVES.getLootTable();
	private static final Optional<ResourceKey<LootTable>> CREAKING = EntityTypes.CREAKING.getDefaultLootTable();

	private StaffLoot() {}

	public static void register(PaleStaffConfig config) {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			// Only vanilla's own copy. A datapack that has rewritten one of these has said what it
			// wants in it, and appending to that overrules an author who was more specific.
			if (source != LootTableSource.VANILLA) return;

			if (CREAKING.isPresent() && key.equals(CREAKING.get())) {
				builder.pool(staff()
					.when(LootItemKilledByPlayerCondition.killedByPlayer())
					.when(LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(
						registries.lookupOrThrow(Registries.ENCHANTMENT), config.creakingChance, config.creakingLootingBonus))
					.build());
			}

			if (LEAVES.isPresent() && key.equals(LEAVES.get())) {
				Optional<Holder.Reference<Biome>> paleGarden = registries.lookupOrThrow(Registries.BIOME).get(Biomes.PALE_GARDEN);
				if (paleGarden.isEmpty()) return;
				builder.pool(staff()
					.when(LocationCheck.checkLocation(LocationPredicate.Builder.inBiome(paleGarden.get())))
					.when(LootItemRandomChanceCondition.randomChance(config.leafChance))
					.build());
			}
		});
	}

	private static LootPool.Builder staff() {
		return LootPool.lootPool()
			.setRolls(ContextIntProviders.exactly(1))
			.add(LootItem.lootTableItem(PaleStaff.PALE_STAFF));
	}
}
