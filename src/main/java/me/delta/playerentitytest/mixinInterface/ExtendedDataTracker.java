package me.delta.playerentitytest.mixinInterface;

import net.minecraft.entity.data.DataTracker;

import java.util.List;

public interface ExtendedDataTracker {
    static ExtendedDataTracker get(DataTracker dataTracker)
    {
        return (ExtendedDataTracker) dataTracker;
    }

    List<DataTracker.SerializedEntry<?>> getEntries();
}
