package com.grandline.net;

import com.grandline.combat.CombatManager;
import com.grandline.combat.Loadouts;
import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.dialogue.Dialogues;
import com.grandline.dialogue.Shops;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-side packet handlers. Every request is validated here; clients never decide outcomes. */
public final class ServerNet {
    static void createCharacter(GLNetwork.CreateCharacter p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            Component err = CharacterManager.create(sp, p.data());
            if (err != null) {
                sp.sendSystemMessage(err.copy().withStyle(ChatFormatting.RED));
                if (!Characters.get(sp).created) CharacterManager.openCreationScreen(sp);
            } else {
                GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("close", new CompoundTag()));
            }
        });
    }

    static void combatInput(GLNetwork.CombatInput p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) CombatManager.handle(sp, p);
        });
    }

    static void uiAction(GLNetwork.UiAction p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            CharacterData d = Characters.get(sp);
            CompoundTag t = p.data();
            switch (p.action()) {
                case "spend_stat" -> {
                    String s = t.getString("stat");
                    for (Stat st : Stat.values()) {
                        if (st.id().equals(s) && d.statPoints > 0 && d.stat(st) < 100) {
                            d.statPoints--;
                            d.stats.merge(st, 1, Integer::sum);
                            CharacterManager.applyAttributes(sp);
                        }
                    }
                }
                case "set_style" -> {
                    String s = t.getString("style");
                    FightingStyle st = FightingStyles.get(s);
                    if (st != null && d.knownStyles.contains(s)) {
                        d.activeStyle = s;
                        Loadouts.refresh(sp, d);
                    }
                }
                case "set_slot" -> {
                    int set = t.getInt("set"), slot = t.getInt("slot");
                    String id = t.getString("ability");
                    if (set >= 0 && set < 3 && slot >= 0 && slot < 9) d.loadout[set][slot] = id.isEmpty() ? null : id;
                }
                case "refresh_loadout" -> Loadouts.refresh(sp, d);
                case "dialogue" -> Dialogues.choose(sp, t.getInt("npc"), t.getString("option"));
                case "buy" -> Shops.buy(sp, t.getInt("npc"), t.getInt("index"));
                case "pvp_flag" -> {
                    d.pvpFlag = t.getBoolean("on");
                    sp.sendSystemMessage(Component.translatable(d.pvpFlag ? "grandline.pvp.on" : "grandline.pvp.off"));
                }
                case "open_chart" -> com.grandline.navigation.Navigation.openChart(sp);
                case "ship" -> com.grandline.ship.ShipNet.handle(sp, t);
                default -> {}
            }
            CharacterManager.markDirty(sp);
        });
    }

    static void shipControl(GLNetwork.ShipControl p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) com.grandline.ship.ShipNet.control(sp, p);
        });
    }

    private ServerNet() {}
}
