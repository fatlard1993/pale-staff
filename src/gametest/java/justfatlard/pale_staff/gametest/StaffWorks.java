package justfatlard.pale_staff.gametest;

import justfatlard.pale_staff.Bloom;
import justfatlard.pale_staff.Imbuement;
import justfatlard.pale_staff.PaleStaff;
import justfatlard.pale_staff.Spell;
import justfatlard.pale_staff.StaffEnchantments;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The staff does what the readme says: brewing ingredients imbue it and potions do not, a cast lands
 * on what it is aimed at and spends a charge, Splash catches the neighbours, a fire charge lights
 * the ground and will not be cast on oneself, a golden dandelion makes baby mooblooms, gentles a
 * zombie and flowers the ground, a wind charge blows a cow away and jumps the caster, an echo
 * shard booms a zombie through a wall, and with Lingering each leaves a trap that springs on what
 * walks in and spares its caster; a heart of the sea calls lightning, an ender pearl blinks the
 * caster or trades places, and leaves a gate that sends a follower back. Then a frame of the hotbar and a moobloom, which no assertion can
 * judge, and Wellspring drawing a charge back while carried.
 */
public final class StaffWorks implements FabricClientGameTest {
	private static final TagKey<EntityType<?>> CREATURES = TagKey.create(Registries.ENTITY_TYPE,
		Identifier.fromNamespaceAndPath(PaleStaff.MOD_ID, "bloom_creatures"));

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set noon");
			server.runCommand("gamemode survival @a");

			server.runOnServer(StaffWorks::crafting);

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			server.runOnServer(s -> meadow(s.overworld(), origin));
			server.runCommand("tp @a %d %d %d 0 0".formatted(origin.getX(), origin.getY(), origin.getZ()));
			context.waitTicks(10);
			server.runOnServer(s -> casting(s, connection.getServerPlayer()));
			server.runOnServer(s -> splashing(s, connection.getServerPlayer()));
			server.runOnServer(s -> flame(s, connection.getServerPlayer(), origin));
			server.runOnServer(s -> meadow(s.overworld(), origin));
			server.runOnServer(s -> bloom(s, connection.getServerPlayer(), origin));

			// The picture before anything noisier: no portal mist, no advancement toasts.
			server.runOnServer(s -> showcase(s, connection.getServerPlayer(), origin));
			server.runCommand("tp @a %d %d %d 0 15".formatted(origin.getX(), origin.getY(), origin.getZ() - 1));
			context.waitTicks(30);
			context.takeScreenshot(TestScreenshotOptions.of("bloom").withSize(1920, 1080).disableCounterPrefix());
			server.runOnServer(s -> s.overworld().getEntitiesOfClass(Cow.class, new AABB(origin).inflate(10)).forEach(Entity::discard));
			server.runOnServer(s -> meadow(s.overworld(), origin));
			server.runOnServer(s -> gust(s, connection.getServerPlayer()));
			context.waitTicks(40);
			server.runCommand("tp @a %d %d %d 0 0".formatted(origin.getX(), origin.getY(), origin.getZ()));
			context.waitTicks(5);
			server.runOnServer(s -> boom(s, connection.getServerPlayer(), origin));
			traps(context, server, connection, origin);
			server.runOnServer(s -> storm(s, connection.getServerPlayer()));
			context.waitTicks(10);
			server.runOnServer(s -> meadow(s.overworld(), origin));
			server.runCommand("tp @a %d %d %d 0 0".formatted(origin.getX(), origin.getY(), origin.getZ()));
			context.waitTicks(5);
			blink(context, server, connection, origin);
			server.runCommand("tp @a %d %d %d 0 0".formatted(origin.getX(), origin.getY(), origin.getZ()));
			context.waitTicks(5);


			// Wellspring: a drained staff, carried, has a charge back within one refill period.
			server.runOnServer(s -> {
				ItemStack staff = imbued(s.overworld(), Items.SUGAR);
				Imbuement.setCharge(staff, 0);
				staff.enchant(enchantment(s, StaffEnchantments.WELLSPRING), 1);
				connection.getServerPlayer().getInventory().setItem(8, staff);
			});
			context.waitTicks(620);
			int refilled = server.computeOnServer(s -> Imbuement.charge(connection.getServerPlayer().getInventory().getItem(8)));
			check(refilled == 1, "a drained Wellspring staff draws back one charge in thirty seconds, got " + refilled);
		}
	}

	private static void crafting(MinecraftServer s) {
		ServerLevel level = s.overworld();
		ItemStack staff = new ItemStack(PaleStaff.PALE_STAFF);

		ItemStack swift = craft(level, staff, new ItemStack(Items.SUGAR), new ItemStack(Items.SUGAR));
		check(Imbuement.spell(swift) == Spell.EFFECT && Imbuement.contents(swift).is(Potions.SWIFTNESS),
			"sugar brews swiftness into the staff");
		check(Imbuement.charge(swift) == 8, "two ingredients fill eight casts, got " + Imbuement.charge(swift));
		check(Imbuement.source(swift).equals("minecraft:sugar"), "the socket holds the sugar, got " + Imbuement.source(swift));

		ItemStack topped = craft(level, swift, new ItemStack(Items.SUGAR));
		check(Imbuement.charge(topped) == 12, "the same ingredient tops up, got " + Imbuement.charge(topped));

		ItemStack weak = craft(level, topped, new ItemStack(Items.FERMENTED_SPIDER_EYE));
		check(Imbuement.contents(weak).is(Potions.WEAKNESS) && Imbuement.charge(weak) == 4,
			"fermented spider eye, brewed on water, replaces swiftness with weakness");

		ItemStack flame = craft(level, weak, new ItemStack(Items.FIRE_CHARGE));
		check(Imbuement.spell(flame) == Spell.FLAME && Imbuement.contents(flame).equals(PotionContents.EMPTY),
			"a fire charge makes a flame staff, carrying no potion");
		check(Imbuement.spell(craft(level, flame, new ItemStack(Items.GOLDEN_DANDELION))) == Spell.BLOOM,
			"a golden dandelion makes a bloom staff");
		check(Imbuement.spell(craft(level, flame, new ItemStack(Items.WIND_CHARGE))) == Spell.GUST,
			"a wind charge makes a gust staff");
		check(Imbuement.spell(craft(level, flame, new ItemStack(Items.ECHO_SHARD))) == Spell.BOOM,
			"an echo shard makes a sonic boom staff");
		check(Imbuement.spell(craft(level, flame, new ItemStack(Items.HEART_OF_THE_SEA))) == Spell.STORM,
			"a heart of the sea makes a storm staff");
		check(Imbuement.spell(craft(level, flame, new ItemStack(Items.ENDER_PEARL))) == Spell.BLINK,
			"an ender pearl makes a blink staff");

		check(recipe(level, staff, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS)).isEmpty(), "a potion is refused");
		check(recipe(level, staff, new ItemStack(Items.POPPY)).isEmpty(), "a flower is refused");
		check(recipe(level, staff, new ItemStack(Items.GOLDEN_APPLE)).isEmpty(), "a food is refused");
		ItemStack full = swift.copy();
		Imbuement.setCharge(full, Imbuement.capacity(full));
		check(recipe(level, full, new ItemStack(Items.SUGAR)).isEmpty(), "a full staff refuses more of the same");
		check(recipe(level, swift, new ItemStack(Items.SUGAR), new ItemStack(Items.BLAZE_POWDER)).isEmpty(),
			"mixed ingredients are refused");
	}

	private static void casting(MinecraftServer s, ServerPlayer player) {
		ServerLevel level = s.overworld();
		LivingEntity target = spawnAhead(level, player, EntityTypes.COW, 4, 0);
		ItemStack staff = imbued(level, Items.SPIDER_EYE);
		int before = Imbuement.charge(staff);
		cast(level, player, staff, 0, false);
		check(target.hasEffect(MobEffects.POISON), "the cow the bolt struck is poisoned");
		check(Imbuement.charge(player.getMainHandItem()) == before - 1, "a cast spends one charge");
		check(player.getMainHandItem().getDamageValue() == 1, "a cast wears the staff by one");
		target.discard();
	}

	private static void splashing(MinecraftServer s, ServerPlayer player) {
		ServerLevel level = s.overworld();
		LivingEntity struck = spawnAhead(level, player, EntityTypes.COW, 6, 0);
		LivingEntity beside = spawnAhead(level, player, EntityTypes.COW, 6, 1.5);
		ItemStack staff = imbued(level, Items.FERMENTED_SPIDER_EYE);
		staff.enchant(enchantment(s, StaffEnchantments.SPLASH), 1);
		cast(level, player, staff, 0, false);
		check(struck.hasEffect(MobEffects.WEAKNESS), "the cow struck is caught in the burst");
		check(beside.hasEffect(MobEffects.WEAKNESS), "and so is the one beside it");
		struck.discard();
		beside.discard();
	}

	private static void flame(MinecraftServer s, ServerPlayer player, BlockPos origin) {
		ServerLevel level = s.overworld();
		LivingEntity cow = spawnAhead(level, player, EntityTypes.COW, 4, 0);
		ItemStack staff = imbued(level, Items.FIRE_CHARGE);
		cast(level, player, staff, 0, false);
		check(cow.isOnFire(), "the cow the bolt struck burns");
		cow.discard();

		cast(level, player, staff, 30, false);
		check(BlockPos.betweenClosedStream(origin.offset(-2, 0, 0), origin.offset(2, 1, 4))
			.anyMatch(pos -> level.getBlockState(pos).is(BlockTags.FIRE)), "fire where the bolt met the ground");

		int before = Imbuement.charge(player.getMainHandItem());
		cast(level, player, player.getMainHandItem(), 0, true);
		check(!player.isOnFire() && Imbuement.charge(player.getMainHandItem()) == before,
			"a fire charge is not cast on oneself, and costs nothing for trying");
	}

	private static void bloom(MinecraftServer s, ServerPlayer player, BlockPos origin) {
		ServerLevel level = s.overworld();
		Cow cow = (Cow) spawnAhead(level, player, EntityTypes.COW, 4, 0);
		ItemStack staff = imbued(level, Items.GOLDEN_DANDELION);
		cast(level, player, staff, 0, false);
		check(cow.getVariant().is(Bloom.MOOBLOOM), "the cow struck is a moobloom");
		check(cow.isBaby() && cow.isAgeLocked(), "and a baby, kept one");
		cow.discard();

		float chance = PaleStaff.config().bloomConversionChance;
		PaleStaff.config().bloomConversionChance = 1;
		LivingEntity zombie = spawnAhead(level, player, EntityTypes.ZOMBIE, 4, 0);
		cast(level, player, player.getMainHandItem(), 0, false);
		PaleStaff.config().bloomConversionChance = chance;
		List<Mob> gentled = level.getEntitiesOfClass(Mob.class, new AABB(zombie.blockPosition()).inflate(1),
			mob -> mob.is(CREATURES));
		check(zombie.isRemoved() && gentled.size() == 1, "the zombie became a creature");
		check(gentled.getFirst().isBaby(), "a baby one, got " + gentled.getFirst().getType());
		if (gentled.getFirst() instanceof AgeableMob ageable) check(ageable.isAgeLocked(), "kept a baby");
		gentled.forEach(Entity::discard);

		cast(level, player, player.getMainHandItem(), 30, false);
		long flowers = BlockPos.betweenClosedStream(origin.offset(-5, 0, -3), origin.offset(5, 1, 7))
			.filter(pos -> level.getBlockState(pos).is(BlockTags.SMALL_FLOWERS)).count();
		check(flowers > 0, "flowers where the bolt met the ground");
	}

	private static void gust(MinecraftServer s, ServerPlayer player) {
		ServerLevel level = s.overworld();
		LivingEntity cow = spawnAhead(level, player, EntityTypes.COW, 4, 0);
		ItemStack staff = imbued(level, Items.WIND_CHARGE);
		cast(level, player, staff, 0, false);
		check(cow.getDeltaMovement().length() > 0.3, "the cow struck is blown away, moving " + cow.getDeltaMovement());
		cow.discard();

		player.setDeltaMovement(0, 0, 0);
		cast(level, player, player.getMainHandItem(), 0, true);
		check(player.getDeltaMovement().y > 0.3, "a gust at one's feet is a wind-charge jump, moving " + player.getDeltaMovement());
	}

	private static void boom(MinecraftServer s, ServerPlayer player, BlockPos origin) {
		ServerLevel level = s.overworld();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, 2), origin.offset(1, 2, 2))) {
			level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		}
		LivingEntity zombie = spawnAhead(level, player, EntityTypes.ZOMBIE, 5, 0);
		float before = zombie.getHealth();
		ItemStack staff = imbued(level, Items.ECHO_SHARD);
		cast(level, player, staff, 0, false);
		check(zombie.getHealth() <= before - 9, "the boom reaches the zombie through the wall, health " + zombie.getHealth());
		zombie.discard();

		float health = player.getHealth();
		int charge = Imbuement.charge(player.getMainHandItem());
		cast(level, player, player.getMainHandItem(), 0, true);
		check(player.getHealth() == health && Imbuement.charge(player.getMainHandItem()) == charge,
			"a sonic boom is not cast on oneself, and costs nothing for trying");
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, 2), origin.offset(1, 2, 2))) {
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}
	}

	private static void storm(MinecraftServer s, ServerPlayer player) {
		ServerLevel level = s.overworld();
		LivingEntity pig = spawnAhead(level, player, EntityTypes.PIG, 6, 0);
		ItemStack staff = imbued(level, Items.HEART_OF_THE_SEA);
		// A pig is short: aimed a little down, as a player would, or the bolt goes over its back.
		cast(level, player, staff, 8, false);
		List<LightningBolt> bolts = level.getEntitiesOfClass(LightningBolt.class, new AABB(pig.blockPosition()).inflate(2));
		check(!bolts.isEmpty(), "lightning comes down on the pig the bolt struck");
		int charge = Imbuement.charge(player.getMainHandItem());
		cast(level, player, player.getMainHandItem(), 0, true);
		check(Imbuement.charge(player.getMainHandItem()) == charge, "a storm is not called down on oneself");
		pig.discard();
	}

	/**
	 * An ender pearl: the caster goes where the bolt meets the ground and pays a pearl's damage; with
	 * Lingering a gate is left there that sends a follower back to where the caster set out; a bolt
	 * that strikes a cow trades places with it.
	 */
	private static void blink(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
			BlockPos origin) {
		Vec3 start = server.computeOnServer(s -> connection.getServerPlayer().position());
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer player = connection.getServerPlayer();
			ItemStack staff = imbued(level, Items.ENDER_PEARL);
			staff.enchant(enchantment(s, StaffEnchantments.LINGERING), 1);
			float health = player.getHealth();
			cast(level, player, staff, 30, false);
			check(player.position().distanceTo(start) > 1.5,
				"the caster blinks to where the bolt met the ground, moved " + player.position().distanceTo(start));
			check(player.getHealth() <= health - 4, "and pays a pearl's five points for it, health " + player.getHealth());
			player.setHealth(player.getMaxHealth());
			check(trap(level, origin).position().distanceTo(player.position()) < 1, "a gate is left where the caster arrived");
		});
		context.waitTicks(20);
		LivingEntity follower = server.computeOnServer(s -> {
			AreaEffectCloud gate = trap(s.overworld(), origin);
			Mob cow = EntityTypes.COW.create(s.overworld(), EntitySpawnReason.COMMAND);
			cow.setNoAi(true);
			cow.snapTo(gate.getX(), gate.getY(), gate.getZ(), 0, 0);
			s.overworld().addFreshEntity(cow);
			return cow;
		});
		context.waitTicks(3);
		server.runOnServer(s -> {
			check(follower.position().distanceTo(start) < 1, "a cow that follows through the gate is sent back where the caster set out, at "
				+ follower.position());
			follower.discard();
			s.overworld().getEntitiesOfClass(AreaEffectCloud.class, new AABB(origin).inflate(30)).forEach(Entity::discard);
		});
		server.runCommand("tp @a %d %d %d 0 0".formatted(origin.getX(), origin.getY(), origin.getZ()));
		context.waitTicks(5);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer player = connection.getServerPlayer();
			LivingEntity cow = spawnAhead(level, player, EntityTypes.COW, 4, 0);
			Vec3 cowWas = cow.position(), mine = player.position();
			cast(level, player, imbued(level, Items.ENDER_PEARL), 0, false);
			check(player.position().distanceTo(cowWas) < 0.5 && cow.position().distanceTo(mine) < 0.5,
				"the caster and the cow the bolt struck trade places");
			cow.discard();
			player.setHealth(player.getMaxHealth());
		});
	}

	/**
	 * Lingering on a wind charge or an echo shard: a trap where the bolt lands, armed after half a
	 * second, that goes off on what steps in and spares its caster.
	 */
	private static void traps(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
			BlockPos origin) {
		server.runOnServer(s -> meadow(s.overworld(), origin));
		for (var spell : List.of(Items.WIND_CHARGE, Items.ECHO_SHARD)) {
			server.runOnServer(s -> {
				ItemStack staff = imbued(s.overworld(), spell);
				staff.enchant(enchantment(s, StaffEnchantments.LINGERING), 1);
				cast(s.overworld(), connection.getServerPlayer(), staff, 30, false);
			});
			context.waitTicks(20);
			LivingEntity walker = server.computeOnServer(s -> {
				AreaEffectCloud trap = trap(s.overworld(), origin);
				LivingEntity mob = (spell == Items.WIND_CHARGE ? EntityTypes.COW : EntityTypes.ZOMBIE).create(s.overworld(), EntitySpawnReason.COMMAND);
				((Mob) mob).setNoAi(true);
				mob.snapTo(trap.getX(), trap.getY(), trap.getZ(), 0, 0);
				s.overworld().addFreshEntity(mob);
				return mob;
			});
			context.waitTicks(2);
			server.runOnServer(s -> {
				if (spell == Items.WIND_CHARGE) {
					check(walker.getDeltaMovement().length() > 0.3, "a cow walking into a gust trap is blown away, moving " + walker.getDeltaMovement());
				} else {
					check(walker.getHealth() <= walker.getMaxHealth() - 9, "a zombie walking into a boom trap is boomed, health " + walker.getHealth());
				}
				walker.discard();
			});
		}

		server.runOnServer(s -> {
			AreaEffectCloud trap = trap(s.overworld(), origin);
			ServerPlayer player = connection.getServerPlayer();
			player.teleportTo(trap.getX(), trap.getY(), trap.getZ());
		});
		float health = server.computeOnServer(s -> connection.getServerPlayer().getHealth());
		context.waitTicks(5);
		check(server.computeOnServer(s -> connection.getServerPlayer().getHealth()) == health, "a trap spares its caster");
		server.runOnServer(s -> s.overworld().getEntitiesOfClass(AreaEffectCloud.class, new AABB(origin).inflate(30)).forEach(Entity::discard));
		server.runCommand("tp @a %d %d %d 0 0".formatted(origin.getX(), origin.getY(), origin.getZ()));
		context.waitTicks(5);
	}

	/** The newest trap near the origin. */
	private static AreaEffectCloud trap(ServerLevel level, BlockPos origin) {
		List<AreaEffectCloud> traps = level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(origin).inflate(30),
			cloud -> cloud.entityTags().contains(PaleStaff.MOD_ID + ".trap"));
		check(!traps.isEmpty(), "a lingering bolt left a trap");
		return traps.stream().min(java.util.Comparator.comparingInt(cloud -> cloud.tickCount)).orElseThrow();
	}

	/** For the picture: the staffs in the hotbar, a moobloom and its calf on the meadow ahead. */
	private static void showcase(MinecraftServer s, ServerPlayer player, BlockPos origin) {
		ServerLevel level = s.overworld();
		player.getInventory().clearContent();
		ItemStack empty = new ItemStack(PaleStaff.PALE_STAFF);
		Imbuement.refresh(empty);
		ItemStack sugar = imbued(level, Items.SUGAR);
		Imbuement.setCharge(sugar, 6);
		ItemStack fire = imbued(level, Items.FIRE_CHARGE);
		ItemStack bloom = imbued(level, Items.GOLDEN_DANDELION);
		Imbuement.setCharge(bloom, 2);
		ItemStack blaze = imbued(level, Items.BLAZE_POWDER);
		blaze.setDamageValue(200);
		List<ItemStack> row = List.of(bloom, empty, sugar, fire, blaze);
		for (int i = 0; i < row.size(); i++) player.getInventory().setItem(i, row.get(i));
		player.getInventory().setSelectedSlot(0);

		var registry = level.registryAccess().lookupOrThrow(Registries.COW_VARIANT);
		for (boolean baby : List.of(false, true)) {
			Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			cow.setNoAi(true);
			registry.get(Bloom.MOOBLOOM).ifPresent(cow::setVariant);
			cow.setBaby(baby);
			cow.snapTo(origin.getX() + (baby ? 1.2 : -0.8), origin.getY(), origin.getZ() + 4.5, baby ? 200 : 160, 0);
			level.addFreshEntity(cow);
		}
	}

	/** A patch of grass ahead with nothing on it, for bolts to land on and flowers to come up in. */
	private static void meadow(ServerLevel level, BlockPos origin) {
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -1, -3), origin.offset(6, -1, 8))) {
			level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
			level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
		}
	}

	/** Aim, and use: straight ahead at {@code pitch} degrees down, or on oneself. */
	private static void cast(ServerLevel level, ServerPlayer player, ItemStack staff, float pitch, boolean atSelf) {
		player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(staff));
		player.setYRot(0);
		player.setXRot(pitch);
		player.setShiftKeyDown(atSelf);
		player.setItemInHand(InteractionHand.MAIN_HAND, staff);
		PaleStaff.PALE_STAFF.use(level, player, InteractionHand.MAIN_HAND);
		player.setShiftKeyDown(false);
	}

	private static ItemStack imbued(ServerLevel level, net.minecraft.world.item.Item ingredient) {
		return craft(level, new ItemStack(PaleStaff.PALE_STAFF), new ItemStack(ingredient), new ItemStack(ingredient),
			new ItemStack(ingredient), new ItemStack(ingredient));
	}

	private static LivingEntity spawnAhead(ServerLevel level, ServerPlayer player, EntityType<? extends Mob> type,
			double ahead, double aside) {
		Mob mob = type.create(level, EntitySpawnReason.COMMAND);
		mob.setNoAi(true);
		mob.snapTo(player.getX() + aside, player.getY(), player.getZ() + ahead, 0, 0);
		level.addFreshEntity(mob);
		return mob;
	}

	private static ItemStack craft(ServerLevel level, ItemStack staff, ItemStack... ingredients) {
		ItemStack result = recipe(level, staff, ingredients);
		check(!result.isEmpty(), "the staff crafts with " + List.of(ingredients));
		return result;
	}

	private static ItemStack recipe(ServerLevel level, ItemStack staff, ItemStack... ingredients) {
		List<ItemStack> grid = new ArrayList<>(List.of(staff));
		grid.addAll(List.of(ingredients));
		while (grid.size() < 9) grid.add(ItemStack.EMPTY);
		CraftingInput input = CraftingInput.of(3, 3, grid);
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(holder -> holder.value().assemble(input))
			.orElse(ItemStack.EMPTY);
	}

	private static Holder<Enchantment> enchantment(MinecraftServer s, ResourceKey<Enchantment> key) {
		return s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static void check(boolean condition, String what) {
		if (!condition) throw new AssertionError(what);
	}
}
