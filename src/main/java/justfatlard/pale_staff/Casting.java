package justfatlard.pale_staff;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.Trust;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One cast: a bolt down the caster's line of sight that gives whatever it strikes what the staff
 * carries, or, sneaking, the same given to the caster.
 *
 * <p>The bolt is instant, a line of the imbuement's colour drawn in particles, not a projectile:
 * nothing to sync, nothing to fall out of the world, and every client draws it.
 *
 * <p>The enchantments, each the counterpart of something the game already does:
 * <ul>
 *   <li>Potency and Prolonging are glowstone and redstone, and exclusive as those are. On a fire
 *       charge they scorch and burn longer; on a golden dandelion Potency turns more mobs; on a
 *       wind charge Potency widens the burst; on an echo shard it hits harder.</li>
 *   <li>Splash and Lingering are gunpowder and dragon's breath: a burst where the bolt strikes,
 *       scaled by distance the way a splash potion's is, or something left behind where it lands:
 *       a cloud, a spread of fire, a wider bed of flowers, a trap that gusts, booms or strikes
 *       whatever steps in, or a gate that sends back whatever follows a blink.</li>
 *   <li>Multishot fires three for one charge, Piercing carries on through, Quick Charge shortens
 *       the wait between casts, as they do on a crossbow.</li>
 * </ul>
 */
public final class Casting {
	public static final double RANGE = 24;
	private static final int BASE_COOLDOWN = 20;
	private static final int QUICK_CHARGE_PER_LEVEL = 5;
	private static final float MULTISHOT_SPREAD_DEGREES = 10;
	private static final int LINGERING_TICKS = 200;
	private static final float LINGERING_RADIUS = 3;
	private static final float SPLASH_RADIUS = 3;
	private static final float SPLASH_RADIUS_PER_LEVEL = 1.5F;
	private static final int LAND_RADIUS = 2;
	private static final float BLOOM_DENSITY = 0.5F;

	private Casting() {}

	/** What every target of one cast receives, worked out once from the staff. */
	private record Payload(Spell spell, List<MobEffectInstance> effects, int colour, int potency, float prolonging,
			int splash, boolean lingering) {}

	/** Whether the cast went off; a fire charge or an echo shard will not be cast on oneself. */
	public static boolean cast(ServerLevel level, Player caster, ItemStack staff, boolean atSelf) {
		Payload payload = payload(staff);
		if (atSelf && !payload.spell().castsOnSelf) return false;
		// Fire and lightning are fire: an op who has not trusted the caster with it has the last word.
		if ((payload.spell() == Spell.FLAME || payload.spell() == Spell.STORM) && !may(caster, Trust.FIRE)) {
			caster.sendOverlayMessage(Component.translatableWithFallback("pandorical.trust.refused.fire",
				"An op has not trusted you with fire and lava"));
			return false;
		}

		if (payload.spell() == Spell.BOOM) {
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
				SoundSource.PLAYERS, 3, 1);
		} else {
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.EVOKER_CAST_SPELL,
				SoundSource.PLAYERS, 0.7F, 1.4F + level.getRandom().nextFloat() * 0.2F);
		}

		if (atSelf) {
			Vec3 feet = caster.position();
			if (payload.splash() > 0) burst(level, caster, payload, feet);
			else if (payload.spell() != Spell.GUST) touch(level, caster, caster, payload, Vec3.ZERO);
			land(level, caster, payload, caster.blockPosition().below(), Direction.UP, feet);
			level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, payload.colour()),
				caster.getX(), caster.getY(0.5), caster.getZ(), 24, 0.4, 0.6, 0.4, 0.5);
			return true;
		}

		// One caster, one place to be: a blink goes once, and stops at the first thing it meets.
		boolean single = payload.spell() == Spell.BLINK;
		int piercing = payload.splash() > 0 || single ? 0 : StaffEnchantments.level(staff, Enchantments.PIERCING);
		List<Vec3> directions = new ArrayList<>();
		directions.add(caster.getViewVector(1));
		if (!single && StaffEnchantments.level(staff, Enchantments.MULTISHOT) > 0) {
			directions.add(Vec3.directionFromRotation(caster.getXRot(), caster.getYRot() - MULTISHOT_SPREAD_DEGREES));
			directions.add(Vec3.directionFromRotation(caster.getXRot(), caster.getYRot() + MULTISHOT_SPREAD_DEGREES));
		}
		for (Vec3 direction : directions) bolt(level, caster, payload, direction, piercing);
		return true;
	}

	public static int cooldown(ItemStack staff) {
		int quickCharge = StaffEnchantments.level(staff, Enchantments.QUICK_CHARGE);
		return Math.max(QUICK_CHARGE_PER_LEVEL, BASE_COOLDOWN - QUICK_CHARGE_PER_LEVEL * quickCharge);
	}

	/** Multishot's three bolts wear the staff as a crossbow's three arrows wear the crossbow. */
	public static int wear(ItemStack staff) {
		boolean three = StaffEnchantments.level(staff, Enchantments.MULTISHOT) > 0 && Imbuement.spell(staff) != Spell.BLINK;
		return three ? 3 : 1;
	}

	private static Payload payload(ItemStack staff) {
		int potency = StaffEnchantments.level(staff, StaffEnchantments.POTENCY);
		List<MobEffectInstance> effects = new ArrayList<>();
		Imbuement.contents(staff).forEachEffect(effect -> effects.add(new MobEffectInstance(effect.getEffect(),
			effect.getDuration(), effect.getAmplifier() + potency, effect.isAmbient(), effect.isVisible(), effect.showIcon())),
			Imbuement.durationScale(staff));
		return new Payload(Imbuement.spell(staff), effects, Imbuement.colour(staff), potency, Imbuement.prolonging(staff),
			Imbuement.spell(staff) == Spell.BLINK ? 0 : StaffEnchantments.level(staff, StaffEnchantments.SPLASH),
			StaffEnchantments.level(staff, StaffEnchantments.LINGERING) > 0);
	}

	private static void bolt(ServerLevel level, Player caster, Payload payload, Vec3 direction, int piercing) {
		Vec3 from = caster.getEyePosition();
		Vec3 to = from.add(direction.scale(RANGE));
		// A sonic boom goes through walls, as the warden's does.
		BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		@Nullable BlockHitResult ground = wall.getType() == HitResult.Type.MISS || payload.spell() == Spell.BOOM ? null : wall;
		if (ground != null) to = ground.getLocation();

		List<LivingEntity> struck = struck(level, caster, from, to, piercing + 1);
		Vec3 end = to;
		if (!struck.isEmpty() && (payload.splash() > 0 || struck.size() > piercing)) {
			LivingEntity last = struck.getLast();
			end = last.getBoundingBox().clip(from, to).orElse(last.position());
			ground = null;
		}
		boolean stopped = ground != null || !struck.isEmpty();

		if (payload.spell() == Spell.BOOM) Boom.trail(level, from.add(direction.scale(0.8)), end);
		else trail(level, payload.colour(), from.add(direction.scale(0.8)), end);

		if (payload.spell() == Spell.BLINK) {
			blink(level, caster, payload, struck, end.subtract(direction.scale(0.5)));
			return;
		}
		if (payload.splash() > 0) {
			if (stopped) burst(level, caster, payload, end);
		} else {
			for (LivingEntity target : struck) touch(level, caster, target, payload, direction);
		}
		if (ground != null) {
			land(level, caster, payload, ground.getBlockPos(), ground.getDirection(), end);
		} else if (payload.lingering()) {
			// Struck a creature, or a boom that went through every wall: it lingers where it stopped, or
			// for a boom that struck nothing, at the first wall it went through.
			if (stopped) linger(level, caster, payload, end);
			else if (payload.spell() == Spell.BOOM && wall.getType() != HitResult.Type.MISS) linger(level, caster, payload, wall.getLocation());
		}
	}

	/**
	 * The caster goes where the bolt ended, short of what stopped it as a pearl lands short of a
	 * wall, or trades places with what it struck; with Lingering, a gate where they arrive sends
	 * anything that follows back to where they left.
	 */
	private static void blink(ServerLevel level, Player caster, Payload payload, List<LivingEntity> struck, Vec3 end) {
		Vec3 from = caster.position();
		if (struck.isEmpty()) Blink.to(level, caster, end);
		else Blink.swap(level, caster, struck.getFirst());
		if (payload.lingering()) {
			Trap.lay(level, caster, Spell.BLINK, caster.position(), payload.potency(), payload.prolonging(), from);
		}
	}

	/** The living things along the bolt, nearest first, as many as it can pass through. */
	private static List<LivingEntity> struck(ServerLevel level, Player caster, Vec3 from, Vec3 to, int limit) {
		AABB along = new AABB(from, to).inflate(1);
		List<LivingEntity> hit = new ArrayList<>();
		for (Entity entity : level.getEntities(caster, along, e -> e instanceof LivingEntity && e.isAlive() && e.isPickable())) {
			if (entity.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) hit.add((LivingEntity) entity);
		}
		hit.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
		return hit.size() > limit ? new ArrayList<>(hit.subList(0, limit)) : hit;
	}

	/** What one creature struck gets, at full strength; {@code direction} is the way the bolt was going. */
	private static void touch(ServerLevel level, Player caster, LivingEntity target, Payload payload, Vec3 direction) {
		// Burning, booming, striking, buffeting or changing it is harm; an effect is sorted one by one.
		if (payload.spell() != Spell.EFFECT && payload.spell() != Spell.BLINK && !mayHurt(caster, target)) return;
		switch (payload.spell()) {
			case EFFECT -> give(level, caster, target, payload, 1);
			case FLAME -> Flame.strike(level, caster, target, payload.prolonging(), payload.potency());
			case BLOOM -> Bloom.strike(level, target, payload.potency());
			case GUST -> Gust.strike(level, caster, target, Gust.radius(payload.potency(), 0));
			case BOOM -> Boom.strike(level, caster, target, direction, payload.potency());
			case STORM -> Storm.strike(level, caster, target.position(), payload.potency());
			case BLINK -> Blink.swap(level, caster, target);
		}
	}

	/**
	 * Lingering's share where a cast stops, for the spells whose lingering is left at a point: a
	 * cloud for an effect, a trap for a gust or a boom. Fire and flowers linger on the ground instead.
	 */
	private static void linger(ServerLevel level, Player caster, Payload payload, Vec3 at) {
		switch (payload.spell()) {
			case EFFECT -> cloud(level, caster, payload, at);
			case GUST, BOOM, STORM -> Trap.lay(level, caster, payload.spell(), at, payload.potency(), payload.prolonging(), null);
			case FLAME, BLOOM, BLINK -> {
			}
		}
	}

	/** What Pandorical says about the caster, who is always a player on a server. */
	private static boolean may(Player caster, Trust what) {
		return !(caster instanceof ServerPlayer player) || PandoricalApi.trust().may(player, what);
	}

	static boolean mayHurt(Player caster, LivingEntity target) {
		return target == caster || !(caster instanceof ServerPlayer player) || PandoricalApi.trust().mayHurt(player, target);
	}

	/** Where the bolt meets a block: fire, flowers, a burst, and whatever lingers. */
	private static void land(ServerLevel level, Player caster, Payload payload, BlockPos pos, Direction face, Vec3 at) {
		switch (payload.spell()) {
			case EFFECT -> {
				if (payload.lingering()) linger(level, caster, payload, at);
			}
			case FLAME -> {
				Flame.land(level, caster, pos, face);
				if (payload.lingering()) Flame.spread(level, caster, pos, LAND_RADIUS);
			}
			case BLOOM -> {
				int radius = LAND_RADIUS + (payload.lingering() ? 2 : 0) + payload.splash();
				Bloom.land(level, pos, radius, payload.lingering() ? 1 : BLOOM_DENSITY);
			}
			case GUST -> {
				if (payload.splash() == 0) Gust.burst(level, caster, at, Gust.radius(payload.potency(), 0));
				if (payload.lingering()) linger(level, caster, payload, at);
			}
			case STORM -> {
				Storm.strike(level, caster, at, payload.potency());
				if (payload.lingering()) linger(level, caster, payload, at);
			}
			case BOOM, BLINK -> {
			}
		}
	}

	/** As a splash potion bursts: everything within reach, less the further from the middle. */
	private static void burst(ServerLevel level, Player caster, Payload payload, Vec3 centre) {
		if (payload.spell() == Spell.GUST) {
			// A gust is a burst already: Splash makes the one burst bigger, rather than one per creature.
			Gust.burst(level, caster, centre, Gust.radius(payload.potency(), payload.splash()));
			return;
		}
		float radius = SPLASH_RADIUS + SPLASH_RADIUS_PER_LEVEL * (payload.splash() - 1);
		AABB reach = AABB.ofSize(centre, 0, 0, 0).inflate(radius, radius / 2, radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, reach)) {
			double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(centre));
			if (distance >= radius) continue;
			if (payload.spell() == Spell.EFFECT) give(level, caster, target, payload, 1 - distance / radius);
			else if (target != caster) touch(level, caster, target, payload, target.position().subtract(centre));
		}
		boolean instant = payload.effects().stream().anyMatch(e -> e.getEffect().value().isInstantaneous());
		level.levelEvent(instant ? LevelEvent.PARTICLES_INSTANT_POTION_SPLASH : LevelEvent.PARTICLES_SPELL_POTION_SPLASH,
			BlockPos.containing(centre), payload.colour());
	}

	/** The game's own lingering cloud, carrying the cast as it would carry a lingering potion. */
	private static void cloud(ServerLevel level, Player caster, Payload payload, Vec3 at) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		cloud.setOwner(caster);
		cloud.setRadius(LINGERING_RADIUS);
		cloud.setRadiusOnUse(-0.5F);
		cloud.setDuration(LINGERING_TICKS);
		cloud.setWaitTime(10);
		cloud.setRadiusPerTick(-LINGERING_RADIUS / LINGERING_TICKS);
		cloud.setPotionContents(new PotionContents(Optional.empty(), Optional.of(payload.colour()), payload.effects(), Optional.empty()));
		cloud.setPotionDurationScale(0.5F);
		level.addFreshEntity(cloud);
	}

	private static void give(ServerLevel level, Player caster, LivingEntity target, Payload payload, double scale) {
		if (!target.isAffectedByPotions()) return;
		boolean spare = !mayHurt(caster, target);
		for (MobEffectInstance effect : payload.effects()) {
			Holder<MobEffect> holder = effect.getEffect();
			if (spare && holder.value().getCategory() == MobEffectCategory.HARMFUL) continue;
			if (holder.value().isInstantaneous()) {
				holder.value().applyInstantaneousEffect(level, caster, caster, target, effect.getAmplifier(), scale);
			} else {
				int duration = effect.mapDuration(d -> (int) (d * scale + 0.5));
				if (duration > 20) {
					target.addEffect(new MobEffectInstance(holder, duration, effect.getAmplifier(),
						effect.isAmbient(), effect.isVisible(), effect.showIcon()), caster);
				}
			}
		}
	}

	private static void trail(ServerLevel level, int colour, Vec3 from, Vec3 to) {
		ColorParticleOption mote = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, colour);
		Vec3 step = to.subtract(from);
		int count = Math.max(1, (int) (step.length() * 3));
		for (int i = 0; i <= count; i++) {
			Vec3 at = from.add(step.scale((double) i / count));
			level.sendParticles(mote, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
	}
}
