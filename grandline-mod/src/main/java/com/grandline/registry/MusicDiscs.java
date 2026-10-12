package com.grandline.registry;

import com.grandline.GrandLine;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Music discs of four of the mod's own tracks (jukebox songs in data/grandline/jukebox_song, mono "disc.*" sound
 * events so the jukebox fades with distance). Found in treasure and wreck chests. The asset pipeline reads the ids
 * from this file ({@code ITEMS.register("…")}).
 */
public final class MusicDiscs {
    public static final DeferredItem<Item> DISC_EAST_BLUE = disc("music_disc_east_blue", "east_blue");
    public static final DeferredItem<Item> DISC_GRAND_LINE = disc("music_disc_grand_line", "grand_line");
    public static final DeferredItem<Item> DISC_WANO = disc("music_disc_wano", "wano");
    public static final DeferredItem<Item> DISC_BOSS = disc("music_disc_boss", "boss");

    private static DeferredItem<Item> disc(String id, String song) {
        ResourceKey<JukeboxSong> key = ResourceKey.create(Registries.JUKEBOX_SONG, GrandLine.id(song));
        return ModItems.ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(key)));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(MusicDiscs::tab);
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != ModCreativeTabs.MAIN.getKey()) return;
        e.accept(DISC_EAST_BLUE.get());
        e.accept(DISC_GRAND_LINE.get());
        e.accept(DISC_WANO.get());
        e.accept(DISC_BOSS.get());
    }

    private MusicDiscs() {}
}
