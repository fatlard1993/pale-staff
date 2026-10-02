package justfatlard.pale_staff;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Lingering's share of a wind charge, an echo shard, a heart of the sea or an ender pearl: a zone
 * where the bolt landed that goes off on whatever steps into it, each time something comes in. A
 * gust under it, a boom into it, lightning on it, or, for a pearl, a gate that sends it back to
 * where the caster blinked from.
 *
 * <p>The zone is the game's own lingering cloud carrying nothing, which every client draws and the
 * world saves; what it is and how strong is written on it as tags, so it is armed again when the
 * world is loaded. What is inside is kept here, not saved: a reload re-reads it without firing, so
 * nothing standing in a trap is caught by the world loading. Nothing already standing in it when it
 * arms is caught either, the creature the bolt struck among them. It spares its caster.
 */
public final class Trap {
	private static final String TAG = PaleStaff.MOD_ID + ".trap";
	private static final String SPELL_TAG = TAG + ".";
	private static final String POTENCY_TAG = PaleStaff.MOD_ID + ".potency.";
	private static final String DEST_TAG = PaleStaff.MOD_ID + ".dest.";
	private static final float RADIUS = 2;
	private static final int SECONDS = 30;

	/** Each armed trap, and what was in it last tick; no entry yet for one that has not looked. */
	private static final Map<AreaEffectCloud, @Nullable Set<UUID>> ARMED = new WeakHashMap<>();

	private Trap() {}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AreaEffectCloud cloud && cloud.entityTags().contains(TAG)) ARMED.put(cloud, null);
		});
		ServerTickEvents.END_LEVEL_TICK.register(Trap::tick);
	}

	/** @param dest for a gate, where it sends what steps in; ignored otherwise */
	public static void lay(ServerLevel level, Player caster, Spell spell, Vec3 at, int potency, float prolonging,
			@Nullable Vec3 dest) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		cloud.setOwner(caster);
		cloud.setRadius(RADIUS);
		cloud.setDuration((int) (SECONDS * 20 * prolonging));
		cloud.setWaitTime(10);
		cloud.setCustomParticle(particle(spell));
		cloud.addTag(TAG);
		cloud.addTag(SPELL_TAG + spell.id());
		cloud.addTag(POTENCY_TAG + potency);
		if (dest != null) cloud.addTag(DEST_TAG + dest.x + "," + dest.y + "," + dest.z);
		level.addFreshEntity(cloud);
	}

	private static ParticleOptions particle(Spell spell) {
		return switch (spell) {
			case BOOM -> ParticleTypes.SCULK_CHARGE_POP;
			case STORM -> ParticleTypes.ELECTRIC_SPARK;
			case BLINK -> ParticleTypes.PORTAL;
			default -> ParticleTypes.CLOUD;
		};
	}

	private static void tick(ServerLevel level) {
		ARMED.keySet().removeIf(Entity::isRemoved);
		for (Map.Entry<AreaEffectCloud, @Nullable Set<UUID>> entry : List.copyOf(ARMED.entrySet())) {
			AreaEffectCloud cloud = entry.getKey();
			if (cloud.level() != level || cloud.isWaiting()) continue;

			LivingEntity owner = cloud.getOwner();
			Set<UUID> inside = new HashSet<>();
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, cloud.getBoundingBox().expandTowards(0, 1, 0))) {
				double dx = entity.getX() - cloud.getX(), dz = entity.getZ() - cloud.getZ();
				if (entity == owner || !entity.isAlive() || dx * dx + dz * dz > RADIUS * RADIUS) continue;
				inside.add(entity.getUUID());
				if (entry.getValue() != null && !entry.getValue().contains(entity.getUUID())) spring(level, cloud, owner, entity);
			}
			ARMED.put(cloud, inside);
		}
	}

	private static void spring(ServerLevel level, AreaEffectCloud cloud, @Nullable LivingEntity owner, LivingEntity entity) {
		int potency = potency(cloud);
		switch (spell(cloud)) {
			case BOOM -> {
				Vec3 away = entity.position().subtract(cloud.position()).multiply(1, 0, 1);
				Boom.strike(level, owner != null ? owner : cloud, entity, away.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : away, potency);
				level.sendParticles(ParticleTypes.SONIC_BOOM, entity.getX(), entity.getY(0.5), entity.getZ(), 1, 0, 0, 0, 0);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2, 1);
			}
			case STORM -> Storm.strike(level, owner, entity.position(), potency);
			case BLINK -> {
				Vec3 dest = dest(cloud);
				if (dest != null) Blink.send(level, entity, dest);
			}
			default -> Gust.burst(level, owner, entity.position(), Gust.radius(potency, 0));
		}
	}

	private static Spell spell(AreaEffectCloud cloud) {
		for (Spell spell : Spell.values()) if (cloud.entityTags().contains(SPELL_TAG + spell.id())) return spell;
		return Spell.GUST;
	}

	private static @Nullable Vec3 dest(AreaEffectCloud cloud) {
		for (String tag : cloud.entityTags()) {
			if (!tag.startsWith(DEST_TAG)) continue;
			String[] xyz = tag.substring(DEST_TAG.length()).split(",");
			try {
				return new Vec3(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
			} catch (RuntimeException e) {
				return null;
			}
		}
		return null;
	}

	private static int potency(AreaEffectCloud cloud) {
		for (String tag : cloud.entityTags()) {
			if (tag.startsWith(POTENCY_TAG)) {
				try {
					return Integer.parseInt(tag.substring(POTENCY_TAG.length()));
				} catch (NumberFormatException e) {
					return 0;
				}
			}
		}
		return 0;
	}
}
