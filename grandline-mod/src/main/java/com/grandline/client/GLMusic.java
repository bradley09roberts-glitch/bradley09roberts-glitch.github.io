package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.npc.GLNpc;
import com.grandline.registry.ModSounds;
import com.grandline.world.DeepWorld;
import com.grandline.world.SkyWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Situational music: the mod's own tracks by place and situation. A boss bar that asks for boss music plays the boss
 * theme, a fight (you hit or were hit by a foe in the last 12 s) the battle theme; otherwise the sea you sail (from
 * the biome), the island you stand on (sent by the server: Navigation), the sky islands or the deep sea. The title
 * screen plays the Grand Line theme. Other dimensions keep vanilla music.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class GLMusic {
    /** Where the server says we are: 0 at sea, 1 on an island, 2 in Wano. */
    public static int place = 0;
    private static long lastFight = -100000;
    private static Music menu, boss, battle, eastBlue, grandLine, newWorld, island, sky, deep, wano;

    private static Music region(Holder<SoundEvent> s) {
        return ModSounds.music(s, 1200, 3600, false);
    }

    private static void init() {
        if (menu != null) return;
        menu = ModSounds.music(ModSounds.MUSIC_GRAND_LINE, 20, 600, true);
        boss = ModSounds.music(ModSounds.MUSIC_BOSS, 0, 0, true);
        battle = ModSounds.music(ModSounds.MUSIC_BATTLE, 0, 0, true);
        eastBlue = region(ModSounds.MUSIC_EAST_BLUE);
        grandLine = region(ModSounds.MUSIC_GRAND_LINE);
        newWorld = region(ModSounds.MUSIC_NEW_WORLD);
        island = region(ModSounds.MUSIC_ISLAND);
        sky = region(ModSounds.MUSIC_SKY);
        deep = region(ModSounds.MUSIC_DEEP_SEA);
        wano = region(ModSounds.MUSIC_WANO);
    }

    /** A fight is on (also called for cannon fire nearby). */
    public static void fight() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) lastFight = mc.level.getGameTime();
    }

    static boolean foe(Entity e) {
        return e instanceof Enemy || e instanceof GLNpc || e instanceof Player || e.getType().getCategory() == MobCategory.MONSTER;
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (e.getEntity().level().isClientSide && e.getEntity() == Minecraft.getInstance().player && foe(e.getTarget())) fight();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.hurtTime <= 0 || p.getLastDamageSource() == null) return;
        Entity by = p.getLastDamageSource().getEntity();
        if (by instanceof LivingEntity && by != p && foe(by)) fight();
    }

    /** Cannon fire, a boss's roar or a Sea King's call close by: the battle theme starts. */
    @SubscribeEvent
    public static void onSound(PlaySoundEvent e) {
        SoundInstance s = e.getOriginalSound();
        if (s == null || !s.getLocation().getNamespace().equals(GrandLine.MODID)) return;
        String path = s.getLocation().getPath();
        if (!path.equals("ship.cannon") && !path.equals("boss.roar") && !path.equals("sea_king.call")) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && p.distanceToSqr(s.getX(), s.getY(), s.getZ()) < 48 * 48) fight();
    }

    @SubscribeEvent
    public static void select(SelectMusicEvent e) {
        init();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            e.setMusic(menu);
            return;
        }
        ResourceKey<Level> dim = mc.level.dimension();
        boolean ours = dim == Level.OVERWORLD || dim == SkyWorld.LEVEL || dim == DeepWorld.LEVEL;
        if (!ours) return;
        if (mc.gui.getBossOverlay().shouldPlayMusic()) {
            e.setMusic(boss);
            return;
        }
        if (mc.level.getGameTime() - lastFight < 240) {
            e.setMusic(battle);
            return;
        }
        if (dim == SkyWorld.LEVEL) {
            e.setMusic(sky);
            return;
        }
        if (dim == DeepWorld.LEVEL) {
            e.setMusic(deep);
            return;
        }
        if (place == 2) {
            e.setMusic(wano);
            return;
        }
        String sea = mc.level.getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().getPath()).orElse("");
        e.setMusic(switch (sea) {
            case "east_blue", "north_blue", "south_blue", "west_blue" -> place == 1 ? island : eastBlue;
            case "new_world_sea", "world_edge" -> place == 1 ? island : newWorld;
            case "sky_island", "white_sea" -> sky;
            case "deep_sea", "fishman_island" -> deep;
            default -> place == 1 ? island : grandLine; // Paradise, Reverse Mountain, Calm Belt, Red Line
        });
    }

    /** The track the current situation would pick (client self-test). */
    public static Music current() {
        SelectMusicEvent ev = new SelectMusicEvent(Minecraft.getInstance().getSituationalMusic(), null);
        select(ev);
        return ev.getMusic();
    }

    private GLMusic() {}
}
