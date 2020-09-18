package dev.brighten.anticheat.check.impl.combat.killaura;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInUseEntityPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInWindowClickPacket;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.api.check.CheckType;

@CheckInfo(name = "Killaura (D)", description = "Checks if a user attacks while inventory is open.",
        checkType = CheckType.KILLAURA, developer = true)
public class KillauraD extends Check {

    @Packet
    public void onPacket(WrappedInFlyingPacket packet) {
        if((packet.isPos() || packet.isLook())
                && data.playerInfo.lastWindowClick.hasNotPassed(3)
                && data.playerInfo.lastAttack.hasNotPassed(1)
                && data.playerInfo.inventoryOpen) {
            vl++;
            flag("window=%v attack=%v",
                    data.playerInfo.lastWindowClick.getPassed(), data.playerInfo.lastAttack.getPassed());
        }
    }
}
