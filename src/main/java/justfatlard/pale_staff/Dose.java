package justfatlard.pale_staff;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What one ingredient crafted with the staff puts into it.
 *
 * <p>A potion's effect comes from the ingredient that brews it: whatever the game's brewing recipes
 * turn an awkward potion into with this ingredient, or a water bottle failing that, which is how
 * fermented spider eye makes weakness. Read from the recipes, so a datapack's or another mod's brew
 * imbues too. The staff never takes the potion itself.
 *
 * @param spell what kind of thing a cast does
 * @param contents for an effect, what each cast carries at full duration; empty otherwise
 * @param scale the share of that duration one cast carries
 * @param charges how many casts the ingredient fills
 * @param source the ingredient's item, which the staff shows set in its socket
 */
public record Dose(Spell spell, PotionContents contents, float scale, int charges, Item source) {
	/** Effects the game gives no other way: levitation as a shulker's bullet gives it, and glowing. */
	private static final Map<Item, MobEffectInstance> EXTRAS = Map.of(
		Items.SHULKER_SHELL, new MobEffectInstance(MobEffects.LEVITATION, 200),
		Items.GLOW_INK_SAC, new MobEffectInstance(MobEffects.GLOWING, 600));

	private static final Map<Item, Spell> SPELLS = Map.of(
		Items.FIRE_CHARGE, Spell.FLAME,
		Items.GOLDEN_DANDELION, Spell.BLOOM,
		Items.WIND_CHARGE, Spell.GUST,
		Items.ECHO_SHARD, Spell.BOOM,
		Items.HEART_OF_THE_SEA, Spell.STORM,
		Items.ENDER_PEARL, Spell.BLINK);

	/**
	 * Whether this tops up a staff already carrying {@code otherSpell} and {@code otherContents}. The
	 * share a cast carries is left out: a staff keeps the one it was imbued with, so changing the
	 * setting does not make more of the same wash out what a staff already holds.
	 */
	public boolean matches(Spell otherSpell, PotionContents otherContents) {
		return spell == otherSpell && contents.equals(otherContents);
	}

	public static Optional<Dose> of(ItemStack ingredient, ServerLevel level) {
		int charges = PaleStaff.config().chargesPerIngredient;
		Item item = ingredient.getItem();

		Spell spell = SPELLS.get(item);
		if (spell != null) return Optional.of(new Dose(spell, PotionContents.EMPTY, 1, charges, item));

		MobEffectInstance extra = EXTRAS.get(item);
		if (extra != null) {
			return Optional.of(new Dose(Spell.EFFECT,
				new PotionContents(Optional.empty(), Optional.empty(), List.of(extra), Optional.empty()), 1, charges, item));
		}

		return brewed(ingredient, level).map(contents ->
			new Dose(Spell.EFFECT, contents, PaleStaff.config().castShareOfPotion, charges, item));
	}

	private static Optional<PotionContents> brewed(ItemStack reagent, ServerLevel level) {
		for (var base : List.of(Potions.AWKWARD, Potions.WATER)) {
			BrewingInput input = new BrewingInput(PotionContents.createItemStack(Items.POTION, base), reagent.copyWithCount(1));
			Optional<PotionContents> made = level.getServer().getRecipeManager().getRecipeFor(RecipeType.BREWING, input, level)
				.map(recipe -> recipe.value().assemble(input).getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY))
				.filter(PotionContents::hasEffects);
			if (made.isPresent()) return made;
		}
		return Optional.empty();
	}
}
