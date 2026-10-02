package justfatlard.pale_staff;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A heart of the sea, cast: the game's own lightning where the bolt strikes, at any hour and in any
 * weather, so everything lightning does it does: a pig to a zombified piglin, a villager to a
 * witch, a creeper charged, copper scraped clean, fire where it lands.
 */
public final class Storm {
	/** Potency's share: this far round the strike, one more bolt per level. */
	private static final double SCATTER = 2.5;

	private Storm() {}

	public static void strike(ServerLevel level, @Nullable Entity cause, Vec3 at, int potency) {
		bolt(level, cause, at);
		for (int i = 0; i < potency; i++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2;
			bolt(level, cause, at.add(Math.cos(angle) * SCATTER, 0, Math.sin(angle) * SCATTER));
		}
	}

	private static void bolt(ServerLevel level, @Nullable Entity cause, Vec3 at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) return;
		bolt.snapTo(at.x, at.y, at.z);
		bolt.setCause(cause instanceof ServerPlayer player ? player : null);
		level.addFreshEntity(bolt);
	}
}
