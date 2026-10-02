package justfatlard.pale_staff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** {@code config/pale-staff.json}, written with its defaults on first run. */
public final class PaleStaffConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(PaleStaff.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pale-staff.json");

	/** Per pale oak leaf that comes down inside a pale garden, broken or decayed. One in 2500. */
	public float leafChance = 0.0004F;
	/** Per creaking killed by a player, which for one bound to a heart means breaking the heart. */
	public float creakingChance = 0.05F;
	/** Added to {@link #creakingChance} per level of Looting. */
	public float creakingLootingBonus = 0.02F;
	/** Casts each ingredient crafted in fills. */
	public int chargesPerIngredient = 4;
	/** The share of its potion's duration one cast of a brewing ingredient carries. */
	public float castShareOfPotion = 0.25F;
	/** That a golden dandelion bolt turns a hostile mob gentle. Potency adds 0.15 per level. */
	public float bloomConversionChance = 0.25F;
	/** Casts an unenchanted staff holds. Reservoir adds half of this per level. */
	public int baseCapacity = 16;

	public static PaleStaffConfig load() {
		PaleStaffConfig config = new PaleStaffConfig();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				PaleStaffConfig read = GSON.fromJson(reader, PaleStaffConfig.class);
				if (read != null) config = read;
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Failed to read {}, using defaults", PATH, e);
			}
		}
		config.chargesPerIngredient = Math.max(1, config.chargesPerIngredient);
		config.baseCapacity = Math.max(1, config.baseCapacity);
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to write {}", PATH, e);
		}
	}
}
