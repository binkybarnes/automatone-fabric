package baritone.selection;

import baritone.AutomatoneClient;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.selection.ISelection;
import baritone.api.selection.ISelectionManager;
import baritone.api.utils.BetterBlockPos;
import java.util.LinkedList;
import java.util.ListIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class SelectionManager implements ISelectionManager {

    private final Entity holder;
    private final LinkedList<ISelection> selections = new LinkedList<>();
    private ISelection[] selectionsArr = new ISelection[0];

    public SelectionManager(Entity holder) {
        this.holder = holder;
    }

    private void resetSelectionsArr() {
        selectionsArr = selections.toArray(new ISelection[0]);
        KEY.sync(this.holder);
    }

    @Override
    public synchronized ISelection addSelection(ISelection selection) {
        selections.add(selection);
        resetSelectionsArr();
        return selection;
    }

    @Override
    public ISelection addSelection(BetterBlockPos pos1, BetterBlockPos pos2) {
        return addSelection(new Selection(pos1, pos2));
    }

    @Override
    public synchronized ISelection removeSelection(ISelection selection) {
        selections.remove(selection);
        resetSelectionsArr();
        return selection;
    }

    @Override
    public synchronized ISelection[] removeAllSelections() {
        ISelection[] selectionsArr = getSelections();
        selections.clear();
        resetSelectionsArr();
        return selectionsArr;
    }

    @Override
    public ISelection[] getSelections() {
        return selectionsArr;
    }

    @Override
    public synchronized ISelection getOnlySelection() {
        if (selections.size() == 1) {
            return selections.peekFirst();
        }

        return null;
    }

    @Override
    public ISelection getLastSelection() {
        return selections.peekLast();
    }

    @Override
    public synchronized ISelection expand(ISelection selection, Direction direction, int blocks) {
        for (ListIterator<ISelection> it = selections.listIterator(); it.hasNext(); ) {
            ISelection current = it.next();

            if (current == selection) {
                it.remove();
                it.add(current.expand(direction, blocks));
                resetSelectionsArr();
                return it.previous();
            }
        }

        return null;
    }

    @Override
    public synchronized ISelection contract(ISelection selection, Direction direction, int blocks) {
        for (ListIterator<ISelection> it = selections.listIterator(); it.hasNext(); ) {
            ISelection current = it.next();

            if (current == selection) {
                it.remove();
                it.add(current.contract(direction, blocks));
                resetSelectionsArr();
                return it.previous();
            }
        }

        return null;
    }

    @Override
    public synchronized ISelection shift(ISelection selection, Direction direction, int blocks) {
        for (ListIterator<ISelection> it = selections.listIterator(); it.hasNext(); ) {
            ISelection current = it.next();

            if (current == selection) {
                it.remove();
                it.add(current.shift(direction, blocks));
                resetSelectionsArr();
                return it.previous();
            }
        }

        return null;
    }

    @Override
    public void readFromNbt(CompoundTag tag) {
        // NO-OP
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        // NO-OP
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.holder || (
                IBaritone.KEY.maybeGet(this.holder)
                        .map(IBaritone::settings)
                        .orElseGet(BaritoneAPI::getGlobalSettings)
                        .syncWithOps.get()
                        && player.server.getProfilePermissions(player.getGameProfile()) >= 2
        );
    }

    @Override
    public void writeSyncPacket(FriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeVarInt(this.selectionsArr.length);

        for (ISelection sel : this.selectionsArr) {
            buf.writeBlockPos(sel.pos1());
            buf.writeBlockPos(sel.pos2());
        }
    }

    @Override
    public void applySyncPacket(FriendlyByteBuf buf) {
        this.removeAllSelections();

        int length = buf.readVarInt();

        for (int i = 0; i < length; i++) {
            BlockPos pos1 = buf.readBlockPos();
            BlockPos pos2 = buf.readBlockPos();
            this.addSelection(new BetterBlockPos(pos1), new BetterBlockPos(pos2));
        }

        if (this.selections.isEmpty()) {
            AutomatoneClient.selectionRenderList.remove(this);
        } else {
            AutomatoneClient.selectionRenderList.add(this);
        }
    }
}
