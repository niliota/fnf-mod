package space.niliota.fnfmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import space.niliota.fnfmod.Judging.JudgedNote;
import space.niliota.fnfmod.Judging.NoteJudge;
import space.niliota.fnfmod.Judging.PlayStats;
import space.niliota.fnfmod.Judging.Rating;
import space.niliota.fnfmod.network.ClientSpectate;

import java.util.Locale;

// Draws the games being played near me in the world: a board floating above each player's head with their strums and
// notes, turned towards the camera like a nametag. In VS, boyfriend's side floats above the boyfriend player and the
// opponent's side above the opponent player. Their score sits under the strums, the rating popups next to them.
//
// The board is drawn in "pixels" like the HUD (a note is 32 pixels), scaled down into the world.
// The notes fall down onto the strums, which sit above the player's head. Nothing is above the notes: they fade in
// out of thin air at the top of the board.
public class WorldNotes {
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("fnfmod", "note_assets.png");
    private static final float SCALE = 0.0125f;      // world blocks per board pixel (a note = 0.4 blocks)
    private static final double ABOVE_HEAD = 0.7;    // blocks between the player's head and the strums (the score fits in between, just above the nametag)
    private static final int NOTE = 32;
    private static final int WINDOW = 140;           // how high above the strums notes show up (pixels)
    private static final int BOARD_TOP = -NOTE - WINDOW; // where notes show up (the strums are at -NOTE..0)
    private static final int FADE = 40;              // notes fade in over this many pixels at the top
    private static final int HOLD_WIDTH = Math.round(NOTE * 52f / 160f);
    private static final int HOLD_END_HEIGHT = Math.round(HOLD_WIDTH * 64f / 52f);
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final long POPUP_TIME = 600;

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(WorldNotes::render);
    }

    private static void render(WorldRenderContext context) {
        if (ClientSpectate.games().isEmpty() || context.world() == null) return;
        if (Gameplay.running) return; // not while I play (with "My songs" the server doesn't know I'm playing, so they still come in)
        for (ClientSpectate.Watched game : ClientSpectate.games()) {
            double position = game.songPosition();
            game.boyfriend.remoteUpdate(position);
            if (game.opponent != null) game.opponent.remoteUpdate(position);

            Player boyfriend = context.world().getPlayerByUUID(game.start.boyfriendId());
            if (boyfriend != null) {
                drawBoard(context, boyfriend, game, true, position);
            }
            if (game.opponent != null) {
                Player opponent = context.world().getPlayerByUUID(game.start.opponentId());
                if (opponent != null) drawBoard(context, opponent, game, false, position);
            }
        }
    }

    private static void drawBoard(WorldRenderContext context, Player player, ClientSpectate.Watched game, boolean boyfriendSide, double position) {
        if (player.isInvisible()) return;
        NoteJudge side = boyfriendSide ? game.boyfriend : game.opponent;
        boolean[] keys = boyfriendSide ? game.boyfriendKeys : game.opponentKeys;
        double[] lastHit = boyfriendSide ? game.boyfriendLastHit : game.opponentLastHit;

        PoseStack pose = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        Vec3 feet = player.getPosition(context.tickCounter().getGameTimeDeltaPartialTick(false));

        pose.pushPose();
        pose.translate(feet.x - camera.x, feet.y + player.getBbHeight() + ABOVE_HEAD - camera.y, feet.z - camera.z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation()); // face the camera, like a nametag
        pose.scale(SCALE, -SCALE, SCALE); // y goes down, like on the HUD
        Matrix4f matrix = pose.last().pose();
        MultiBufferSource buffers = context.consumers();
        VertexConsumer sprites = buffers.getBuffer(RenderType.text(ATLAS));

        // strums: the row at the bottom (y -NOTE..0), 4 lanes centered
        int left = -2 * NOTE;
        int strumTop = -NOTE;
        boolean[] holding = new boolean[4];
        for (JudgedNote n : side.notes) if (n.isBeingHeld()) holding[n.note.lane] = true;
        for (int lane = 0; lane < 4; lane++) {
            int x = left + lane * NOTE;
            boolean glowing = position - lastHit[lane] < 150 || holding[lane];
            if (glowing) {
                int glow = Math.round(NOTE * 208f / 160f), offset = (glow - NOTE) / 2;
                sprite(sprites, matrix, x - offset, strumTop - offset, glow, glow, Arrow.glow(lane), 1f, false);
            } else {
                sprite(sprites, matrix, x, strumTop, NOTE, NOTE, keys[lane] ? Arrow.pressed(lane) : Arrow.strum(lane), 1f, false);
            }
        }

        // notes and hold tails coming down onto the strums
        double speed = game.chart.scrollSpeeds.speedAt(position, boyfriendSide);
        double pixelsPerMs = 0.12 * speed;
        int strumMiddle = strumTop + NOTE / 2;
        for (JudgedNote n : side.notes) {
            double distance = (n.note.time - position) * pixelsPerMs;
            if (distance > WINDOW) break;
            int x = left + n.note.lane * NOTE;
            float alpha = n.missed ? 0.35f : 1f;

            if (n.isHold() && !(n.hit && n.isDone() && !n.missed)) {
                double tailEnd = (n.pieceTime(n.pieceCount - 1) - position) * pixelsPerMs + HOLD_END_HEIGHT;
                double tailStart = !n.hit ? distance : !n.missed ? Math.max(distance, 0) : (n.pieceTime(n.piecesHit) - position) * pixelsPerMs;
                tailEnd = Math.min(tailEnd, strumMiddle - BOARD_TOP); // not above the top of the board
                drawHoldTail(sprites, matrix, n.note.lane, x + (NOTE - HOLD_WIDTH) / 2, strumMiddle - (int) tailStart, strumMiddle - (int) tailEnd, alpha * 0.6f);
            }
            if (!n.hit && distance > -NOTE) {
                sprite(sprites, matrix, x, strumTop - (int) distance, NOTE, NOTE, Arrow.note(n.note.lane), alpha, false);
            }
        }

        // score under the strums, rating popup next to them
        Font font = Minecraft.getInstance().font;
        PlayStats stats = side.stats;
        String score = String.format(Locale.ROOT, "%,d  ·  %s", stats.score,
                stats.totalPlayed == 0 ? "?" : String.format(Locale.ROOT, "%.2f%%", stats.accuracy() * 100));
        text(font, pose, buffers, score, 2, 0xFFFFFFFF);

        Rating rating = boyfriendSide ? game.boyfriendRating : game.opponentRating;
        long age = System.currentTimeMillis() - (boyfriendSide ? game.boyfriendRatingAt : game.opponentRatingAt);
        if (rating != null && age < POPUP_TIME) {
            int alpha = Math.max(0x10, (int)(255 * (1 - (double) age / POPUP_TIME)));
            String popup = rating.displayName + (stats.combo >= 2 ? " x" + stats.combo : "");
            font.drawInBatch(popup, 2 * NOTE + 6, strumTop + 12, (alpha << 24) | ratingColor(rating), false, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, LIGHT);
        }

        pose.popPose();
    }

    // stretched hold pieces from the note (bottom) up to the hold end at the top, which is drawn upside down
    private static void drawHoldTail(VertexConsumer sprites, Matrix4f matrix, int lane, int x, int bottom, int top, float alpha) {
        int length = bottom - top;
        if (length <= 0) return;
        if (length <= HOLD_END_HEIGHT) {
            sprite(sprites, matrix, x, top, HOLD_WIDTH, length, Arrow.holdEnd(lane), alpha, true);
            return;
        }
        int bodyTop = top + HOLD_END_HEIGHT;
        int fadeEnd = BOARD_TOP + FADE;
        if (bodyTop < fadeEnd && bottom > fadeEnd) {
            // the body goes into the fade: two pieces, so only the top one fades (the hold piece is stretched, so it looks the same)
            sprite(sprites, matrix, x, bodyTop, HOLD_WIDTH, fadeEnd - bodyTop, Arrow.hold(lane), alpha, false);
            sprite(sprites, matrix, x, fadeEnd, HOLD_WIDTH, bottom - fadeEnd, Arrow.hold(lane), alpha, false);
        } else {
            sprite(sprites, matrix, x, bodyTop, HOLD_WIDTH, bottom - bodyTop, Arrow.hold(lane), alpha, false);
        }
        sprite(sprites, matrix, x, top, HOLD_WIDTH, HOLD_END_HEIGHT, Arrow.holdEnd(lane), alpha, true);
    }

    // 0 at the top of the board, 1 from FADE pixels below it: notes fade in out of thin air
    private static float fade(float y) {
        return Math.max(0, Math.min(1, (y - BOARD_TOP) / FADE));
    }

    // one sprite from the spritesheet as a flat square in the world (flipped = upside down)
    private static void sprite(VertexConsumer buffer, Matrix4f matrix, float x, float y, float width, float height, Arrow arrow, float alpha, boolean flipped) {
        float u0 = arrow.spriteX / (float) Arrow.TEXTURE_WIDTH, u1 = (arrow.spriteX + arrow.regionWidth) / (float) Arrow.TEXTURE_WIDTH;
        float v0 = arrow.spriteY / (float) Arrow.TEXTURE_HEIGHT, v1 = (arrow.spriteY + arrow.regionHeight) / (float) Arrow.TEXTURE_HEIGHT;
        if (flipped) {
            float swap = v0;
            v0 = v1;
            v1 = swap;
        }
        int top = (int) (alpha * fade(y) * 255), bottom = (int) (alpha * fade(y + height) * 255); // faded near the top of the board
        // same corner order Minecraft uses for text, so it faces the camera the same way
        buffer.addVertex(matrix, x, y, 0).setColor(255, 255, 255, top).setUv(u0, v0).setLight(LIGHT);
        buffer.addVertex(matrix, x, y + height, 0).setColor(255, 255, 255, bottom).setUv(u0, v1).setLight(LIGHT);
        buffer.addVertex(matrix, x + width, y + height, 0).setColor(255, 255, 255, bottom).setUv(u1, v1).setLight(LIGHT);
        buffer.addVertex(matrix, x + width, y, 0).setColor(255, 255, 255, top).setUv(u1, v0).setLight(LIGHT);
    }

    // centered text with a see-through dark background, like a nametag.
    // Minecraft puts the background a hair towards the camera (+z here, the board faces the camera), so it covers the
    // letters and they flicker away. So it's drawn twice: background (and text) a bit further back, then the text again
    // clearly in front of it.
    private static void text(Font font, PoseStack pose, MultiBufferSource buffers, String text, int y, int color) {
        float x = -font.width(text) / 2f;
        pose.pushPose();
        pose.translate(0, 0, -1); // further from the camera (-z points away from it)
        font.drawInBatch(text, x, y, color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x40000000, LIGHT);
        pose.popPose();
        font.drawInBatch(text, x, y, color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
    }

    private static int ratingColor(Rating rating) {
        return switch (rating) {
            case SICK -> 0x55FFFF;
            case GOOD -> 0x55FF55;
            case BAD  -> 0xFFAA00;
            case SHIT -> 0xAA5555;
        };
    }
}
