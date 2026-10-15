package com.pzmoodle.immersion.compat;

import com.pzmoodle.immersion.moodle.MoodleAttachments;
import com.pzmoodle.immersion.moodle.Skill;
import com.pzmoodle.immersion.moodle.StatData;
import com.pzmoodle.immersion.trait.Occupation;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;

/** TACZ 可选联动，方法签名不引用 TACZ 类型，未安装时也能安全加载。 */
public final class TaczCompat {
    public static final double AIM_DAMAGE_PER_LEVEL = 0.05;

    private static Field shooterField;
    private static Field dataField;
    private static Field reloadTimestampField;
    private static Field apiShooterField;

    public static void onGunHit(LivingEntity shooter) {
        StatData stats = stats(shooter);
        if (stats != null) stats.addXp(Skill.AIMING, 10);
    }

    public static void onReloadStarted(Object reload) {
        LivingEntity shooter = shooter(reload);
        StatData stats = stats(shooter);
        if (stats != null) stats.addXp(Skill.RELOADING, 10);
    }

    public static float reloadSpeedScale(Object reload) {
        int level = reloadLevel(shooter(reload));
        return (float) Math.max(0.5, 1.0 - level * 0.05);
    }

    /** 按原始起始时间戳把当前实际换弹进度加速，避免只改同步显示。 */
    public static void adjustReloadTimestamp(Object reload, long originalTimestamp) {
        if (originalTimestamp < 0) return;
        float scale = reloadSpeedScale(reload);
        if (scale >= 1.0f) return;
        try {
            Object data = data(reload);
            if (data == null) return;
            long now = System.currentTimeMillis();
            long elapsed = Math.max(0L, now - originalTimestamp);
            long effectiveElapsed = Math.round(elapsed / scale);
            reloadTimestampField().setLong(data, now - effectiveElapsed);
        } catch (Throwable ignored) {
        }
    }

    public static long reloadTimestamp(Object reload) {
        try {
            Object data = data(reload);
            return data == null ? -1L : reloadTimestampField().getLong(data);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    public static float damageScale(LivingEntity attacker) {
        int level = aimingLevel(attacker);
        return (float) (1.0 + level * AIM_DAMAGE_PER_LEVEL);
    }

    /** ModernKineticGunScriptAPI 不在本模组编译依赖中，用反射取它持有的射手。 */
    public static float reloadSpeedScaleFromApi(Object api) {
        try {
            if (apiShooterField == null) {
                apiShooterField = api.getClass().getDeclaredField("shooter");
                apiShooterField.setAccessible(true);
            }
            Object owner = apiShooterField.get(api);
            return reloadSpeedScaleFor(owner instanceof LivingEntity living ? living : null);
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    public static float reloadSpeedScaleFor(LivingEntity shooter) {
        int level = reloadLevel(shooter);
        return (float) Math.max(0.5, 1.0 - level * 0.05);
    }

    /** TaCZ 子弹伤害入口使用这个倍率，覆盖普通生物和 TaCZ 假人路径。 */
    public static float bulletDamageScale(Object bullet) {
        try {
            Entity owner = (Entity) bullet.getClass().getMethod("getOwner").invoke(bullet);
            return owner instanceof LivingEntity living ? damageScale(living) : 1.0f;
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    public static boolean isTaczBullet(Object directEntity) {
        return directEntity != null
                && directEntity.getClass().getName().startsWith("com.tacz.guns.entity.EntityKineticBullet");
    }

    private static int aimingLevel(LivingEntity entity) {
        if (!(entity instanceof Player player)) return 0;
        StatData stats = MoodleAttachments.stats(player);
        var data = MoodleAttachments.get(player);
        return Occupation.combine(Occupation.stat(data.occupation, Skill.AIMING),
                OccupationTrait.traitBonus(data.traits, Skill.AIMING), stats.level(Skill.AIMING));
    }

    private static int reloadLevel(LivingEntity entity) {
        if (!(entity instanceof Player player)) return 0;
        StatData stats = MoodleAttachments.stats(player);
        var data = MoodleAttachments.get(player);
        return Occupation.combine(Occupation.stat(data.occupation, Skill.RELOADING),
                OccupationTrait.traitBonus(data.traits, Skill.RELOADING), stats.level(Skill.RELOADING));
    }

    private static StatData stats(LivingEntity entity) {
        return entity instanceof Player player ? MoodleAttachments.stats(player) : null;
    }

    private static LivingEntity shooter(Object reload) {
        try {
            if (shooterField == null) {
                shooterField = reload.getClass().getDeclaredField("shooter");
                shooterField.setAccessible(true);
            }
            return (LivingEntity) shooterField.get(reload);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object data(Object reload) throws ReflectiveOperationException {
        if (dataField == null) {
            dataField = reload.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
        }
        return dataField.get(reload);
    }

    private static Field reloadTimestampField() throws ReflectiveOperationException {
        if (reloadTimestampField == null) {
            Class<?> holder = dataField.getType();
            reloadTimestampField = holder.getField("reloadTimestamp");
            reloadTimestampField.setAccessible(true);
        }
        return reloadTimestampField;
    }

    private TaczCompat() {}
}
