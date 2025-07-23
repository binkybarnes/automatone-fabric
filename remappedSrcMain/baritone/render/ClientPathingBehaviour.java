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

package baritone.render;

import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.calc.IPathFinder;
import baritone.api.pathing.goals.Goal;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;

public class ClientPathingBehaviour {
    public final Entity entity;
    private RenderedPath current;
    private RenderedPath next;

    public ClientPathingBehaviour(Entity entity) {
        this.entity = entity;
    }

    public Goal getGoal() {
        // Reaching across sides is fun
        return Optional.ofNullable(Minecraft.getInstance().getSingleplayerServer())
                .map(s -> s.getLevel(this.entity.level().dimension()))
                .map(w -> w.getEntity(this.entity.getUUID()))
                .map(IBaritone.KEY::getNullable)
                .map(IBaritone::getPathingBehavior)
                .map(IPathingBehavior::getGoal)
                .orElse(null);
    }

    public Optional<? extends IPathFinder> getInProgress() {
        // Reaching across sides is fun
        return Optional.ofNullable(Minecraft.getInstance().getSingleplayerServer())
                .map(s -> s.getLevel(this.entity.level().dimension()))
                .map(w -> w.getEntity(this.entity.getUUID()))
                .map(IBaritone.KEY::getNullable)
                .map(IBaritone::getPathingBehavior)
                .flatMap(IPathingBehavior::getInProgress);
    }

    public RenderedPath getCurrent() {
        return this.current;
    }

    public RenderedPath getNext() {
        return this.next;
    }

    public void readFromPacket(FriendlyByteBuf buf) {
        this.current = RenderedPath.fromPacket(buf);
        this.next = RenderedPath.fromPacket(buf);
    }
}
