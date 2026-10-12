package com.grandline.quest;

import com.grandline.GrandLine;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * "collect" objectives: the target is an item id ({@code minecraft:gold_ingot}) or an item tag
 * ({@code #minecraft:small_flowers}). Progress is what the player carries (re-checked on every pickup and every two
 * seconds, so it also counts crafted, looted and dropped items); when the quest completes the items are handed over.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class QuestCollect {
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post e) {
        if (e.getPlayer() instanceof ServerPlayer sp) sync(sp);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 40 == 21) sync(sp);
    }

    /** Recount every active collect objective from the inventory; completes quests whose goods are all there. */
    public static void sync(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        if (d.questProgress.isEmpty()) return;
        for (Map.Entry<String, CompoundTag> e : Map.copyOf(d.questProgress).entrySet()) {
            QuestDef q = QuestRegistry.get(e.getKey());
            if (q == null) continue;
            CompoundTag prog = e.getValue();
            boolean changed = false;
            for (int i = 0; i < q.objectives().size(); i++) {
                QuestDef.Objective o = q.objectives().get(i);
                if (!o.type().equals("collect")) continue;
                int have = Math.min(o.count(), count(sp, o.target()));
                int cur = prog.getInt("o" + i);
                if (have == cur) continue;
                prog.putInt("o" + i, have);
                changed = true;
                if (have > cur)
                    sp.displayClientMessage(Component.translatable("grandline.quest.progress", QuestRegistry.title(q), have, o.count()), true);
            }
            if (!changed) continue;
            CharacterManager.markDirty(sp);
            if (Quests.complete(q, prog)) Quests.finish(sp, d, q);
        }
    }

    /** Called when a quest completes: the collected goods go to the quest giver. */
    static void handOver(ServerPlayer sp, QuestDef q) {
        for (QuestDef.Objective o : q.objectives()) {
            if (!o.type().equals("collect")) continue;
            int left = o.count();
            var inv = sp.getInventory();
            for (int s = 0; s < inv.getContainerSize() && left > 0; s++) {
                ItemStack st = inv.getItem(s);
                if (st.isEmpty() || !matches(st, o.target())) continue;
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
            }
        }
    }

    /** How many items matching the target the player carries. */
    public static int count(ServerPlayer sp, String target) {
        int n = 0;
        var inv = sp.getInventory();
        for (int s = 0; s < inv.getContainerSize(); s++) {
            ItemStack st = inv.getItem(s);
            if (!st.isEmpty() && matches(st, target)) n += st.getCount();
        }
        return n;
    }

    static boolean matches(ItemStack st, String target) {
        if (target.startsWith("#")) {
            TagKey<Item> tag = tag(target);
            return tag != null && st.is(tag);
        }
        Item it = item(target);
        return it != null && st.is(it);
    }

    /** Whether a collect target names a real item or item tag (for validation). */
    public static boolean valid(String target) {
        if (target.startsWith("#")) {
            TagKey<Item> tag = tag(target);
            return tag != null && BuiltInRegistries.ITEM.getTag(tag).map(h -> h.size() > 0).orElse(false);
        }
        return item(target) != null;
    }

    @Nullable
    private static TagKey<Item> tag(String target) {
        ResourceLocation rl = ResourceLocation.tryParse(target.substring(1));
        return rl == null ? null : TagKey.create(Registries.ITEM, rl);
    }

    @Nullable
    private static Item item(String target) {
        ResourceLocation rl = ResourceLocation.tryParse(target);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        return BuiltInRegistries.ITEM.get(rl);
    }

    private QuestCollect() {}
}
