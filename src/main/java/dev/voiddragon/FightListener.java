package dev.voiddragon;

import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

final class FightListener implements Listener {
    private final VoidDragonPlugin plugin;

    FightListener(VoidDragonPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof EnderDragon d)) return;
        if (d.getWorld().getEnvironment() != World.Environment.THE_END) return;
        if (!plugin.getConfig().getBoolean("dragon.auto-adopt", true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> { if (d.isValid()) plugin.adopt(d); });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        if (victim instanceof ComplexEntityPart part) victim = part.getParent();

        if (victim instanceof EnderDragon d) {
            DragonFight f = plugin.fights().get(d.getUniqueId());
            if (f != null) f.onDragonDamage(e);
            return;
        }
        if (victim instanceof EnderCrystal c) {
            Entity damager = e instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
            for (DragonFight f : plugin.fights().values()) {
                if (f.ownsCrystal(c.getUniqueId()) && f.onCrystalDamage(c, damager)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof EnderDragon d) {
            DragonFight f = plugin.fights().get(d.getUniqueId());
            if (f != null) f.end();
            return;
        }
        if (e.getEntity() instanceof Vex mv
                && mv.getPersistentDataContainer().has(plugin.minionKey(), PersistentDataType.BYTE)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            mv.getWorld().spawnParticle(org.bukkit.Particle.POOF, mv.getLocation(), 12, 0.3, 0.3, 0.3, 0.05);
            return;
        }
        if (e.getEntity() instanceof Vex v) {
            String raw = v.getPersistentDataContainer().get(plugin.wraithKey(), PersistentDataType.STRING);
            if (raw == null) return;
            e.getDrops().clear();
            e.setDroppedExp(0);
            plugin.modelHook().detach(v);
            UUID crystalId = UUID.fromString(raw);
            for (DragonFight f : plugin.fights().values()) f.onWraithDeath(crystalId, v.getUniqueId());
        }
    }
}
