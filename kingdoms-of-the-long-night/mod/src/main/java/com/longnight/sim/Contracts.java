package com.longnight.sim;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Work a settlement posts on its notice board, generated from its real needs: the materials a
 * stalled building is waiting for, goods in short supply, fuel before winter, and bounties on
 * outlaw bands that have troubled it. Players accept a contract, deliver through the storehouse
 * (sales and gifts both count), and are paid from the settlement's treasury when it is done.
 */
public final class Contracts {
    private Contracts() {}

    public static final class Contract {
        public UUID id = UUID.randomUUID();
        public String kind = "deliver";       // deliver | bounty
        public Commodity good;
        public int amount;
        public double delivered;
        public int reward;                    // pennies, paid on completion (on top of sale prices)
        public long postedDay, expiresDay;
        public UUID takenBy;                  // player
        public UUID target;                   // bounty: outlaw camp
        public String reason = "";

        public boolean open() {
            return takenBy == null;
        }

        public String describe(WorldSim sim) {
            if (kind.equals("bounty")) {
                Settlement camp = sim.settlement(target);
                return "Bounty: break the outlaw band at " + (camp == null ? "?" : camp.name) + " (" + (camp == null ? 0 : camp.residents.size())
                        + " outlaws left). Reward " + reward + "p.";
            }
            return "Deliver " + amount + " " + good.displayName.toLowerCase() + " (" + (int) delivered + " so far) - " + reason
                    + ". Bonus " + reward + "p.";
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("kind", kind);
            if (good != null) t.putString("good", good.id());
            t.putInt("amount", amount);
            t.putDouble("delivered", delivered);
            t.putInt("reward", reward);
            t.putLong("posted", postedDay);
            t.putLong("expires", expiresDay);
            Nbt.putUUID(t, "taken", takenBy);
            Nbt.putUUID(t, "target", target);
            t.putString("reason", reason);
            return t;
        }

        static Contract load(CompoundTag t) {
            Contract c = new Contract();
            c.id = t.getUUID("id");
            c.kind = t.getString("kind");
            c.good = t.contains("good") ? Commodity.byId(t.getString("good")) : null;
            c.amount = t.getInt("amount");
            c.delivered = t.getDouble("delivered");
            c.reward = t.getInt("reward");
            c.postedDay = t.getLong("posted");
            c.expiresDay = t.getLong("expires");
            c.takenBy = Nbt.getUUID(t, "taken");
            c.target = Nbt.getUUID(t, "target");
            c.reason = t.getString("reason");
            return c;
        }
    }

    public static void daily(WorldSim sim, long day) {
        for (Settlement s : sim.settlements.values()) {
            if (s.kind != Settlement.Kind.SETTLEMENT) continue;
            // expire old contracts
            Iterator<Contract> it = s.contracts.iterator();
            while (it.hasNext()) {
                Contract c = it.next();
                boolean campGone = c.kind.equals("bounty") && (sim.settlement(c.target) == null
                        || sim.settlement(c.target).kind != Settlement.Kind.BANDIT_CAMP);
                if (day > c.expiresDay || (campGone && c.takenBy == null)) it.remove();
            }
            if (s.contracts.size() >= 4 || s.treasury < 20) continue;
            // materials for a stalled building
            for (Building b : s.buildings) {
                if (!b.underConstruction() || !b.problem.startsWith("Waiting for")) continue;
                Commodity need = null;
                for (Commodity c : b.cost.keySet()) if (b.problem.contains(c.displayName.toLowerCase())) need = c;
                if (need != null && !has(s, need)) post(sim, s, need, 24, "for the new " + b.type.displayName.toLowerCase(), day);
                break;
            }
            // shortages
            for (Commodity c : Economy.shortages(sim, s)) {
                if (s.contracts.size() >= 3) break;
                if (!has(s, c) && sim.rng.nextFloat() < 0.4f) post(sim, s, c, c.edible() ? 32 : 16, "the stores run low", day);
            }
            // winter preparation: fuel in Leaffall and Dimming
            int month = Calendar.month(day, sim.rules.daysPerYear);
            if ((month == 5 || month == 6) && s.stock.get(Commodity.FUEL) < s.residents.size() * 2 && !has(s, Commodity.FUEL)) {
                post(sim, s, Commodity.FUEL, 24, "firewood and coal before the Deepnight", day);
            }
            // bounty on the band that last threatened us
            if (s.raidsSurvived > 0 || s.raidWarning > 0) {
                Settlement camp = sim.settlement(s.raidSource);
                if (camp == null) camp = sim.nearestSettlement(s.center, Settlement.Kind.BANDIT_CAMP);
                if (camp != null && camp.center != null && camp.center.distSqr(s.center) < 1000 * 1000 && !hasBounty(s, camp.id)) {
                    Contract c = new Contract();
                    c.kind = "bounty";
                    c.target = camp.id;
                    c.reward = Math.min(s.treasury / 2, 60 + camp.residents.size() * 25);
                    c.postedDay = day;
                    c.expiresDay = day + 12;
                    c.reason = "outlaws threaten " + s.name;
                    if (c.reward > 0) s.contracts.add(c);
                }
            }
        }
    }

    static boolean has(Settlement s, Commodity c) {
        for (Contract x : s.contracts) if (c == x.good) return true;
        return false;
    }

    static boolean hasBounty(Settlement s, UUID camp) {
        for (Contract x : s.contracts) if (camp.equals(x.target)) return true;
        return false;
    }

    static void post(WorldSim sim, Settlement s, Commodity c, int amount, String reason, long day) {
        Contract k = new Contract();
        k.good = c;
        k.amount = amount;
        k.reason = reason;
        k.reward = Math.max(5, Math.min(s.treasury / 3, (int) Math.round(amount * c.basePrice * 0.4)));
        k.postedDay = day;
        k.expiresDay = day + 6;
        s.contracts.add(k);
    }

    /** Called when a player hands goods to the settlement. Returns the bonus paid (0 if none completed). */
    public static int delivered(WorldSim sim, Settlement s, UUID player, Commodity c, double units) {
        int bonus = 0;
        for (Contract k : new ArrayList<>(s.contracts)) {
            if (!player.equals(k.takenBy) || k.good != c || units <= 0) continue;
            double used = Math.min(units, k.amount - k.delivered);
            k.delivered += used;
            units -= used;
            if (k.delivered >= k.amount - 1e-6) {
                int pay = Math.min(k.reward, s.treasury);
                s.treasury -= pay;
                bonus += pay;
                s.changeReputation(player, 8);
                s.contracts.remove(k);
                s.chronicle(sim.lastDay, "A traveller fulfilled the contract for " + k.amount + " " + c.displayName.toLowerCase() + ".");
            }
        }
        return bonus;
    }

    /** An outlaw band was broken; pay out bounties to the player who did most of the killing. */
    public static List<String> campDestroyed(WorldSim sim, Settlement camp, UUID player) {
        List<String> paid = new ArrayList<>();
        for (Settlement s : sim.settlements.values()) {
            for (Contract k : new ArrayList<>(s.contracts)) {
                if (!k.kind.equals("bounty") || !camp.id.equals(k.target)) continue;
                if (player == null || (k.takenBy != null && !k.takenBy.equals(player))) continue;
                int pay = Math.min(k.reward, s.treasury);
                s.treasury -= pay;
                s.changeReputation(player, 20);
                s.contracts.remove(k);
                paid.add(s.name + ":" + pay);
            }
        }
        return paid;
    }

    public static List<Contract> load(net.minecraft.nbt.ListTag l) {
        List<Contract> out = new ArrayList<>();
        for (var x : l) out.add(Contract.load((CompoundTag) x));
        return out;
    }

    public static net.minecraft.nbt.ListTag save(List<Contract> l) {
        net.minecraft.nbt.ListTag t = new net.minecraft.nbt.ListTag();
        for (Contract c : l) t.add(c.save());
        return t;
    }
}
