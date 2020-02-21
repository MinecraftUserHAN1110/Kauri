package dev.brighten.anticheat.premium.impl;

import cc.funkemunky.api.tinyprotocol.packet.in.*;
import cc.funkemunky.api.tinyprotocol.packet.out.WrappedOutHeldItemSlot;
import dev.brighten.anticheat.check.api.*;
import dev.brighten.api.check.CheckType;

@CheckInfo(name = "Inventory (B)", description = "Checks if player sends impossible packets while inventory is open.",
        checkType = CheckType.INVENTORY, punishVL = 10, vlToFlag = 4)
@Cancellable(cancelType = CancelType.INVENTORY)
public class InventoryB extends Check {

    @Packet
    public void onPlace(WrappedInBlockPlacePacket packet) {
        if(data.playerInfo.inventoryOpen) {
            vl++;
            flag("id=%1;type=%2", data.playerInfo.inventoryId, "place");
        }
    }

    @Packet
    public void onDig(WrappedInBlockDigPacket packet) {
        if(data.playerInfo.inventoryOpen) {
            vl++;
            flag("id=%1;type=%2", data.playerInfo.inventoryId, "dig");
        }
    }

    //TODO Test for false positives
    @Packet
    public void onChat(WrappedInChatPacket packet) {
        if(data.playerInfo.inventoryOpen) {
            vl++;
            flag("id=%1;type=%2", data.playerInfo.inventoryId, "chat");
        }
    }

    private boolean serverSlot;
    @Packet
    public void onServerItem(WrappedOutHeldItemSlot packet) {
        serverSlot = true;
    }

    //TODO Test for false positives
    @Packet
    public void onItem(WrappedInHeldItemSlotPacket packet) {
        if(!serverSlot && data.playerInfo.inventoryOpen) {
            vl++;
            flag("id=%1;type=%2", data.playerInfo.inventoryId, "item-slot");
        }
        serverSlot = false;
    }

    @Packet
    public void onFlying(WrappedInFlyingPacket packet) {
        vl-= vl > 0 ? 0.005 : 0;
    }
}
