package fr.frabous.stellar.common;

import fr.frabous.stellar.api.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/** Server half of SS-05: drives every captured body along {@link GravityInversionRules#drive}. */
final class GravityInversionField {
    /** Shear from the flipping gravity and the collapse ignore armour, like the black hole tides. */
    static final DamageSource SHEAR = new DamageSource("stellar.inversion").setDamageBypassesArmor();
    static final DamageSource COLLAPSE = new DamageSource("stellar.collapse").setDamageBypassesArmor();
    /** World tick until which a body thrown by the field takes no fall damage. */
    private static final String FALL_GUARD = "StellarInversionFall";
    private static final int FALL_GRACE = 100;

    private final LaunchState state;
    /** Players carried by our hidden levitation, which keeps the server from kicking them for flying. */
    private final Set<EntityPlayerMP> levitated = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Entity> shocked = Collections.newSetFromMap(new IdentityHashMap<>());

    GravityInversionField(LaunchState state) { this.state = state; }

    void update(WorldServer world, long age, List<LaunchState> blackholes) {
        BlockTarget target = state.getTarget();
        double cx = target.getX() + 0.5, cz = target.getZ() + 0.5;
        cue(world, age, target);
        if (age > GravityInversionRules.END - 10) return;
        long now = world.getTotalWorldTime();
        double radius = GravityInversionRules.REACH;
        AxisAlignedBB box = new AxisAlignedBB(cx - radius, Math.max(0, target.getY() - GravityInversionRules.FLOOR), cz - radius,
                cx + radius, Math.min(256, target.getY() + GravityInversionRules.CEILING), cz + radius);
        boolean pulse = GravityInversionRules.damagePulse(age);
        double ringFrom = GravityInversionRules.shock(age - 1), ringTo = GravityInversionRules.shock(age);
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, box)) {
            if (!entity.isEntityAlive() || !eligible(entity)
                    || !GravityInversionRules.contains(target, entity.posX, entity.posY, entity.posZ)) continue;
            if (BlackholeField.ownerAt(blackholes, now, entity.posX, entity.posZ) != null) continue;
            if (world.getChunkProvider().getLoadedChunk(MathHelper.floor(entity.posX) >> 4, MathHelper.floor(entity.posZ) >> 4) == null) continue;
            double dx = entity.posX - cx, dz = entity.posZ - cz;
            if (age == GravityInversionRules.IMPACT && entity instanceof EntityLivingBase) {
                hurt((EntityLivingBase) entity, COLLAPSE, GravityInversionRules.COLLAPSE_DAMAGE);
                continue;
            }
            if (age > GravityInversionRules.IMPACT) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > ringFrom && distance <= ringTo && distance > 3 && shocked.add(entity)) shove(entity, dx, dz, distance);
                continue;
            }
            boolean loose = !(entity instanceof EntityLivingBase);
            double ground = world.getHeight(MathHelper.floor(entity.posX), MathHelper.floor(entity.posZ));
            double[] drive = GravityInversionRules.drive(state.getSeed(), age, dx, entity.posY - ground, dz, entity.getEntityId(), loose);
            if (drive == null) continue;
            move(world, entity, drive, now, age < GravityInversionRules.SLAM);
            if (pulse && entity instanceof EntityLivingBase) hurt((EntityLivingBase) entity, SHEAR, GravityInversionRules.PULSE_DAMAGE);
        }
        if (age >= GravityInversionRules.SLAM) release();
    }

    private boolean eligible(Entity entity) {
        if (entity.isRiding() || entity.noClip) return false;
        // Machine players have no client: levitation packets would have nowhere to go.
        if (entity instanceof FakePlayer) return false;
        // The caster and creative players ride the field too; vanilla still spares creative players the damage.
        if (entity instanceof EntityPlayer) return !((EntityPlayer) entity).isSpectator();
        return entity instanceof EntityLivingBase || entity instanceof EntityItem || entity instanceof EntityXPOrb
                || entity instanceof IProjectile || entity instanceof EntityFireball
                || entity instanceof EntityTNTPrimed || entity instanceof EntityFallingBlock;
    }

    private void move(WorldServer world, Entity entity, double[] drive, long now, boolean carry) {
        double vx = drive[0], vy = drive[1], vz = drive[2];
        // Never throw a body into a chunk the server does not hold.
        if (!world.isBlockLoaded(new BlockPos(entity.posX + vx * 4, 64, entity.posZ + vz * 4), false)) { vx = 0; vz = 0; }
        if (entity instanceof EntityLivingBase) {
            double hold = 1 - ((EntityLivingBase) entity).getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue();
            // Heavy bodies resist sideways throws, never the lift itself.
            hold = 0.35 + 0.65 * MathHelper.clamp(hold, 0, 1);
            vx *= hold; vz *= hold;
        }
        entity.motionX = vx; entity.motionY = vy; entity.motionZ = vz;
        entity.onGround = false;
        entity.fallDistance = 0;
        if (entity instanceof EntityFireball) {
            EntityFireball fireball = (EntityFireball) entity;
            fireball.accelerationX = vx * 0.1; fireball.accelerationY = vy * 0.1; fireball.accelerationZ = vz * 0.1;
        }
        entity.getEntityData().setLong(FALL_GUARD, now + FALL_GRACE);
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            // Creative flight would damp every throw: the field takes the player's wings until it lets go.
            if (player.capabilities.isFlying) { player.capabilities.isFlying = false; player.sendPlayerAbilities(); }
            if (carry) levitate(player);
        }
        sync(entity);
    }

    private void levitate(EntityPlayerMP player) {
        PotionEffect current = player.getActivePotionEffect(MobEffects.LEVITATION);
        if (current == null || current.getDuration() < 4 && levitated.contains(player)) {
            player.addPotionEffect(new PotionEffect(MobEffects.LEVITATION, 12, 0, false, false));
            levitated.add(player);
        }
    }

    /** The slam must fall freely: our levitation ends the moment the hang breaks. */
    private void release() {
        for (EntityPlayerMP player : levitated) {
            PotionEffect current = player.getActivePotionEffect(MobEffects.LEVITATION);
            if (current != null && current.getDuration() <= 12 && !current.doesShowParticles()) player.removePotionEffect(MobEffects.LEVITATION);
        }
        levitated.clear();
    }

    void finish() {
        release();
        shocked.clear();
    }

    private static void shove(Entity entity, double dx, double dz, double distance) {
        double hold = 1;
        if (entity instanceof EntityLivingBase)
            hold = 1 - ((EntityLivingBase) entity).getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue();
        if (hold <= 0) return;
        double share = 1 - 0.6 * distance / GravityInversionRules.SHOCK_RADIUS;
        double out = GravityInversionRules.SHOCK_PUSH * share * hold / distance;
        entity.addVelocity(dx * out, GravityInversionRules.SHOCK_LIFT * share * hold, dz * out);
        sync(entity);
    }

    private static void hurt(EntityLivingBase entity, DamageSource source, float amount) {
        int previousImmunity = entity.hurtResistantTime;
        entity.hurtResistantTime = 0;
        try { entity.attackEntityFrom(source, amount); }
        finally { entity.hurtResistantTime = Math.max(previousImmunity, entity.hurtResistantTime); }
    }

    private static void sync(Entity entity) {
        entity.velocityChanged = true;
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            if (player.connection != null && player.connection.getNetworkManager().isChannelOpen())
                player.connection.sendPacket(new SPacketEntityVelocity(player));
        }
    }

    static boolean protectFall(EntityLivingBase entity) {
        if (!entity.getEntityData().hasKey(FALL_GUARD)) return false;
        long until = entity.getEntityData().getLong(FALL_GUARD);
        if (entity.world.getTotalWorldTime() <= until) return true;
        entity.getEntityData().removeTag(FALL_GUARD);
        return false;
    }

    /** Vanilla layers every witness hears positionally; modded clients add their own score on top. */
    private static void cue(WorldServer world, long age, BlockTarget target) {
        BlockPos at = new BlockPos(target.getX(), target.getY(), target.getZ());
        if (age == 20) play(world, at, SoundEvents.BLOCK_PORTAL_TRIGGER, 10, 0.5F);
        if (age == GravityInversionRules.ASCENSION) {
            play(world, at, SoundEvents.ENTITY_WITHER_SPAWN, 12, 0.5F);
            play(world, at, SoundEvents.BLOCK_END_PORTAL_SPAWN, 12, 0.4F);
        }
        if (GravityInversionRules.flip(age) >= 0 && (age - GravityInversionRules.ROTATION) % GravityInversionRules.FLIP == 0) {
            play(world, at, SoundEvents.ENTITY_ENDERMEN_TELEPORT, 16, 0.5F + 0.1F * GravityInversionRules.flip(age));
            play(world, at, SoundEvents.ENTITY_LIGHTNING_THUNDER, 10, 1.4F);
        }
        if (age == GravityInversionRules.SLAM) play(world, at, SoundEvents.ENTITY_ENDERDRAGON_GROWL, 16, 0.6F);
        if (age == GravityInversionRules.IMPACT) {
            play(world, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 16, 0.4F);
            play(world, at, SoundEvents.ENTITY_LIGHTNING_THUNDER, 16, 0.5F);
            play(world, at, SoundEvents.ENTITY_LIGHTNING_IMPACT, 16, 0.5F);
        }
    }

    private static void play(WorldServer world, BlockPos at, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, at, sound, SoundCategory.PLAYERS, volume, pitch);
    }
}
