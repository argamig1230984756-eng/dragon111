package dev.voiddragon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VoidDragonPlugin extends JavaPlugin {

    private final Map<UUID, DragonFight> fights = new HashMap<>();
    private ModelHook modelHook;
    private NamespacedKey wraithKey;
    private NamespacedKey minionKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wraithKey = new NamespacedKey(this, "wraith_crystal");
        minionKey = new NamespacedKey(this, "void_minion");
        if (modelHook == null) modelHook = new LoggingModelHook(getLogger());
        getServer().getPluginManager().registerEvents(new FightListener(this), this);

        // adopt dragons that already exist (server restart mid-fight)
        Bukkit.getScheduler().runTask(this, () -> {
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() != World.Environment.THE_END) continue;
                for (EnderDragon d : w.getEntitiesByClass(EnderDragon.class)) adopt(d);
            }
        });
    }

    @Override
    public void onDisable() {
        for (DragonFight f : List.copyOf(fights.values())) f.end();
        fights.clear();
    }

    public ModelHook modelHook() { return modelHook; }

    /** Call this from your own plugin (or onLoad) to plug in BetterModel / ModelEngine. */
    public void setModelHook(ModelHook hook) { this.modelHook = hook; }

    NamespacedKey wraithKey() { return wraithKey; }
    NamespacedKey minionKey() { return minionKey; }
    Map<UUID, DragonFight> fights() { return fights; }

    DragonFight adopt(EnderDragon dragon) {
        return fights.computeIfAbsent(dragon.getUniqueId(), id -> new DragonFight(this, dragon));
    }

    void forget(UUID dragonId) { fights.remove(dragonId); }

    private DragonFight nearestFight(CommandSender sender) {
        DragonFight best = null;
        double bestD = Double.MAX_VALUE;
        for (DragonFight f : fights.values()) {
            if (sender instanceof Player pl && pl.getWorld().equals(f.dragonLocation().getWorld())) {
                double d = pl.getLocation().distanceSquared(f.dragonLocation());
                if (d < bestD) { bestD = d; best = f; }
            } else if (best == null) {
                best = f;
            }
        }
        return best;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        World end = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.THE_END).findFirst().orElse(null);
        switch (sub) {
            case "start" -> {
                if (end == null) { sender.sendMessage("No End world found."); return true; }
                if (!fights.isEmpty()) {
                    sender.sendMessage("A fight is already running. Use /voiddragon reset and kill the old dragon first.");
                    return true;
                }
                EnderDragon d = end.spawn(new Location(end, 0.5, 100, 0.5), EnderDragon.class);
                d.setPhase(EnderDragon.Phase.CIRCLING);
                adopt(d);
                sender.sendMessage("Void Dragon spawned. First attack in ~10 seconds.");
            }
            case "phase2" -> {
                DragonFight f = nearestFight(sender);
                if (f == null) { sender.sendMessage("No active fight."); return true; }
                f.startPhase2();
                sender.sendMessage("Phase 2 started.");
            }
            case "phase3" -> {
                DragonFight f = nearestFight(sender);
                if (f == null) { sender.sendMessage("No active fight."); return true; }
                if (f.tier() != 1) { sender.sendMessage("Phase 3 can only start from phase 2 (use /voiddragon phase2 first and wait 6s)."); return true; }
                f.startPhase3();
                sender.sendMessage("Phase 3 started.");
            }
            case "attack" -> {
                DragonFight f = nearestFight(sender);
                if (f == null) { sender.sendMessage("No active fight."); return true; }
                if (args.length < 2 || !f.forceAttack(args[1])) {
                    sender.sendMessage("/voiddragon attack <fireballs|lightning|beam|shockwave|gravity|meteors|dive_bomb|tail|breath|minions>");
                } else {
                    sender.sendMessage("Attack launched.");
                }
            }
            case "status" -> {
                if (fights.isEmpty()) { sender.sendMessage("No active fight."); return true; }
                for (DragonFight f : fights.values()) sender.sendMessage("Fight: " + f.stageName());
            }
            case "reset" -> {
                for (DragonFight f : List.copyOf(fights.values())) f.end();
                sender.sendMessage("Fight state cleared (dragon left alone).");
            }
            default -> sender.sendMessage("/voiddragon <start|phase2|phase3|attack <name>|status|reset>");
        }
        return true;
    }
}
