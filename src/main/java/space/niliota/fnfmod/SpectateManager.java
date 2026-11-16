package space.niliota.fnfmod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.niliota.fnfmod.network.PlayingPayload;
import space.niliota.fnfmod.network.SongDataPayload;
import space.niliota.fnfmod.network.SpectateEventsPayload;
import space.niliota.fnfmod.network.SpectateStartPayload;
import space.niliota.fnfmod.network.SpectateStopPayload;
import space.niliota.fnfmod.network.VsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Lets players see songs being played near them (solo or VS): the arrows float above the players' heads (ClientSpectate).
//  * Players tell the server when they start/stop a song (PlayingPayload). Solo games use the song the server last
//    sent them, VS games come from VsManager.
//  * Every press/hit/miss of a game is kept, so someone who walks up halfway through still sees the right scores.
//  * Every half second, every player who isn't playing gets every game within START_RANGE blocks,
//    until they're more than STOP_RANGE away (a bit further, so it doesn't flicker on and off at the edge).
public class SpectateManager {
    private static final double START_RANGE = 16, STOP_RANGE = 24;

    private static class Game {
        final int id;
        final UUID boyfriend, opponent; // opponent = null: the computer (solo)
        final SongDataPayload song;
        final long musicStartsAt;       // System.currentTimeMillis() when the music started (or will start)
        final List<VsEvent> boyfriendEvents = new ArrayList<>(), opponentEvents = new ArrayList<>();
        final Set<UUID> spectators = new HashSet<>();

        Game(int id, UUID boyfriend, UUID opponent, SongDataPayload song, long musicStartsAt) {
            this.id = id;
            this.boyfriend = boyfriend;
            this.opponent = opponent;
            this.song = song;
            this.musicStartsAt = musicStartsAt;
        }

        boolean has(UUID player) {
            return player.equals(boyfriend) || player.equals(opponent);
        }
    }

    private static final List<Game> games = new ArrayList<>();
    private static final Map<UUID, SongDataPayload> lastSong = new HashMap<>(); // the last song sent to each player (solo)
    private static final Set<UUID> playing = new HashSet<>();                  // players playing a song right now
    private static int nextId = 1;
    private static int ticks = 0;

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PlayingPayload.TYPE, (payload, context) -> onPlaying(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            lastSong.remove(id);
            playing.remove(id);
            for (Game game : games) game.spectators.remove(id);
            gameEnded(id, server);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 10 == 0) updateSpectators(server);
        });
    }

    // SongSender: a song was sent to this player (if they start it solo, that's the game)
    public static void songSent(ServerPlayer player, SongDataPayload song) {
        lastSong.put(player.getUUID(), song);
    }

    private static void onPlaying(ServerPlayer player, PlayingPayload payload) {
        UUID id = player.getUUID();
        if (!payload.playing()) {
            playing.remove(id);
            if (VsManager.vsGame(id) == null) gameEnded(id, player.getServer()); // VS games end with the match
            return;
        }

        playing.add(id);
        stopWatchingAll(player, player.getServer()); // players don't watch others while they play
        for (Game game : games) if (game.has(id)) return; // already has a game (the rival started the VS one)

        long musicStartsAt = System.currentTimeMillis() + Math.max(0, payload.countdownMs());
        VsManager.VsGame vs = VsManager.vsGame(id);
        if (vs != null) {
            games.add(new Game(nextId++, vs.boyfriend(), vs.opponent(), vs.song(), musicStartsAt));
        } else if (lastSong.containsKey(id)) {
            games.add(new Game(nextId++, id, null, lastSong.get(id), musicStartsAt));
        }
    }

    // VsManager: a player's presses/hits/misses
    public static void onEvents(ServerPlayer player, List<VsEvent> events) {
        for (Game game : games) {
            if (!game.has(player.getUUID())) continue;
            boolean boyfriendSide = player.getUUID().equals(game.boyfriend);
            (boyfriendSide ? game.boyfriendEvents : game.opponentEvents).addAll(events);
            for (UUID spectator : game.spectators) {
                ServerPlayer watcher = player.getServer().getPlayerList().getPlayer(spectator);
                if (watcher != null) ServerPlayNetworking.send(watcher, new SpectateEventsPayload(game.id, boyfriendSide, events));
            }
            return;
        }
    }

    // A game with this player in it is over (solo song stopped, VS match ended, player left)
    public static void gameEnded(UUID player, MinecraftServer server) {
        games.removeIf(game -> {
            if (!game.has(player)) return false;
            for (UUID spectator : game.spectators) {
                ServerPlayer watcher = server.getPlayerList().getPlayer(spectator);
                if (watcher != null) ServerPlayNetworking.send(watcher, new SpectateStopPayload(game.id));
            }
            return true;
        });
    }

    // Who sees what: every game within range, for everyone who has the mod and isn't playing
    private static void updateSpectators(MinecraftServer server) {
        if (games.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (playing.contains(player.getUUID()) || !ServerPlayNetworking.canSend(player, SpectateStartPayload.TYPE)) continue;

            for (Game game : games) {
                if (game.has(player.getUUID())) continue;
                double distance = distanceTo(game, player, server);
                boolean watching = game.spectators.contains(player.getUUID());

                if (watching && distance > STOP_RANGE) {
                    game.spectators.remove(player.getUUID());
                    ServerPlayNetworking.send(player, new SpectateStopPayload(game.id));
                } else if (!watching && distance <= START_RANGE) {
                    startWatching(player, game, server);
                }
            }
        }
    }

    private static void startWatching(ServerPlayer player, Game game, MinecraftServer server) {
        game.spectators.add(player.getUUID());
        ServerPlayer opponent = game.opponent == null ? null : server.getPlayerList().getPlayer(game.opponent);
        ServerPlayNetworking.send(player, new SpectateStartPayload(game.id, game.song,
                nameOf(server, game.boyfriend), game.boyfriend,
                opponent == null ? "" : opponent.getGameProfile().getName(), game.opponent == null ? SpectateStartPayload.NOBODY : game.opponent,
                System.currentTimeMillis() - game.musicStartsAt));

        // everything that happened before they came, in pieces
        sendInPieces(player, game.id, true, game.boyfriendEvents);
        sendInPieces(player, game.id, false, game.opponentEvents);
    }

    private static void stopWatchingAll(ServerPlayer player, MinecraftServer server) {
        for (Game game : games) {
            if (game.spectators.remove(player.getUUID())) ServerPlayNetworking.send(player, new SpectateStopPayload(game.id));
        }
    }

    private static void sendInPieces(ServerPlayer player, int gameId, boolean boyfriendSide, List<VsEvent> events) {
        for (int start = 0; start < events.size(); start += VsEvent.MAX_PER_PACKET) {
            List<VsEvent> piece = new ArrayList<>(events.subList(start, Math.min(events.size(), start + VsEvent.MAX_PER_PACKET)));
            ServerPlayNetworking.send(player, new SpectateEventsPayload(gameId, boyfriendSide, piece));
        }
    }

    // distance to the closest player of the game, or infinity if none of them are in the same world
    private static double distanceTo(Game game, ServerPlayer player, MinecraftServer server) {
        double best = Double.POSITIVE_INFINITY;
        for (UUID id : new UUID[] {game.boyfriend, game.opponent}) {
            if (id == null) continue;
            ServerPlayer other = server.getPlayerList().getPlayer(id);
            if (other != null && other.level() == player.level()) best = Math.min(best, other.distanceTo(player));
        }
        return best;
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player != null ? player.getGameProfile().getName() : "?";
    }
}
