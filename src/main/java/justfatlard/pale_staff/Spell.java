package justfatlard.pale_staff;

import java.util.Locale;

/** What kind of thing a staff carries, which decides what a cast does. */
public enum Spell {
	/** A potion's effect, given to what the bolt strikes. */
	EFFECT(0),
	/** A fire charge: sets what it strikes alight, and the ground where it lands. */
	FLAME(0xFFE8641E, false),
	/**
	 * A golden dandelion: cows struck become mooblooms and the hostile may become something gentle,
	 * all of them babies kept small as the dandelion keeps them; the ground where it lands flowers.
	 */
	BLOOM(0xFFF5C518),
	/** A wind charge: its burst where the bolt strikes; at your feet, a wind-charge jump. */
	GUST(0xFFCDE6F0),
	/** An echo shard: the warden's sonic boom, through armour and through walls. */
	BOOM(0xFF29DFEB, false),
	/** A heart of the sea: lightning where the bolt strikes. */
	STORM(0xFF2E6FD6, false),
	/** An ender pearl: the caster goes where the bolt ends, or trades places with what it strikes. */
	BLINK(0xFF1E6E5E, false);

	/** The colour of the bar and the gem; an effect's comes from its potion instead. */
	public final int colour;
	/** Whether a sneak-cast may land on the caster: not one that would burn or boom them. */
	public final boolean castsOnSelf;

	Spell(int colour) {
		this(colour, true);
	}

	Spell(int colour, boolean castsOnSelf) {
		this.colour = colour;
		this.castsOnSelf = castsOnSelf;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Spell byId(String id) {
		for (Spell spell : values()) if (spell.id().equals(id)) return spell;
		return EFFECT;
	}
}
