package com.squidgame.net;

import com.squidgame.SquidGameMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the client HUD needs, sent a few times per second to every participant and spectator. Games add
 * their own indicators as {@link Widget}s so the client renders them generically (no per-game HUD packets).
 */
public record HudPayload(int phase, String gameId, Component title, Component objective, int timerTicks, int timerTotal,
                         int alive, int total, int myNumber, int myStatus, List<Widget> widgets)
        implements CustomPacketPayload {

    public static final Type<HudPayload> TYPE = new Type<>(SquidGameMod.id("hud"));

    /** Player's own status values for {@link #myStatus}. */
    public static final int STATUS_NONE = 0, STATUS_ALIVE = 1, STATUS_ELIMINATED = 2, STATUS_SPECTATOR = 3, STATUS_WINNER = 4;

    /** Widget kinds. */
    public static final int BAR = 0, COUNTER = 1, BANNER = 2, LIGHT = 3, LINE = 4;

    /**
     * One HUD element. BAR: value/max filled bar tinted {@code color}; COUNTER: icon + "value/max" (or just value if
     * max &lt; 0); BANNER: big centred warning text; LIGHT: value 0 = green, 1 = amber, 2 = red; LINE: a plain text
     * line under the objective. {@code icon} is a texture name under textures/gui/hud/ without extension (may be empty).
     */
    public record Widget(int kind, String id, Component label, float value, float max, int color, String icon) {
        public static Widget bar(String id, Component label, float value, float max, int color) {
            return new Widget(BAR, id, label, value, max, color, "");
        }

        public static Widget counter(String id, String icon, Component label, float value, float max) {
            return new Widget(COUNTER, id, label, value, max, 0xFFFFFFFF, icon);
        }

        public static Widget banner(String id, Component text, int color) {
            return new Widget(BANNER, id, text, 0, 0, color, "");
        }

        public static Widget light(String id, Component label, int state) {
            return new Widget(LIGHT, id, label, state, 2, 0xFFFFFFFF, "");
        }

        public static Widget line(String id, Component text) {
            return new Widget(LINE, id, text, 0, 0, 0xFFFFFFFF, "");
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Widget> CODEC = new StreamCodec<>() {
            @Override
            public Widget decode(RegistryFriendlyByteBuf buf) {
                int kind = buf.readVarInt();
                String id = buf.readUtf(64);
                Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
                float value = buf.readFloat();
                float max = buf.readFloat();
                int color = buf.readInt();
                String icon = buf.readUtf(64);
                return new Widget(kind, id, label, value, max, color, icon);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Widget w) {
                buf.writeVarInt(w.kind);
                buf.writeUtf(w.id, 64);
                ComponentSerialization.STREAM_CODEC.encode(buf, w.label);
                buf.writeFloat(w.value);
                buf.writeFloat(w.max);
                buf.writeInt(w.color);
                buf.writeUtf(w.icon, 64);
            }
        };
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> CODEC = new StreamCodec<>() {
        @Override
        public HudPayload decode(RegistryFriendlyByteBuf buf) {
            int phase = buf.readVarInt();
            String game = buf.readUtf(64);
            Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
            Component objective = ComponentSerialization.STREAM_CODEC.decode(buf);
            int timer = buf.readVarInt();
            int timerTotal = buf.readVarInt();
            int alive = buf.readVarInt();
            int total = buf.readVarInt();
            int number = buf.readVarInt();
            int status = buf.readVarInt();
            int n = buf.readVarInt();
            List<Widget> widgets = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                widgets.add(Widget.CODEC.decode(buf));
            }
            return new HudPayload(phase, game, title, objective, timer, timerTotal, alive, total, number, status, widgets);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, HudPayload p) {
            buf.writeVarInt(p.phase);
            buf.writeUtf(p.gameId, 64);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.title);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.objective);
            buf.writeVarInt(p.timerTicks);
            buf.writeVarInt(p.timerTotal);
            buf.writeVarInt(p.alive);
            buf.writeVarInt(p.total);
            buf.writeVarInt(p.myNumber);
            buf.writeVarInt(p.myStatus);
            buf.writeVarInt(p.widgets.size());
            for (Widget w : p.widgets) {
                Widget.CODEC.encode(buf, w);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
