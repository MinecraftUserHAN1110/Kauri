package dev.brighten.anticheat.check.impl.packets.badpackets;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInEntityActionPacket;
import cc.funkemunky.api.utils.TickTimer;
import cc.funkemunky.api.utils.math.cond.MaxInteger;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.api.check.CheckType;

@CheckInfo(name = "BadPackets (B)", description = "Checks for the spamming of sneak changes.",
        checkType = CheckType.BADPACKETS, punishVL = 40)
public class BadPacketsB extends Check {

    private TickTimer lastSneak = new TickTimer(0);
    private MaxInteger ticks = new MaxInteger(Integer.MAX_VALUE);
    @Packet
    public void onPlace(WrappedInEntityActionPacket action) {
        if(action.getAction().name().contains("SNEAK")) {
            if(lastSneak.hasNotPassed()) {
                ticks.add();
                if(ticks.value() > 80) {
                    vl++;
                    flag("ticks=%1 ping=%p tps=%t", ticks.value());
                }
            } else ticks.subtract(ticks.value() > 40 ? 8 : 4);
            lastSneak.reset();
        }
    }
}
