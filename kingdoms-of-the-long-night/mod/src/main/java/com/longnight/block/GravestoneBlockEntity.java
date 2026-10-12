package com.longnight.block;

import com.longnight.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class GravestoneBlockEntity extends BlockEntity {
    public UUID person;
    public String name = "";
    public String dates = "";
    public String cause = "";
    public String faith = "";
    public String epitaph = "";
    public String rite = "";

    public GravestoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVESTONE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider registries) {
        super.saveAdditional(t, registries);
        if (person != null) t.putUUID("person", person);
        t.putString("name", name);
        t.putString("dates", dates);
        t.putString("cause", cause);
        t.putString("faith", faith);
        t.putString("epitaph", epitaph);
        t.putString("rite", rite);
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider registries) {
        super.loadAdditional(t, registries);
        person = t.hasUUID("person") ? t.getUUID("person") : null;
        name = t.getString("name");
        dates = t.getString("dates");
        cause = t.getString("cause");
        faith = t.getString("faith");
        epitaph = t.getString("epitaph");
        rite = t.getString("rite");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
