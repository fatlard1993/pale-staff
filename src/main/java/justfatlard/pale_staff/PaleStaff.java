package justfatlard.pale_staff;

import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaleStaff implements ModInitializer {
	public static final String MOD_ID = "pale-staff-justfatlard";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Identifier STAFF_ID = Identifier.fromNamespaceAndPath(MOD_ID, "pale_staff");

	/** Between a bow's and a crossbow's: it is a ranged weapon first. */
	private static final int DURABILITY = 450;
	/** A stone sword's blow, at an axe's pace. */
	private static final float ATTACK_DAMAGE = 3, ATTACK_SPEED = -3;

	public static final Item PALE_STAFF = new PaleStaffItem(new Item.Properties()
		.setId(ResourceKey.create(Registries.ITEM, STAFF_ID))
		.durability(DURABILITY)
		.enchantable(15)
		.repairable(Items.RESIN_CLUMP)
		.rarity(Rarity.RARE)
		.component(DataComponents.WEAPON, new Weapon(1))
		.attributes(ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE,
				AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED,
				AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build()));

	private static PaleStaffConfig config;
	private static @Nullable MinecraftServer server;

	public static PaleStaffConfig config() {
		return config;
	}

	/** The running server, for the one place a recipe has to ask it something without a level in hand. */
	public static @Nullable MinecraftServer server() {
		return server;
	}

	@Override
	public void onInitialize() {
		config = PaleStaffConfig.load();

		Registry.register(BuiltInRegistries.ITEM, STAFF_ID, PALE_STAFF);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MOD_ID, "imbue_staff"),
			ImbueStaffRecipe.SERIALIZER);

		PandoricalApi.content().registerItem(STAFF_ID.toString(), new ItemRegistration()
			.model(MOD_ID + ":item/pale_staff")
			.maxStackSize(1));
		PandoricalApi.content().registerModAssets(MOD_ID);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output ->
			output.insertAfter(Items.CROSSBOW, PALE_STAFF));

		StaffLoot.register(config);
		Trap.register();
		StaffSettings.register(config);
		ServerLifecycleEvents.SERVER_STARTING.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);

		LOGGER.info("Loaded pale-staff");
	}
}
