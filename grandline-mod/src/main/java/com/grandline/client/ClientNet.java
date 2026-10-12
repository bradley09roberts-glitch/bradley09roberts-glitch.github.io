package com.grandline.client;

import com.grandline.client.anim.Animator;
import com.grandline.client.screen.*;
import com.grandline.net.GLNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-side packet handlers (only ever invoked on the logical client). */
public final class ClientNet {
    public static void syncCharacter(GLNetwork.SyncCharacter p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientState.LOCAL.deserializeNBT(null, p.data());
            ClientState.hasData = true;
        });
    }

    public static void entityVisuals(GLNetwork.EntityVisuals p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var old = ClientState.VISUALS.get(p.entityId());
            var merged = p.data().copy();
            if (old != null) {
                for (String k : new String[]{"obs", "arm", "coq", "obsRange"}) if (!merged.contains(k) && old.contains(k)) merged.put(k, old.get(k));
            }
            ClientState.VISUALS.put(p.entityId(), merged);
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Entity e = mc.level.getEntity(p.entityId());
                if (e != null) e.refreshDimensions();
                if (e instanceof net.minecraft.world.entity.player.Player pl) pl.refreshDisplayName(); // careers: the title on the name tag
                if (e == mc.player && mc.player != null) mc.player.setForcedPose(merged.getBoolean("knocked") ? net.minecraft.world.entity.Pose.SWIMMING : null);
            }
        });
    }

    public static void playAnim(GLNetwork.PlayAnim p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Animator.play(p.entityId(), p.anim(), p.speed()));
    }

    public static void openScreen(GLNetwork.OpenScreen p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            switch (p.screen()) {
                case "create_character" -> {
                    if (!(mc.screen instanceof CharacterCreationScreen)) mc.setScreen(new CharacterCreationScreen(p.data()));
                }
                case "dialogue" -> mc.setScreen(new DialogueScreen(p.data()));
                case "shop" -> mc.setScreen(new ShopScreen(p.data()));
                case "chart" -> mc.setScreen(new ChartScreen(p.data()));
                case "treasure_map" -> mc.setScreen(new com.grandline.client.screen.TreasureMapScreen(p.data()));
                case "shipyard" -> mc.setScreen(new ShipyardScreen(p.data()));
                case "close" -> {
                    if (mc.screen != null) mc.setScreen(null);
                }
                default -> {}
            }
        });
    }

    public static void fx(GLNetwork.Fx p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientFx.play(p.kind(), p.x(), p.y(), p.z(), p.a(), p.b(), p.c()));
    }

    public static void impulse(GLNetwork.Impulse p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var pl = Minecraft.getInstance().player;
            if (pl == null) return;
            Vec3 v = new Vec3(p.x(), p.y(), p.z());
            pl.setDeltaMovement(p.set() ? v : pl.getDeltaMovement().add(v));
            pl.fallDistance = 0;
        });
    }

    private ClientNet() {}
}
