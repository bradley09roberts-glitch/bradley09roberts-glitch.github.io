package net.emberveil.core;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Emberveil Core: the pack's companion mod.
 * <ul>
 *   <li>Delivers the Wayfarer's Almanac (a Patchouli book) once per player, on first join.</li>
 *   <li>Registers the always-on "Emberveil Tuning" data pack (spawns, loot, structure spacing,
 *       weapon movesets) above other mods' data so its overrides win.</li>
 *   <li>Client side (see {@link net.emberveil.core.client.EmberveilClient}): the Almanac keybind and
 *       the {@code /almanac} client command.</li>
 * </ul>
 */
@Mod(Emberveil.MODID)
public final class Emberveil {
    public static final String MODID = "emberveil";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation ALMANAC_ID = id("almanac");

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    /**
     * Set once the player has been handed the Almanac. Saved with the player and copied on death,
     * so dying, relogging or changing dimension never produces a duplicate book.
     */
    public static final Supplier<AttachmentType<Boolean>> RECEIVED_ALMANAC = ATTACHMENT_TYPES.register(
            "received_almanac",
            () -> AttachmentType.builder(() -> Boolean.FALSE).serialize(Codec.BOOL).copyOnDeath().build());

    public Emberveil(IEventBus modBus, ModContainer container) {
        ATTACHMENT_TYPES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, EmberveilConfig.SPEC);
        modBus.addListener(Emberveil::addPacks);
        NeoForge.EVENT_BUS.addListener(AlmanacGrant::onPlayerLoggedIn);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** The tuning data pack lives inside this jar under /packs/tuning and is always enabled, at top priority. */
    private static void addPacks(AddPackFindersEvent event) {
        event.addPackFinders(id("packs/tuning"), PackType.SERVER_DATA,
                Component.literal("Emberveil Tuning"), PackSource.BUILT_IN, true, Pack.Position.TOP);
    }
}
