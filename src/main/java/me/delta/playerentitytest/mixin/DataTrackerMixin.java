package me.delta.playerentitytest.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.delta.playerentitytest.mixinInterface.ExtendedDataTracker;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

@Mixin(DataTracker.class)
public abstract class DataTrackerMixin implements ExtendedDataTracker {


    @Shadow @Final
    private Int2ObjectMap<DataTracker.Entry<?>> entries;
    @Shadow @Final
    private
    ReadWriteLock lock;

    @Override
    public List<DataTracker.SerializedEntry<?>> getEntries() {
        List<DataTracker.SerializedEntry<?>> list = null;
        this.lock.readLock().lock();

        for(var entry : this.entries.values()) {
            if (list == null) {
                list = new ArrayList<>();
            }

            list.add(entry.toSerialized());
        }

        this.lock.readLock().unlock();
        return list;
    }
}
