package com.grandline.base;

import com.grandline.base.CrewBases.Claim;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.ship.ShipEntity;
import com.grandline.ship.ShipRegistry;
import com.grandline.ship.Shipyard;
import com.grandline.ship.SpecialRoutes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base upgrades, bought with Beli and building materials (taken from the builder's pack first, then the warehouse):
 * <ul>
 *   <li>watchtower: raid ships are sighted far out and every crew member online is warned; raiders glow</li>
 *   <li>dojo: style and Haki mastery training inside the base counts half again</li>
 *   <li>galley: dishes cooked inside the base come out a quality step better; a hot meal every minute</li>
 *   <li>medical bay: crew inside the base heal a heart every 2 s</li>
 *   <li>shipyard slip: ship upgrades for crew ships at the base dock, a quarter off the shipwright's price</li>
 *   <li>quarters: room for up to four hired hands who live at the base ({@link BaseHands})</li>
 * </ul>
 */
public final class BaseUpgrades {
    public static final String WATCHTOWER = "watchtower", DOJO = "dojo", GALLEY = "galley", MEDBAY = "medbay", SLIP = "slip", QUARTERS = "quarters";

    /** {@code count} of anything matching {@code ingredient}, named after {@code shown}. */
    public record Material(Ingredient ingredient, int count, Item shown) {}

    public record Upgrade(String id, long beli, List<Material> materials) {}

    public static final Map<String, Upgrade> ALL = new LinkedHashMap<>();

    private static void def(String id, long beli, Material... m) {
        ALL.put(id, new Upgrade(id, beli, List.of(m)));
    }

    private static Material m(Ingredient i, int n, Item shown) {
        return new Material(i, n, shown);
    }

    static {
        def(WATCHTOWER, 20_000, m(Ingredient.of(ItemTags.LOGS), 24, Items.OAK_LOG), m(Ingredient.of(Items.COBBLESTONE), 16, Items.COBBLESTONE));
        def(DOJO, 35_000, m(Ingredient.of(ItemTags.PLANKS), 32, Items.OAK_PLANKS), m(Ingredient.of(Items.IRON_INGOT), 12, Items.IRON_INGOT));
        def(GALLEY, 15_000, m(Ingredient.of(Items.COBBLESTONE), 16, Items.COBBLESTONE), m(Ingredient.of(Items.IRON_INGOT), 8, Items.IRON_INGOT));
        def(MEDBAY, 25_000, m(Ingredient.of(ItemTags.WOOL), 12, Items.WHITE_WOOL), m(Ingredient.of(Items.GOLD_INGOT), 6, Items.GOLD_INGOT));
        def(SLIP, 30_000, m(Ingredient.of(ItemTags.PLANKS), 64, Items.OAK_PLANKS), m(Ingredient.of(Items.IRON_INGOT), 16, Items.IRON_INGOT));
        def(QUARTERS, 20_000, m(Ingredient.of(ItemTags.PLANKS), 32, Items.OAK_PLANKS), m(Ingredient.of(ItemTags.WOOL), 8, Items.WHITE_WOOL));
    }

    /** Ship upgrades at the slip cost this fraction of the shipwright's price. */
    public static final double SLIP_DISCOUNT = 0.75;

    public static MutableComponent name(String id) {
        return Component.translatable("grandline.base.upgrade." + id);
    }

    /** "20,000 Beli + 24 Oak Log + 16 Cobblestone" */
    public static MutableComponent cost(Upgrade u) {
        MutableComponent c = Component.translatable("grandline.base.beli", String.format("%,d", u.beli()));
        for (Material m : u.materials()) c.append(" + " + m.count() + " ").append(m.shown().getDescription());
        return c;
    }

    /** How many of a material the builder and the warehouse hold between them. */
    static int available(ServerPlayer sp, Claim c, Material m) {
        int n = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (m.ingredient().test(inv.getItem(i))) n += inv.getItem(i).getCount();
        for (int i = 0; i < c.warehouse.getContainerSize(); i++) if (m.ingredient().test(c.warehouse.getItem(i))) n += c.warehouse.getItem(i).getCount();
        return n;
    }

    private static void take(Container from, Material m, int[] left) {
        for (int i = 0; i < from.getContainerSize() && left[0] > 0; i++) {
            ItemStack st = from.getItem(i);
            if (!m.ingredient().test(st)) continue;
            int k = Math.min(left[0], st.getCount());
            st.shrink(k);
            left[0] -= k;
            from.setChanged();
        }
    }

    /** Why the upgrade cannot be built now (translation key), or null. */
    @Nullable
    public static Component missing(ServerPlayer sp, Claim c, Upgrade u) {
        if (c.has(u.id())) return Component.translatable("grandline.base.upgrade_have", name(u.id()));
        CharacterData d = Characters.get(sp);
        if (d.beli < u.beli() && !sp.isCreative()) return Component.translatable("grandline.base.upgrade_poor", String.format("%,d", u.beli()));
        for (Material m : u.materials()) {
            int have = available(sp, c, m);
            if (have < m.count() && !sp.isCreative()) return Component.translatable("grandline.base.upgrade_short", m.count() - have, m.shown().getDescription());
        }
        return null;
    }

    /** Build an upgrade: any crew member may, inside or near the base. */
    public static boolean build(ServerPlayer sp, Claim c, String id) {
        Upgrade u = ALL.get(id);
        if (u == null || !CrewBases.isCrew(sp, c)) return false;
        Component why = missing(sp, c, u);
        if (why != null) {
            CrewBases.tell(sp, why.copy().withStyle(ChatFormatting.RED));
            return false;
        }
        if (!sp.isCreative()) {
            Characters.get(sp).beli -= u.beli();
            for (Material m : u.materials()) {
                int[] left = {m.count()};
                take(sp.getInventory(), m, left);
                take(c.warehouse, m, left);
            }
            CharacterManager.markDirty(sp);
        }
        c.upgrades.add(id);
        c.dirty();
        sp.level().playSound(null, c.flag, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 1f);
        CrewBases.tellCrew(sp.server, c, Component.translatable("grandline.base.upgrade_built", name(id), CrewBases.islandName(sp.server, c)).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Price of a ship upgrade at the base slip (null: not offered there; the coating is only done at Sabaody). */
    @Nullable
    public static Long slipPrice(String upgrade) {
        Long p = Shipyard.UPGRADES.get(upgrade);
        if (p == null || upgrade.equals(SpecialRoutes.COATING)) return null;
        return Math.round(p * SLIP_DISCOUNT);
    }

    /** Fit a ship upgrade to the player's own ship moored at the base dock, at the slip's price. */
    public static boolean shipUpgrade(ServerPlayer sp, Claim c, String upgrade) {
        Long price = slipPrice(upgrade);
        if (!c.has(SLIP) || price == null || !CrewBases.isCrew(sp, c)) {
            CrewBases.tell(sp, Component.translatable("grandline.base.slip_none").withStyle(ChatFormatting.RED));
            return false;
        }
        ShipEntity ship = null;
        for (ShipEntity s : CrewBases.mooredShips(sp.serverLevel(), c, CrewBases.online(sp.server)))
            if (sp.getUUID().equals(s.owner) && (ship == null || s.distanceToSqr(sp) < ship.distanceToSqr(sp))) ship = s;
        if (ship == null) {
            CrewBases.tell(sp, Component.translatable("grandline.base.slip_no_ship").withStyle(ChatFormatting.RED));
            return false;
        }
        CharacterData d = Characters.get(sp);
        if (ship.hasUpgrade(upgrade)) return false;
        if (d.beli < price && !sp.isCreative()) {
            CrewBases.tell(sp, Component.translatable("grandline.base.upgrade_poor", String.format("%,d", price)).withStyle(ChatFormatting.RED));
            return false;
        }
        if (!sp.isCreative()) d.beli -= price;
        ship.addUpgrade(upgrade);
        if (upgrade.equals("reinforced_hull")) ship.repair(ship.maxHull());
        ShipRegistry.get(sp.server).update(ship);
        CharacterManager.markDirty(sp);
        com.grandline.journey.Journey.onShipUpgraded(sp, upgrade);
        CrewBases.tell(sp, Component.translatable("grandline.base.slip_done", Component.translatable("ship_upgrade.grandline." + upgrade), ship.shipName(), String.format("%,d", price))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    private BaseUpgrades() {}
}
