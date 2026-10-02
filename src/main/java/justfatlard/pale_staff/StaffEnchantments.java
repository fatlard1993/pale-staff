package justfatlard.pale_staff;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The staff's own enchantments, and its read of the vanilla ones it borrows.
 *
 * <p>The borrowed ones come in through the crossbow tag, so the enchanting table, the anvil and the
 * librarian treat the staff as they treat a crossbow. None of their vanilla effects fire, since those
 * hang off a loaded arrow; the staff reads the level and gives each its counterpart on a bolt
 * instead.
 */
public final class StaffEnchantments {
	/** Glowstone's counterpart: each level strengthens what a cast carries by one. */
	public static final ResourceKey<Enchantment> POTENCY = own("potency");
	/** Redstone's: each level makes a cast last half as long again. */
	public static final ResourceKey<Enchantment> PROLONGING = own("prolonging");
	/** Gunpowder's: the bolt bursts on what it strikes and catches everything near. */
	public static final ResourceKey<Enchantment> SPLASH = own("splash");
	/** Dragon's breath's: the bolt leaves a cloud where it lands. */
	public static final ResourceKey<Enchantment> LINGERING = own("lingering");
	/** Each level holds half as many casts again. */
	public static final ResourceKey<Enchantment> RESERVOIR = own("reservoir");
	/** Infinity's: an imbued staff draws back its charge on its own, slowly. */
	public static final ResourceKey<Enchantment> WELLSPRING = own("wellspring");

	private StaffEnchantments() {}

	private static ResourceKey<Enchantment> own(String name) {
		return ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(PaleStaff.MOD_ID, name));
	}

	/** Read off the stack, so it needs no registry: what is not on it is level 0. */
	public static int level(ItemStack stack, ResourceKey<Enchantment> key) {
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
			if (entry.getKey().is(key)) return entry.getIntValue();
		}
		return 0;
	}
}
