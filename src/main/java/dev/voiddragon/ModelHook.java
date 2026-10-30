package dev.voiddragon;

import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Bridge between the fight logic and whatever model engine you use
 * (BetterModel, ModelEngine, ...). Implement this once and register it with
 * {@link VoidDragonPlugin#setModelHook(ModelHook)}.
 *
 * Models: void_dragon (+ void_dragon_enraged), void_wraith. Both have a "fly" animation.
 */
public interface ModelHook {

    /** Attach the void_dragon model to the dragon entity (hide the vanilla body). */
    void attachDragon(EnderDragon dragon);

    /** Attach the void_wraith model to a wraith (the backing entity is an invisible Vex). */
    void attachWraith(LivingEntity wraith);

    /** Phase 2 look: swap to void_dragon_enraged (or swap its texture). */
    void setDragonEnraged(EnderDragon dragon, boolean enraged);

    /** Speed multiplier for the "fly" animation (1.0 = normal). */
    void setDragonAnimationSpeed(EnderDragon dragon, double speed);

    /** Remove any model attached to this entity. */
    void detach(Entity entity);
}
