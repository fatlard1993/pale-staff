package justfatlard.pale_staff;

import justfatlard.pandorical.api.PandoricalApi;

/**
 * The config on Pandorical's settings page, for ops: each change is saved to the file at once. The
 * page holds whole numbers, so the chances are shown as percentages and the leaf as one in so many.
 */
public final class StaffSettings {
	private StaffSettings() {}

	public static void register(PaleStaffConfig config) {
		var group = PandoricalApi.settings().serverGroup(PaleStaff.MOD_ID, "Pale Staff");

		group.number("leafOneIn", "Pale oak leaves drop a staff, one in", 100, 20000, 100, Math.round(1 / config.leafChance))
			.describe("Per leaf that comes down inside a pale garden; takes hold at the next /reload")
			.backedBy(player -> Math.round(1 / config.leafChance), (player, oneIn) -> {
				config.leafChance = 1F / oneIn;
				config.save();
			});
		group.number("creakingPercent", "Creakings drop a staff, percent", 0, 100, 1, Math.round(config.creakingChance * 100))
			.describe("Per creaking killed by a player, before Looting; takes hold at the next /reload")
			.backedBy(player -> Math.round(config.creakingChance * 100), (player, percent) -> {
				config.creakingChance = percent / 100F;
				config.save();
			});
		group.number("chargesPerIngredient", "Casts per ingredient", 1, 16, 1, config.chargesPerIngredient)
			.backedBy(player -> config.chargesPerIngredient, (player, charges) -> {
				config.chargesPerIngredient = charges;
				config.save();
			});
		group.number("baseCapacity", "Casts a staff holds", 4, 64, 4, config.baseCapacity)
			.describe("Before Reservoir, which adds half again per level")
			.backedBy(player -> config.baseCapacity, (player, capacity) -> {
				config.baseCapacity = capacity;
				config.save();
			});
		group.number("castSharePercent", "A cast's share of its potion, percent", 5, 100, 5, Math.round(config.castShareOfPotion * 100))
			.describe("How much of a potion's duration one cast of its brewing ingredient gives")
			.backedBy(player -> Math.round(config.castShareOfPotion * 100), (player, percent) -> {
				config.castShareOfPotion = percent / 100F;
				config.save();
			});
		group.number("bloomPercent", "Bloom turns a hostile mob, percent", 0, 100, 5, Math.round(config.bloomConversionChance * 100))
			.describe("Before Potency, which adds 15 per level")
			.backedBy(player -> Math.round(config.bloomConversionChance * 100), (player, percent) -> {
				config.bloomConversionChance = percent / 100F;
				config.save();
			});
	}
}
