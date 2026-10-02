package justfatlard.pale_staff.mixin;

import justfatlard.pale_staff.access.AgeLocking;
import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AgeableMob.class)
public abstract class AgeableMobMixin implements AgeLocking {
	@Shadow
	protected abstract void setAgeLocked(boolean locked);

	@Override
	public void paleStaff$lockAge() {
		setAgeLocked(true);
	}
}
