package com.grandline.boss;

import com.grandline.GrandLine;
import com.grandline.npc.Bounties;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.crew.CrewManager;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.Quests;
import com.grandline.registry.ModDataComponents;
import com.grandline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Unbreakable;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * What a defeated boss leaves behind: its relic (a named weapon that exists once per world and only from that boss,
 * dropped where it fell), a style scroll and a Haki scroll for everyone who fought it, a large bounty change (or a
 * reward for the law), and story credit for the whole group on top of the existing kill / yield / arrest credit.
 */
public final class BossRewards {
    public static final String RELIC_TAG = "grandline_relic";
    private static final Set<UUID> PAID = Collections.synchronizedSet(new HashSet<>());

    /** @param how "death", "yield" or "arrest" */
    static void defeated(HumanoidNpc boss, BossDef def, @Nullable Entity by, String how, @Nullable BossFight f) {
        if (!(boss.level() instanceof ServerLevel level) || !PAID.add(boss.getUUID())) return;
        NotableState.Entry en = NotableState.get(level.getServer()).peek(def.id);
        if (en == null || !boss.getUUID().equals(en.entity)) return; // only the character's current body counts
        Set<ServerPlayer> who = new LinkedHashSet<>();
        if (by instanceof ServerPlayer sp) who.add(sp);
        else if (by instanceof GLNpc g && CrewManager.isCrew(g)) {
            ServerPlayer cap = CrewManager.captainOf(g);
            if (cap != null) who.add(cap);
        }
        if (f != null) for (LivingEntity e : f.participants.values())
            if (e instanceof ServerPlayer sp && !sp.isSpectator() && sp.distanceToSqr(boss) < 96 * 96) who.add(sp);
        if (who.isEmpty()) return; // fell to something else: nobody to reward
        BossRecords rec = BossRecords.get(level.getServer());
        if (rec.dropRelic(def.id)) {
            ItemEntity ie = new ItemEntity(level, boss.getX(), boss.getY() + 0.5, boss.getZ(), relic(def));
            ie.setUnlimitedLifetime();
            ie.setGlowingTag(true);
            ie.setDefaultPickUpDelay();
            level.addFreshEntity(ie);
        }
        for (ServerPlayer sp : who) {
            if (rec.claim(sp.getUUID(), def.id)) {
                give(sp, styleScroll(def));
                give(sp, hakiScroll(def.hakiKind));
                bounty(sp, def, boss);
            }
            Quests.progress(sp, "defeat", def.id, 1); // the whole group shares the story credit
            sp.sendSystemMessage(Component.translatable("grandline.boss.defeated." + how, boss.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        GrandLine.LOGGER.info("Boss {} {} ({} players)", def.id, how, who.size());
    }

    /** The relic: the base weapon, renamed, unbreakable and stronger, tagged with its boss. */
    public static ItemStack relic(BossDef def) {
        Item item = BuiltInRegistries.ITEM.get(GrandLine.id(def.relicItem));
        if (item == Items.AIR) item = BuiltInRegistries.ITEM.get(GrandLine.id("fine_katana"));
        ItemStack st = new ItemStack(item);
        st.set(DataComponents.CUSTOM_NAME, Component.translatable(def.relicNameKey()).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));
        var n = NotableCharacters.get(def.id);
        Component from = n == null ? Component.literal(def.id) : NotableCharacters.name(n);
        st.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable(def.relicNameKey() + ".desc").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true)),
                Component.translatable("grandline.boss.relic.lore", from).setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(false)))));
        st.set(DataComponents.RARITY, Rarity.EPIC);
        st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        st.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        ItemAttributeModifiers base = st.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : base.modifiers()) {
            AttributeModifier m = e.modifier();
            if (e.attribute().value() == Attributes.ATTACK_DAMAGE.value()) m = new AttributeModifier(m.id(), m.amount() + def.relicBonus, m.operation());
            b.add(e.attribute(), m, e.slot());
        }
        st.set(DataComponents.ATTRIBUTE_MODIFIERS, b.build());
        CustomData.update(DataComponents.CUSTOM_DATA, st, t -> t.putString(RELIC_TAG, def.id));
        return st;
    }

    /** The boss a relic came from, or "" for anything else. */
    public static String relicOf(ItemStack st) {
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        if (d == null) return "";
        CompoundTag t = d.copyTag();
        return t.getString(RELIC_TAG);
    }

    static ItemStack styleScroll(BossDef def) {
        ItemStack s = new ItemStack(ModItems.STYLE_SCROLL.get());
        s.set(ModDataComponents.STYLE.get(), def.scrollStyle.isEmpty() ? "rokushiki" : def.scrollStyle);
        return s;
    }

    public static ItemStack hakiScroll(String kind) {
        ItemStack s = new ItemStack(BossItems.HAKI_SCROLL.get());
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putString(HakiScrollItem.TAG, kind));
        return s;
    }

    private static void give(ServerPlayer sp, ItemStack st) {
        if (!sp.getInventory().add(st)) sp.drop(st, false);
    }

    /**
     * Outlaws (and everyone not sworn to the law) earn a large bounty for bringing down one of the strongest
     * people in the sea; the law instead pays Marines and agents who bring down an outlaw. A Marine who brings down
     * one of the Government's own pays for it in standing ({@link Bounties#raise}).
     */
    static void bounty(ServerPlayer sp, BossDef def, GLNpc boss) {
        CharacterData d = Characters.get(sp);
        boolean outlawBoss = !Faction.isLaw(boss.faction());
        if ((Faction.isLaw(d.faction) || d.faction == Faction.BOUNTY_HUNTER) && outlawBoss) {
            long reward = def.bounty / (d.faction == Faction.BOUNTY_HUNTER ? 500 : 1000);
            d.beli += reward;
            d.addReputation(d.faction.id(), 250);
            sp.sendSystemMessage(Component.translatable("grandline.boss.reward.law", String.format("%,d", reward), boss.getDisplayName()).withStyle(ChatFormatting.GOLD));
        } else {
            Bounties.raise(sp, d, def.bounty, boss.getDisplayName());
        }
        CharacterManager.markDirty(sp);
    }

    /** Test hook: forget which bosses have paid out this session (the world records stay). */
    static void forgetPaid(UUID boss) {
        PAID.remove(boss);
    }

    private BossRewards() {}
}
