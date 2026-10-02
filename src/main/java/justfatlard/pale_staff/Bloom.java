package justfatlard.pale_staff;

import justfatlard.pale_staff.access.AgeLocking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * A golden dandelion, cast.
 *
 * <p>Every creature it makes is a baby, and kept one: the golden dandelion is what the game uses to
 * stop a baby growing up, so the bolt does what the flower does. A golden dandelion in hand unlocks
 * one, as it would any other.
 *
 * <ul>
 *   <li>A cow struck, or a mooshroom, becomes a moobloom: a cow of this mod's yellow variant.</li>
 *   <li>A hostile mob struck may become a creature from {@code #pale-staff-justfatlard:bloom_creatures}.
 *       Those in {@code #pale-staff-justfatlard:bloom_immune} never do.</li>
 *   <li>Where the bolt lands, flowers from {@code #pale-staff-justfatlard:bloom_flowers} come up on
 *       the ground round about: whatever the game lets a flower grow on, grass and dirt and moss.</li>
 * </ul>
 */
public final class Bloom {
	public static final ResourceKey<CowVariant> MOOBLOOM = ResourceKey.create(Registries.COW_VARIANT,
		Identifier.fromNamespaceAndPath(PaleStaff.MOD_ID, "moobloom"));
	private static final TagKey<EntityType<?>> CREATURES = TagKey.create(Registries.ENTITY_TYPE,
		Identifier.fromNamespaceAndPath(PaleStaff.MOD_ID, "bloom_creatures"));
	private static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE,
		Identifier.fromNamespaceAndPath(PaleStaff.MOD_ID, "bloom_immune"));
	private static final TagKey<Block> FLOWERS = TagKey.create(Registries.BLOCK,
		Identifier.fromNamespaceAndPath(PaleStaff.MOD_ID, "bloom_flowers"));

	/** Potency's share: each level makes a hostile mob that much likelier to turn. */
	private static final float CHANCE_PER_POTENCY = 0.15F;

	private Bloom() {}

	public static void strike(ServerLevel level, LivingEntity target, int potency) {
		if (target instanceof Cow cow) {
			moobloom(level, cow);
		} else if (target instanceof MushroomCow mooshroom) {
			mooshroom.convertTo(EntityTypes.COW, ConversionParams.single(mooshroom, false, false), cow -> moobloom(level, cow));
		} else if (target instanceof Mob mob && target instanceof Enemy && !mob.is(IMMUNE)) {
			float chance = PaleStaff.config().bloomConversionChance + CHANCE_PER_POTENCY * potency;
			if (level.getRandom().nextFloat() < chance) gentle(level, mob);
		}
	}

	/** Flowers on the ground within {@code radius} of where the bolt landed, each spot by {@code density}. */
	public static void land(ServerLevel level, BlockPos centre, int radius, float density) {
		Optional<HolderSet.Named<Block>> flowers = BuiltInRegistries.BLOCK.get(FLOWERS);
		if (flowers.isEmpty() || flowers.get().size() == 0) return;
		for (BlockPos ground : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 2, radius))) {
			int dx = ground.getX() - centre.getX(), dz = ground.getZ() - centre.getZ();
			if (dx * dx + dz * dz > radius * radius) continue;
			if (!level.getBlockState(ground).is(BlockTags.SUPPORTS_VEGETATION)) continue;
			BlockPos above = ground.above();
			if (!level.getBlockState(above).isAir() || level.getRandom().nextFloat() >= density) continue;
			BlockState flower = flowers.get().getRandomElement(level.getRandom()).orElseThrow().value().defaultBlockState();
			if (!flower.canSurvive(level, above)) continue;
			level.setBlockAndUpdate(above, flower);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, above.getX() + 0.5, above.getY() + 0.3, above.getZ() + 0.5,
				3, 0.25, 0.2, 0.25, 0);
		}
	}

	private static void moobloom(ServerLevel level, Cow cow) {
		level.registryAccess().lookupOrThrow(Registries.COW_VARIANT).get(MOOBLOOM).ifPresent(cow::setVariant);
		keepSmall(level, cow);
	}

	/** Into a random creature from the tag, keeping its name and where it stood. */
	@SuppressWarnings("unchecked")
	private static void gentle(ServerLevel level, Mob mob) {
		Optional<Holder<EntityType<?>>> picked = BuiltInRegistries.ENTITY_TYPE.get(CREATURES)
			.flatMap(set -> set.getRandomElement(level.getRandom()));
		// A tag can name anything; only a mob can be converted into, and the type cannot say what it
		// makes without making one.
		if (picked.isEmpty() || !(picked.get().value().create(level, EntitySpawnReason.CONVERSION) instanceof Mob)) return;
		mob.convertTo((EntityType<Mob>) picked.get().value(), ConversionParams.single(mob, false, false),
			creature -> keepSmall(level, creature));
	}

	private static void keepSmall(ServerLevel level, Mob mob) {
		mob.setBaby(true);
		if (mob instanceof AgeableMob ageable && !ageable.isAgeLocked()) ((AgeLocking) ageable).paleStaff$lockAge();
		mob.setPersistenceRequired();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY(0.6), mob.getZ(), 10, 0.4, 0.4, 0.4, 0);
		level.playSound(null, mob.blockPosition(), SoundEvents.GOLDEN_DANDELION_USE, SoundSource.PLAYERS, 1, 1);
	}
}
