package dev.voiddragon;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

final class Util {
    private Util() {}

    /** Works on both 1.21.1 ("generic.max_health") and 1.21.3+ ("max_health"). */
    static Attribute maxHealthAttribute() {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (a == null) a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
        return a;
    }

    static Attribute attackDamageAttribute() {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("attack_damage"));
        if (a == null) a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.attack_damage"));
        return a;
    }

    static void setAttackDamage(LivingEntity e, double value) {
        Attribute attr = attackDamageAttribute();
        AttributeInstance inst = attr == null ? null : e.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    static void setMaxHealth(LivingEntity e, double value) {
        Attribute attr = maxHealthAttribute();
        AttributeInstance inst = attr == null ? null : e.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
        e.setHealth(value);
    }

    static double maxHealth(LivingEntity e) {
        Attribute attr = maxHealthAttribute();
        AttributeInstance inst = attr == null ? null : e.getAttribute(attr);
        return inst == null ? 200.0 : inst.getValue();
    }

    /** Random point on a sphere shell. */
    static Location onSphere(Location c, double r, java.util.Random rnd) {
        double u = rnd.nextDouble() * 2 - 1;
        double t = rnd.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return c.clone().add(r * s * Math.cos(t), r * u, r * s * Math.sin(t));
    }

    /** Shortest distance from point p to the segment a-b. */
    static double distToSegment(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1e-6) return p.distance(a);
        double t = Math.max(0, Math.min(1, p.clone().subtract(a).dot(ab) / len2));
        return p.distance(a.clone().add(ab.multiply(t)));
    }
}
