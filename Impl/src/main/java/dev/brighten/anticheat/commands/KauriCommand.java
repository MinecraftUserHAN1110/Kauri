package dev.brighten.anticheat.commands;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.commands.ancmd.Command;
import cc.funkemunky.api.commands.ancmd.CommandAdapter;
import cc.funkemunky.api.profiling.ResultsType;
import cc.funkemunky.api.reflections.types.WrappedClass;
import cc.funkemunky.api.utils.Color;
import cc.funkemunky.api.utils.Init;
import cc.funkemunky.api.utils.Materials;
import cc.funkemunky.api.utils.XMaterial;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.BukkitCommandCompletions;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import dev.brighten.anticheat.Kauri;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Config;
import dev.brighten.anticheat.data.ObjectData;
import dev.brighten.anticheat.utils.MiscUtils;
import dev.brighten.anticheat.utils.Pastebin;
import dev.brighten.anticheat.utils.StringUtils;
import lombok.val;
import net.minecraft.server.v1_7_R4.CommandSeed;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Init
@CommandAlias("kauri|anticheat")
@CommandPermission("kauri.command")
public class KauriCommand extends BaseCommand {

    private static List<Player> testers = new ArrayList<>();

    public KauriCommand() {
        //Registering completions
        BukkitCommandCompletions cc = (BukkitCommandCompletions) Kauri.INSTANCE.commandManager
                .getCommandCompletions();

        cc.registerCompletion("checks", (c) ->
            Check.checkClasses.values().stream().map(CheckInfo::name).collect(Collectors.toList()));
        cc.registerCompletion("materials", (c) -> Arrays.stream(Material.values()).map(Enum::name)
                .collect(Collectors.toList()));
    }

    @HelpCommand
    @Syntax("")
    public static void onHelp(CommandSender sender, CommandHelp help) {
        sender.sendMessage(cc.funkemunky.api.utils.MiscUtils.line(Color.Dark_Gray));
        help.showHelp();
        sender.sendMessage(cc.funkemunky.api.utils.MiscUtils.line(Color.Dark_Gray));
    }
    
    @Subcommand("test")
    @Syntax("")
    @CommandPermission("kauri.command.test")
    @Description("Toggle test debug alerts")
    public void onTest(Player player) {
        if(testers.contains(player)) {
            if(testers.remove(player)) {
                player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage()
                        .msg("tester-remove-success", "&cRemoved you from test messaging for developers."));
            } else player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage()
                    .msg("tester-remove-error", "&cThere was an error removing you from test messaging."));
        } else {
            testers.add(player);
            player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage()
                    .msg("testers-added", "&aYou have been added to the test messaging list for developers."));
        }
    }

    @Subcommand("alerts")
    @Syntax("")
    @CommandPermission("kauri.command.alerts")
    @Description("Toggle your cheat alerts")
    public void onCommand(Player player) {
        ObjectData data = Kauri.INSTANCE.dataManager.getData(player);

        if(data != null) {
            if(data.alerts = !data.alerts) {
                Kauri.INSTANCE.dataManager.hasAlerts.add(data);
                player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage().msg("alerts-on",
                        "&aYou are now viewing cheat alerts."));
            } else {
                Kauri.INSTANCE.dataManager.hasAlerts.remove(data);
                player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage().msg("alerts-none",
                        "&cYou are no longer viewing cheat alerts."));
            }
        } else player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage().msg("data-error",
                "&cThere was an error trying to find your data."));
    }

    @Subcommand("alerts dev")
    @Syntax("")
    @CommandPermission("kauri.command.alerts.dev")
    @Description("Toggle developer cheat alerts")
    public void onDevAlerts(Player player) {
        ObjectData data = Kauri.INSTANCE.dataManager.getData(player);

        if(data != null) {
            if(data.devAlerts = !data.devAlerts) {
                Kauri.INSTANCE.dataManager.devAlerts.add(data);
                player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage().msg("dev-alerts-on",
                        "&aYou are now viewing developer cheat alerts."));
            } else {
                Kauri.INSTANCE.dataManager.devAlerts.remove(data);
                player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage().msg("dev-alerts-none",
                        "&cYou are no longer viewing developer cheat alerts."));
            }
        } else player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage().msg("data-error",
                "&cThere was an error trying to find your data."));
    }

    @Subcommand("debug")
    @Syntax("<check> [player]")
    @CommandPermission("kauri.command.debug")
    @Description("debug a check")
    @CommandCompletion("@checks|none @players")
    public void onCommand(Player player, @Single String check, OnlinePlayer target) {
        ObjectData data = Kauri.INSTANCE.dataManager.getData(player);

        if(data == null) {
            player.sendMessage(Color.Red + "There was an error trying to find your data object.");
            return;
        }

        if(check.equalsIgnoreCase("none")) {

            data.debugging = null;
            data.debugged = null;
            Kauri.INSTANCE.dataManager.debugging.remove(data);

            Kauri.INSTANCE.dataManager.dataMap.values().stream()
                    .filter(d -> d.boxDebuggers.contains(player))
                    .forEach(d -> d.boxDebuggers.remove(player));
            player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage()
                    .msg("debug-off", "&aTurned off your debugging."));
        } else if(target != null) {
            if(check.equalsIgnoreCase("sniff")) {
                val targetData = Kauri.INSTANCE.dataManager.getData(target.getPlayer());
                if(!targetData.sniffing) {
                    player.sendMessage("Sniffing + " + target.getPlayer().getName());
                    targetData.sniffing = true;
                } else {
                    player.sendMessage("Stopped sniff. Pasting...");
                    targetData.sniffing = false;
                    try {
                        player.sendMessage("Paste: " + Pastebin.makePaste(
                                String.join("\n", targetData.sniffedPackets.toArray(new String[0])),
                                "Sniffed from " + target.getPlayer().getName(), Pastebin.Privacy.UNLISTED));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    targetData.sniffedPackets.clear();
                }
            } else {
                if(Check.isCheck(check.replace("_", " "))) {
                    data.debugging = check.replace("_", " ");
                    data.debugged = target.getPlayer().getUniqueId();

                    player.sendMessage(Color.Green + "You are now debugging " + data.debugging
                            + " on target " + target.getPlayer().getName() + "!");
                } else player
                        .sendMessage(Color.Red + "The argument input \"" + check + "\" is not a check.");
            }
        } else player.sendMessage(Color.Red + "Could not find a target to debug.");
    }

    @Subcommand("block")
    @Description("Check the material type information")
    @CommandCompletion("@materials")
    @Syntax("block [id,name]")
    @CommandPermission("kauri.command.block")
    public void onBlock(CommandSender sender, @Optional String block) {
        Material material;
        if(block != null) {
            if(MiscUtils.isInteger(block)) {
                material = Material.getMaterial(Integer.parseInt(block));
            } else material = Arrays.stream(Material.values())
                    .filter(mat -> mat.name().equalsIgnoreCase(block)).findFirst()
                    .orElse((XMaterial.AIR.parseMaterial()));
        } else if(sender instanceof Player) {
            Player player = (Player) sender;
            if(player.getItemInHand() != null) {
                material = player.getItemInHand().getType();
            } else {
                sender.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage()
                        .msg("block-no-item-in-hand",
                                "&cPlease hold an item in your hand or use the proper arguments."));
                return;
            }
        } else {
            sender.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage()
                    .msg("error-invalid-args", "&cInvalid arguments! Check the help page."));
            return;
        }

        if(material != null) {
            sender.sendMessage(cc.funkemunky.api.utils.MiscUtils.line(Color.Dark_Gray));
            sender.sendMessage(Color.Gold + Color.Bold + material.name() + Color.Gray + ":");
            sender.sendMessage("");
            sender.sendMessage(Color.translate("&eXMaterial: &f" + XMaterial
                    .requestXMaterial(material.name(), (byte)0)));
            sender.sendMessage(Color.translate("&eBitmask&7: &f" + Materials.getBitmask(material)));
            WrappedClass wrapped = new WrappedClass(Materials.class);

            wrapped.getFields(field -> field.getType().equals(int.class) && Modifier.isStatic(field.getModifiers()))
                    .stream().sorted(Comparator.comparing(field -> field.getField().getName()))
                    .forEach(field -> {
                        int bitMask = field.get(null);

                        boolean flag = Materials.checkFlag(material, bitMask);
                        sender.sendMessage(Color.translate("&e" + field.getField().getName()
                                + "&7: " + (flag ? "&a" : "&c") + flag));
                    });
            sender.sendMessage(cc.funkemunky.api.utils.MiscUtils.line(Color.Dark_Gray));
        } else sender.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage()
                .msg("block-no-material", "&cNo material was found. Please check your arguments."));
    }

    @Subcommand("debug box")
    @CommandPermission("kauri.command.debug")
    @Description("debug the collisions of players")
    @Syntax("[player]")
    @CommandCompletion("@players")
    public void onDebugBox(Player player, @Optional OnlinePlayer target) {
        String[] debuggingPlayers;
        ObjectData.debugBoxes(false, player);
        if(target == null) {
            ObjectData.debugBoxes(true, player, player.getUniqueId());
            debuggingPlayers = new String[] {player.getName()};
        } else {
            debuggingPlayers = new String[] {target.getPlayer().getName()};
            ObjectData.debugBoxes(true, player, target.getPlayer().getUniqueId());
        }

        player.sendMessage(Kauri.INSTANCE.msgHandler.getLanguage()
                .msg("debug-boxes", "&aYou are now debugging the collisions of %players%.")
                .replace("%players%", String.join(", ", debuggingPlayers)));

    }

    @Subcommand("delay")
    @Description("change the delay between alerts")
    @Syntax("[ms]")
    @CommandPermission("kauri.command.delay")
    public void onCommand(CommandSender sender, long delay) {
        sender.sendMessage(Color.Gray + "Setting delay to "
                + Color.White + delay + "ms" + Color.Gray + "...");

        Config.alertsDelay = delay;
        Kauri.INSTANCE.getConfig().set("alerts.delay", delay);
        Kauri.INSTANCE.saveConfig();
        sender.sendMessage(Color.Green + "Delay set!");
    }

    @Subcommand("forceban")
    @Description("force ban a player")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @CommandPermission("kauri.command.forceban")
    public void onForceBan(CommandSender sender, OnlinePlayer target) {
        ObjectData data = Kauri.INSTANCE.dataManager.getData(target.getPlayer().getPlayer());

        MiscUtils.forceBanPlayer(data);
        sender.sendMessage(Color.Green + "Force banned the player.");
    }

    private static String getMsg(String name, String def) {
        return Kauri.INSTANCE.msgHandler.getLanguage().msg("command.lag." + name, def);
    }

    @Subcommand("lag")
    @Description("view important lag information")
    @Syntax("")
    @CommandPermission("kauri.command.lag")
    public void onCommand(CommandSender sender) {
        StringUtils.Messages.LINE.send(sender);
        MiscUtils.sendMessage(sender, getMsg("main.title",
                Color.Gold + Color.Bold + "Server Lag Information"));
        sender.sendMessage("");
        MiscUtils.sendMessage(sender, getMsg("main.tps", "&eTPS&8: &f%.2f%%"),
                Kauri.INSTANCE.getTps());
        AtomicLong chunkCount = new AtomicLong(0);
        Bukkit.getWorlds().forEach(world -> chunkCount.addAndGet(world.getLoadedChunks().length));
        MiscUtils.sendMessage(sender, getMsg("main.chunks", "&eChunks&8: &f%s"), chunkCount.get());
        MiscUtils.sendMessage(sender, getMsg("main.memory",
                "&eMemory &7(&f&oFree&7&o/&f&oTotal&7&o/&f&oAllocated&7)&8: &f%.2fGB&7/&f%.2fGB&7/&f%.2fGB"),
                Runtime.getRuntime().freeMemory() / 1E9,
                Runtime.getRuntime().totalMemory() / 1E9, Runtime.getRuntime().maxMemory() / 1E9);
        val results = Kauri.INSTANCE.profiler.results(ResultsType.TOTAL);
        MiscUtils.sendMessage(sender, getMsg("main.cpu-usage", "&eKauri CPU Usage&8: &f%.5f%%"),
                results.keySet().stream()
                        .filter(key -> !key.contains("check:"))
                        .mapToDouble(key -> results.get(key).two / 1000000D)
                        .filter(val -> !Double.isNaN(val) && !Double.isInfinite(val))
                        .sum() / 50D * 100);
        StringUtils.Messages.LINE.send(sender);
    }

    @Subcommand("lag gc")
    @CommandPermission("kauri.command.lag.gc")
    @Description("run a java garbage collector")
    public void onLagGc(CommandSender sender) {
        sender.sendMessage(getMsg("start-gc", "&7Starting garbage collector..."));

        long stamp = System.nanoTime();
        double time;
        Runtime.getRuntime().gc();
        time = (System.nanoTime() - stamp) / 1E6D;

        StringUtils.Messages.GC_COMPLETE.send(sender, time);
    }

    @Subcommand("lag player")
    @Description("view a player's connection info")
    @CommandPermission("kauri.command.lag.player")
    public void onLagPlayer(CommandSender sender, OnlinePlayer target) {
        ObjectData data = Kauri.INSTANCE.dataManager.getData(target.getPlayer());

        if(data != null) {
            StringUtils.Messages.LINE.send(sender);
            StringUtils.sendMessage(sender, Color.Gold + Color.Bold + target.getPlayer().getName()
                    + "'s Lag Information");
            StringUtils.sendMessage(sender, "");
            StringUtils.sendMessage(sender, "&ePing&7: &f"
                    + data.lagInfo.ping + "ms&7/&f" + data.lagInfo.transPing + " tick");
            StringUtils.sendMessage(sender, "&eLast Skip&7: &f" + data.lagInfo.lastPacketDrop.getPassed());
            StringUtils.sendMessage(sender, "&eLagging&7: &f" + data.lagInfo.lagging);
            StringUtils.Messages.LINE.send(sender);
        } else StringUtils.Messages.DATA_ERROR.send(sender);
    }

   public static List<Player> getTesters() {
       testers.stream().filter(Objects::isNull).forEach(testers::remove);

       return testers;
   }
}
