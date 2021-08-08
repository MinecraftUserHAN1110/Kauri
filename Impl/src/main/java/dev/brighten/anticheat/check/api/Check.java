package dev.brighten.anticheat.check.api;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.bungee.BungeeAPI;
import cc.funkemunky.api.reflections.types.WrappedClass;
import cc.funkemunky.api.reflections.types.WrappedMethod;
import cc.funkemunky.api.tinyprotocol.api.ProtocolVersion;
import cc.funkemunky.api.tinyprotocol.api.TinyProtocolHandler;
import cc.funkemunky.api.tinyprotocol.api.packets.channelhandler.TinyProtocol1_7;
import cc.funkemunky.api.tinyprotocol.api.packets.channelhandler.TinyProtocol1_8;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.tinyprotocol.packet.out.WrappedOutCloseWindowPacket;
import cc.funkemunky.api.utils.*;
import dev.brighten.anticheat.Kauri;
import dev.brighten.anticheat.data.ObjectData;
import dev.brighten.anticheat.discord.DiscordAPI;
import dev.brighten.anticheat.utils.api.BukkitAPI;
import dev.brighten.anticheat.utils.timer.Timer;
import dev.brighten.anticheat.utils.timer.impl.TickTimer;
import dev.brighten.api.KauriAPI;
import dev.brighten.api.KauriVersion;
import dev.brighten.api.check.CancelType;
import dev.brighten.api.check.CheckType;
import dev.brighten.api.check.KauriCheck;
import dev.brighten.api.listener.KauriCancelEvent;
import dev.brighten.api.listener.KauriFlagEvent;
import dev.brighten.api.listener.KauriPunishEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.val;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@NoArgsConstructor
public class Check implements KauriCheck {

    public static Map<WrappedClass, CheckInfo> checkClasses = new ConcurrentHashMap<>();
    public static Map<WrappedClass, CheckSettings> checkSettings = new ConcurrentHashMap<>();

    private static WrappedClass protocolClass = ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_8)
            ? new WrappedClass(TinyProtocol1_7.class) : new WrappedClass(TinyProtocol1_8.class);
    private static WrappedMethod getChannel = protocolClass.getMethod("getChannel", Player.class);


    public ObjectData data;
    @Getter
    public String name, description;
    @Getter
    @Setter
    public boolean enabled, executable, cancellable;
    @Getter
    public boolean developer;
    @Getter
    @Setter
    public float vl, punishVl, vlToFlag;
    public ProtocolVersion minVersion, maxVersion;
    @Getter
    public CheckType checkType;

    public CancelType cancelMode;
    @Getter
    private KauriVersion plan;

    public boolean exempt, banExempt;

    private final Timer lastAlert = new TickTimer(MathUtils.millisToTicks(Config.alertsDelay));

    public void setData(ObjectData data) {
        this.data = data;
    }

    public static void register(Check check) {
        if(!check.getClass().isAnnotationPresent(CheckInfo.class)) {
            MiscUtils.printToConsole("Could not register "  + check.getClass().getSimpleName()
                    + " because @CheckInfo was not present.");
            return;
        }
        CheckInfo info = check.getClass().getAnnotation(CheckInfo.class);
        MiscUtils.printToConsole("Registering... " + info.name());
        WrappedClass checkClass = new WrappedClass(check.getClass());
        String name = info.name();

        CancelType type = null;
        if(check.getClass().isAnnotationPresent(Cancellable.class))
            type = check.getClass().getAnnotation(Cancellable.class).cancelType();
        CheckSettings settings = new CheckSettings(info.name(), info.description(), info.checkType(), type,
                info.planVersion(), info.punishVL(), info.vlToFlag(), info.minVersion(), info.maxVersion());

        String path = "checks." + name;
        settings.enabled = new ConfigDefault<>(info.enabled(),
                path + ".enabled", Kauri.INSTANCE).get();
        settings.executable = new ConfigDefault<>(info.executable(),
                path + ".executable", Kauri.INSTANCE).get();
        settings.cancellable = new ConfigDefault<>(info.cancellable(),
                path + ".cancellable", Kauri.INSTANCE).get();

        final String spath = path + ".settings.";
        checkClass.getFields(field -> Modifier.isStatic(field.getModifiers())
                && field.isAnnotationPresent(Setting.class))
                .forEach(field -> {
                    Setting setting = field.getAnnotation(Setting.class);

                    MiscUtils.printToConsole("Found setting " + setting.name() + "! Processing...");
                    field.set(null, new ConfigDefault<>(field.get(null),
                            spath + setting.name(), Kauri.INSTANCE).get());
                });

        checkSettings.put(checkClass, settings);
        checkClasses.put(checkClass, info);
        MiscUtils.printToConsole("Registered check " + info.name());
    }

    public void flag(String information, Object... variables) {
        flag(false, information, variables);
    }

    public void flag(boolean devAlerts, String information, Object... variables) {
        flag(devAlerts, 100000000, information, variables);
    }

    public void flag(int resetVLTime, String information, Object... variables) {
        flag(false, resetVLTime, information, variables);
    }

    protected long lastFlagRun = 0L;

    public void flag(boolean devAlerts, int resetVLTime, String information, Object... variables) {
        if(Atlas.getInstance().getBungeeManager().isBungee() && !data.atlasBungeeInstalled) {
            Bukkit.getLogger().log(Level.SEVERE, data.getPlayer().getName() + " would have flagged but " +
                    "AtlasBungee is not installed on your BungeeCord. Please download the appropriate version for" +
                    " Atlas v"+ Atlas.getInstance().getDescription().getVersion()
                    + "on: https://github.com/funkemunky/Atlas/releases");
            return;
        }
        Kauri.INSTANCE.executor.execute(() -> {
            if(Kauri.INSTANCE.getTps() < 18)
                vl = 0;

            if(KauriAPI.INSTANCE.exemptHandler.isExempt(data.uuid, checkType)) return;
            if(System.currentTimeMillis() - lastFlagRun < 50L) return;
            lastFlagRun = System.currentTimeMillis();

            final String finalInformation = String.format(information, variables);
            KauriFlagEvent event = new KauriFlagEvent(data.getPlayer(), this, finalInformation);

            Atlas.getInstance().getEventManager().callEvent(event);

            if(event.isCancelled()) return;

            if(cancellable && cancelMode != null && vl > vlToFlag) {
                KauriCancelEvent cancelEvent = new KauriCancelEvent(data.getPlayer(), cancelMode);

                Atlas.getInstance().getEventManager().callEvent(cancelEvent);
                if(!cancelEvent.isCancelled()) {
                    switch(cancelEvent.getCancelType()) {
                        case ATTACK: {
                            for(int i = 0 ; i < 2 ; i++) {
                                synchronized (data.typesToCancel) {
                                    data.typesToCancel.add(cancelMode);
                                }
                            }
                            break;
                        }
                        case INVENTORY: {
                            TinyProtocolHandler.sendPacket(data.getPlayer(),
                                    new WrappedOutCloseWindowPacket(data.playerInfo.inventoryId));
                            break;
                        }
                        default: {
                            synchronized (data.typesToCancel) {
                                data.typesToCancel.add(cancelMode);
                            }
                            break;
                        }
                    }
                }
            }

            boolean dev = devAlerts || (developer || vl <= vlToFlag) || Kauri.INSTANCE.getTps() < 18;
            if(lastAlert.isPassed(resetVLTime)) vl = 0;
            final String info = finalInformation
                    .replace("%p", String.valueOf(data.lagInfo.transPing))
                    .replace("%t", String.valueOf(MathUtils.round(Kauri.INSTANCE.getTps(), 2)));
            if (Kauri.INSTANCE.lastTickLag.isPassed()
                    && System.currentTimeMillis() - Kauri.INSTANCE.lastTick < 100L) {
                if(vl > 0) Kauri.INSTANCE.loggerManager.addLog(data, this, info);

                if (lastAlert.isPassed(MathUtils.millisToTicks(Config.alertsDelay))) {
                    //Sending Discord webhook alert
                    if(DiscordAPI.INSTANCE != null)
                        DiscordAPI.INSTANCE.sendFlag(data.getPlayer(), this, dev, vl);
                    List<TextComponent> components = new ArrayList<>();

                    if(dev) {
                        components.add(new TextComponent(createTxt("&8[&cDev&8] ")));
                    }
                    val text = createTxt(Kauri.INSTANCE.msgHandler.getLanguage().msg("cheat-alert",
                            "&8[&6&lKauri&8] &f%player% &7flagged &f%check%" +
                                    " &8(&ex%sl%&8) %experimental%"), info);

                    text.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] {
                            createTxt(Kauri.INSTANCE.msgHandler.getLanguage().msg("cheat-alert-hover",
                                    "&eDescription&8: &f%desc%" +
                                            "\n&eInfo: &f%info%\n&r\n&7&oClick to teleport to player."), info)}));
                    text.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            formatAlert("/" + Config.alertCommand, info)));

                    components.add(text);

                    TextComponent[] toSend = components.toArray(new TextComponent[0]);

                    if(Config.testMode && (dev ? !data.devAlerts : !data.alerts))
                        data.getPlayer().spigot().sendMessage(toSend);

                    if(Config.alertsConsole) MiscUtils.printToConsole(new TextComponent(toSend).toPlainText());
                    if(!dev)
                        Kauri.INSTANCE.dataManager.hasAlerts
                                .forEach(data -> data.getPlayer().spigot().sendMessage(toSend));
                    else Kauri.INSTANCE.dataManager.devAlerts
                            .forEach(data -> data.getPlayer().spigot().sendMessage(toSend));
                    lastAlert.reset();
                }

                punish();

                if (Config.bungeeAlerts) {
                    try {
                        Atlas.getInstance().getBungeeManager()
                                .sendObjects("override", data.getPlayer().getUniqueId(), name,
                                        MathUtils.round(vl, 2), info);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private TextComponent createTxt(String txt) {
        return new TextComponent(formatAlert(Color.translate(txt), ""));
    }
    private TextComponent createTxt(String txt, String info) {
        return new TextComponent(formatAlert(Color.translate(txt), info));
    }

    public boolean isPosition(WrappedInFlyingPacket packet) {
        return packet.isPos() && (data.playerInfo.deltaXZ > 0 || data.playerInfo.deltaY != 0);
    }

    public void fixMovementBugs() {
        BukkitAPI.INSTANCE.setGliding(data.getPlayer(), false);
    }

    private String formatAlert(String toFormat, String info) {
        return Color.translate(toFormat.replace("%desc%", String.join("\n",
                MiscUtils
                        .splitIntoLine(description, 20)))
                .replace("%player%", data.getPlayer().getName())
                .replace("%check%", name)
                .replace("%info%", info)
                .replace("%sl%", String.valueOf(MathUtils.round(vl, 1)))
                .replace("%experimental%", developer ? "&c(Experimental)" : ""));
    }

    public void punish() {
        if(Atlas.getInstance().getBungeeManager().isBungee() && !data.atlasBungeeInstalled) {
            Bukkit.getLogger().log(Level.SEVERE, data.getPlayer().getName() + " would have been punished but " +
                    "AtlasBungee is not installed on your BungeeCord. Please download the appropriate version for" +
                    " Atlas v"+ Atlas.getInstance().getDescription().getVersion()
                    + "on: https://github.com/funkemunky/Atlas/releases");
            return;
        }
        if(developer || punishVl == -1 || vl <= punishVl
                || System.currentTimeMillis() - Kauri.INSTANCE.lastTick > 200L) return;

        DiscordAPI.INSTANCE.sendBan(data.getPlayer(),  this, banExempt);

        vl = 0;

        if(!executable || banExempt) return;

        KauriPunishEvent punishEvent = new KauriPunishEvent(data.getPlayer(), this);

        Atlas.getInstance().getEventManager().callEvent(punishEvent);

        if(!punishEvent.isCancelled()) {
            Kauri.INSTANCE.loggerManager.addPunishment(data, this);
            if(!data.banned) {
                if(!Config.broadcastMessage.equalsIgnoreCase("off")) {
                    if (!Config.bungeeBroadcast) {
                        RunUtils.task(() -> {
                            if (!Config.broadcastMessage.equalsIgnoreCase("off")) {
                                Bukkit.broadcastMessage(Color.translate(Config.broadcastMessage
                                        .replace("%name%", data.getPlayer().getName())
                                        .replace("%check%", getName())));
                            }
                        }, Kauri.INSTANCE);
                    } else {
                        BungeeAPI.broadcastMessage(Color.translate(Config.broadcastMessage
                                .replace("%name%", data.getPlayer().getName())).replace("%check%", getName()));
                    }
                }
                if(!Config.bungeePunishments) {
                    RunUtils.task(() -> {
                        ConsoleCommandSender sender = Bukkit.getConsoleSender();
                        Config.punishCommands.
                                forEach(cmd -> Bukkit.dispatchCommand(
                                        sender,
                                        cmd.replace("%name%", data.getPlayer().getName())));
                        vl = 0;
                    }, Kauri.INSTANCE);
                } else {
                    Config.punishCommands.
                            forEach(cmd -> BungeeAPI.sendCommand(cmd.replace("%name%", data.getPlayer().getName())));
                }
                data.banned = true;
            }
        }
    }

    public void debug(String information, Object... variables) {
        if(Kauri.INSTANCE.dataManager.debugging.size() == 0) return;

        final String finalInformation = String.format(information, variables);
        Kauri.INSTANCE.dataManager.debugging.stream()
                .filter(data -> data.debugged.equals(this.data.uuid) && data.debugging.equalsIgnoreCase(name))
                .forEach(data -> data.getPlayer()
                        .sendMessage(Color.translate("&8[&c&lDEBUG&8] &7" + finalInformation)));
    }

    /** Player utils **/

    public void kickPlayer(String reason) {
        Bukkit.getLogger().log(Level.INFO, "Kauri is kicking player" + data.getPlayer().getName() + " for: \""
                + reason + "\"");
        RunUtils.task(() -> data.getPlayer().kickPlayer(Color.translate(reason)));
    }

    /** Static members **/

    public static void registerChecks() {

    }

    public static boolean isCheck(String name) {
        return checkClasses.values().stream().anyMatch(val -> val.name().equalsIgnoreCase(name));
    }

    public static CheckInfo getCheckInfo(String name) {
        return checkClasses.values().stream().filter(val -> val.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public static CheckSettings getCheckSettings(String name) {
        return checkSettings.values().stream().filter(val -> val.name.equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public void closePlayerChannel() {
        val channel = getChannel.invoke(TinyProtocolHandler.getInstance(), data.getPlayer());

        val wrapped = new WrappedClass(channel.getClass());

        wrapped.getMethod("close").invoke(channel);
    }
}
