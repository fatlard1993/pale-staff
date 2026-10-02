package justfatlard.pale_staff;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The staff: use to cast what it carries down your line of sight, sneak-use to cast it on yourself
 * (a fire charge refuses that, with a hiss).
 * A blunt weapon besides, worn by a swing as by a cast.
 */
public class PaleStaffItem extends Item {
	/** Wellspring draws back one charge this often, held or carried. */
	private static final int WELLSPRING_TICKS = 600;

	public PaleStaffItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack staff = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server)) return InteractionResult.PASS;

		if (!Imbuement.isImbued(staff) || Imbuement.charge(staff) <= 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
				SoundSource.PLAYERS, 0.5F, 1.6F);
			return InteractionResult.FAIL;
		}

		if (!Casting.cast(server, player, staff, player.isSecondaryUseActive())) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH,
				SoundSource.PLAYERS, 0.4F, 1.6F);
			return InteractionResult.FAIL;
		}
		if (!player.hasInfiniteMaterials()) Imbuement.setCharge(staff, Imbuement.charge(staff) - 1);
		staff.hurtAndBreak(Casting.wear(staff), player, hand);
		player.getCooldowns().addCooldown(staff, Casting.cooldown(staff));
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void inventoryTick(ItemStack staff, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (level.getGameTime() % WELLSPRING_TICKS == 0
				&& Imbuement.isImbued(staff)
				&& Imbuement.charge(staff) < Imbuement.capacity(staff)
				&& StaffEnchantments.level(staff, StaffEnchantments.WELLSPRING) > 0) {
			Imbuement.setCharge(staff, Imbuement.charge(staff) + 1);
		}
		Imbuement.refresh(staff);
	}
}
