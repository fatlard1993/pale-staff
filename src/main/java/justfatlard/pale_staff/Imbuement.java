package justfatlard.pale_staff;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a staff carries and how much of it is left, kept in the game's own components.
 *
 * <p>The truth is in {@code custom_data}: the spell, the charge, the share of a potion each cast
 * carries, and the item that imbued it. An effect is also the stack's {@code potion_contents}, the
 * component a potion has, so every client lists it in the tooltip; {@code potion_duration_scale} is
 * one cast's share, so the durations shown are the durations a cast gives.
 *
 * <p>The rest is drawn from those and never read back: the lore, and {@code custom_model_data},
 * which is what the item model reads. Its flag says imbued, its float is how full, its string is
 * the imbuing item, which picks the socket, and its colour tints the bar.
 */
public final class Imbuement {
	private static final String KEY = PaleStaff.MOD_ID;
	private static final String SPELL = "spell";
	private static final String CHARGE = "charge";
	private static final String DOSE = "dose";
	private static final String SOURCE = "source";

	private Imbuement() {}

	public static boolean isImbued(ItemStack staff) {
		return state(staff).contains(SPELL);
	}

	public static Spell spell(ItemStack staff) {
		return Spell.byId(state(staff).getStringOr(SPELL, ""));
	}

	public static PotionContents contents(ItemStack staff) {
		return staff.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
	}

	public static int charge(ItemStack staff) {
		return state(staff).getIntOr(CHARGE, 0);
	}

	/** The share of the potion's full duration one cast carries, before Prolonging. */
	public static float dose(ItemStack staff) {
		return state(staff).getFloatOr(DOSE, 1);
	}

	/** The item that imbued the staff, by id. */
	public static String source(ItemStack staff) {
		return state(staff).getStringOr(SOURCE, "");
	}

	public static int capacity(ItemStack staff) {
		int base = PaleStaff.config().baseCapacity;
		return base + base * StaffEnchantments.level(staff, StaffEnchantments.RESERVOIR) / 2;
	}

	/** Prolonging's half again per level, on top of the dose. */
	public static float durationScale(ItemStack staff) {
		return dose(staff) * prolonging(staff);
	}

	public static float prolonging(ItemStack staff) {
		return 1 + 0.5F * StaffEnchantments.level(staff, StaffEnchantments.PROLONGING);
	}

	public static int colour(ItemStack staff) {
		Spell spell = spell(staff);
		return spell == Spell.EFFECT ? ARGB.opaque(contents(staff).getColor()) : spell.colour;
	}

	public static void imbue(ItemStack staff, Dose dose, int charge) {
		imbue(staff, dose.spell(), dose.contents(), dose.scale(), charge, BuiltInRegistries.ITEM.getKey(dose.source()).toString());
	}

	private static void imbue(ItemStack staff, Spell spell, PotionContents contents, float dose, int charge, String source) {
		if (spell == Spell.EFFECT) staff.set(DataComponents.POTION_CONTENTS, contents);
		else staff.remove(DataComponents.POTION_CONTENTS);
		CustomData.update(DataComponents.CUSTOM_DATA, staff, root -> {
			CompoundTag state = new CompoundTag();
			state.putString(SPELL, spell.id());
			state.putFloat(DOSE, dose);
			state.putString(SOURCE, source);
			state.putInt(CHARGE, Math.clamp(charge, 0, capacity(staff)));
			root.put(KEY, state);
		});
		refresh(staff);
	}

	public static void setCharge(ItemStack staff, int charge) {
		imbue(staff, spell(staff), contents(staff), dose(staff), charge, source(staff));
	}

	/**
	 * Bring the bar, the socket, the tooltip durations and the lore up to date with the charge and
	 * the enchantments. Cheap when nothing has changed, which is most ticks: an enchantment put on
	 * at an anvil is the one change that arrives without passing through here.
	 */
	public static void refresh(ItemStack staff) {
		CustomModelData model = null;
		List<Component> lines = new ArrayList<>();
		if (isImbued(staff)) {
			int capacity = capacity(staff);
			int charge = Math.min(charge(staff), capacity);
			model = new CustomModelData(List.of((float) charge / capacity), List.of(true), List.of(source(staff)),
				List.of(colour(staff)));
			Spell spell = spell(staff);
			if (spell != Spell.EFFECT) lines.add(line(".spell." + spell.id(), ChatFormatting.GOLD));
			lines.add(line(".charge", ChatFormatting.GRAY, charge, capacity));
			if (spell == Spell.EFFECT) setScale(staff, durationScale(staff));
		} else {
			lines.add(line(".unimbued", ChatFormatting.GRAY));
		}
		if (!isImbued(staff) || spell(staff) != Spell.EFFECT) staff.remove(DataComponents.POTION_DURATION_SCALE);

		if (!Objects.equals(staff.get(DataComponents.CUSTOM_MODEL_DATA), model)) {
			if (model == null) staff.remove(DataComponents.CUSTOM_MODEL_DATA);
			else staff.set(DataComponents.CUSTOM_MODEL_DATA, model);
		}
		ItemLore lore = new ItemLore(lines);
		if (!lore.equals(staff.get(DataComponents.LORE))) staff.set(DataComponents.LORE, lore);
	}

	private static Component line(String key, ChatFormatting colour, Object... args) {
		return Component.translatable("item." + PaleStaff.MOD_ID + ".pale_staff" + key, args)
			.withStyle(style -> style.withItalic(false).withColor(colour));
	}

	private static void setScale(ItemStack staff, float scale) {
		Float current = staff.get(DataComponents.POTION_DURATION_SCALE);
		if (current == null || current != scale) staff.set(DataComponents.POTION_DURATION_SCALE, scale);
	}

	private static CompoundTag state(ItemStack staff) {
		return staff.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(KEY);
	}
}
