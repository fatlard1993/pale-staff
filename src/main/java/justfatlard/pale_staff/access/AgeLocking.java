package justfatlard.pale_staff.access;

/**
 * The golden dandelion's age lock, which the game keeps protected on {@code AgeableMob}; every
 * {@code AgeableMob} implements this, through {@code AgeableMobMixin}.
 */
public interface AgeLocking {
	void paleStaff$lockAge();
}
