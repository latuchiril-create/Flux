package im.expensive.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import im.expensive.events.EventDisplay;
import im.expensive.functions.api.Category;
import im.expensive.functions.api.Function;
import im.expensive.functions.api.FunctionRegister;
import im.expensive.functions.settings.impl.BooleanSetting;
import im.expensive.functions.settings.impl.ModeSetting;
import im.expensive.functions.impl.combat.KillAura;
import im.expensive.utils.projections.ProjectionUtil;
import im.expensive.utils.render.ColorUtils;
import im.expensive.utils.render.DisplayUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.potion.Effects;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@FunctionRegister(name = "TargetESP", type = Category.Render)
public class TargetESP extends Function {

    private final KillAura killAura;
    private final ModeSetting mode = new ModeSetting("Вид", "Стандарт", "Стандарт", "Цепи", "Ромбики", "Призраки");
    private final BooleanSetting liquidGlass = new BooleanSetting("Жидкое стекло", true);

    private final ResourceLocation chainTex = new ResourceLocation("expensive/images/chain.png");
    private final ResourceLocation diamondTex = new ResourceLocation("expensive/images/target.png");
    private final ResourceLocation glowTex = new ResourceLocation("expensive/images/glow.png");

    private int lastEntityId = -1;
    private long appearStartMs = 0L;
    private boolean hadTargetLastTick = false;
    private long lastUpdateMs = System.currentTimeMillis();
    private double lastCxW = 0, lastCyW = 0, lastCzW = 0, lastHeight = 1.8;
    private final DiamondInst[] diamonds = new DiamondInst[6];
    private long nextRevealAtMs = 0L;
    private final ArrayList<DiamondShard> diamondShards = new ArrayList<>();
    private final ArrayList<MiniDiamond> miniDiamonds = new ArrayList<>();
    private long lastMiniAttackMs = 0L;
    private long lastTrailSpawnMs = 0L;
    private double lastTargetDist = -1.0;

    private final ArrayList<Fragment> debris = new ArrayList<>();

    private static final int MAX_NODES = 24;
    private static final double BREAK_DIST = 2.5;
    private static final float NODE_ADD_MIN_STEP = 0.12f;
    private static final float RELAX_SEG_BASE = 0.30f;
    private static final float RELAX_STIFFNESS = 0.28f;

    private static final int ST_ATTACHED = 0;
    private static final int ST_DETACHING = 1;
    private static final int ST_DETACHED = 2;
    private static final int ST_ATTACHING = 3;

    private final Map<Integer, TargetESP> trails = new HashMap<>();

    public TargetESP(KillAura killAura) {
        this.killAura = killAura;
        addSettings(mode, liquidGlass);
    }

    private static class MiniDiamond {
        Vector3d pos;
        Vector3d vel;
        float life;
        float maxLife;
        double yaw, pitch, roll;
        double size;
        long bornMs;
        int seed;
        double waveAmp;
        double waveFreqHz;
        double wavePrev;
        long freezeUntilMs;
        MiniDiamond(Vector3d pos, Vector3d vel, float life, double size, long bornMs, int seed) {
            this.pos = pos; this.vel = vel; this.life = life; this.maxLife = life;
            this.size = size; this.bornMs = bornMs; this.seed = seed;
            this.yaw = Math.random() * Math.PI * 2; this.pitch = Math.random() * 0.5 - 0.25; this.roll = Math.random() * 0.5 - 0.25;
            this.waveAmp = 0.0; this.waveFreqHz = 0.0; this.wavePrev = 0.0;
            this.freezeUntilMs = 0L;
        }
    }

    private void spawnMiniDiamondsAt(double ox, double oy, double oz, int index, long atkMs) {
        for (int j = 0; j < 3; j++) {
            double ang = Math.random() * Math.PI * 2;
            double sp = 0.45 + Math.random() * 0.25;
            Vector3d vel = new Vector3d(Math.cos(ang) * sp, 0.25 + Math.random() * 0.15, Math.sin(ang) * sp);
            Vector3d pos = new Vector3d(ox, oy, oz);
            float life = 9.0f + (float) (Math.random() * 0.6f - 0.3f);
            double size = 0.06 + Math.random() * 0.02;
            MiniDiamond md = new MiniDiamond(pos, vel, life, size, atkMs, index * 17 + j);
            md.waveAmp = 0.22 + Math.random() * 0.08;
            md.waveFreqHz = 1.4 + Math.random() * 0.6;
            md.freezeUntilMs = md.bornMs + 8000L;
            miniDiamonds.add(md);
        }
    }

    private void spawnMiniTrailDiamondsAt(double ox, double oy, double oz, long nowMs, Vector3d targetVel) {
        double speed = targetVel.length();
        int count = 1 + (speed > 0.2 ? 1 : 0);
        for (int k = 0; k < count; k++) {
            double ang = Math.random() * Math.PI * 2;
            double side = 0.15 + Math.random() * 0.15;
            Vector3d back = speed > 1e-4 ? targetVel.normalize().scale(-0.25 - Math.random() * 0.15) : new Vector3d(0, 0, 0);
            Vector3d lateral = new Vector3d(Math.cos(ang) * side, 0, Math.sin(ang) * side);
            Vector3d vel = new Vector3d(back.x + lateral.x, 0.05 + Math.random() * 0.05, back.z + lateral.z);
            Vector3d pos = new Vector3d(ox, oy, oz);
            float life = 9.0f + (float) (Math.random() * 0.6f - 0.3f);
            double size = 0.055 + Math.random() * 0.02;
            MiniDiamond md = new MiniDiamond(pos, vel, life, size, nowMs, 97 + k);
            md.waveAmp = 0.0; md.waveFreqHz = 0.0;
            md.freezeUntilMs = md.bornMs + 8000L;
            miniDiamonds.add(md);
        }
    }

    private void updateMiniDiamonds(float dt) {
        if (miniDiamonds.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<MiniDiamond> it = miniDiamonds.iterator(); it.hasNext(); ) {
            MiniDiamond m = it.next();
            m.life -= dt;
            if (now < m.freezeUntilMs) {
                if (m.life <= 0f) it.remove();
                continue;
            }
            long fallEnd = m.freezeUntilMs + 1000L;
            boolean falling = now >= m.freezeUntilMs && now < fallEnd;
            if (falling) {
                m.vel = new Vector3d(m.vel.x * 0.985, m.vel.y * 0.985 - 0.45 * dt, m.vel.z * 0.985);
            } else {
                m.vel = new Vector3d(m.vel.x * 0.992, m.vel.y * 0.992 - 0.08 * dt, m.vel.z * 0.992);
            }
            m.pos = m.pos.add(m.vel.x * dt, m.vel.y * dt, m.vel.z * dt);
            if (!falling && m.waveAmp > 0.0 && m.waveFreqHz > 0.0) {
                double tSec = (System.currentTimeMillis() - m.bornMs) / 1000.0;
                double phase = 2.0 * Math.PI * m.waveFreqHz * tSec;
                double off = m.waveAmp * Math.sin(phase);
                double dOff = off - m.wavePrev;
                m.pos = m.pos.add(0.0, dOff, 0.0);
                m.wavePrev = off;
            }
            m.yaw += 1.6 * dt; m.pitch += 1.2 * dt; m.roll += 0.9 * dt;
            if (m.life <= 0f) it.remove();
        }
    }

    private void renderMiniDiamonds(long nowMs, int hue, float appearK, float alphaDistK) {
        if (miniDiamonds.isEmpty()) return;
        for (MiniDiamond m : miniDiamonds) {
            float k = MathHelper.clamp(m.life / m.maxLife, 0f, 1f);
            float aIn = MathHelper.clamp(1f - (m.maxLife - m.life) / 0.25f, 0f, 1f);
            float aOut = MathHelper.clamp(m.life / 0.35f, 0f, 1f);
            float alphaK = Math.min(aIn, aOut);
            int baseRGB = ColorUtils.setAlpha(0xFFFFFF, (int) (220 * alphaK));
            int glowRGB = ColorUtils.setAlpha(hue, (int) (120 * alphaK));

            Vector3d top = new Vector3d(0, m.size, 0);
            Vector3d bottom = new Vector3d(0, -m.size, 0);
            Vector3d px = new Vector3d(m.size, 0, 0);
            Vector3d nx = new Vector3d(-m.size, 0, 0);
            Vector3d pz = new Vector3d(0, 0, m.size);
            Vector3d nz = new Vector3d(0, 0, -m.size);
            Vector3d[][] faces = new Vector3d[][]{
                    {top, px, pz}, {top, pz, nx}, {top, nx, nz}, {top, nz, px},
                    {bottom, pz, px}, {bottom, nx, pz}, {bottom, nz, nx}, {bottom, px, nz}
            };

            for (Vector3d[] tri : faces) {
                Vector3d v0 = worldify(m.pos.x, m.pos.y, m.pos.z, tri[0], m.yaw, m.pitch, m.roll);
                Vector3d v1 = worldify(m.pos.x, m.pos.y, m.pos.z, tri[1], m.yaw, m.pitch, m.roll);
                Vector3d v2 = worldify(m.pos.x, m.pos.y, m.pos.z, tri[2], m.yaw, m.pitch, m.roll);
                Vector3d n = v1.subtract(v0).crossProduct(v2.subtract(v0)).normalize();
                Vector3d lightDir = new Vector3d(0.6, 1.0, 0.4).normalize();
                double ndl = Math.max(0.1, n.dotProduct(lightDir));
                int faceColor = shadeColor(baseRGB, (float) ndl);

                Vector2f p0 = ProjectionUtil.project(v0.x, v0.y, v0.z);
                Vector2f p1 = ProjectionUtil.project(v1.x, v1.y, v1.z);
                Vector2f p2 = ProjectionUtil.project(v2.x, v2.y, v2.z);
                if (p0 == null || p1 == null || p2 == null) continue;
                if (Float.isNaN(p0.x) || Float.isNaN(p1.x) || Float.isNaN(p2.x)) continue;
                drawFilledTri(p0, p1, p2, faceColor);
                drawLine2D(p0, p1, glowRGB, 1.2f);
                drawLine2D(p1, p2, glowRGB, 1.2f);
                drawLine2D(p2, p0, glowRGB, 1.2f);
            }
        }
    }
    private static float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        float inv = (t - 1f);
        return 1f + inv * inv * inv;
    }

    private static float easeOutBack(float t, float overshoot) {
        t = MathHelper.clamp(t, 0f, 1f);
        float s = (overshoot <= 0f ? 1.70158f : overshoot);
        t -= 1f;
        return (t * t * ((s + 1f) * t + s) + 1f);
    }

    @Subscribe
    private void onDisplay(EventDisplay e) {
        if (e.getType() != EventDisplay.Type.PRE) {
            return;
        }
        long now = System.currentTimeMillis();
        float dt = Math.max(0.001f, (now - lastUpdateMs) / 1000f);
        lastUpdateMs = now;

        boolean hasTarget = killAura.isState() && killAura.getTarget() != null;

        if (!hasTarget && hadTargetLastTick) {
            spawnDebris(now);
        }

        updateDebris(dt);

        if (hasTarget) {
            int eid = killAura.getTarget().getEntityId();
            if (eid != lastEntityId) {
                lastEntityId = eid;
                appearStartMs = System.currentTimeMillis();
            }
            Vector3d interpolated = killAura.getTarget().getPositon(e.getPartialTicks());
            Vector2f pos = ProjectionUtil.project(interpolated.x, interpolated.y + killAura.getTarget().getHeight() / 2f, interpolated.z);

            boolean recentHit = (now - killAura.getLastAttackMs()) <= 260L;
            boolean poisoned = killAura.getTarget().isPotionActive(Effects.POISON);
            boolean useOverride = recentHit || poisoned;
            int overrideRGB = recentHit ? 0xFF0000 : (poisoned ? 0x00FF00 : 0xFFFFFF);

            switch (mode.getIndex()) {
                case 0 -> {
                    double sin = Math.sin(System.currentTimeMillis() / 1000.0);
                    float size = 80.0F;

                    GlStateManager.pushMatrix();
                    GlStateManager.translatef(pos.x, pos.y, 0);
                    GlStateManager.rotatef((float) sin * 360, 0, 0, 1);
                    GlStateManager.translatef(-pos.x, -pos.y, 0);
                    int baseGlowRGB = useOverride ? overrideRGB : HUD.getColor(0, 1);
                    int glow = ColorUtils.setAlpha(baseGlowRGB, 220);
                    DisplayUtils.drawImageGlowOnly(new ResourceLocation("expensive/images/target.png"),
                            pos.x - size / 2f, pos.y - size / 2f, size, size,
                            glow, 0.75f, 7);
                    GlStateManager.popMatrix();
                }
                case 1 -> {
                    Vector3d targetPos = interpolated;
                    double cx = targetPos.x;
                    double cy = targetPos.y + killAura.getTarget().getHeight() * 0.50;
                    double cz = targetPos.z;

                    lastCxW = cx; lastCyW = cy; lastCzW = cz; lastHeight = killAura.getTarget().getHeight();

                    float radius = 0.62f;
                    int segments = 22;
                    float tilt1 = 28f;
                    float tilt2 = -22f;

                    float dist = (float) mc.player.getDistance(killAura.getTarget());
                    float appearT = MathHelper.clamp((System.currentTimeMillis() - appearStartMs) / 600f, 0f, 1f);
                    float appearK = MathHelper.clamp(appearT * 1.2f, 0f, 1f);
                    float alphaDistK = 1.0f / (1.0f + 0.12f * dist);
                    int baseColor = ColorUtils.setAlpha(useOverride ? overrideRGB : 0xFFFFFF, (int) (190 * appearK));
                    int glowColor = ColorUtils.setAlpha(useOverride ? overrideRGB : HUD.getColor(0, 1), (int) (155 * appearK * alphaDistK));

                    net.minecraft.util.math.AxisAlignedBB bb = killAura.getTarget().getBoundingBox();
                    renderTiltedRing(cx, cy, cz, radius, tilt1, segments, baseColor, glowColor,
                            bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
                    renderTiltedRing(cx, cy, cz, radius, tilt2, segments, baseColor, glowColor,
                            bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
                }
                case 2 -> {
                    Vector3d targetPos = interpolated;
                    double cx = targetPos.x;
                    double cy = targetPos.y + killAura.getTarget().getHeight() * 0.55;
                    double cz = targetPos.z;

                    float dist = (float) mc.player.getDistance(killAura.getTarget());
                    float appearT = MathHelper.clamp((System.currentTimeMillis() - appearStartMs) / 600f, 0f, 1f);
                    float appearK = MathHelper.clamp(appearT * 1.2f, 0f, 1f);
                    float alphaDistK = 1.0f / (1.0f + 0.12f * dist);
                    int hue = useOverride ? overrideRGB : HUD.getColor(0, 1);
                    int baseRGB = ColorUtils.setAlpha(0xFFFFFF, (int) (220 * appearK));
                    int glowRGB = ColorUtils.setAlpha(hue, (int) (180 * appearK * alphaDistK));

                    double t = System.currentTimeMillis() / 1000.0;
                    double orbitR = 0.9;
                    double speed = 1.4;
                    long curMs = System.currentTimeMillis();
                    if (diamonds[0] == null) {
                        for (int i = 0; i < diamonds.length; i++) diamonds[i] = new DiamondInst();
                        nextRevealAtMs = System.currentTimeMillis() + 5000L;
                    }

                    int sw = mc.getMainWindow().getScaledWidth();
                    int sh = mc.getMainWindow().getScaledHeight();
                    float cx2d = sw / 2f, cy2d = sh / 2f;
                    int aimedIndex = -1; float aimedBest = Float.MAX_VALUE;
                    for (int ai = 0; ai < 6; ai++) {
                        double aang = t * speed + ai * (2 * Math.PI / 6.0);
                        double aox = cx + orbitR * Math.cos(aang);
                        double aoz = cz + orbitR * Math.sin(aang);
                        double aoy = cy + 0.22 * Math.sin(t * 2.0 + ai * 0.7);
                        Vector2f ap = ProjectionUtil.project(aox, aoy, aoz);
                        if (ap == null || Float.isNaN(ap.x)) continue;
                        float dx = ap.x - cx2d, dy = ap.y - cy2d;
                        float d2 = dx * dx + dy * dy;
                        if (d2 < aimedBest) { aimedBest = d2; aimedIndex = ai; }
                    }

                    if (recentHit && curMs - lastMiniAttackMs > 150L) {
                        long atk = curMs;
                        for (int i = 0; i < 6; i++) {
                            double ang = t * speed + i * (2 * Math.PI / 6.0);
                            double ox = cx + orbitR * Math.cos(ang);
                            double oz = cz + orbitR * Math.sin(ang);
                            double oy = cy + 0.22 * Math.sin(t * 2.0 + i * 0.7);
                            spawnMiniDiamondsAt(ox, oy, oz, i, atk);
                        }
                        lastMiniAttackMs = atk;
                    }

                    try {
                        double distNow = mc.player.getDistance(killAura.getTarget());
                        Vector3d tv = killAura.getTarget().getMotion();
                        boolean movingAway = lastTargetDist >= 0 && (distNow - lastTargetDist) > 0.03;
                        double tSpeed = tv.lengthSquared();
                        if (movingAway && tSpeed > 0.005 && (curMs - lastTrailSpawnMs) > 120L) {
                            spawnMiniTrailDiamondsAt(cx, cy, cz, curMs, tv);
                            lastTrailSpawnMs = curMs;
                        }
                        lastTargetDist = distNow;
                    } catch (Throwable ignore) { }
                    Vector3d top = new Vector3d(0, 0.14, 0);
                    Vector3d bottom = new Vector3d(0, -0.14, 0);
                    Vector3d px = new Vector3d(0.14, 0, 0);
                    Vector3d nx = new Vector3d(-0.14, 0, 0);
                    Vector3d pz = new Vector3d(0, 0, 0.14);
                    Vector3d nz = new Vector3d(0, 0, -0.14);

                    Vector3d[][] faces = new Vector3d[][]{
                            {top, px, pz}, {top, pz, nx}, {top, nx, nz}, {top, nz, px},
                            {bottom, pz, px}, {bottom, nx, pz}, {bottom, nz, nx}, {bottom, px, nz}
                    };

                    long nowMs = System.currentTimeMillis();
                    if (nowMs >= nextRevealAtMs) {
                        int best = -1; double bestScore = -1e9;
                        for (int i = 0; i < 6; i++) {
                            double angTest = t * speed + i * (2 * Math.PI / 6.0);
                            double oxT = cx + orbitR * Math.cos(angTest);
                            double ozT = cz + orbitR * Math.sin(angTest);
                            double oyT = cy;
                            Vector2f pp = ProjectionUtil.project(oxT, oyT, ozT);
                            if (pp == null) continue;
                            double d2 = mc.player.getDistanceSq(oxT, oyT, ozT);
                            double score = -d2;
                            if (score > bestScore) { bestScore = score; best = i; }
                        }
                        if (best >= 0) {
                            if (diamonds[best] == null) diamonds[best] = new DiamondInst();
                            diamonds[best].disasmStartMs = nowMs;
                            nextRevealAtMs = nowMs + 5000L;
                        }
                    }

                    for (int i = 0; i < 6; i++) {
                        double ang = t * speed + i * (2 * Math.PI / 6.0);
                        double ox = cx + orbitR * Math.cos(ang);
                        double oz = cz + orbitR * Math.sin(ang);
                        double oy = cy + 0.22 * Math.sin(t * 2.0 + i * 0.7);

                        double yaw = ang + Math.PI / 4.0;
                        double pitch = 0.4 * Math.sin(t * 1.6 + i);
                        double roll = 0.25 * Math.cos(t * 1.1 + i * 0.3);

                        float explodeK = 0f;
                        if (diamonds[i] != null && diamonds[i].disasmStartMs > 0) {
                            float tt = (nowMs - diamonds[i].disasmStartMs) / 1200f;
                            if (tt <= 1f) {
                                explodeK = easeOutBack(tt, 1.4f);
                            } else if (tt <= 1.6f) {
                                explodeK = Math.max(0f, 1.6f - tt);
                            } else if (tt > 6f) {
                                diamonds[i].disasmStartMs = nowMs;
                            }
                        }

                        for (Vector3d[] tri : faces) {
                            Vector3d l0 = tri[0];
                            Vector3d l1 = tri[1];
                            Vector3d l2 = tri[2];
                            if (liquidGlass.get()) {
                                l0 = liquidDeform(l0, nowMs, i);
                                l1 = liquidDeform(l1, nowMs, i + 11);
                                l2 = liquidDeform(l2, nowMs, i + 23);
                            }
                            Vector3d v0 = worldify(ox, oy, oz, l0, yaw, pitch, roll);
                            Vector3d v1 = worldify(ox, oy, oz, l1, yaw, pitch, roll);
                            Vector3d v2 = worldify(ox, oy, oz, l2, yaw, pitch, roll);

                            Vector3d n = v1.subtract(v0).crossProduct(v2.subtract(v0)).normalize();
                            Vector3d lightDir = new Vector3d(0.6, 1.0, 0.4).normalize();
                            double ndl = Math.max(0.1, n.dotProduct(lightDir));
                            int faceColor = shadeColor(baseRGB, (float) ndl);
                            if (liquidGlass.get()) {
                                int a = (faceColor >>> 24);
                                a = (int) (a * 0.6f);
                                faceColor = ColorUtils.setAlpha(faceColor, a);
                            }

                            float aimedPulse = 0f;
                            if (i == aimedIndex) {
                                aimedPulse = 0.35f + 0.25f * (float) (0.5 + 0.5 * Math.sin(curMs / 160.0));
                                if (liquidGlass.get()) aimedPulse *= 0.55f;
                            }
                            float explodeVisualK = Math.max(explodeK, Math.min(1f, aimedPulse));
                            if (explodeVisualK > 0f) {
                                float ek = explodeVisualK;
                                float lgMod = liquidGlass.get() ? 0.62f : 1.0f;
                                ek *= lgMod;
                                float press = easeOutBack(ek, 1.1f);
                                double normalOff = 0.10 * press * lgMod;
                                double radialOff = 0.07 * press * lgMod;
                                double jitter = (Math.sin(i * 13.37 + curMs * 0.005) * 0.008) * press;

                                Vector3d cdir0 = new Vector3d(v0.x - ox, v0.y - oy, v0.z - oz).normalize();
                                Vector3d cdir1 = new Vector3d(v1.x - ox, v1.y - oy, v1.z - oz).normalize();
                                Vector3d cdir2 = new Vector3d(v2.x - ox, v2.y - oy, v2.z - oz).normalize();

                                double globalScale = 1.0 + 0.10 * press * lgMod;
                                Vector3d g0 = new Vector3d(ox + (v0.x - ox) * globalScale, oy + (v0.y - oy) * globalScale, oz + (v0.z - oz) * globalScale);
                                Vector3d g1 = new Vector3d(ox + (v1.x - ox) * globalScale, oy + (v1.y - oy) * globalScale, oz + (v1.z - oz) * globalScale);
                                Vector3d g2 = new Vector3d(ox + (v2.x - ox) * globalScale, oy + (v2.y - oy) * globalScale, oz + (v2.z - oz) * globalScale);

                                v0 = new Vector3d(g0.x + n.x * normalOff + cdir0.x * (radialOff + jitter),
                                        g0.y + n.y * normalOff + cdir0.y * (radialOff + jitter),
                                        g0.z + n.z * normalOff + cdir0.z * (radialOff + jitter));
                                v1 = new Vector3d(g1.x + n.x * normalOff + cdir1.x * (radialOff + jitter),
                                        g1.y + n.y * normalOff + cdir1.y * (radialOff + jitter),
                                        g1.z + n.z * normalOff + cdir1.z * (radialOff + jitter));
                                v2 = new Vector3d(g2.x + n.x * normalOff + cdir2.x * (radialOff + jitter),
                                        g2.y + n.y * normalOff + cdir2.y * (radialOff + jitter),
                                        g2.z + n.z * normalOff + cdir2.z * (radialOff + jitter));

                                float edgeBoost = liquidGlass.get() ? (0.84f + 0.08f * ek) : (0.88f + 0.12f * ek);
                                faceColor = ColorUtils.setAlpha(faceColor, (int) ((faceColor >>> 24) * edgeBoost));
                            }

                            Vector2f p0 = ProjectionUtil.project(v0.x, v0.y, v0.z);
                            Vector2f p1 = ProjectionUtil.project(v1.x, v1.y, v1.z);
                            Vector2f p2 = ProjectionUtil.project(v2.x, v2.y, v2.z);
                            if (p0 == null || p1 == null || p2 == null) continue;
                            if (Float.isNaN(p0.x) || Float.isNaN(p1.x) || Float.isNaN(p2.x)) continue;

                            drawFilledTri(p0, p1, p2, faceColor);
                            float lw = liquidGlass.get() ? 2.2f : 2.0f;
                            drawLine2D(p0, p1, glowRGB, lw);
                            drawLine2D(p1, p2, glowRGB, lw);
                            drawLine2D(p2, p0, glowRGB, lw);
                        }

                        if (liquidGlass.get()) {
                            Vector2f pcBase = ProjectionUtil.project(ox, oy, oz);
                            if (pcBase != null && !Float.isNaN(pcBase.x)) {
                                float baseR = 12f;
                                int coreA = (int) (130 * appearK * alphaDistK);
                                int coreGlowA = (int) (120 * appearK * alphaDistK);
                                DisplayUtils.drawImageGlow(glowTex,
                                        pcBase.x - baseR / 2f,
                                        pcBase.y - baseR / 2f,
                                        baseR,
                                        baseR,
                                        ColorUtils.setAlpha(0xFFFFFF, coreA),
                                        ColorUtils.setAlpha(hue, coreGlowA),
                                        0.6f,
                                        5);
                                float blurR1 = 34f;
                                float blurR2 = 52f;
                                int blurA1 = (int) (70 * appearK * alphaDistK);
                                int blurA2 = (int) (38 * appearK * alphaDistK);
                                DisplayUtils.drawImageGlow(glowTex,
                                        pcBase.x - blurR1 / 2f,
                                        pcBase.y - blurR1 / 2f,
                                        blurR1,
                                        blurR1,
                                        ColorUtils.setAlpha(0xFFFFFF, blurA1),
                                        ColorUtils.setAlpha(hue, (int)(blurA1 * 0.9f)),
                                        0.55f,
                                        4);
                                DisplayUtils.drawImageGlow(glowTex,
                                        pcBase.x - blurR2 / 2f,
                                        pcBase.y - blurR2 / 2f,
                                        blurR2,
                                        blurR2,
                                        ColorUtils.setAlpha(0xFFFFFF, blurA2),
                                        ColorUtils.setAlpha(hue, (int)(blurA2 * 0.9f)),
                                        0.5f,
                                        3);
                                int sCount = 7;
                                for (int s = 0; s < sCount; s++) {
                                    double aa = s * (2 * Math.PI / sCount) + curMs / 420.0 + i * 0.3;
                                    float rr = baseR * 0.42f;
                                    float sx = (float) (pcBase.x + rr * Math.cos(aa));
                                    float sy = (float) (pcBase.y + rr * Math.sin(aa));
                                    float sz = 5f;
                                    DisplayUtils.drawImageGlow(glowTex,
                                            sx - 2.5f, sy - 2.5f, 5f, 5f,
                                            ColorUtils.setAlpha(0xFFFFFF, (int) (90 * appearK * alphaDistK)),
                                            ColorUtils.setAlpha(hue, (int) (100 * appearK * alphaDistK)),
                                            0.55f,
                                            3);
                                }
                            }
                        }

                        if (explodeK > 0f) {
                            int coreA = (int) (200 * appearK * alphaDistK);
                            if (liquidGlass.get()) coreA = (int) (coreA * 0.7f);
                            int coreColor = ColorUtils.setAlpha(hue, coreA);
                            Vector2f pc = ProjectionUtil.project(ox, oy, oz);
                            if (pc != null && !Float.isNaN(pc.x)) {
                                float coreR = (float) (18f + 8f * Math.sin(nowMs / 90.0 + i));
                                if (liquidGlass.get()) coreR *= 1.08f;
                                DisplayUtils.drawImageGlow(glowTex,
                                        pc.x - coreR / 2f,
                                        pc.y - coreR / 2f,
                                        coreR,
                                        coreR,
                                        ColorUtils.setAlpha(0xFFFFFF, liquidGlass.get() ? 90 : 130),
                                        coreColor,
                                        liquidGlass.get() ? 0.44f : 0.52f,
                                        6);
                                float wave = MathHelper.clamp(explodeK, 0f, 1f);
                                float waveR = 22f + 46f * wave;
                                int wa = (int) (70 * (1f - wave));
                                if (liquidGlass.get()) { waveR *= 0.8f; wa = (int) (wa * 0.5f); }
                                DisplayUtils.drawImageGlow(glowTex,
                                        pc.x - waveR / 2f,
                                        pc.y - waveR / 2f,
                                        waveR,
                                        waveR,
                                        ColorUtils.setAlpha(0xFFFFFF, wa),
                                        ColorUtils.setAlpha(hue, (int)(wa * 0.8f)),
                                        liquidGlass.get() ? 0.36f : 0.46f,
                                        4);
                                int stars = liquidGlass.get() ? 4 : 10;
                                for (int s = 0; s < stars; s++) {
                                    double aa = s * (2 * Math.PI / stars) + nowMs / 300.0 + i;
                                    float rr = coreR * 0.35f;
                                    float sx = (float) (pc.x + rr * Math.cos(aa));
                                    float sy = (float) (pc.y + rr * Math.sin(aa));
                                    float sz = 6f;
                                    DisplayUtils.drawImageGlow(glowTex,
                                            sx - 2.5f, sy - 2.5f, 5f, 5f,
                                            ColorUtils.setAlpha(0xFFFFFF, 70),
                                            ColorUtils.setAlpha(hue, 90),
                                            0.5f,
                                            4);
                                }
                            }
                        }
                    }

                    updateMiniDiamonds(dt);
                    renderMiniDiamonds(nowMs, hue, appearK, alphaDistK);
                }
                case 3 -> {
                    Vector3d targetPos = interpolated;
                    double h = killAura.getTarget().getHeight();
                    double baseY = targetPos.y + h * 0.15;

                    float dist = (float) mc.player.getDistance(killAura.getTarget());
                    float appearT = MathHelper.clamp((System.currentTimeMillis() - appearStartMs) / 600f, 0f, 1f);
                    float appearK = MathHelper.clamp(appearT * 1.2f, 0f, 1f);
                    float alphaDistK = 1.0f / (1.0f + 0.12f * dist);
                    int baseColor = ColorUtils.setAlpha(0xFFFFFF, (int) (120 * appearK));
                    int hueColor = useOverride ? overrideRGB : HUD.getColor(0, 1);
                    int glowColor = ColorUtils.setAlpha(hueColor, (int) (200 * appearK * alphaDistK));

                    double t = System.currentTimeMillis() / 1000.0;
                    int count = 3;
                    Vector2f pc = ProjectionUtil.project(targetPos.x, targetPos.y + h * 0.55, targetPos.z);
                    for (int i = 0; i < count; i++) {
                        double ringR = 0.8 + 0.25 * Math.sin(t * 1.1 + i * 0.9);
                        double ang = t * 1.35 + i * (2 * Math.PI / count);
                        double ox = targetPos.x + ringR * Math.cos(ang);
                        double oz = targetPos.z + ringR * Math.sin(ang);
                        double oy = baseY + (h * 0.65) * (0.15 + 0.85 * (0.5 + 0.5 * Math.sin(t * 1.8 + i * 0.7)));

                        Vector2f p = ProjectionUtil.project(ox, oy, oz);
                        if (p == null || Float.isNaN(p.x) || Float.isNaN(p.y)) continue;

                        float pulse = 0.85f + 0.25f * (float) Math.sin(t * 2.4 + i);
                        float sizeW = 28f * pulse;
                        float sizeH = 54f * pulse;
                        float rot = (float) Math.toDegrees(ang) * 0.5f + (float) (t * 40.0);

                        GlStateManager.pushMatrix();
                        GlStateManager.translatef(p.x, p.y, 0);
                        GlStateManager.rotatef(rot, 0, 0, 1);
                        GlStateManager.translatef(-p.x, -p.y, 0);
                        DisplayUtils.drawImageGlow(glowTex,
                                p.x - sizeW / 2f,
                                p.y - sizeH / 2f,
                                sizeW,
                                sizeH,
                                baseColor,
                                glowColor,
                                0.6f,
                                6);
                        float back = -10f;
                        GlStateManager.translatef(0, back, 0);
                        DisplayUtils.drawImageGlow(glowTex,
                                p.x - sizeW * 0.8f / 2f,
                                p.y - sizeH * 0.8f / 2f,
                                sizeW * 0.8f,
                                sizeH * 0.8f,
                                ColorUtils.setAlpha(0xFFFFFF, (int)(70 * appearK)),
                                ColorUtils.setAlpha(hueColor, (int)(120 * appearK * alphaDistK)),
                                0.5f,
                                5);
                        GlStateManager.popMatrix();
                    }
                }
            }
            hadTargetLastTick = true;
        } else {
            if (hadTargetLastTick) {
                spawnDiamondShatter(System.currentTimeMillis());
            }
            hadTargetLastTick = false;
        }

        renderDebris();
        updateDiamondShards(dt);
        renderDiamondShards();
    }

    private void spawnDiamondShatter(long nowMs) {
        diamondShards.clear();
        Vector3d cx = new Vector3d(lastCxW, lastCyW, lastCzW);
        Vector3d top = new Vector3d(0, 0.14, 0);
        Vector3d bottom = new Vector3d(0, -0.14, 0);
        Vector3d px = new Vector3d(0.14, 0, 0);
        Vector3d nx = new Vector3d(-0.14, 0, 0);
        Vector3d pz = new Vector3d(0, 0, 0.14);
        Vector3d nz = new Vector3d(0, 0, -0.14);
        Vector3d[][] faces = new Vector3d[][]{
                {top, px, pz}, {top, pz, nx}, {top, nx, nz}, {top, nz, px},
                {bottom, pz, px}, {bottom, nx, pz}, {bottom, nz, nx}, {bottom, px, nz}
        };
        double yaw = Math.random() * Math.PI * 2, pitch = Math.random() * 0.6 - 0.3, roll = Math.random() * 0.6 - 0.3;
        for (Vector3d[] tri : faces) {
            Vector3d v0 = worldify(cx.x, cx.y, cx.z, tri[0], yaw, pitch, roll);
            Vector3d v1 = worldify(cx.x, cx.y, cx.z, tri[1], yaw, pitch, roll);
            Vector3d v2 = worldify(cx.x, cx.y, cx.z, tri[2], yaw, pitch, roll);
            Vector3d n = v1.subtract(v0).crossProduct(v2.subtract(v0)).normalize();
            double sp = 0.6 + Math.random() * 0.8;
            double vx = n.x * sp + (Math.random() - 0.5) * 0.3;
            double vy = n.y * sp + (Math.random() - 0.2) * 0.2;
            double vz = n.z * sp + (Math.random() - 0.5) * 0.3;
            diamondShards.add(new DiamondShard(v0, v1, v2, vx, vy, vz, nowMs));
        }
        im.expensive.utils.client.ClientUtil.playSoundOneShot("glass_break", 90f);
    }

    private void updateDiamondShards(float dt) {
        if (diamondShards.isEmpty()) return;
        for (Iterator<DiamondShard> it = diamondShards.iterator(); it.hasNext(); ) {
            DiamondShard s = it.next();
            s.vy -= 0.04 * dt;
            s.vx *= 0.995; s.vy *= 0.995; s.vz *= 0.995;
            s.a = Math.max(0f, s.a - 120f * dt);
            s.v0 = s.v0.add(s.vx * dt, s.vy * dt, s.vz * dt);
            s.v1 = s.v1.add(s.vx * dt, s.vy * dt, s.vz * dt);
            s.v2 = s.v2.add(s.vx * dt, s.vy * dt, s.vz * dt);
            if (s.a <= 2f) it.remove();
        }
    }

    private void renderDiamondShards() {
        if (diamondShards.isEmpty()) return;
        int hue = HUD.getColor(0, 1);
        for (DiamondShard s : diamondShards) {
            Vector2f p0 = ProjectionUtil.project(s.v0.x, s.v0.y, s.v0.z);
            Vector2f p1 = ProjectionUtil.project(s.v1.x, s.v1.y, s.v1.z);
            Vector2f p2 = ProjectionUtil.project(s.v2.x, s.v2.y, s.v2.z);
            if (p0 == null || p1 == null || p2 == null) continue;
            int face = ColorUtils.setAlpha(0xFFFFFF, (int) s.a);
            drawFilledTri(p0, p1, p2, face);
            int edge = ColorUtils.setAlpha(hue, (int) Math.min(180, s.a));
            drawLine2D(p0, p1, edge, 1.5f);
            drawLine2D(p1, p2, edge, 1.5f);
            drawLine2D(p2, p0, edge, 1.5f);
            Vector2f c = new Vector2f((p0.x + p1.x + p2.x) / 3f, (p0.y + p1.y + p2.y) / 3f);
            float r = 10f;
            DisplayUtils.drawImageGlow(glowTex, c.x - r/2f, c.y - r/2f, r, r,
                    ColorUtils.setAlpha(0xFFFFFF, (int)(s.a * 0.6f)),
                    ColorUtils.setAlpha(hue, (int)(s.a * 0.7f)),
                    0.6f, 4);
        }
    }

    private static class DiamondInst {
        long disasmStartMs = -1L;
    }

    private static class DiamondShard {
        Vector3d v0, v1, v2;
        double vx, vy, vz;
        float a = 220f;
        long born;
        DiamondShard(Vector3d v0, Vector3d v1, Vector3d v2, double vx, double vy, double vz, long born) {
            this.v0 = v0; this.v1 = v1; this.v2 = v2;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.born = born;
        }
    }

    private void renderTiltedRing(double cx, double cy, double cz,
                                  float radius, float tiltDeg, int segments,
                                  int baseColor, int glowColor,
                                  double bbMinX, double bbMinY, double bbMinZ,
                                  double bbMaxX, double bbMaxY, double bbMaxZ) {
        if (segments < 3) return;
        float tilt = (float) Math.toRadians(tiltDeg);
        for (int i = 0; i < segments; i++) {
            float t0 = (i / (float) segments) * (float) (Math.PI * 2.0);
            float t1 = ((i + 1) / (float) segments) * (float) (Math.PI * 2.0);

            double x0 = cx + radius * Math.cos(t0);
            double z0 = cz + radius * Math.sin(t0) * Math.cos(tilt);
            double y0 = cy + radius * Math.sin(t0) * Math.sin(tilt);

            double x1 = cx + radius * Math.cos(t1);
            double z1 = cz + radius * Math.sin(t1) * Math.cos(tilt);
            double y1 = cy + radius * Math.sin(t1) * Math.sin(tilt);

            Vector2f p0 = ProjectionUtil.project(x0, y0, z0);
            Vector2f p1 = ProjectionUtil.project(x1, y1, z1);
            if (p0 == null || p1 == null) continue;
            if (Float.isNaN(p0.x) || Float.isNaN(p0.y) || Float.isNaN(p1.x) || Float.isNaN(p1.y)) continue;

            float cx2 = (p0.x + p1.x) * 0.5f;
            float cy2 = (p0.y + p1.y) * 0.5f;
            float dx = p1.x - p0.x;
            float dy = p1.y - p0.y;
            float segLen = (float) Math.hypot(dx, dy);
            if (segLen < 1.0f) continue;
            float angDeg = (float) Math.toDegrees(Math.atan2(dy, dx));

            Vector3d cam = mc.player.getEyePosition(1.0f);
            double mx = (x0 + x1) * 0.5;
            double my = (y0 + y1) * 0.5;
            double mz = (z0 + z1) * 0.5;
            double rdx = mx - cam.x;
            double rdy = my - cam.y;
            double rdz = mz - cam.z;
            double tMax = Math.sqrt(rdx * rdx + rdy * rdy + rdz * rdz);
            boolean occluded = rayIntersectsAABB(cam.x, cam.y, cam.z, rdx, rdy, rdz, tMax,
                    bbMinX, bbMinY, bbMinZ, bbMaxX, bbMaxY, bbMaxZ);
            int ba = (baseColor >>> 24) & 0xFF;
            int ga = (glowColor >>> 24) & 0xFF;
            float occMul = occluded ? 0.35f : 1.0f;
            int baseC = ColorUtils.setAlpha(baseColor, (int) (ba * occMul));
            int glowC = ColorUtils.setAlpha(glowColor, (int) (ga * occMul));

            float thickness = 20f;

            GlStateManager.pushMatrix();
            GlStateManager.translatef(cx2, cy2, 0);
            GlStateManager.rotatef(angDeg, 0, 0, 1);
            GlStateManager.translatef(-cx2, -cy2, 0);
            DisplayUtils.drawImageGlow(chainTex,
                    cx2 - segLen / 2f,
                    cy2 - thickness / 2f,
                    segLen,
                    thickness,
                    baseC,
                    glowC,
                    0.45f,
                    4);
            GlStateManager.popMatrix();
        }
    }

    private Vector3d worldify(double ox, double oy, double oz, Vector3d local, double yaw, double pitch, double roll) {
        Vector3d v = new Vector3d(local.x, local.y, local.z);
        v = rotY(v, yaw);
        v = rotX(v, pitch);
        v = rotZ(v, roll);
        return new Vector3d(ox + v.x, oy + v.y, oz + v.z);
    }

    private Vector3d rotX(Vector3d v, double a) {
        double ca = Math.cos(a), sa = Math.sin(a);
        double y = v.y * ca - v.z * sa;
        double z = v.y * sa + v.z * ca;
        return new Vector3d(v.x, y, z);
    }

    private Vector3d rotY(Vector3d v, double a) {
        double ca = Math.cos(a), sa = Math.sin(a);
        double x = v.x * ca + v.z * sa;
        double z = -v.x * sa + v.z * ca;
        return new Vector3d(x, v.y, z);
    }

    private Vector3d rotZ(Vector3d v, double a) {
        double ca = Math.cos(a), sa = Math.sin(a);
        double x = v.x * ca - v.y * sa;
        double y = v.x * sa + v.y * ca;
        return new Vector3d(x, y, v.z);
    }

    private static Vector3d liquidDeform(Vector3d v, long nowMs, int seed) {
        double t = nowMs / 1000.0;
        double wx = 1.0 + 0.03 * Math.sin(t * 1.7 + seed * 0.9);
        double wy = 1.0 + 0.03 * Math.cos(t * 1.3 + seed * 0.7);
        double wz = 1.0 + 0.03 * Math.sin(t * 2.0 + seed * 1.1);
        Vector3d aniso = new Vector3d(v.x * wx, v.y * wy, v.z * wz);
        double ripple = 0.06 * Math.sin(t * 2.2 + aniso.x * 8.0 + aniso.y * 9.0 + aniso.z * 7.0 + seed);
        double k = 1.0 + ripple;
        return new Vector3d(aniso.x * k, aniso.y * k, aniso.z * k);
    }

    private int shadeColor(int rgba, float k) {
        int a = (rgba >>> 24) & 0xFF;
        int r = (rgba >>> 16) & 0xFF;
        int g = (rgba >>> 8) & 0xFF;
        int b = (rgba) & 0xFF;
        r = Math.min(255, (int) (r * k));
        g = Math.min(255, (int) (g * k));
        b = Math.min(255, (int) (b * k));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawFilledTri(Vector2f p0, Vector2f p1, Vector2f p2, int rgba) {
        int a = (rgba >>> 24) & 0xFF;
        int r = (rgba >>> 16) & 0xFF;
        int g = (rgba >>> 8) & 0xFF;
        int b = (rgba) & 0xFF;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableTexture();
        RenderSystem.disableCull();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(p0.x, p0.y, 0).color(r, g, b, a).endVertex();
        buf.pos(p1.x, p1.y, 0).color(r, g, b, a).endVertex();
        buf.pos(p2.x, p2.y, 0).color(r, g, b, a).endVertex();
        tess.draw();
        RenderSystem.enableCull();
        RenderSystem.enableTexture();
        RenderSystem.enableDepthTest();
    }

    private void drawLine2D(Vector2f p0, Vector2f p1, int rgba, float width) {
        int a = (rgba >>> 24) & 0xFF;
        int r = (rgba >>> 16) & 0xFF;
        int g = (rgba >>> 8) & 0xFF;
        int b = (rgba) & 0xFF;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();
        RenderSystem.disableCull();
        GL11.glLineWidth(width);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(p0.x, p0.y, 0).color(r, g, b, a).endVertex();
        buf.pos(p1.x, p1.y, 0).color(r, g, b, a).endVertex();
        tess.draw();
        RenderSystem.enableCull();
        RenderSystem.enableTexture();
    }


    private void spawnDebris(long nowMs) {
        int segments = 22;
        float tiltDeg1 = 28f;
        float tiltDeg2 = -22f;
        float radius = 0.62f;

        im.expensive.utils.client.ClientUtil.playSoundOneShot("chains", 100f);

        spawnRingDebris(radius, tiltDeg1, segments);
        spawnRingDebris(radius, tiltDeg2, segments);
    }

    private void spawnRingDebris(float radius, float tiltDeg, int segments) {
        float tilt = (float) Math.toRadians(tiltDeg);
        int step = Math.max(2, segments / 10);
        for (int i = 0; i < segments; i += step) {
            float tDeg = (i / (float) segments) * 360f;
            float rad = (float) Math.toRadians(tDeg);

            double x = lastCxW;
            double y = lastCyW;
            double z = lastCzW;

            double tx = -Math.sin(rad);
            double tz = Math.cos(rad) * Math.cos(tilt);
            double ty = Math.cos(rad) * Math.sin(tilt);
            double nx = Math.cos(rad);
            double nz = Math.sin(rad) * Math.cos(tilt);
            double ny = Math.sin(rad) * Math.sin(tilt);

            double speedT = 1.2 + 0.3 * (Math.random());
            double speedN = 0.6 + 0.5 * (Math.random());

            Fragment f = new Fragment();
            f.x = x; f.y = y; f.z = z;
            f.vx = tx * speedT + nx * speedN;
            f.vy = ty * speedT + ny * speedN + 1.1;
            f.vz = tz * speedT + nz * speedN;
            f.rotDeg = (float) (Math.random() * 360.0);
            f.vrotDeg = (float) (-120.0 + Math.random() * 240.0);
            f.maxLife = (float) (0.9 + Math.random() * 0.6);
            f.life = f.maxLife;

            f.linkLen = (float) (26.0 + Math.random() * 10.0);
            f.thickness = (float) (20.0 + Math.random() * 10.0);

            f.arc = true;
            f.arcRadius = radius;
            f.arcTiltDeg = tiltDeg;
            f.arcAngleStartDeg = tDeg + (float) (-6 + Math.random() * 12);
            f.arcAngleSpanDeg = (float) (28.0 + Math.random() * 20.0);
            float approxPerSeg = 360f / segments;
            f.segCount = Math.max(3, Math.min(6, Math.round(f.arcAngleSpanDeg / approxPerSeg)));

            debris.add(f);
        }
    }

    private void updateDebris(float dt) {
        if (debris.isEmpty()) return;
        final double g = 9.8 * 0.9;
        final double drag = 0.96;
        final double restitution = 0.42;
        final double frictionXY = 0.68;
        final double stopEps = 0.05;

        java.util.Iterator<Fragment> it = debris.iterator();
        while (it.hasNext()) {
            Fragment f = it.next();
            f.vy -= g * dt;
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            f.z += f.vz * dt;
            f.vx *= drag; f.vy *= 0.98; f.vz *= drag;
            f.rotDeg += f.vrotDeg * dt;
            f.life -= dt;

            try {
                if (mc.world != null) {
                    int bx = net.minecraft.util.math.MathHelper.floor(f.x);
                    int bz = net.minecraft.util.math.MathHelper.floor(f.z);
                    int groundY = mc.world.getHeight(net.minecraft.world.gen.Heightmap.Type.MOTION_BLOCKING, new net.minecraft.util.math.BlockPos(bx, 0, bz)).getY();
                    double yGround = groundY + 0.02;
                    if (f.y <= yGround) {
                        f.y = yGround;
                        if (Math.abs(f.vy) > stopEps) {
                            f.vy = -f.vy * restitution;
                        } else {
                            f.vy = 0;
                        }
                        f.vx *= frictionXY;
                        f.vz *= frictionXY;
                        f.vrotDeg *= 0.75f;
                    }
                }
            } catch (Throwable ignore) { }

            if (f.life <= 0) it.remove();
        }
    }

    private void renderDebris() {
        if (debris.isEmpty()) return;
        for (Fragment f : debris) {
            float k = MathHelper.clamp(f.life / f.maxLife, 0f, 1f);
            int baseColor = ColorUtils.setAlpha(-1, (int) (220 * k));
            int glowColor = ColorUtils.setAlpha(HUD.getColor(0, 1), (int) (180 * k));

            if (f.arc && f.segCount > 0 && f.arcRadius > 0f) {
                float tilt = (float) Math.toRadians(f.arcTiltDeg);
                float dAng = f.arcAngleSpanDeg / Math.max(1, (f.segCount));
                for (int j = 0; j < f.segCount; j++) {
                    float a0 = f.arcAngleStartDeg + j * dAng;
                    float a1 = a0 + dAng;
                    float r0 = (float) Math.toRadians(a0);
                    float r1 = (float) Math.toRadians(a1);

                    double lx0 = Math.cos(r0) * f.arcRadius;
                    double lz0 = Math.sin(r0) * f.arcRadius * Math.cos(tilt);
                    double ly0 = Math.sin(r0) * f.arcRadius * Math.sin(tilt);
                    double lx1 = Math.cos(r1) * f.arcRadius;
                    double lz1 = Math.sin(r1) * f.arcRadius * Math.cos(tilt);
                    double ly1 = Math.sin(r1) * f.arcRadius * Math.sin(tilt);

                    double x0 = f.x + lx0; double y0 = f.y + ly0; double z0 = f.z + lz0;
                    double x1 = f.x + lx1; double y1 = f.y + ly1; double z1 = f.z + lz1;

                    Vector2f p0 = ProjectionUtil.project(x0, y0, z0);
                    Vector2f p1 = ProjectionUtil.project(x1, y1, z1);
                    if (p0 == null || p1 == null) continue;
                    if (Float.isNaN(p0.x) || Float.isNaN(p0.y) || Float.isNaN(p1.x) || Float.isNaN(p1.y)) continue;

                    float dx = p1.x - p0.x; float dy = p1.y - p0.y;
                    float segLen = (float) Math.hypot(dx, dy);
                    if (segLen < 1.5f) continue;
                    float angDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
                    float cx2 = (p0.x + p1.x) * 0.5f;
                    float cy2 = (p0.y + p1.y) * 0.5f;

                    float nx = -dy / segLen; float ny = dx / segLen;
                    float shadowShift = 1.2f;
                    int shadowColor = ColorUtils.setAlpha(0x000000, (int) (80 * k));

                    GlStateManager.pushMatrix();
                    GlStateManager.translatef(cx2 + nx * shadowShift, cy2 + ny * shadowShift, 0);
                    GlStateManager.rotatef(angDeg, 0, 0, 1);
                    GlStateManager.translatef(-(cx2 + nx * shadowShift), -(cy2 + ny * shadowShift), 0);
                    DisplayUtils.drawImageGlow(chainTex,
                            cx2 - f.linkLen / 2f,
                            cy2 - f.thickness / 2f,
                            f.linkLen,
                            f.thickness,
                            shadowColor,
                            shadowColor,
                            0.25f,
                            2);
                    GlStateManager.popMatrix();

                    GlStateManager.pushMatrix();
                    GlStateManager.translatef(cx2, cy2, 0);
                    GlStateManager.rotatef(angDeg, 0, 0, 1);
                    GlStateManager.translatef(-cx2, -cy2, 0);
                    float drawLen = Math.max(10f, Math.min(f.linkLen, segLen * 1.25f));
                    DisplayUtils.drawImageGlow(chainTex,
                            cx2 - drawLen / 2f,
                            cy2 - f.thickness / 2f,
                            drawLen,
                            f.thickness,
                            baseColor,
                            glowColor,
                            0.5f,
                            4);
                    GlStateManager.popMatrix();
                }
            } else {
                Vector2f sp = ProjectionUtil.project(f.x, f.y, f.z);
                if (sp == null || Float.isNaN(sp.x) || Float.isNaN(sp.y)) continue;
                GlStateManager.pushMatrix();
                GlStateManager.translatef(sp.x, sp.y, 0);
                GlStateManager.rotatef(f.rotDeg, 0, 0, 1);
                GlStateManager.translatef(-sp.x, -sp.y, 0);
                DisplayUtils.drawImageGlow(chainTex,
                        sp.x - f.linkLen / 2f,
                        sp.y - f.thickness / 2f,
                        f.linkLen,
                        f.thickness,
                        baseColor,
                        glowColor,
                        0.45f,
                        3);
                GlStateManager.popMatrix();
            }
        }
    }

    private static boolean rayIntersectsAABB(double ox, double oy, double oz,
                                             double dx, double dy, double dz, double tMax,
                                             double minX, double minY, double minZ,
                                             double maxX, double maxY, double maxZ) {
        double tmin = 0.0;
        double tmax = tMax;

        if (Math.abs(dx) < 1e-8) {
            if (ox < minX || ox > maxX) return false;
        } else {
            double inv = 1.0 / dx;
            double t1 = (minX - ox) * inv;
            double t2 = (maxX - ox) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmax < tmin) return false;
        }

        if (Math.abs(dy) < 1e-8) {
            if (oy < minY || oy > maxY) return false;
        } else {
            double inv = 1.0 / dy;
            double t1 = (minY - oy) * inv;
            double t2 = (maxY - oy) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmax < tmin) return false;
        }

        if (Math.abs(dz) < 1e-8) {
            if (oz < minZ || oz > maxZ) return false;
        } else {
            double inv = 1.0 / dz;
            double t1 = (minZ - oz) * inv;
            double t2 = (maxZ - oz) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmax < tmin) return false;
        }

        return tmax >= 0.0 && tmin <= tMax;
    }

    class Fragment {
        double x, y, z;
        double vx, vy, vz;
        float rotDeg, vrotDeg;
        float life, maxLife;
        float linkLen, thickness;
        boolean arc;
        float arcRadius;
        float arcTiltDeg;
        float arcAngleStartDeg;
        float arcAngleSpanDeg;
        int segCount;
    }
}
