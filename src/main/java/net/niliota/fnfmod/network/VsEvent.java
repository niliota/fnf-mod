package net.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

// One thing that happened in a VS player's game, sent to their rival so their side can be shown live.
//  KEY_DOWN / KEY_UP - value = lane (the strum looks pressed)
//  HIT               - value = note number, extra = Rating (its position in the Rating enum)
//  MISS              - value = note number
//  HOLD_DROPPED      - value = note number
public record VsEvent(int type, int value, int extra) {
    public static final int KEY_DOWN = 0, KEY_UP = 1, HIT = 2, MISS = 3, HOLD_DROPPED = 4;
    public static final int MAX_PER_PACKET = 512; // way more than anyone can do in a tick, anything above is ignored

    public static void writeList(FriendlyByteBuf buf, List<VsEvent> events) {
        buf.writeVarInt(events.size());
        for (VsEvent event : events) {
            buf.writeVarInt(event.type());
            buf.writeVarInt(event.value());
            buf.writeVarInt(event.extra());
        }
    }

    public static List<VsEvent> readList(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PER_PACKET) throw new IllegalArgumentException("Too many VS events: " + count);
        List<VsEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(new VsEvent(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return events;
    }
}
