package dev.voiddragon;

import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.logging.Logger;

/** Default hook: does nothing visual, just logs, so the plugin runs without a model engine. */
public final class LoggingModelHook implements ModelHook {
    private final Logger log;

    public LoggingModelHook(Logger log) { this.log = log; }

    @Override public void attachDragon(EnderDragon d) { log.info("[model] attach void_dragon"); }
    @Override public void attachWraith(LivingEntity w) { /* too spammy to log */ }
    @Override public void setDragonEnraged(EnderDragon d, boolean on) { log.info("[model] enraged=" + on); }
    @Override public void setDragonAnimationSpeed(EnderDragon d, double s) { log.info("[model] anim speed=" + s); }
    @Override public void detach(Entity e) { }
}
