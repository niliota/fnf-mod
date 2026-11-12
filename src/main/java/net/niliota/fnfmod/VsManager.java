package net.niliota.fnfmod;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.niliota.fnfmod.item.ModItems;
import net.niliota.fnfmod.network.SongDataPayload;
import net.niliota.fnfmod.network.VsMatchPayload;
import net.niliota.fnfmod.network.VsPickPayload;
import net.niliota.fnfmod.network.VsReadyPayload;
import net.niliota.fnfmod.network.VsRivalScorePayload;
import net.niliota.fnfmod.network.VsScore;
import net.niliota.fnfmod.network.VsScorePayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// Runs VS matches on the server: two players play the same song on opposite sides, the higher score wins.
//
//  1. challenge   - right-click a player with the Mic, or pick them in the Mic menu (/fnfvs challenge <player>).
//                   They get a chat message with clickable [Accept] [Decline] (/fnfvs accept|decline <player>), valid for 30 s.
//  2. picking     - both get the song menus, whoever picks a song first decides the song and their side.
//  3. loading     - both get the song (same as a normal song request) and load it, then say they're ready.
//  4. playing     - when both are ready, both countdowns start at the same time. Live scores get passed between them.
//  5. results     - when both finished (or 15 s after the first one did), the higher score wins. Leaving or stopping = losing.
// /fnfvs cancel ends the match at any point.
public class VsManager {
    private static final int CHALLENGE_TICKS = 30 * 20;    // a challenge is valid for 30 seconds
    private static final int FINISH_WAIT_TICKS = 15 * 20;  // after one player finished, wait this long for the other's result

    private enum State { PICKING, LOADING, PLAYING }

    private record Challenge(UUID from, UUID to, long createdAt) {}

    private static class Match {
        final UUID a, b;
        State state = State.PICKING;
        String songName = "";
        boolean aIsBoyfriend = true;
        boolean aReady, bReady;
        VsScore aScore, bScore;  // the latest scores
        long firstDoneAt = -1;   // tick when the first player finished

        Match(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        UUID other(UUID player) {
            return player.equals(a) ? b : a;
        }
    }

    private static final List<Challenge> challenges = new ArrayList<>();
    private static final Map<UUID, Match> matches = new HashMap<>(); // both players point to their match
    private static long tick = 0;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("fnfvs")
                        .then(Commands.literal("challenge").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            challenge(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"));
                            return 1;
                        })))
                        .then(Commands.literal("accept").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            accept(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"));
                            return 1;
                        })))
                        .then(Commands.literal("decline").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            decline(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"));
                            return 1;
                        })))
                        .then(Commands.literal("cancel").executes(context -> {
                            cancel(context.getSource().getPlayerOrException());
                            return 1;
                        }))
        ));

        // Right-clicking a player with the Mic challenges them.
        // SUCCESS on both sides, so the Mic doesn't also open its menu on the client.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!player.getItemInHand(hand).is(ModItems.MIC) || !(entity instanceof Player)) return InteractionResult.PASS;
            if (player instanceof ServerPlayer from && entity instanceof ServerPlayer to) challenge(from, to);
            return InteractionResult.SUCCESS;
        });

        ServerPlayNetworking.registerGlobalReceiver(VsPickPayload.TYPE, (payload, context) -> onPick(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(VsReadyPayload.TYPE, (payload, context) -> onReady(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(VsScorePayload.TYPE, (payload, context) -> onScore(context.player(), payload.score()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLeave(handler.getPlayer(), server));
        ServerTickEvents.END_SERVER_TICK.register(VsManager::onTick);
    }

    // ---- challenges ----

    private static void challenge(ServerPlayer from, ServerPlayer to) {
        if (from == to) return;
        if (!ServerPlayNetworking.canSend(to, VsMatchPayload.TYPE)) {
            from.sendSystemMessage(text(name(to) + " doesn't have FNFmod installed.", ChatFormatting.RED));
            return;
        }
        if (matches.containsKey(from.getUUID())) {
            from.sendSystemMessage(text("You're already in a VS match (/fnfvs cancel to leave it).", ChatFormatting.RED));
            return;
        }
        if (matches.containsKey(to.getUUID())) {
            from.sendSystemMessage(text(name(to) + " is already in a VS match.", ChatFormatting.RED));
            return;
        }

        // the same challenge again within a second (a right-click can arrive twice) does nothing
        for (Challenge existing : challenges) {
            if (existing.from().equals(from.getUUID()) && existing.to().equals(to.getUUID()) && tick - existing.createdAt() < 20) return;
        }
        challenges.removeIf(existing -> existing.from().equals(from.getUUID()) && existing.to().equals(to.getUUID()));
        challenges.add(new Challenge(from.getUUID(), to.getUUID(), tick));

        MutableComponent message = text(name(from) + " challenges you to an FNF battle! ", ChatFormatting.AQUA)
                .append(button("[Accept]", ChatFormatting.GREEN, "/fnfvs accept " + name(from), "Accept the challenge"))
                .append(Component.literal(" "))
                .append(button("[Decline]", ChatFormatting.RED, "/fnfvs decline " + name(from), "Decline the challenge"));
        to.sendSystemMessage(message);
        from.sendSystemMessage(text("Challenged " + name(to) + ", they have 30 seconds to accept.", ChatFormatting.GRAY));
    }

    private static void accept(ServerPlayer to, ServerPlayer from) {
        Challenge challenge = findChallenge(from.getUUID(), to.getUUID());
        if (challenge == null) {
            to.sendSystemMessage(text("That challenge doesn't exist anymore.", ChatFormatting.RED));
            return;
        }
        if (matches.containsKey(from.getUUID()) || matches.containsKey(to.getUUID())) {
            to.sendSystemMessage(text("One of you is already in a VS match.", ChatFormatting.RED));
            return;
        }

        // every other challenge involving either player is gone now
        challenges.removeIf(c -> involves(c, from.getUUID()) || involves(c, to.getUUID()));

        Match match = new Match(from.getUUID(), to.getUUID());
        matches.put(from.getUUID(), match);
        matches.put(to.getUUID(), match);

        for (ServerPlayer player : List.of(from, to)) {
            ServerPlayer rival = player == from ? to : from;
            player.sendSystemMessage(text("VS " + name(rival) + "! Pick a song from the Mic menu, whoever picks first decides.", ChatFormatting.AQUA));
            ServerPlayNetworking.send(player, new VsMatchPayload(VsMatchPayload.PICKING, name(rival), rival.getUUID(), true, ""));
        }
    }

    private static void decline(ServerPlayer to, ServerPlayer from) {
        Challenge challenge = findChallenge(from.getUUID(), to.getUUID());
        if (challenge == null) return;
        challenges.remove(challenge);
        from.sendSystemMessage(text(name(to) + " declined your challenge.", ChatFormatting.GRAY));
        to.sendSystemMessage(text("Declined.", ChatFormatting.GRAY));
    }

    private static void cancel(ServerPlayer player) {
        challenges.removeIf(c -> c.from().equals(player.getUUID()));
        Match match = matches.get(player.getUUID());
        if (match == null) return;
        endMatch(match, player.getServer(), name(player) + " cancelled the VS match.");
    }

    // ---- the match ----

    private static void onPick(ServerPlayer player, VsPickPayload pick) {
        Match match = matches.get(player.getUUID());
        if (match == null || match.state != State.PICKING) return; // the other player was faster

        ServerPlayer rival = player.getServer().getPlayerList().getPlayer(match.other(player.getUUID()));
        if (rival == null) return;

        SongDataPayload song = SongSender.buildSongData(player, pick.request());
        if (song == null) return; // couldn't load it, the picker got a message and can pick something else

        match.state = State.LOADING;
        match.songName = song.songName();
        match.aIsBoyfriend = player.getUUID().equals(match.a) == pick.boyfriend();

        for (ServerPlayer p : List.of(player, rival)) {
            ServerPlayer other = p == player ? rival : player;
            boolean boyfriend = p.getUUID().equals(match.a) == match.aIsBoyfriend;
            p.sendSystemMessage(text(name(player) + " picked " + song.songName() + ". You play the "
                    + (boyfriend ? "boyfriend" : "opponent") + " side.", ChatFormatting.AQUA));
            ServerPlayNetworking.send(p, new VsMatchPayload(VsMatchPayload.LOADING, name(other), other.getUUID(), boyfriend, song.songName()));
            ServerPlayNetworking.send(p, song);
        }
    }

    private static void onReady(ServerPlayer player) {
        Match match = matches.get(player.getUUID());
        if (match == null || match.state != State.LOADING) return;

        if (player.getUUID().equals(match.a)) match.aReady = true;
        else match.bReady = true;
        if (!match.aReady || !match.bReady) return;

        // both loaded: start both countdowns at the same time
        match.state = State.PLAYING;
        for (UUID id : List.of(match.a, match.b)) {
            ServerPlayer p = player.getServer().getPlayerList().getPlayer(id);
            ServerPlayer other = player.getServer().getPlayerList().getPlayer(match.other(id));
            if (p == null || other == null) continue;
            boolean boyfriend = id.equals(match.a) == match.aIsBoyfriend;
            ServerPlayNetworking.send(p, new VsMatchPayload(VsMatchPayload.GO, name(other), other.getUUID(), boyfriend, match.songName));
        }
    }

    private static void onScore(ServerPlayer player, VsScore score) {
        Match match = matches.get(player.getUUID());
        if (match == null || match.state != State.PLAYING) return;

        if (player.getUUID().equals(match.a)) match.aScore = score;
        else match.bScore = score;

        // pass it on to the rival for their live display
        ServerPlayer rival = player.getServer().getPlayerList().getPlayer(match.other(player.getUUID()));
        if (rival != null) ServerPlayNetworking.send(rival, new VsRivalScorePayload(score));

        if (score.isDone() && match.firstDoneAt < 0) match.firstDoneAt = tick;
        if (isDone(match.aScore) && isDone(match.bScore)) finishMatch(match, player.getServer());
    }

    private static void onTick(MinecraftServer server) {
        tick++;

        // challenges run out after 30 seconds
        challenges.removeIf(c -> {
            if (tick - c.createdAt() < CHALLENGE_TICKS) return false;
            ServerPlayer from = server.getPlayerList().getPlayer(c.from());
            ServerPlayer to = server.getPlayerList().getPlayer(c.to());
            if (from != null && to != null) from.sendSystemMessage(text("Your challenge to " + name(to) + " ran out.", ChatFormatting.GRAY));
            return true;
        });

        // one player finished a while ago but the other's result never came: decide with what we have
        for (Match match : new ArrayList<>(matches.values())) {
            if (match.state == State.PLAYING && match.firstDoneAt >= 0 && tick - match.firstDoneAt > FINISH_WAIT_TICKS) {
                finishMatch(match, server);
            }
        }
    }

    private static void onLeave(ServerPlayer player, MinecraftServer server) {
        challenges.removeIf(c -> involves(c, player.getUUID()));
        Match match = matches.get(player.getUUID());
        if (match == null) return;

        if (match.state == State.PICKING) {
            endMatch(match, server, name(player) + " left, the VS match is cancelled.");
        } else {
            // leaving after a song was picked counts as losing
            if (player.getUUID().equals(match.a)) match.aScore = new VsScore(0, 0, 0, 0, VsScore.GAVE_UP);
            else match.bScore = new VsScore(0, 0, 0, 0, VsScore.GAVE_UP);
            finishMatch(match, server);
        }
    }

    // Decides the winner and tells both players.
    private static void finishMatch(Match match, MinecraftServer server) {
        String aName = nameOf(server, match.a), bName = nameOf(server, match.b);
        int result = VsScore.compare(match.aScore, match.bScore);
        String verdict = result > 0 ? aName + " wins!" : result < 0 ? bName + " wins!" : "It's a draw!";

        String message = "VS " + match.songName + ": " + verdict
                + "\n" + scoreLine(aName, match.aScore)
                + "\n" + scoreLine(bName, match.bScore);
        endMatch(match, server, message);
    }

    private static void endMatch(Match match, MinecraftServer server, String message) {
        matches.remove(match.a);
        matches.remove(match.b);
        for (UUID id : List.of(match.a, match.b)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            p.sendSystemMessage(text(message, ChatFormatting.AQUA));
            ServerPlayNetworking.send(p, new VsMatchPayload(VsMatchPayload.ENDED, nameOf(server, match.other(id)), match.other(id), true, match.songName));
        }
    }

    // ---- small helpers ----

    private static String scoreLine(String name, VsScore score) {
        if (score == null) return "  " + name + ": no result";
        if (score.status() == VsScore.GAVE_UP) return "  " + name + ": gave up";
        return String.format(Locale.ROOT, "  %s: %d points, %.2f%%, %d misses, max combo %d",
                name, score.score(), score.accuracy() * 100, score.misses(), score.maxCombo());
    }

    private static boolean isDone(VsScore score) {
        return score != null && score.isDone();
    }

    private static Challenge findChallenge(UUID from, UUID to) {
        for (Challenge c : challenges) {
            if (c.from().equals(from) && c.to().equals(to)) return c;
        }
        return null;
    }

    private static boolean involves(Challenge c, UUID player) {
        return c.from().equals(player) || c.to().equals(player);
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player != null ? name(player) : "?";
    }

    private static MutableComponent text(String message, ChatFormatting color) {
        return Component.literal(message).withStyle(color);
    }

    // clickable chat text that runs a command
    private static MutableComponent button(String label, ChatFormatting color, String command, String hover) {
        return Component.literal(label).withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }
}
