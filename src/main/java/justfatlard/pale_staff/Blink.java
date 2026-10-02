package justfatlard.pale_staff;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * An ender pearl, cast: the caster goes where the bolt ends, or trades places with what it strikes.
 *
 * <p>It costs what a thrown pearl costs, the same five points of damage and the same one-in-twenty
 * endermite left where the caster stood, so the staff is a pearl that can be aimed, not a free one.
 */
public final class Blink {
	private static final float PEARL_DAMAGE = 5;
	private static final float ENDERMITE_CHANCE = 0.05F;

	private Blink() {}

	public static void to(ServerLevel level, Player caster, Vec3 at) {
		Vec3 from = caster.position();
		maybeEndermite(level, caster);
		move(level, caster, at);
		caster.hurtServer(level, level.damageSources().enderPearl(), PEARL_DAMAGE);
		puff(level, from);
		puff(level, at);
	}

	/** Trade places with what the bolt struck. */
	public static void swap(ServerLevel level, Player caster, LivingEntity target) {
		Vec3 mine = caster.position(), theirs = target.position();
		maybeEndermite(level, caster);
		move(level, target, mine);
		move(level, caster, theirs);
		caster.hurtServer(level, level.damageSources().enderPearl(), PEARL_DAMAGE);
		puff(level, mine);
		puff(level, theirs);
	}

	/** Send something elsewhere, the way a gate does: no cost, it was not the one who cast. */
	public static void send(ServerLevel level, Entity entity, Vec3 to) {
		Vec3 from = entity.position();
		move(level, entity, to);
		puff(level, from);
		puff(level, to);
	}

	private static void move(ServerLevel level, Entity entity, Vec3 to) {
		Entity moved = entity.teleport(new TeleportTransition(level, to, Vec3.ZERO, 0, 0, Relative.ROTATION, TeleportTransition.DO_NOTHING));
		if (moved != null) {
			moved.resetFallDistance();
			if (moved instanceof LivingEntity living) living.resetCurrentImpulseContext();
		}
	}

	private static void maybeEndermite(ServerLevel level, Player caster) {
		if (level.getRandom().nextFloat() >= ENDERMITE_CHANCE || !level.isSpawningMonsters()
				|| level.getLevelData().getDifficulty() == Difficulty.PEACEFUL) return;
		Endermite endermite = EntityTypes.ENDERMITE.create(level, EntitySpawnReason.TRIGGERED);
		if (endermite == null) return;
		endermite.snapTo(caster.getX(), caster.getY(), caster.getZ(), caster.getYRot(), caster.getXRot());
		level.addFreshEntity(endermite);
	}

	private static void puff(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 32, 0.3, 0.8, 0.3, 0.5);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1, 1);
	}
}
