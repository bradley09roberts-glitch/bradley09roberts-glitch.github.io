package com.longnight.game;

import com.longnight.sim.Commodity;
import com.longnight.sim.Economy;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Buying and selling at a settlement's ledger. Every transaction moves goods between the
 * player's inventory and the ledger atomically, and coins between the player and the treasury,
 * so goods and money are conserved: nothing can be duplicated by trading back and forth.
 */
public final class Trade {
    private Trade() {}

    public static final double SELL_FACTOR = 0.75;
    public static final double BUY_FACTOR = 1.25;

    public static int buyPrice(WorldSim sim, Settlement s, Commodity c) {
        return Math.max(1, (int) Math.ceil(Economy.price(sim, s, c) * BUY_FACTOR));
    }

    public static int sellPrice(WorldSim sim, Settlement s, Commodity c) {
        return Math.max(0, (int) Math.floor(Economy.price(sim, s, c) * SELL_FACTOR));
    }

    /** The player offers the stack in hand. Sneaking donates it instead of selling. */
    public static void handIn(ServerPlayer player, WorldSim sim, Settlement s, InteractionHand hand, boolean donate) {
        ItemStack stack = player.getItemInHand(hand);
        int coinValue = Coins.valueOf(stack.getItem());
        if (coinValue > 0) {
            int amount = coinValue * stack.getCount();
            stack.setCount(0);
            s.treasury += amount;
            s.changeReputation(player.getUUID(), Math.max(1, amount / 20));
            Ui.msg(player, "You give " + Coins.format(amount) + " to the coffers of " + s.name + ". They will remember it.");
            sim.setDirty();
            return;
        }
        TradeGoods.Units u = TradeGoods.of(stack);
        if (u == null) {
            Ui.msg(player, "The storekeeper shakes their head: \"We've no use for that here.\"");
            return;
        }
        int room = (int) Math.floor((s.storageCapacity() - s.stock.total()) / u.perItem());
        int count = Math.min(stack.getCount(), Math.max(0, room));
        if (count <= 0) {
            Ui.msg(player, "\"Our stores are full to the rafters. We need another storehouse before we can take more.\"");
            return;
        }
        int unitPrice = sellPrice(sim, s, u.commodity());
        if (!donate) {
            int affordable = unitPrice <= 0 ? count : (int) Math.min(count, s.treasury / Math.max(1.0, unitPrice * u.perItem()));
            if (affordable <= 0) {
                Ui.msg(player, "\"I'd buy, but the coffers of " + s.name + " are empty. Sneak and offer it if you'd give it freely.\"");
                return;
            }
            count = affordable;
        }
        double units = count * u.perItem();
        String what = count + " " + stack.getHoverName().getString().toLowerCase();
        stack.shrink(count);
        s.stock.add(u.commodity(), units);
        int bonus = com.longnight.sim.Contracts.delivered(sim, s, player.getUUID(), u.commodity(), units);
        if (bonus > 0) {
            Coins.give(player, bonus);
            Ui.msg(player, "Contract fulfilled! The steward pays you a bonus of " + Coins.format(bonus) + ".");
        }
        if (donate) {
            s.changeReputation(player.getUUID(), Math.max(1, (int) (units * u.commodity().basePrice / 15)));
            Ui.msg(player, "You give " + what + " to the stores of " + s.name + ". Folk here will remember your generosity. (Their "
                    + u.commodity().displayName.toLowerCase() + " in store: " + fmt(s.stock.get(u.commodity())) + ")");
        } else {
            int pay = (int) Math.round(units * unitPrice);
            pay = Math.min(pay, s.treasury);
            s.treasury -= pay;
            Coins.give(player, pay);
            s.changeReputation(player.getUUID(), Math.max(0, pay / 40));
            Ui.msg(player, "Sold " + count + " (" + fmt(units) + " " + u.commodity().displayName.toLowerCase() + ") for " + Coins.format(pay) + ".");
        }
        sim.setDirty();
    }

    public static boolean buy(ServerPlayer player, WorldSim sim, Settlement s, Commodity c, int count) {
        Item item = TradeGoods.itemFor(c);
        if (item == null || count <= 0) {
            Ui.msg(player, "That cannot be carried away.");
            return false;
        }
        if (s.stock.whole(c) < count) {
            Ui.msg(player, "\"We only have " + s.stock.whole(c) + " " + c.displayName.toLowerCase() + " to spare.\"");
            return false;
        }
        int unit = buyPrice(sim, s, c);
        int discount = s.citizenPlayers.contains(player.getUUID()) ? 10 : 0;
        int total = (int) Math.ceil(unit * count * (100 - discount) / 100.0);
        if (Coins.purse(player) < total) {
            Ui.msg(player, "That costs " + Coins.format(total) + "; you carry " + Coins.format(Coins.purse(player)) + ".");
            return false;
        }
        if (!s.stock.take(c, count)) return false;
        if (!Coins.take(player, total)) {
            s.stock.add(c, count); // cannot happen (checked above) but never lose goods
            return false;
        }
        s.treasury += total;
        Coins.add(player, new ItemStack(item, count));
        s.changeReputation(player.getUUID(), Math.max(0, total / 50));
        Ui.msg(player, "Bought " + count + " " + c.displayName.toLowerCase() + " for " + Coins.format(total) + ".");
        sim.setDirty();
        return true;
    }

    static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format("%.1f", d);
    }
}
