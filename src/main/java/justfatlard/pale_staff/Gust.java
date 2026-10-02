package justfatlard.pale_staff;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * A wind charge, cast: the burst a thrown one makes, where the bolt strikes.
 *
 * <p>The burst is the game's own, set off with a wind charge that is never spawned as its cause,
 * because the game asks the cause what kind of burst it is: one from a wind charge pushes without
 * hurting, flips levers and opens doors without breaking anything, leaves item frames hanging, and
 * spares a player the fall it launched them into. So a sneak-cast at your feet is a wind-charge
 * jump, landing and all.
 */
public final class Gust {
	/** As the wind charge's own: knocks back, breaks nothing, and some blocks stop it. */
	private static final ExplosionDamageCalculator WIND = new SimpleExplosionDamageCalculator(true, false,
		Optional.of(1.22F), BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity()));
	/** A thrown wind charge's burst. */
	public static final float RADIUS = 1.2F;

	private Gust() {}

	/** Potency's share of a gust is a wider burst, and Splash's a wider one still. */
	public static float radius(int potency, int splash) {
		return RADIUS * (1 + 0.5F * potency + splash);
	}

	/** As a wind charge that hits a mob: a buffet, then the burst on it. */
	public static void strike(ServerLevel level, Player caster, LivingEntity target, float radius) {
		WindCharge cause = cause(level, caster, target.position());
		target.hurtServer(level, level.damageSources().windCharge(cause, caster), 1);
		burst(level, caster, target.position(), radius);
	}

	public static void burst(ServerLevel level, @Nullable LivingEntity owner, Vec3 at, float radius) {
		level.explode(cause(level, owner, at), null, WIND, at.x, at.y, at.z, radius, false, Level.ExplosionInteraction.TRIGGER,
			ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE, WeightedList.of(), SoundEvents.WIND_CHARGE_BURST);
	}

	private static WindCharge cause(ServerLevel level, @Nullable LivingEntity owner, Vec3 at) {
		WindCharge cause = new WindCharge(level, at.x, at.y, at.z, Vec3.ZERO);
		cause.setOwner(owner);
		return cause;
	}
}
