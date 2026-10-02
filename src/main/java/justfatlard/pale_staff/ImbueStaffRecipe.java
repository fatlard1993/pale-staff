package justfatlard.pale_staff;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The staff with one or more of the same ingredient, anywhere in the grid.
 *
 * <p>The same as it already carries tops it up; something else replaces what it carries, the old
 * charge lost. All the ingredients have to agree, and the craft is refused when it would change
 * nothing, so a full staff cannot swallow an ingredient for no casts.
 *
 * <p>What an ingredient brews is asked of the server's recipes, which {@code assemble} is given no
 * level to reach; the overworld stands in, since recipes are the same in every dimension.
 */
public class ImbueStaffRecipe extends CustomRecipe {
	public static final ImbueStaffRecipe INSTANCE = new ImbueStaffRecipe();
	public static final MapCodec<ImbueStaffRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, ImbueStaffRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<ImbueStaffRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private record Plan(ItemStack staff, Dose dose, int count) {}

	private static @Nullable Plan plan(CraftingInput input, ServerLevel level) {
		ItemStack staff = ItemStack.EMPTY;
		List<Dose> doses = new ArrayList<>();
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (stack.isEmpty()) continue;
			if (stack.is(PaleStaff.PALE_STAFF)) {
				if (!staff.isEmpty()) return null;
				staff = stack;
				continue;
			}
			Dose dose = Dose.of(stack, level).orElse(null);
			if (dose == null) return null;
			if (!doses.isEmpty() && !doses.getFirst().matches(dose.spell(), dose.contents())) return null;
			doses.add(dose);
		}
		if (staff.isEmpty() || doses.isEmpty()) return null;

		Dose dose = doses.getFirst();
		if (topsUp(staff, dose) && Imbuement.charge(staff) >= Imbuement.capacity(staff)) return null;
		return new Plan(staff, dose, doses.size());
	}

	private static boolean topsUp(ItemStack staff, Dose dose) {
		return Imbuement.isImbued(staff)
			&& dose.matches(Imbuement.spell(staff), Imbuement.contents(staff));
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return level instanceof ServerLevel server && plan(input, server) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		MinecraftServer server = PaleStaff.server();
		Plan plan = server == null ? null : plan(input, server.overworld());
		if (plan == null) return ItemStack.EMPTY;

		ItemStack result = plan.staff().copyWithCount(1);
		int added = plan.dose().charges() * plan.count();
		if (topsUp(result, plan.dose())) Imbuement.setCharge(result, Imbuement.charge(result) + added);
		else Imbuement.imbue(result, plan.dose(), added);
		return result;
	}

	@Override
	public RecipeSerializer<ImbueStaffRecipe> getSerializer() {
		return SERIALIZER;
	}
}
