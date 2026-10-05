package com.squidgame.game.finale;

import com.squidgame.SquidGameMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: everything the fight overlay draws, sent every tick to the two fighters and every other tick to
 * everybody else in the arena. Fighter 0 is always the attacker and fighter 1 the defender. Values are small
 * integers (vitals in tenths) to keep the packet tiny.
 *
 * @param stage     {@link #NONE}, {@link #COIN}, {@link #READY}, {@link #FIGHT}, {@link #OUTRO} or {@link #CEREMONY}
 * @param myRole    the receiver's role: 0 = spectator, 1 = attacker, 2 = defender
 * @param number    contestant numbers of the attacker and the defender
 * @param health    tenths of health 0..1000
 * @param stamina   tenths of stamina 0..1000
 * @param flags     bit 0 guard up, 1 exhausted, 2 invulnerable, 3 charging, 4 staggered
 * @param capture   percent of the circle capture, 0..100
 * @param ticksLeft ticks left of the duel
 * @param duel      1-based duel number and the total number of duels
 * @param edge      tenths of a block between the receiver and the line (-1 = unknown / not a fighter)
 * @param took      incremented counter, kind and damage of the last blow the receiver took
 * @param dealt     incremented counter, kind and damage of the last blow the receiver landed
 * @param coinTick  ticks since the coin toss began (the client animates the coin from it)
 * @param denied    incremented counter: the receiver's last action was refused for lack of stamina
 */
public record FightStatePayload(int stage, int myRole, int[] number, int[] health, int[] stamina, int[] flags,
                                int capture, int ticksLeft, int[] duel, int edge, int[] took, int[] dealt,
                                int coinTick, int denied) implements CustomPacketPayload {
    public static final Type<FightStatePayload> TYPE = new Type<>(SquidGameMod.id("fight_state"));

    public static final int NONE = 0, COIN = 1, READY = 2, FIGHT = 3, OUTRO = 4, CEREMONY = 5;
    public static final int FLAG_GUARD = 1, FLAG_EXHAUSTED = 2, FLAG_INVULNERABLE = 4, FLAG_CHARGING = 8, FLAG_STAGGERED = 16;
    /** Kinds of a blow, as sent in {@code took} / {@code dealt}. */
    public static final int HIT_LIGHT = 1, HIT_HEAVY = 2, HIT_SHOVE = 3, HIT_BLOCKED = 4, HIT_PARRIED = 5, HIT_BREAK = 6, HIT_DODGED = 7;

    public static final StreamCodec<RegistryFriendlyByteBuf, FightStatePayload> CODEC = new StreamCodec<>() {
        @Override
        public FightStatePayload decode(RegistryFriendlyByteBuf buf) {
            int stage = buf.readVarInt();
            int role = buf.readVarInt();
            int[] number = {buf.readVarInt(), buf.readVarInt()};
            int[] health = {buf.readVarInt(), buf.readVarInt()};
            int[] stamina = {buf.readVarInt(), buf.readVarInt()};
            int[] flags = {buf.readVarInt(), buf.readVarInt()};
            int capture = buf.readVarInt();
            int left = buf.readVarInt();
            int[] duel = {buf.readVarInt(), buf.readVarInt()};
            int edge = buf.readVarInt() - 1;
            int[] took = {buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
            int[] dealt = {buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
            int coin = buf.readVarInt();
            int denied = buf.readVarInt();
            return new FightStatePayload(stage, role, number, health, stamina, flags, capture, left, duel, edge, took, dealt, coin, denied);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FightStatePayload p) {
            buf.writeVarInt(p.stage);
            buf.writeVarInt(p.myRole);
            for (int i = 0; i < 2; i++) {
                buf.writeVarInt(p.number[i]);
            }
            for (int i = 0; i < 2; i++) {
                buf.writeVarInt(p.health[i]);
            }
            for (int i = 0; i < 2; i++) {
                buf.writeVarInt(p.stamina[i]);
            }
            for (int i = 0; i < 2; i++) {
                buf.writeVarInt(p.flags[i]);
            }
            buf.writeVarInt(p.capture);
            buf.writeVarInt(p.ticksLeft);
            buf.writeVarInt(p.duel[0]);
            buf.writeVarInt(p.duel[1]);
            buf.writeVarInt(p.edge + 1);
            for (int v : p.took) {
                buf.writeVarInt(v);
            }
            for (int v : p.dealt) {
                buf.writeVarInt(v);
            }
            buf.writeVarInt(p.coinTick);
            buf.writeVarInt(p.denied);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
