package com.grandline.creature;

import com.grandline.GrandLine;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zoan full-beast forms. A Zoan user (player or NPC) in the "full" form takes the shape of their beast: the hitbox
 * becomes the beast's (scaled by the entity's scale attribute, which the form already raises) and the client draws a
 * GeckoLib beast model instead of the humanoid. The beast id is the fruit's {@code zoanModel}, or the last word of
 * the fruit id (ushi_bison → bison, tori_phoenix → phoenix). Fruits without a known beast keep the humanoid shape.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class BeastForms {
    /** Hitbox at scale 1 (blocks), eye height as a fraction of height, whether it flies, and how far back the third-person camera sits. */
    public record Beast(String id, float width, float height, float eyeFrac, boolean flyer, float camera) {
        public EntityDimensions dimensions() {
            return EntityDimensions.scalable(width, height).withEyeHeight(height * eyeFrac);
        }
    }

    private static final Map<String, Beast> BEASTS = new LinkedHashMap<>();

    static {
        add("wolf", 0.9f, 0.95f, 0.82f, false, 1.0f);
        add("leopard", 0.85f, 0.85f, 0.82f, false, 1.0f);
        add("bison", 1.3f, 1.4f, 0.72f, false, 1.2f);
        add("phoenix", 1.0f, 1.25f, 0.85f, true, 1.4f);
        add("elephant", 1.6f, 2.0f, 0.8f, false, 1.4f);
        add("allosaurus", 1.2f, 1.9f, 0.86f, false, 1.5f);
        add("pteranodon", 1.1f, 1.1f, 0.85f, true, 1.4f);
        add("spinosaurus", 1.4f, 2.2f, 0.8f, false, 1.6f);
        add("seiryu", 1.6f, 1.5f, 0.7f, true, 1.9f);
        add("orochi", 1.7f, 2.2f, 0.9f, false, 1.5f);
        add("kraken", 1.8f, 1.6f, 0.75f, false, 1.5f);
    }

    private static void add(String id, float w, float h, float eye, boolean flyer, float camera) {
        BEASTS.put(id, new Beast(id, w, h, eye, flyer, camera));
    }

    @Nullable
    public static Beast get(String id) {
        return BEASTS.get(id);
    }

    public static Collection<Beast> all() {
        return Collections.unmodifiableCollection(BEASTS.values());
    }

    /** Beast model id of a fruit ("" for non-Zoans). */
    public static String modelFor(@Nullable String fruitId) {
        if (fruitId == null || fruitId.isEmpty()) return "";
        DevilFruit f = DevilFruits.get(fruitId);
        if (f == null || !f.type.isZoan()) return "";
        if (f.zoanModel != null && !f.zoanModel.isEmpty()) return f.zoanModel;
        int i = fruitId.lastIndexOf('_');
        return i >= 0 ? fruitId.substring(i + 1) : fruitId;
    }

    /** The beast an entity is currently shaped as (full Zoan form with a known beast), or null. */
    @Nullable
    public static Beast of(LivingEntity e) {
        String fruit, form;
        if (e instanceof Player p) {
            if (p.level().isClientSide) {
                CompoundTag v = com.grandline.client.ClientState.visuals(p.getId());
                fruit = v.getString("fruit");
                form = v.getString("zoanForm");
            } else {
                CharacterData d = Characters.get(p);
                fruit = d.fruit;
                form = d.zoanForm;
            }
        } else if (e instanceof GLNpc n) {
            fruit = n.fruitId();
            form = n.zoanForm();
        } else return null;
        if (!"full".equals(form)) return null;
        return BEASTS.get(modelFor(fruit));
    }

    /** Full beast forms take the beast's hitbox, scaled like the rest of the body. */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof LivingEntity le) || le.getAttributes() == null) return; // still constructing
        if (e.getPose() == Pose.SLEEPING || e.getPose() == Pose.DYING) return;
        Beast b;
        try {
            b = of(le);
        } catch (RuntimeException ex) {
            return;
        }
        if (b != null) e.setNewSize(b.dimensions().scale(le.getScale()));
    }

    private BeastForms() {}
}
