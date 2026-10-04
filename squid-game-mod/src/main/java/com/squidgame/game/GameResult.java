package com.squidgame.game;

import com.squidgame.tournament.Contestant;
import net.minecraft.network.chat.Component;

import java.util.List;

/** What a game reports when it concludes. Survivors are every contestant still alive; eliminated those who just went out. */
public record GameResult(List<Contestant> survivors, List<Contestant> eliminated, Component headline, Component detail) {
}
