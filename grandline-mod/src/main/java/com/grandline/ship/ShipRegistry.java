package com.grandline.ship;

import com.grandline.GrandLine;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Every player-owned ship: owner, class, name, interior slot, last known position, sunk state, upgrades. */
public final class ShipRegistry extends SavedData {
    public static final class Record {
        public UUID shipId, owner;
        public String ownerName = "", name = "", cls = "sloop", upgrades = "", dim = "minecraft:overworld";
        public int interior = -1;
        public double x, y, z;
        public boolean sunk;
        public int sailColor = 0xF4EEDC, hullColor = 0x8A5A32;
        /** Figurehead/emblem/flag ({@link ShipLook#encode()}). */
        public String look = ShipLook.DEFAULT.encode();
        /** A captured ship not yet sold or refitted at a shipwright. */
        public boolean prize;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", shipId);
            if (owner != null) t.putUUID("owner", owner);
            t.putString("ownerName", ownerName);
            t.putString("name", name);
            t.putString("cls", cls);
            t.putString("upgrades", upgrades);
            t.putString("dim", dim);
            t.putInt("interior", interior);
            t.putDouble("x", x);
            t.putDouble("y", y);
            t.putDouble("z", z);
            t.putBoolean("sunk", sunk);
            t.putInt("sail", sailColor);
            t.putInt("hullc", hullColor);
            t.putString("look", look);
            t.putBoolean("prize", prize);
            return t;
        }

        static Record load(CompoundTag t) {
            Record r = new Record();
            r.shipId = t.getUUID("id");
            r.owner = t.hasUUID("owner") ? t.getUUID("owner") : null;
            r.ownerName = t.getString("ownerName");
            r.name = t.getString("name");
            r.cls = t.getString("cls");
            r.upgrades = t.getString("upgrades");
            r.dim = t.contains("dim") ? t.getString("dim") : "minecraft:overworld";
            r.interior = t.getInt("interior");
            r.x = t.getDouble("x");
            r.y = t.getDouble("y");
            r.z = t.getDouble("z");
            r.sunk = t.getBoolean("sunk");
            r.sailColor = t.getInt("sail");
            r.hullColor = t.getInt("hullc");
            r.look = t.contains("look") ? t.getString("look") : ShipLook.DEFAULT.encode();
            r.prize = t.getBoolean("prize");
            return r;
        }
    }

    private final Map<UUID, Record> ships = new LinkedHashMap<>();
    private int nextInterior = 0;

    public static ShipRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ShipRegistry::new, ShipRegistry::load, null), GrandLine.MODID + "_ships");
    }

    public Record register(ShipEntity ship) {
        Record r = new Record();
        r.shipId = ship.shipId;
        r.owner = ship.owner;
        r.ownerName = ship.ownerName;
        r.cls = ship.cls.id();
        if (ship.cls.interior && ship.interiorIndex < 0) ship.interiorIndex = nextInterior++;
        r.interior = ship.interiorIndex;
        ships.put(r.shipId, r);
        update(ship);
        return r;
    }

    public void update(ShipEntity ship) {
        Record r = ships.get(ship.shipId);
        if (r == null) return;
        r.x = ship.getX();
        r.y = ship.getY();
        r.z = ship.getZ();
        r.dim = ship.level().dimension().location().toString();
        r.name = ship.shipName();
        r.upgrades = String.join(",", ship.upgrades());
        r.sailColor = ship.sailColor();
        r.hullColor = ship.hullColor();
        r.look = ship.look().encode();
        r.prize = ship.flag(ShipEntity.F_PRIZE);
        r.sunk = false;
        setDirty();
    }

    public void markSunk(ShipEntity ship) {
        Record r = ships.get(ship.shipId);
        if (r == null) return;
        if (r.prize) { // a prize that goes down is lost for good (no salvaging someone else's ship)
            remove(ship.shipId);
            return;
        }
        r.sunk = true;
        r.x = ship.getX();
        r.z = ship.getZ();
        setDirty();
    }

    public void remove(UUID id) {
        if (ships.remove(id) != null) setDirty();
    }

    @Nullable
    public Record get(UUID id) {
        return ships.get(id);
    }

    @Nullable
    public Record byInterior(int idx) {
        for (Record r : ships.values()) if (r.interior == idx) return r;
        return null;
    }

    public List<Record> ownedBy(UUID owner) {
        List<Record> l = new ArrayList<>();
        for (Record r : ships.values()) if (owner.equals(r.owner)) l.add(r);
        return l;
    }

    public Collection<Record> all() {
        return ships.values();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag m = new CompoundTag();
        for (Record r : ships.values()) m.put(r.shipId.toString(), r.save());
        tag.put("ships", m);
        tag.putInt("nextInterior", nextInterior);
        return tag;
    }

    public static ShipRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        ShipRegistry reg = new ShipRegistry();
        CompoundTag m = tag.getCompound("ships");
        for (String k : m.getAllKeys()) {
            Record r = Record.load(m.getCompound(k));
            reg.ships.put(r.shipId, r);
        }
        reg.nextInterior = tag.getInt("nextInterior");
        return reg;
    }
}
