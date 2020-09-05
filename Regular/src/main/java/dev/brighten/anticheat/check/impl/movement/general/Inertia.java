package dev.brighten.anticheat.check.impl.movement.general;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.utils.MathUtils;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.api.check.CheckType;
import lombok.val;
import org.bukkit.Location;

@CheckInfo(name = "Inertia", description = "Checks for the lack of proper motion coordinates with mouse movements.",
        checkType = CheckType.GENERAL, developer = true)
public class Inertia extends Check {

    private int buffer;

    @Packet
    public void onPacket(WrappedInFlyingPacket packet) {
        if(packet.isPos() && packet.isLook()) {
            double dir = getDirection(data.playerInfo.from.toLocation(data.getPlayer().getWorld()),
                    data.playerInfo.to.toLocation(data.getPlayer().getWorld()));

            val f = data.predictionService.moveForward;
            val s = data.predictionService.moveStrafing;
            if(f < 0) dir+= 180;
            if(s != 0) dir+=
                    (f != 0 ? (s > 0 ? 45 : -45) * (f < 0 ? -1 : 1) : (s > 0 ? 90 : -90));

            dir = MathUtils.yawTo180D(dir);
            float yaw = MathUtils.yawTo180F(data.playerInfo.to.yaw);

            boolean velocity = data.playerInfo.lastVelocity.hasNotPassed(40);
            boolean ground = data.playerInfo.groundTicks > 3;

            double delta = Math.abs(dir - yaw);
            if(!velocity && !data.blockInfo.collidesHorizontally
                    && ground && delta > 25 && data.playerInfo.deltaXZ > 0) {
                if(data.playerInfo.deltaYaw < 40 && ++buffer > 5) {
                    vl++;
                    flag("d=%v.2 y=%v.1 dx=%v", delta, yaw, data.moveProcessor.deltaX);
                }
            } else buffer = 0;

            debug("dir=%v.3 yaw=%v.3 onGround=%v velocity=%v key=%v buffer=%v world=%v",
                    dir, yaw, ground, velocity, data.predictionService.key, buffer, data.playerInfo.worldLoaded);
        }
    }

    private static double getDirection(Location from, Location to) {
        if (from != null && to != null) {
            double difX = to.getX() - from.getX();
            double difZ = to.getZ() - from.getZ();
            return (Math.atan2(difZ, difX) * 180.0D / 3.141592653589793D) - 90.0F;
        } else {
            return 0.0D;
        }
    }
}
