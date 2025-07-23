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

package baritone.command.defaults;

import baritone.Automatone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.utils.accessor.ServerCommandSourceAccessor;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ClickCommand extends Command {
    public static final ResourceLocation OPEN_CLICK_SCREEN = Automatone.id("open_click_screen");

    public ClickCommand() {
        super("click");
    }

    @Override
    public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        args.requireMax(0);
        try {
            CommandSource commandOutput = ((ServerCommandSourceAccessor) source).automatone$getOutput();
            if (commandOutput instanceof ServerPlayer) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeUUID(baritone.getPlayerContext().entity().getUuid());
                ((ServerPlayer) commandOutput).connection.send(new ClientboundCustomPayloadPacket(OPEN_CLICK_SCREEN, buf));
            }
        } catch (Throwable t) {
            Automatone.LOGGER.error("Failed to open click screen, is this a dedicated server?", t);
        }
        logDirect(source, "aight dude");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Open click";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Opens click dude",
                "",
                "Usage:",
                "> click"
        );
    }
}
