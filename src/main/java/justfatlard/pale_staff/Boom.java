package justfatlard.pale_staff;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * An echo shard, cast: the warden's sonic boom. Its damage goes through armour and shields, and
 * the bolt goes through walls to reach it, as the warden's does.
 */
public final class Boom {
	/** The warden's. */
	private static final float DAMAGE = 10;
	private static final float DAMAGE_PER_POTENCY = 2;

	private Boom() {}

	/** As the warden's lands: the hurt, then a shove along the line it came. */
	public static void strike(ServerLevel level, Entity attacker, LivingEntity target, Vec3 direction, int potency) {
		if (!target.hurtServer(level, level.damageSources().sonicBoom(attacker), DAMAGE + DAMAGE_PER_POTENCY * potency)) return;
		double steadiness = 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		Vec3 along = direction.normalize();
		target.push(along.x * 2.5 * steadiness, along.y * 0.5 * steadiness, along.z * 2.5 * steadiness);
	}

	/** The rings the warden's boom leaves along its path, one a block. */
	public static void trail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from);
		int count = Math.max(1, (int) step.length());
		for (int i = 1; i <= count; i++) {
			Vec3 at = from.add(step.scale((double) i / count));
			level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
	}
}
