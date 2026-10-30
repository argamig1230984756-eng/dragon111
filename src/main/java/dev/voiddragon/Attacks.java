package dev.voiddragon;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vex;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Every active attack of the dragon. Attacks are additive: phase 1 uses tier 0 attacks,
 * phase 2 adds tier 1, phase 3 adds tier 2. All of them are telegraphed so they can be dodged.
 */
final class Attacks {

    enum Type {
        FIREBALLS(0),   // dragon spits dragon-breath fireballs at players
        LIGHTNING(0),   // marked spots get struck after a short warning
        BEAM(1),        // a void laser sweeps across the ground
        SHOCKWAVE(1),   // expanding ring on the ground - jump it
        GRAVITY(2),     // black hole pulls everyone in, then explodes
        METEORS(2),     // rain of falling strikes
        DIVE_BOMB(0),   // dragon locks on, then plunges onto the marked spot
        TAIL(0),        // tail whip: telegraphed sector, the blade sweeps across it
        BREATH(0),      // precise breath cone that tracks (slowly) and burns along its line
        MINIONS(0);     // summons Void Minions

        final int minTier;
        Type(int minTier) { this.minTier = minTier; }
    }

    private static final Color RED = Color.fromRGB(255, 40, 90);
    private static final Color ORANGE = Color.fromRGB(255, 150, 30);
    private static final Color PURPLE = Color.fromRGB(190, 70, 255);
    private static final Color YELLOW = Color.fromRGB(255, 240, 80);

    private final VoidDragonPlugin plugin;
    private final EnderDragon dragon;
    private final World world;
    private final Random rnd;
    private final Set<UUID> minionIds = new HashSet<>();

    Attacks(VoidDragonPlugin plugin, EnderDragon dragon, World world, Random rnd) {
        this.plugin = plugin;
        this.dragon = dragon;
        this.world = world;
        this.rnd = rnd;
    }

    void run(Type type, int tier) {
        switch (type) {
            case FIREBALLS -> fireballs(tier);
            case LIGHTNING -> lightning(tier);
            case BEAM -> beam(tier);
            case SHOCKWAVE -> shockwave(tier);
            case GRAVITY -> gravity(tier);
            case METEORS -> meteors(tier);
            case DIVE_BOMB -> diveBomb(tier);
            case TAIL -> tail(tier);
            case BREATH -> breath(tier);
            case MINIONS -> minions(tier);
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean hittable(Player p) {
        return !p.isDead() && p.getWorld().equals(world)
                && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE;
    }

    private List<Player> targets() {
        List<Player> out = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (hittable(p) && p.getLocation().distanceSquared(dragon.getLocation()) < 160 * 160) out.add(p);
        }
        return out;
    }

    private Player randomTarget() {
        List<Player> ts = targets();
        return ts.isEmpty() ? null : ts.get(rnd.nextInt(ts.size()));
    }

    private void dust(Location at, Color color, float size) {
        world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, size));
    }

    private void ring(Location center, double r, int points, Color color) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            dust(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), color, 1.3f);
        }
    }

    /** A circle appears under the target spot, closes in, then blasts everyone inside. */
    private void strike(Location spot, double radius, double damage, int telegraph, Color color, boolean fromSky) {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!dragon.isValid()) { cancel(); return; }
                double progress = t / (double) telegraph;
                ring(spot.clone().add(0, 0.2, 0), radius * (1.0 + (1.0 - progress)), 24, color);
                ring(spot.clone().add(0, 0.2, 0), radius, 24, color);
                if (fromSky) {
                    world.spawnParticle(Particle.FLAME, spot.clone().add(0, 14 * (1 - progress), 0), 6, 0.3, 0.3, 0.3, 0.02);
                }
                if (t % 8 == 0) {
                    world.playSound(spot, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.2f, 0.5f + (float) progress);
                }
                if (++t >= telegraph) {
                    cancel();
                    world.strikeLightningEffect(spot);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, spot, 1);
                    world.spawnParticle(Particle.REVERSE_PORTAL, spot, 60, radius / 2, 1, radius / 2, 0.3);
                    world.playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 3f, 0.8f);
                    for (Player p : world.getPlayers()) {
                        if (!hittable(p)) continue;
                        Location pl = p.getLocation();
                        double dx = pl.getX() - spot.getX(), dz = pl.getZ() - spot.getZ();
                        if (dx * dx + dz * dz <= radius * radius && Math.abs(pl.getY() - spot.getY()) <= 4) {
                            hurt(p, damage);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ FIREBALLS

    private void fireballs(int tier) {
        if (targets().isEmpty()) return;
        final int shots = 4 + tier * 2;
        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 6f, 1.2f);
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (!dragon.isValid()) { cancel(); return; }
                Player p = randomTarget();
                if (p != null) {
                    Location from = dragon.getLocation().add(0, 1, 0);
                    Vector dir = aim(p, 8, 0).add(0, 1, 0).toVector().subtract(from.toVector());   // leads the target
                    if (dir.lengthSquared() > 1) {
                        dir.normalize();
                        from.add(dir.clone().multiply(7));   // spawn clear of the dragon's own hitbox
                        world.spawn(from, DragonFireball.class, fb -> {
                            fb.setShooter(dragon);
                            fb.setDirection(dir);
                        });
                        world.playSound(from, Sound.ENTITY_ENDER_DRAGON_SHOOT, SoundCategory.HOSTILE, 4f, 1f);
                        world.spawnParticle(Particle.DRAGON_BREATH, from, 25, 0.5, 0.5, 0.5, 0.05);
                    }
                }
                if (++i >= shots) cancel();
            }
        }.runTaskTimer(plugin, 0L, 12L);
    }

    // ------------------------------------------------------------------ LIGHTNING

    private void lightning(int tier) {
        List<Player> ts = targets();
        if (ts.isEmpty()) return;
        world.playSound(dragon.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 6f, 0.7f);
        int strikes = 4 + tier * 3;
        for (int i = 0; i < strikes; i++) {
            final int delay = i * 6;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!dragon.isValid()) return;
                Player p = randomTarget();
                if (p == null) return;
                Location spot = aim(p, 10, 1.2);
                strike(spot, 2.5, 8.0, 30, YELLOW, true);
            }, delay);
        }
    }

    // ------------------------------------------------------------------ BEAM

    private void beam(int tier) {
        Player target = randomTarget();
        if (target == null) return;
        final Location base = target.getLocation().clone();
        double ang = rnd.nextDouble() * Math.PI * 2;
        Vector side = new Vector(Math.cos(ang), 0, Math.sin(ang));
        final Location start = base.clone().add(side.clone().multiply(-15));
        final Location end = base.clone().add(side.clone().multiply(15));
        final int telegraph = 36;
        final int sweep = 56 - tier * 6;
        final double damage = 6.0 + tier * 1.5;

        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 6f, 0.6f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!dragon.isValid()) { cancel(); return; }
                Location from = dragon.getLocation().add(0, 1, 0);

                if (t < telegraph) {
                    // warning: show the sweep path on the ground
                    if (t % 3 == 0) {
                        for (int i = 0; i <= 30; i++) {
                            dust(start.clone().add(end.clone().subtract(start).toVector().multiply(i / 30.0)).add(0, 0.2, 0), RED, 1.2f);
                        }
                    }
                    if (t % 10 == 0) world.playSound(start, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2f, 0.5f + t / 40f);
                } else {
                    double progress = (t - telegraph) / (double) sweep;
                    Location pos = start.clone().add(end.clone().subtract(start).toVector().multiply(progress));
                    Vector line = pos.toVector().subtract(from.toVector());
                    double len = line.length();
                    if (len > 0.1) {
                        Vector step = line.clone().normalize().multiply(0.8);
                        Location cur = from.clone();
                        for (double d = 0; d < len; d += 0.8) {
                            dust(cur, PURPLE, 1.6f);
                            cur.add(step);
                        }
                    }
                    world.spawnParticle(Particle.END_ROD, pos, 8, 0.6, 0.2, 0.6, 0.08);
                    world.spawnParticle(Particle.REVERSE_PORTAL, pos, 20, 0.8, 0.2, 0.8, 0.3);
                    if (t % 3 == 0) world.playSound(pos, Sound.ENTITY_GENERIC_BURN, SoundCategory.HOSTILE, 1.5f, 0.6f);
                    if (t % 4 == 0) {
                        for (Player p : world.getPlayers()) {
                            if (!hittable(p)) continue;
                            double dist = Util.distToSegment(p.getLocation().add(0, 1, 0).toVector(),
                                    from.toVector(), pos.toVector());
                            if (dist < 1.8) {
                                p.setNoDamageTicks(0);
                                hurt(p, damage);
                            }
                        }
                    }
                }
                if (++t >= telegraph + sweep) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ SHOCKWAVE

    private void shockwave(int tier) {
        Player target = randomTarget();
        if (target == null) return;
        final Location center = target.getLocation().clone();
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 6f, 0.5f);
        sendWave(center, 0);
        if (tier >= 2) sendWave(center, 24);   // second wave in the last phase
    }

    private void sendWave(Location center, int delay) {
        new BukkitRunnable() {
            int t = -20 - delay;          // 1 second warning before it moves
            final Set<UUID> hit = new HashSet<>();
            @Override public void run() {
                if (!dragon.isValid()) { cancel(); return; }
                if (t < 0) {
                    if (t % 4 == 0) {
                        world.spawnParticle(Particle.REVERSE_PORTAL, center, 30, 1, 0.1, 1, 0.3);
                        ring(center.clone().add(0, 0.2, 0), 2.0, 16, ORANGE);
                    }
                    t++;
                    return;
                }
                double r = 1.5 + t * 0.55;
                ring(center.clone().add(0, 0.25, 0), r, (int) (r * 6), ORANGE);
                world.spawnParticle(Particle.EXPLOSION, center.clone().add(
                        Math.cos(t) * r, 0.3, Math.sin(t) * r), 1);
                if (t % 4 == 0) world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 0.8f, 1.5f);
                for (Player p : world.getPlayers()) {
                    if (!hittable(p) || hit.contains(p.getUniqueId())) continue;
                    Location pl = p.getLocation();
                    double dx = pl.getX() - center.getX(), dz = pl.getZ() - center.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    double height = pl.getY() - center.getY();
                    // players that are in the air (jumped) are not hit
                    if (Math.abs(dist - r) < 1.2 && height < 0.9 && height > -4) {
                        hit.add(p.getUniqueId());
                        hurt(p, 7.0);
                        Vector push = new Vector(dx, 0, dz);
                        if (push.lengthSquared() > 0.001) {
                            p.setVelocity(p.getVelocity().add(push.normalize().multiply(0.9).setY(0.55)));
                        }
                    }
                }
                if (++t > 40) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ GRAVITY WELL

    private void gravity(int tier) {
        Player target = randomTarget();
        if (target == null) return;
        final Location well = target.getLocation().clone().add(0, 4, 0);
        final int duration = 90;
        final double blastRadius = 6.0;

        world.playSound(well, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 6f, 0.4f);
        for (Player p : world.getPlayers()) {
            if (hittable(p)) p.sendActionBar(net.kyori.adventure.text.Component.text(
                    "A gravity well! Break away before it collapses!", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
        }

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!dragon.isValid()) { cancel(); return; }
                double progress = t / (double) duration;

                // swirling accretion disc
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.35 + i * (Math.PI * 2 / 3);
                    double r = 7 * (1 - progress) + 1;
                    Location p = well.clone().add(Math.cos(a) * r, Math.sin(a * 0.5) * 0.8, Math.sin(a) * r);
                    world.spawnParticle(Particle.PORTAL, p, 4, 0.1, 0.1, 0.1, 0.1);
                    dust(p, Color.fromRGB(20, 0, 40), 2.2f);
                }
                world.spawnParticle(Particle.SQUID_INK, well, 6, 0.4, 0.4, 0.4, 0.02);
                if (t % 10 == 0) world.playSound(well, Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.HOSTILE, 2f, 0.5f + (float) progress);

                // pull
                double pull = 0.05 + 0.07 * progress + tier * 0.01;
                for (Player p : world.getPlayers()) {
                    if (!hittable(p)) continue;
                    Vector to = well.toVector().subtract(p.getLocation().toVector());
                    double d = to.length();
                    if (d < 18 && d > 1.5) {
                        p.setVelocity(p.getVelocity().add(to.normalize().multiply(pull)));
                    }
                }

                if (++t >= duration) {
                    cancel();
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, well, 3, 1.5, 1.5, 1.5, 0);
                    world.spawnParticle(Particle.REVERSE_PORTAL, well, 150, 3, 3, 3, 0.5);
                    world.strikeLightningEffect(well);
                    world.playSound(well, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 5f, 0.5f);
                    for (Player p : world.getPlayers()) {
                        if (!hittable(p)) continue;
                        double d = p.getLocation().distance(well);
                        if (d <= blastRadius) {
                            hurt(p, 12.0);
                            Vector out = p.getLocation().toVector().subtract(well.toVector());
                            if (out.lengthSquared() > 0.001) {
                                p.setVelocity(out.normalize().multiply(1.3).setY(0.6));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ METEORS

    private void meteors(int tier) {
        if (targets().isEmpty()) return;
        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 6f, 0.8f);
        int count = 10 + tier * 2;
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!dragon.isValid()) return;
                Player p = randomTarget();
                if (p == null) return;
                Location spot = aim(p, 8, 2.5);
                strike(spot, 3.0, 9.0, 28, ORANGE, true);
            }, i * 5L);
        }
    }

    // ------------------------------------------------------------------ damage + precision

    /** Every attack goes through here so damage can be tuned in one place (attacks.damage-multiplier). */
    void hurt(Player p, double base) {
        double mul = plugin.getConfig().getDouble("attacks.damage-multiplier", 1.6);
        p.setNoDamageTicks(0);
        p.damage(base * mul, dragon);
    }

    /** Where the player WILL be in leadTicks ticks, plus a small spread (0 = exact). */
    private Location aim(Player p, double leadTicks, double spread) {
        Location l = p.getLocation().clone();
        Vector v = p.getVelocity();
        l.add(v.getX() * leadTicks, 0, v.getZ() * leadTicks);
        if (spread > 0) l.add((rnd.nextDouble() * 2 - 1) * spread, 0, (rnd.nextDouble() * 2 - 1) * spread);
        return l;
    }

    private void line(Location a, Location b, double step, Color color, float size) {
        Vector v = b.toVector().subtract(a.toVector());
        double len = v.length();
        if (len < 0.1) return;
        Vector s = v.clone().normalize().multiply(step);
        Location cur = a.clone();
        for (double d = 0; d < len; d += step) { dust(cur, color, size); cur.add(s); }
    }

    private static double angDiff(double a, double b) {
        return Math.atan2(Math.sin(a - b), Math.cos(a - b));
    }

    // ------------------------------------------------------------------ DIVE BOMB

    private void diveBomb(int tier) {
        final Player target = randomTarget();
        if (target == null) return;
        final int telegraph = Math.max(24, 44 - tier * 8);
        final int dive = 20;
        final double radius = 6.5 + tier;
        final double damage = 14.0 + tier * 3;
        final boolean flip = plugin.getConfig().getBoolean("attacks.flip-dragon-yaw", true);
        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 8f, 1.6f);
        dragon.setPhase(EnderDragon.Phase.HOVER);

        new BukkitRunnable() {
            int t = 0;
            Location spot = target.getLocation().clone();
            Location from = null;
            final Set<UUID> struck = new HashSet<>();
            @Override public void run() {
                if (!dragon.isValid()) { cancel(); return; }
                if (t < telegraph) {
                    // tracks the target, then LOCKS 12 ticks before the dive so it can be dodged
                    if (t < telegraph - 12 && hittable(target)) spot = aim(target, 6, 0);
                    double progress = t / (double) telegraph;
                    ring(spot.clone().add(0, 0.2, 0), radius, 32, RED);
                    ring(spot.clone().add(0, 0.2, 0), radius * (1 - progress), 24, ORANGE);
                    if (t % 2 == 0) line(dragon.getLocation(), spot, 1.5, RED, 1.2f);
                    if (t % 6 == 0) world.playSound(spot, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.5f + (float) progress);
                    t++;
                    return;
                }
                if (from == null) {
                    from = dragon.getLocation().clone();
                    world.playSound(from, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 8f, 0.6f);
                }
                double p = Math.min(1.0, (t - telegraph + 1) / (double) dive);
                Location goal = spot.clone().add(0, 2.5, 0);
                Vector delta = goal.toVector().subtract(from.toVector());
                Location pos = from.clone().add(delta.clone().multiply(p * p));
                pos.setDirection(delta);
                if (flip) pos.setYaw(pos.getYaw() + 180f);
                pos.setPitch(0f);
                dragon.teleport(pos);
                world.spawnParticle(Particle.DRAGON_BREATH, pos, 30, 2, 1, 2, 0.05);
                world.spawnParticle(Particle.REVERSE_PORTAL, pos, 25, 2, 1, 2, 0.4);
                for (Player pl : world.getPlayers()) {
                    if (!hittable(pl) || struck.contains(pl.getUniqueId())) continue;
                    if (pl.getLocation().add(0, 1, 0).distanceSquared(pos) < 4.5 * 4.5) {
                        struck.add(pl.getUniqueId());
                        hurt(pl, 8.0);   // clipped by the dragon on the way down
                    }
                }
                if (p >= 1.0) {
                    cancel();
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, spot, 4, 2, 0.5, 2, 0);
                    world.spawnParticle(Particle.REVERSE_PORTAL, spot, 200, radius / 2, 1, radius / 2, 0.6);
                    world.strikeLightningEffect(spot);
                    world.playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 6f, 0.5f);
                    world.playSound(spot, Sound.ENTITY_ENDER_DRAGON_HURT, SoundCategory.HOSTILE, 4f, 0.6f);
                    for (Player pl : world.getPlayers()) {
                        if (!hittable(pl)) continue;
                        Location l = pl.getLocation();
                        double dx = l.getX() - spot.getX(), dz = l.getZ() - spot.getZ();
                        if (dx * dx + dz * dz <= radius * radius && Math.abs(l.getY() - spot.getY()) <= 5) {
                            hurt(pl, damage);
                            Vector out = new Vector(dx, 0, dz);
                            if (out.lengthSquared() > 0.001) pl.setVelocity(out.normalize().multiply(1.1).setY(0.7));
                        }
                    }
                    sendWave(spot, 0);   // the crash also sends out a shockwave - jump it
                    dragon.setPhase(EnderDragon.Phase.CIRCLING);
                    dragon.setVelocity(new Vector(0, 0.8, 0));
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ TAIL WHIP

    private void tail(int tier) {
        final Player target = randomTarget();
        if (target == null) return;
        final Location o = dragon.getLocation().clone();
        final double ty = target.getLocation().getY();
        o.setY(ty);
        final double dx = target.getLocation().getX() - o.getX(), dz = target.getLocation().getZ() - o.getZ();
        final double base = Math.atan2(dz, dx);
        final double half = Math.toRadians(38 + tier * 4);
        final double radius = Math.max(14, Math.min(30, Math.sqrt(dx * dx + dz * dz) + 6));
        final double inner = 4.0;                       // safe zone right under the dragon
        final int telegraph = 36 - tier * 4;
        final int sweep = 14;
        final double damage = 11.0 + tier * 2;
        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 7f, 0.9f);

        new BukkitRunnable() {
            int t = 0;
            final Set<UUID> hit = new HashSet<>();
            @Override public void run() {
                if (!dragon.isValid()) { cancel(); return; }
                if (t < telegraph) {
                    if (t % 3 == 0) {
                        for (double r = inner; r <= radius; r += 1.5) {
                            dust(o.clone().add(Math.cos(base - half) * r, 0.2, Math.sin(base - half) * r), RED, 1.3f);
                            dust(o.clone().add(Math.cos(base + half) * r, 0.2, Math.sin(base + half) * r), RED, 1.3f);
                        }
                        for (double ang = -half; ang <= half; ang += 0.08) {
                            dust(o.clone().add(Math.cos(base + ang) * radius, 0.2, Math.sin(base + ang) * radius), ORANGE, 1.3f);
                        }
                    }
                    if (t % 8 == 0) world.playSound(o, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 3f, 0.5f + t / 40f);
                    t++;
                    return;
                }
                double progress = (t - telegraph) / (double) sweep;
                double ang = base - half + 2 * half * progress;
                for (double r = inner; r <= radius; r += 0.8) {
                    Location pt = o.clone().add(Math.cos(ang) * r, 0.6, Math.sin(ang) * r);
                    dust(pt, PURPLE, 1.8f);
                    if (((int) (r * 10)) % 3 == 0) world.spawnParticle(Particle.CRIT, pt, 2, 0.2, 0.3, 0.2, 0.1);
                }
                Location tip = o.clone().add(Math.cos(ang) * radius, 0.6, Math.sin(ang) * radius);
                world.spawnParticle(Particle.SWEEP_ATTACK, tip, 3, 0.5, 0.3, 0.5, 0);
                world.playSound(tip, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2f, 0.5f);
                for (Player p : world.getPlayers()) {
                    if (!hittable(p) || hit.contains(p.getUniqueId())) continue;
                    Location l = p.getLocation();
                    double px = l.getX() - o.getX(), pz = l.getZ() - o.getZ();
                    double dist = Math.sqrt(px * px + pz * pz);
                    if (dist < inner || dist > radius || Math.abs(l.getY() - ty) > 4) continue;
                    if (Math.abs(angDiff(Math.atan2(pz, px), ang)) < 0.22) {
                        hit.add(p.getUniqueId());
                        hurt(p, damage);
                        p.setVelocity(new Vector(px, 0, pz).normalize().multiply(1.0).setY(0.6));
                    }
                }
                if (++t >= telegraph + sweep) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ PRECISE BREATH

    private void breath(int tier) {
        final Player target = randomTarget();
        if (target == null) return;
        final int telegraph = 30;
        final int duration = 56 + tier * 10;
        final double damage = 3.5 + tier;                 // per tick of contact (every 4 ticks)
        final double turn = 0.30 + tier * 0.08;           // how fast the aim point may move: strafe to dodge
        final Location aimPt = target.getLocation().clone().add(0, 1, 0);
        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 7f, 1.0f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!dragon.isValid()) { cancel(); return; }
                Location from = dragon.getLocation().add(0, 1, 0);
                if (hittable(target)) {
                    Vector want = target.getLocation().add(0, 1, 0).toVector().subtract(aimPt.toVector());
                    double l = want.length();
                    if (l > turn) want.multiply(turn / l);
                    aimPt.add(want);
                }
                if (t < telegraph) {
                    line(from, aimPt, 1.0, PURPLE, 0.8f);
                    if (t % 8 == 0) world.playSound(from, Sound.ENTITY_ENDER_DRAGON_AMBIENT, SoundCategory.HOSTILE, 3f, 1.4f);
                    t++;
                    return;
                }
                Vector v = aimPt.toVector().subtract(from.toVector());
                double len = v.length();
                if (len > 0.5) {
                    Vector step = v.clone().normalize().multiply(0.9);
                    Location cur = from.clone();
                    for (double d = 0; d < len; d += 0.9) {
                        double w = 0.1 + d * 0.015;
                        world.spawnParticle(Particle.DRAGON_BREATH, cur, 3, w, w, w, 0.01);
                        dust(cur, PURPLE, 1.8f);
                        cur.add(step);
                    }
                    world.spawnParticle(Particle.REVERSE_PORTAL, aimPt, 25, 0.8, 0.3, 0.8, 0.3);
                    if (t % 6 == 0) world.playSound(aimPt, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.5f);
                    if (t % 4 == 0) {
                        for (Player p : world.getPlayers()) {
                            if (!hittable(p)) continue;
                            Location pl = p.getLocation().add(0, 1, 0);
                            double dist = Util.distToSegment(pl.toVector(), from.toVector(), aimPt.toVector());
                            double along = pl.toVector().subtract(from.toVector()).length();
                            if (dist < 1.4 + 0.04 * along) hurt(p, damage);
                        }
                    }
                }
                if (++t >= telegraph + duration) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ MINIONS

    private void minions(int tier) {
        minionIds.removeIf(id -> { Entity e = Bukkit.getEntity(id); return e == null || !e.isValid(); });
        int cap = plugin.getConfig().getInt("minions.max-alive", 10) + tier * 4;
        int count = Math.min(cap - minionIds.size(), plugin.getConfig().getInt("minions.per-summon", 3) + tier * 2);
        if (count <= 0 || targets().isEmpty()) return;
        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 8f, 0.9f);
        world.playSound(dragon.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 6f, 0.6f);
        for (Player p : targets()) {
            p.sendActionBar(Component.text("The dragon calls its Void Minions!", NamedTextColor.DARK_PURPLE));
        }
        final List<Location> spots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double ang = Math.PI * 2 * i / count + rnd.nextDouble();
            spots.add(dragon.getLocation().add(Math.cos(ang) * 7, -2 + rnd.nextDouble() * 3, Math.sin(ang) * 7));
        }
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!dragon.isValid()) { cancel(); return; }
                for (Location s : spots) world.spawnParticle(Particle.REVERSE_PORTAL, s, 12, 0.4, 0.6, 0.4, 0.3);
                if (++t >= 20) {
                    cancel();
                    for (Location s : spots) spawnMinion(s);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnMinion(Location at) {
        final double health = plugin.getConfig().getDouble("minions.health", 12.0);
        final double dmg = plugin.getConfig().getDouble("minions.damage", 6.0);
        final Vex v = world.spawn(at, Vex.class, m -> {
            m.setCanPickupItems(false);
            m.setLimitedLifetime(false);
            m.setPersistent(false);
            m.setGlowing(true);
            m.customName(Component.text("Void Minion", NamedTextColor.DARK_PURPLE));
            m.setCustomNameVisible(true);
            Util.setMaxHealth(m, health);
            Util.setAttackDamage(m, dmg);
            m.getPersistentDataContainer().set(plugin.minionKey(), PersistentDataType.BYTE, (byte) 1);
        });
        Player near = randomTarget();
        if (near != null) v.setTarget(near);
        minionIds.add(v.getUniqueId());
        world.spawnParticle(Particle.REVERSE_PORTAL, at, 40, 0.4, 0.6, 0.4, 0.4);
        world.playSound(at, Sound.ENTITY_VEX_CHARGE, SoundCategory.HOSTILE, 2f, 0.6f);
        // keep them hunting; despawn after 50s so they never pile up
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!v.isValid() || !dragon.isValid()) { cancel(); return; }
                if (v.getTarget() == null || v.getTarget().isDead()) {
                    Player p = randomTarget();
                    if (p != null) v.setTarget(p);
                }
                if (++t >= 50) {
                    world.spawnParticle(Particle.POOF, v.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
                    v.remove();
                    minionIds.remove(v.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    void cleanup() {
        for (UUID id : minionIds) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        minionIds.clear();
    }
}
