package justfatlard.pale_staff;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A fire charge, cast: what the bolt strikes burns, and where it lands catches as a fire charge
 * used on that face would, lighting a campfire or a candle rather than setting fire beside it.
 */
public final class Flame {
	private static final float BURN_SECONDS = 5;
	/** Potency's share: a scorch on top of the burning, per level. */
	private static final float SCORCH_PER_POTENCY = 2;

	private Flame() {}

	public static void strike(ServerLevel level, Player caster, LivingEntity target, float prolonging, int potency) {
		target.igniteForSeconds(BURN_SECONDS * prolonging);
		if (potency > 0) target.hurtServer(level, level.damageSources().onFire(), SCORCH_PER_POTENCY * potency);
	}

	/** Light the block struck, or set fire against the face struck. */
	public static void land(ServerLevel level, Player caster, BlockPos pos, Direction face) {
		if (!mayChange(level, caster, pos)) return;
		BlockState state = level.getBlockState(pos);
		if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
			level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
			level.gameEvent(caster, GameEvent.BLOCK_CHANGE, pos);
			crackle(level, pos);
			return;
		}
		ignite(level, caster, pos.relative(face), face);
	}

	/** Lingering's share: fire across the tops of the blocks round where the bolt lands. */
	public static void spread(ServerLevel level, Player caster, BlockPos centre, int radius) {
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -1, -radius), centre.offset(radius, 1, radius))) {
			if (pos.distSqr(centre) > radius * radius) continue;
			if (level.getRandom().nextFloat() < 0.5F) ignite(level, caster, pos.immutable(), Direction.UP);
		}
	}

	private static void ignite(ServerLevel level, Player caster, BlockPos pos, Direction face) {
		if (!mayChange(level, caster, pos) || !BaseFireBlock.canBePlacedAt(level, pos, face)) return;
		level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
		level.gameEvent(caster, GameEvent.BLOCK_PLACE, pos);
		crackle(level, pos);
	}

	/** Where the caster could have set a block by hand, spawn protection and all. */
	private static boolean mayChange(ServerLevel level, Player caster, BlockPos pos) {
		return caster.mayBuild() && level.mayInteract(caster, pos);
	}

	private static void crackle(ServerLevel level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 0.8F + level.getRandom().nextFloat() * 0.4F);
	}
}
