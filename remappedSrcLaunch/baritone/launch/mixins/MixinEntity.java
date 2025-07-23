/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.launch.mixins;

import baritone.api.IBaritone;
import baritone.api.utils.IEntityAccessor;
import baritone.behavior.PathingBehavior;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityAccessor {
    @Shadow public abstract Level getWorld();

    @Override
    @Invoker("getEyeHeight")
    public abstract float automatone$invokeGetEyeHeight(Pose pose, EntityDimensions dimensions);
    @Override
    @Accessor("type")
    public abstract void automatone$setType(EntityType<?> type);

    @Inject(method = "setRemoved", at = @At("RETURN"))
    private void shutdownPathingOnUnloading(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!getWorld().isClientSide()) {
            IBaritone.KEY.maybeGet(this).ifPresent(b -> ((PathingBehavior) b.getPathingBehavior()).shutdown());
        }
    }
}
