package com.grandline.net;

import com.grandline.GrandLine;
import com.grandline.client.ClientNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** All custom packets. Server→client handlers live in {@link ClientNet} and are only invoked on the client. */
public final class GLNetwork {
    public static final String VERSION = "1";

    public static void register(IEventBus modBus) {
        modBus.addListener(GLNetwork::onRegister);
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(GrandLine.id(name));
    }

    // ------------------------------------------------------------------ S2C

    public record SyncCharacter(CompoundTag data) implements CustomPacketPayload {
        public static final Type<SyncCharacter> TYPE = payloadType("sync_character");
        public static final StreamCodec<ByteBuf, SyncCharacter> CODEC = ByteBufCodecs.COMPOUND_TAG.map(SyncCharacter::new, SyncCharacter::data);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Public per-entity visual state (race, appearance, transformation, haki visuals, combat stance). */
    public record EntityVisuals(int entityId, CompoundTag data) implements CustomPacketPayload {
        public static final Type<EntityVisuals> TYPE = payloadType("entity_visuals");
        public static final StreamCodec<ByteBuf, EntityVisuals> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EntityVisuals::entityId, ByteBufCodecs.COMPOUND_TAG, EntityVisuals::data, EntityVisuals::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PlayAnim(int entityId, String anim, float speed) implements CustomPacketPayload {
        public static final Type<PlayAnim> TYPE = payloadType("play_anim");
        public static final StreamCodec<ByteBuf, PlayAnim> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PlayAnim::entityId, ByteBufCodecs.STRING_UTF8, PlayAnim::anim, ByteBufCodecs.FLOAT, PlayAnim::speed, PlayAnim::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenScreen(String screen, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenScreen> TYPE = payloadType("open_screen");
        public static final StreamCodec<ByteBuf, OpenScreen> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenScreen::screen, ByteBufCodecs.COMPOUND_TAG, OpenScreen::data, OpenScreen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Visual effect burst at a position. {@code kind} selects the effect; a/b/c are effect-specific parameters. */
    public record Fx(String kind, double x, double y, double z, float a, float b, float c) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = payloadType("fx");
        public static final StreamCodec<ByteBuf, Fx> CODEC = new StreamCodec<>() {
            @Override public Fx decode(ByteBuf buf) {
                return new Fx(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat());
            }
            @Override public void encode(ByteBuf buf, Fx fx) {
                ByteBufCodecs.STRING_UTF8.encode(buf, fx.kind);
                buf.writeDouble(fx.x); buf.writeDouble(fx.y); buf.writeDouble(fx.z);
                buf.writeFloat(fx.a); buf.writeFloat(fx.b); buf.writeFloat(fx.c);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Sets or adds velocity to the local player (dodges, launches, knockback on the client-authoritative player). */
    public record Impulse(double x, double y, double z, boolean set) implements CustomPacketPayload {
        public static final Type<Impulse> TYPE = payloadType("impulse");
        public static final StreamCodec<ByteBuf, Impulse> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Impulse::x, ByteBufCodecs.DOUBLE, Impulse::y, ByteBufCodecs.DOUBLE, Impulse::z, ByteBufCodecs.BOOL, Impulse::set, Impulse::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ------------------------------------------------------------------ C2S

    public record CreateCharacter(CompoundTag data) implements CustomPacketPayload {
        public static final Type<CreateCharacter> TYPE = payloadType("create_character");
        public static final StreamCodec<ByteBuf, CreateCharacter> CODEC = ByteBufCodecs.COMPOUND_TAG.map(CreateCharacter::new, CreateCharacter::data);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Combat input. action: see {@code com.grandline.combat.CombatInput}. */
    public record CombatInput(byte action, byte arg0, byte arg1, float x, float z) implements CustomPacketPayload {
        public static final Type<CombatInput> TYPE = payloadType("combat_input");
        public static final StreamCodec<ByteBuf, CombatInput> CODEC = StreamCodec.composite(
                ByteBufCodecs.BYTE, CombatInput::action, ByteBufCodecs.BYTE, CombatInput::arg0, ByteBufCodecs.BYTE, CombatInput::arg1,
                ByteBufCodecs.FLOAT, CombatInput::x, ByteBufCodecs.FLOAT, CombatInput::z, CombatInput::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Generic UI action (spend stat point, pick style, dialogue choice, shop purchase, ...). */
    public record UiAction(String action, CompoundTag data) implements CustomPacketPayload {
        public static final Type<UiAction> TYPE = payloadType("ui_action");
        public static final StreamCodec<ByteBuf, UiAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, UiAction::action, ByteBufCodecs.COMPOUND_TAG, UiAction::data, UiAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Helm input for a ship the player is steering. */
    public record ShipControl(int shipId, float throttle, float rudder, byte flags) implements CustomPacketPayload {
        public static final Type<ShipControl> TYPE = payloadType("ship_control");
        public static final StreamCodec<ByteBuf, ShipControl> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ShipControl::shipId, ByteBufCodecs.FLOAT, ShipControl::throttle, ByteBufCodecs.FLOAT, ShipControl::rudder,
                ByteBufCodecs.BYTE, ShipControl::flags, ShipControl::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);
        r.playToClient(SyncCharacter.TYPE, SyncCharacter.CODEC, (p, c) -> ClientNet.syncCharacter(p, c));
        r.playToClient(EntityVisuals.TYPE, EntityVisuals.CODEC, (p, c) -> ClientNet.entityVisuals(p, c));
        r.playToClient(PlayAnim.TYPE, PlayAnim.CODEC, (p, c) -> ClientNet.playAnim(p, c));
        r.playToClient(OpenScreen.TYPE, OpenScreen.CODEC, (p, c) -> ClientNet.openScreen(p, c));
        r.playToClient(Fx.TYPE, Fx.CODEC, (p, c) -> ClientNet.fx(p, c));
        r.playToClient(Impulse.TYPE, Impulse.CODEC, (p, c) -> ClientNet.impulse(p, c));

        r.playToServer(CreateCharacter.TYPE, CreateCharacter.CODEC, ServerNet::createCharacter);
        r.playToServer(CombatInput.TYPE, CombatInput.CODEC, ServerNet::combatInput);
        r.playToServer(UiAction.TYPE, UiAction.CODEC, ServerNet::uiAction);
        r.playToServer(ShipControl.TYPE, ShipControl.CODEC, ServerNet::shipControl);
    }

    // ------------------------------------------------------------------ helpers

    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void toTrackingAndSelf(Entity entity, CustomPacketPayload payload) {
        if (entity.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    public static void fx(Entity near, String kind, double x, double y, double z, float a, float b, float c) {
        if (near.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(near, new Fx(kind, x, y, z, a, b, c));
    }

    public static void anim(Entity entity, String anim) {
        toTrackingAndSelf(entity, new PlayAnim(entity.getId(), anim, 1f));
    }

    public static void anim(Entity entity, String anim, float speed) {
        toTrackingAndSelf(entity, new PlayAnim(entity.getId(), anim, speed));
    }

    private GLNetwork() {}
}
