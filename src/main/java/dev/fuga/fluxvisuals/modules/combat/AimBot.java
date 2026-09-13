package dev.fuga.fluxvisuals.modules.combat;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

/**
 * Human-like FPS-based aim assist. Rotation is applied per rendered frame
 * (not per tick) so movement speed does not depend on TPS.
 *
 * <p>No per-frame random is used for the aim point: the point is positionally
 * smoothed and only a slow drifting offset is added, otherwise the aim visibly
 * shakes.
 */
public final class AimBot extends Module {
    public enum Point {
        HEAD("Голова"),
        CHEST("Грудь"),
        LEGS("Ноги");

        private final String label;
        Point(String label) { this.label = label; }
        public String label() { return label; }
    }

    public enum Targets {
        PLAYERS("Игроки"),
        MOBS("Мобы"),
        ALL("Все");

        private final String label;
        Targets(String label) { this.label = label; }
        public String label() { return label; }
    }

    // ---- defaults (legit-ish) ----
    private float fov = 60.0F;
    private float distance = 5.0F;
    private float yawSpeed = 7.0F;
    private float pitchSpeed = 6.5F;
    private float humanize = 45.0F; // 0-100
    private float maxSpeed = 280.0F; // deg/sec cap
    private float deadzone = 0.5F; // degrees, do not micro-jitter inside
    private float reactionMs = 110.0F;
    private boolean aimYaw = true;
    private boolean aimPitch = true;
    private boolean stickyTarget = true;
    private boolean checkWalls = true;
    private boolean onlyOnAttack = false;
    private boolean requireAimKey = false;
    private int aimKey = 0;
    private boolean comboEnabled = false;
    private int comboKey = 0;
    private Targets targets = Targets.PLAYERS;
    private final Set<String> multipoints = new HashSet<>(Set.of("Голова", "Грудь"));

    private long targetFirstSeenMs = 0L;
    private long lastSeenPointMs = 0L;
    private int currentTargetId = Integer.MIN_VALUE;
    private int turnDir = 0;
    private Vec3d smoothPoint = null;
    private float noiseYaw = 0.0F;
    private float noisePitch = 0.0F;
    private float noiseTargetYaw = 0.0F;
    private float noiseTargetPitch = 0.0F;
    private long nextNoiseShiftMs = 0L;
    private boolean comboWasDown = false;
    private final java.util.Random random = new java.util.Random();

    public AimBot() {
        super("AimBot", "Smooth human-like FPS aim assist.", ModuleCategory.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client == null || !comboEnabled || comboKey == 0 || comboKey == GLFW.GLFW_KEY_UNKNOWN) {
            comboWasDown = false;
            return;
        }
        boolean down = isKeyDown(client, comboKey);
        if (down && !comboWasDown) {
            TriggerBot triggerBot = FluxVisualsClient.MODULE_MANAGER.getTriggerBot();
            boolean next = !(isEnabled() && triggerBot.isEnabled());
            setEnabled(next);
            triggerBot.setEnabled(next);
        }
        comboWasDown = down;
    }

    @Override
    public void onFrame(MinecraftClient client, float deltaSeconds) {
        if (!isEnabled() || client == null || !(client.player instanceof ClientPlayerEntity player) || client.world == null) {
            currentTargetId = Integer.MIN_VALUE;
            smoothPoint = null;
            return;
        }
        if (client.currentScreen != null) {
            return;
        }
        float dt = MathHelper.clamp(deltaSeconds, 0.0005F, 0.05F);
        // render-interpolated positions: entity ticks run at 20Hz, per-frame
        // aim on raw tick positions visibly steps while walking
        float tickDelta = tickProgress(client);
        Vec3d eye = player.getCameraPosVec(tickDelta);

        // sticky target: in a crowd keep the locked one instead of flicking
        // between entities every frame; release on death, range-out or 600 ms
        // without a visible point (or far outside the FOV cone)
        Entity target = null;
        Vec3d rawPoint = null;
        long nowMs = System.currentTimeMillis();
        if (stickyTarget && currentTargetId != Integer.MIN_VALUE && client.world != null) {
            Entity locked = client.world.getEntityById(currentTargetId);
            if (locked != null && allows(client, player, locked)
                    && player.squaredDistanceTo(locked) <= (distance + 2.0F) * (distance + 2.0F)) {
                ScoredPoint sp = scorePoint(client, player, locked, eye, tickDelta);
                if (sp != null && sp.angle <= fov * 0.5D + 45.0D) {
                    target = locked;
                    rawPoint = sp.point;
                    lastSeenPointMs = nowMs;
                } else if (smoothPoint != null && nowMs - lastSeenPointMs < 600L) {
                    target = locked;
                    rawPoint = smoothPoint; // glide through brief occlusion
                }
            }
        }
        if (target == null) {
            // single pass: best target + its raw aim point, computed once per frame
            double bestAngle = fov * 0.5D;
            for (Entity e : client.world.getEntities()) {
                if (!allows(client, player, e)) continue;
                if (player.squaredDistanceTo(e) > distance * distance) continue;
                ScoredPoint sp = scorePoint(client, player, e, eye, tickDelta);
                if (sp == null) continue;
                if (sp.angle <= bestAngle) {
                    bestAngle = sp.angle;
                    target = e;
                    rawPoint = sp.point;
                }
            }
        }
        if (target == null || rawPoint == null) {
            currentTargetId = Integer.MIN_VALUE;
            smoothPoint = null;
            return;
        }
        if (target.getId() != currentTargetId) {
            currentTargetId = target.getId();
            targetFirstSeenMs = System.currentTimeMillis();
            lastSeenPointMs = targetFirstSeenMs;
            smoothPoint = rawPoint; // snap on switch, then glide
            turnDir = 0;
            noiseYaw = 0.0F;
            noisePitch = 0.0F;
            nextNoiseShiftMs = 0L;
        }
        if (System.currentTimeMillis() - targetFirstSeenMs < reactionMs) {
            return; // human reaction delay
        }
        if (onlyOnAttack && !client.options.attackKey.isPressed()) {
            return;
        }
        if (requireAimKey && aimKey != 0 && !isKeyDown(client, aimKey)) {
            return;
        }

        // positional low-pass: kills per-frame jumping of the aim point
        float pointAlpha = 1.0F - (float) Math.exp(-22.0F * dt);
        smoothPoint = lerpVec(smoothPoint, rawPoint, pointAlpha);

        updateNoise(dt);

        float[] desired = calcYawPitch(eye, smoothPoint);
        float yawDelta = wrapDegrees(desired[0] + noiseYaw - player.getYaw());
        float pitchDelta = desired[1] + noisePitch - player.getPitch();

        // turn commitment: near +-180 deg the wrap sign flips every frame and
        // the aim oscillates instead of turning; commit to one side until close
        if (Math.abs(yawDelta) < 90.0F) {
            turnDir = 0;
        } else if (turnDir == 0) {
            turnDir = yawDelta > 0.0F ? 1 : -1;
        } else if (turnDir > 0 && yawDelta < -90.0F) {
            yawDelta += 360.0F;
        } else if (turnDir < 0 && yawDelta > 90.0F) {
            yawDelta -= 360.0F;
        }

        // deadzone: ignore tiny deltas to avoid robotic micro shake
        if (Math.abs(yawDelta) < deadzone) yawDelta = 0.0F;
        if (Math.abs(pitchDelta) < deadzone) pitchDelta = 0.0F;
        if (yawDelta == 0.0F && pitchDelta == 0.0F) {
            return;
        }

        // far turns go at constant capped speed (fast flicks behind),
        // close range eases exponentially for a smooth human landing
        float yawLambda = 2.0F + yawSpeed * 1.35F;
        float pitchLambda = 2.0F + pitchSpeed * 1.35F;
        float yawStep = Math.abs(yawDelta) > 60.0F
                ? Math.signum(yawDelta) * Math.min(Math.abs(yawDelta), maxSpeed * dt)
                : yawDelta * (1.0F - (float) Math.exp(-yawLambda * dt));
        float pitchStep = Math.abs(pitchDelta) > 60.0F
                ? Math.signum(pitchDelta) * Math.min(Math.abs(pitchDelta), maxSpeed * dt)
                : pitchDelta * (1.0F - (float) Math.exp(-pitchLambda * dt));

        // cap max deg/sec so fast flicks look human
        float maxStep = maxSpeed * dt;
        yawStep = MathHelper.clamp(yawStep, -maxStep, maxStep);
        pitchStep = MathHelper.clamp(pitchStep, -maxStep, maxStep);

        if (aimYaw) {
            player.setYaw(player.getYaw() + yawStep);
        }
        if (aimPitch) {
            player.setPitch(MathHelper.clamp(player.getPitch() + pitchStep, -90.0F, 90.0F));
        }
    }

    /** Slow drifting human offset, retargeted a few times per second. */
    private void updateNoise(float dt) {
        long now = System.currentTimeMillis();
        float hum = humanize / 100.0F;
        if (now >= nextNoiseShiftMs) {
            nextNoiseShiftMs = now + 350L + random.nextInt(250);
            noiseTargetYaw = (random.nextFloat() - 0.5F) * 1.1F * hum;
            noiseTargetPitch = (random.nextFloat() - 0.5F) * 0.8F * hum;
        }
        float alpha = 1.0F - (float) Math.exp(-5.0F * dt);
        noiseYaw += (noiseTargetYaw - noiseYaw) * alpha;
        noisePitch += (noiseTargetPitch - noisePitch) * alpha;
    }

    private record ScoredPoint(Vec3d point, double angle) {
    }

    private ScoredPoint scorePoint(MinecraftClient client, ClientPlayerEntity player, Entity target, Vec3d eye, float tickDelta) {
        Vec3d base = target.getLerpedPos(tickDelta);
        double h = Math.max(0.5D, target.getHeight());
        EnumSet<Point> enabled = enabledPoints();
        if (enabled.isEmpty()) return null;
        Vec3d best = null;
        double bestAngle = Double.MAX_VALUE;
        for (Point pt : enabled) {
            Vec3d p = switch (pt) {
                case HEAD -> base.add(0.0D, h * 0.88D, 0.0D);
                case CHEST -> base.add(0.0D, h * 0.55D, 0.0D);
                case LEGS -> base.add(0.0D, h * 0.18D, 0.0D);
            };
            if (checkWalls && !visible(client, eye, p)) continue;
            float[] yp = calcYawPitch(eye, p);
            double angle = Math.abs(wrapDegrees(yp[0] - player.getYaw())) + Math.abs(yp[1] - player.getPitch()) * 0.9D;
            if (angle < bestAngle) {
                bestAngle = angle;
                best = p;
            }
        }
        return best == null ? null : new ScoredPoint(best, bestAngle);
    }

    private static float tickProgress(MinecraftClient client) {
        try {
            return MathHelper.clamp(client.getRenderTickCounter().getTickProgress(false), 0.0F, 1.0F);
        } catch (Exception ignored) {
            return 1.0F;
        }
    }

    private boolean visible(MinecraftClient client, Vec3d from, Vec3d to) {
        try {
            RaycastContext ctx = new RaycastContext(from, to, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, client.player);
            return client.world.raycast(ctx).getType() == net.minecraft.util.hit.HitResult.Type.MISS;
        } catch (Exception ignored) {
            return true;
        }
    }

    private boolean allows(MinecraftClient client, ClientPlayerEntity player, Entity e) {
        if (e == null || e == player || e.isRemoved() || !e.canHit()) return false;
        if (e instanceof LivingEntity living && !living.isAlive()) return false;
        if (e instanceof PlayerEntity pe) {
            if (pe == player) return false;
            if (targets == Targets.MOBS) return false;
        } else if (e instanceof MobEntity) {
            if (targets == Targets.PLAYERS) return false;
        } else if (!(e instanceof LivingEntity)) {
            return false;
        }
        return true;
    }

    private static Vec3d lerpVec(Vec3d from, Vec3d to, float alpha) {
        if (from == null) return to;
        float a = MathHelper.clamp(alpha, 0.0F, 1.0F);
        return new Vec3d(
                from.x + (to.x - from.x) * a,
                from.y + (to.y - from.y) * a,
                from.z + (to.z - from.z) * a);
    }

    private static float[] calcYawPitch(Vec3d eye, Vec3d target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    private static float wrapDegrees(float v) {
        return MathHelper.wrapDegrees(v);
    }

    private static boolean isKeyDown(MinecraftClient client, int key) {
        if (client.getWindow() == null) return false;
        long h = client.getWindow().getHandle();
        if (key >= 1000 && key <= 1010) {
            return GLFW.glfwGetMouseButton(h, key - 1000) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(h, key) == GLFW.GLFW_PRESS;
    }

    private EnumSet<Point> enabledPoints() {
        EnumSet<Point> out = EnumSet.noneOf(Point.class);
        for (Point p : Point.values()) {
            if (multipoints.contains(p.label())) out.add(p);
        }
        return out;
    }

    // ---- getters/setters for GUI + config ----
    public float getFov() { return fov; }
    public void setFov(float v) { fov = MathHelper.clamp(v, 5.0F, 360.0F); FluxVisualsClient.requestConfigSave(); }
    public float getDistance() { return distance; }
    public void setDistance(float v) { distance = MathHelper.clamp(v, 2.0F, 10.0F); FluxVisualsClient.requestConfigSave(); }
    public float getYawSpeed() { return yawSpeed; }
    public void setYawSpeed(float v) { yawSpeed = MathHelper.clamp(v, 1.0F, 30.0F); FluxVisualsClient.requestConfigSave(); }
    public float getPitchSpeed() { return pitchSpeed; }
    public void setPitchSpeed(float v) { pitchSpeed = MathHelper.clamp(v, 1.0F, 30.0F); FluxVisualsClient.requestConfigSave(); }
    public float getHumanize() { return humanize; }
    public void setHumanize(float v) { humanize = MathHelper.clamp(v, 0.0F, 100.0F); FluxVisualsClient.requestConfigSave(); }
    public float getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(float v) { maxSpeed = MathHelper.clamp(v, 30.0F, 2160.0F); FluxVisualsClient.requestConfigSave(); }
    public float getDeadzone() { return deadzone; }
    public void setDeadzone(float v) { deadzone = MathHelper.clamp(v, 0.0F, 5.0F); FluxVisualsClient.requestConfigSave(); }
    public float getReactionMs() { return reactionMs; }
    public void setReactionMs(float v) { reactionMs = MathHelper.clamp(v, 0.0F, 500.0F); FluxVisualsClient.requestConfigSave(); }
    public boolean isAimYaw() { return aimYaw; }
    public void setAimYaw(boolean v) { aimYaw = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isAimPitch() { return aimPitch; }
    public void setAimPitch(boolean v) { aimPitch = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isStickyTarget() { return stickyTarget; }
    public void setStickyTarget(boolean v) { stickyTarget = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isCheckWalls() { return checkWalls; }
    public void setCheckWalls(boolean v) { checkWalls = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isOnlyOnAttack() { return onlyOnAttack; }
    public void setOnlyOnAttack(boolean v) { onlyOnAttack = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isRequireAimKey() { return requireAimKey; }
    public void setRequireAimKey(boolean v) { requireAimKey = v; FluxVisualsClient.requestConfigSave(); }
    public int getAimKey() { return aimKey; }
    public void setAimKey(int v) { aimKey = v; FluxVisualsClient.requestConfigSave(); }
    public boolean isComboEnabled() { return comboEnabled; }
    public void setComboEnabled(boolean v) { comboEnabled = v; FluxVisualsClient.requestConfigSave(); }
    public int getComboKey() { return comboKey; }
    public void setComboKey(int v) { comboKey = v; FluxVisualsClient.requestConfigSave(); }
    public Targets getTargets() { return targets; }
    public void setTargets(Targets v) { targets = v == null ? Targets.PLAYERS : v; FluxVisualsClient.requestConfigSave(); }
    public String getTargetsName() { return targets == null ? "Игроки" : targets.label(); }
    public void setTargetsName(String name) {
        if (name == null) return;
        for (Targets t : Targets.values()) {
            if (t.label().equalsIgnoreCase(name) || t.name().equalsIgnoreCase(name)) { setTargets(t); return; }
        }
    }
    public Set<String> getMultipoints() { return new HashSet<>(multipoints); }
    public void setMultipoints(Set<String> v) { multipoints.clear(); if (v != null) multipoints.addAll(v); FluxVisualsClient.requestConfigSave(); }
}
