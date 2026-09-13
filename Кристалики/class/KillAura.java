package im.expensive.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.expensive.Expensive;
import im.expensive.command.friends.FriendStorage;
import im.expensive.events.EventInput;
import im.expensive.events.EventMotion;
import im.expensive.events.EventPacket;
import im.expensive.events.EventUpdate;
import im.expensive.functions.api.Category;
import im.expensive.functions.api.Function;
import im.expensive.functions.api.FunctionRegister;
import im.expensive.functions.settings.impl.BooleanSetting;
import im.expensive.functions.settings.impl.ModeListSetting;
import im.expensive.functions.settings.impl.ModeSetting;
import im.expensive.functions.settings.impl.SliderSetting;
import im.expensive.utils.math.SensUtils;
import net.minecraft.network.play.client.CChatMessagePacket;
import net.minecraft.util.text.StringTextComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import im.expensive.utils.math.StopWatch;
import im.expensive.utils.player.InventoryUtil;
import im.expensive.utils.player.MouseUtil;
import im.expensive.utils.player.MoveUtils;
import lombok.Getter;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static java.lang.Math.hypot;
import static net.minecraft.util.math.MathHelper.clamp;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

@FunctionRegister(name = "KillAura", type = Category.Combat)
public class KillAura extends Function {
    @Getter
    private final ModeSetting type = new ModeSetting("Тип", "Плавная", "Плавная", "Резкая", "Neuro");
    private final SliderSetting attackRange = new SliderSetting("Дистанция аттаки", 3f, 3f, 6f, 0.1f);

    final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Голые", true),
            new BooleanSetting("Мобы", false),
            new BooleanSetting("Животные", false),
            new BooleanSetting("Друзья", false),
            new BooleanSetting("Голые невидимки", true),
            new BooleanSetting("Невидимки", true));

    @Getter
    final ModeListSetting options = new ModeListSetting("Опции",
            new BooleanSetting("Только криты", true),
            new BooleanSetting("Ломать щит", true),
            new BooleanSetting("Отжимать щит", true),
            new BooleanSetting("Ускорять ротацию при атаке", false),
            new BooleanSetting("Синхронизировать атаку с ТПС", false),
            new BooleanSetting("Фокусировать одну цель", true),
            new BooleanSetting("Коррекция движения", true));
    final ModeSetting correctionType = new ModeSetting("Тип коррекции", "Незаметный", "Незаметный", "Сфокусированный");

    @Getter
    private final StopWatch stopWatch = new StopWatch();
    private Vector2f rotateVector = new Vector2f(0, 0);
    @Getter
    private LivingEntity target;
    private Entity selected;

    int ticks = 0;
    boolean isRotated;

    final AutoPotion autoPotion;


    private final BooleanSetting multiPoints = new BooleanSetting("Мультипоинты", true);
    private final SliderSetting mpSwitchMin = new SliderSetting("Смена точки мин", 350f, 200f, 1500f, 10f);
    private final SliderSetting mpSwitchMax = new SliderSetting("Смена точки макс", 700f, 300f, 2000f, 10f);
    private final SliderSetting speedMin = new SliderSetting("Мин. скорость", 35f, 10f, 120f, 1f);
    private final SliderSetting speedMax = new SliderSetting("Макс. скорость", 80f, 20f, 180f, 1f);
    private final SliderSetting jitterAmp = new SliderSetting("Тряска базовая", 0.6f, 0.0f, 15.0f, 0.05f);
    private final SliderSetting jitterSpeed = new SliderSetting("Скорость тряски (база)", 2.0f, 0.1f, 50.0f, 0.1f);

    private final SliderSetting jitterYawMul = new SliderSetting("Множитель тряски Yaw", 2.5f, 0.0f, 90.0f, 0.1f);
    private final SliderSetting jitterPitchMul = new SliderSetting("Множитель тряски Pitch", 5.0f, 0.0f, 90.0f, 0.1f);
    private final SliderSetting jitterYawSpeedMul = new SliderSetting("Замедление тряски Yaw", 0.035f, 0.005f, 10f, 0.001f);
    private final SliderSetting jitterPitchSpeedMul = new SliderSetting("Замедление тряски Pitch", 0.040f, 0.005f, 10f, 0.001f);


    private Vector3d aimPoint = null;
    private long nextSwitchAt = 0L;
    private long lastAttackMs = 0L;
    private float jitterPhase = 0f;
    private float currentYawSpeed = 60f;
    private float currentPitchSpeed = 30f;
    private final Random rng = new Random();
    private boolean currentAimIsHead = false;

    private float microYaw = 0f, microPitch = 0f;
    private float microTargetYaw = 0f, microTargetPitch = 0f;
    private long microNextAt = 0L;

    private final SliderSetting microYawRadius = new SliderSetting("Micro радиус Yaw", 10.0f, 0.0f, 15.0f, 0.5f);
    private final SliderSetting microPitchRadius = new SliderSetting("Micro радиус Pitch", 6.0f, 0.0f, 10.0f, 0.5f);
    private final SliderSetting microLerp = new SliderSetting("Micro скорость сглаживания", 0.035f, 0.005f, 0.2f, 0.001f);
    private final SliderSetting microIntervalMin = new SliderSetting("Micro интервал мин (мс)", 600f, 100f, 3000f, 10f);
    private final SliderSetting microIntervalMax = new SliderSetting("Micro интервал макс (мс)", 1200f, 200f, 4000f, 10f);
    private final BooleanSetting microEnabled = new BooleanSetting("Micro смещения", false);
    private boolean lookRight = false;

    private float jitterPhaseYaw = 0f;

    private long sweepStartMs = 0L;
    private int sweepDurationMs = 150;
    private Vector3d sweepFrom = null, sweepCtrl = null, sweepTo = null;

    private long attackAimUntilMs = 0L;
    private float attackYaw = 0f, attackPitch = 0f;

    private boolean attackedThisAir = false;

    private boolean neuroTraining = false;
    private boolean neuroPaused = false;
    private double sumYawSpeed = 0.0;
    private double sumPitchSpeed = 0.0;
    private long samples = 0L;
    private float neuroYawSpeed = 60f;
    private float neuroPitchSpeed = 30f;
    private static final Path NEURO_MODEL_PATH = Paths.get("expensive", "neuro", "killaura.model");


    public KillAura(AutoPotion autoPotion) {
        this.autoPotion = autoPotion;
        addSettings(type, attackRange, targets, options, correctionType,
                multiPoints, mpSwitchMin, mpSwitchMax, speedMin, speedMax,
                jitterAmp, jitterSpeed, jitterYawMul, jitterPitchMul, jitterYawSpeedMul, jitterPitchSpeedMul,
                microYawRadius, microPitchRadius, microLerp, microIntervalMin, microIntervalMax,
                microEnabled);
        loadNeuroModel();
    }

    @Subscribe
    public void onPacket(EventPacket ev) {
        if (!ev.isSend()) return;
        if (!(ev.getPacket() instanceof CChatMessagePacket)) return;
        CChatMessagePacket p = (CChatMessagePacket) ev.getPacket();
        String msg = p.getMessage().trim();
        if (!msg.startsWith(".neuro")) return;

        ev.cancel();

        String lower = msg.toLowerCase();
        if (lower.equals(".neuro learn start")) {
            neuroTraining = true;
            neuroPaused = false;
            sumYawSpeed = 0.0; sumPitchSpeed = 0.0; samples = 0L;
            mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent("Neuro: запись начата"));
        } else if (lower.equals(".neuro learn stop")) {
            if (neuroTraining) {
                neuroPaused = true;
                mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent("Neuro: запись приостановлена"));
            } else {
                mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent("Neuro: обучение не запущено"));
            }
        } else if (lower.equals(".neuro learn end")) {
            if (neuroTraining) {
                neuroPaused = false;
                neuroTraining = false;
                finalizeNeuroTraining();
                mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent("Neuro: обучение завершено и сохранено"));
            } else {
                mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent("Neuro: обучение не запущено"));
            }
        } else {
            mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent("Neuro: неизвестная команда. Используйте .neuro learn start/stop/end"));
        }
    }

    private void finalizeNeuroTraining() {
        if (samples <= 0L) return;
        float avgYaw = (float) (sumYawSpeed / (double) samples);
        float avgPitch = (float) (sumPitchSpeed / (double) samples);
        neuroYawSpeed = Math.max(1f, avgYaw);
        neuroPitchSpeed = Math.max(1f, avgPitch);
        saveNeuroModel();
    }

    private void loadNeuroModel() {
        try {
            if (Files.exists(NEURO_MODEL_PATH)) {
                java.util.List<String> lines = Files.readAllLines(NEURO_MODEL_PATH, StandardCharsets.UTF_8);
                for (String s : lines) {
                    String line = s.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] kv = line.split("=");
                    if (kv.length == 2) {
                        String k = kv[0].trim();
                        String v = kv[1].trim();
                        if (k.equalsIgnoreCase("yawSpeed")) neuroYawSpeed = Float.parseFloat(v);
                        if (k.equalsIgnoreCase("pitchSpeed")) neuroPitchSpeed = Float.parseFloat(v);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveNeuroModel() {
        try {
            Files.createDirectories(NEURO_MODEL_PATH.getParent());
            String data = "# KillAura Neuro Model\n" +
                    "yawSpeed=" + neuroYawSpeed + "\n" +
                    "pitchSpeed=" + neuroPitchSpeed + "\n";
            Files.write(NEURO_MODEL_PATH, data.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }

    @Subscribe
    public void onInput(EventInput eventInput) {
        if (!options.getValueByName("Коррекция движения").get() || mc.player == null) return;

        MoveUtils.fixMovement(eventInput, rotateVector.x);

        if (correctionType.is("Сфокусированный")) {
            if (eventInput.getForward() < 0f) {
                eventInput.setForward(0f);
            }
        }
    }

    private Vector3d getAimPoint() {
        if (target == null) {
            currentAimIsHead = false;
            return mc.player.getLookVec().add(mc.player.getEyePosition(1.0F));
        }

        long now = System.currentTimeMillis();
        long sinceSweep = now - sweepStartMs;
        if (sweepStartMs != 0L && sinceSweep >= 0 && sinceSweep <= sweepDurationMs && sweepFrom != null && sweepCtrl != null && sweepTo != null) {
            float t = (float) sinceSweep / (float) sweepDurationMs;
            float it = 1.0f - t;
            double x = it*it * sweepFrom.x + 2*it*t * sweepCtrl.x + t*t * sweepTo.x;
            double y = it*it * sweepFrom.y + 2*it*t * sweepCtrl.y + t*t * sweepTo.y;
            double z = it*it * sweepFrom.z + 2*it*t * sweepCtrl.z + t*t * sweepTo.z;
            currentAimIsHead = false;
            return new Vector3d(x, y, z);
        }

        if (aimPoint == null || now >= nextSwitchAt) {
            int dir = rng.nextBoolean() ? 1 : -1;
            double jitterF = (rng.nextDouble() - 0.5) * 0.2 * target.getWidth();
            double jitterL = (rng.nextDouble() - 0.5) * 0.3 * target.getWidth();
            Vector3d base = outsidePoint(target, dir);
            aimPoint = new Vector3d(base.x + jitterL, base.y, base.z + jitterF);
            long minMs = Math.round(mpSwitchMin.get());
            long maxMs = Math.round(mpSwitchMax.get());
            if (maxMs < minMs) { long t2 = minMs; minMs = maxMs; maxMs = t2; }
            nextSwitchAt = now + (minMs + rng.nextInt((int) Math.max(1, (maxMs - minMs))));
        }
        currentAimIsHead = false;
        return aimPoint;
    }

    private Vector3d centerPoint(LivingEntity e) {
        double minY = e.getBoundingBox().minY;
        double maxY = e.getBoundingBox().maxY;
        double y = (minY + maxY) * 0.5;
        return new Vector3d(e.getPosX(), y, e.getPosZ());
    }

    private Vector3d randomBodyPoint(LivingEntity e) {
        double minY = e.getBoundingBox().minY;
        double maxY = e.getBoundingBox().maxY;
        double height = Math.max(0.001, maxY - minY);
        double width = Math.max(0.001, e.getWidth());

        double cx = e.getPosX();
        double cz = e.getPosZ();

        double t = 0.45 + rng.nextDouble() * 0.4;
        double y = minY + height * t;

        double r = (0.15 + rng.nextDouble() * 0.35) * width;
        double a = rng.nextDouble() * Math.PI * 2.0;
        double dx = Math.cos(a) * r;
        double dz = Math.sin(a) * r;

        return new Vector3d(cx + dx, y, cz + dz);
    }

    private Vector3d outsidePoint(LivingEntity e) {
        double minY = e.getBoundingBox().minY;
        double maxY = e.getBoundingBox().maxY;
        double y = minY + (maxY - minY) * 0.8;
        double distance = e.getWidth() * 0.8 + 0.2;
        float yawRad = (float) Math.toRadians(rotateVector.x);
        double dx = -Math.sin(yawRad) * distance;
        double dz =  Math.cos(yawRad) * distance;
        return new Vector3d(e.getPosX() + dx, y, e.getPosZ() + dz);
    }

    private Vector3d outsidePoint(LivingEntity e, int dir) {
        double minY = e.getBoundingBox().minY;
        double maxY = e.getBoundingBox().maxY;
        double y = minY + (maxY - minY) * 0.8;
        double forward = e.getWidth() * 0.8 + 0.2;
        double lateral = e.getWidth() * 0.6 * Math.signum(dir);
        float yawRad = (float) Math.toRadians(rotateVector.x);
        double fdx = -Math.sin(yawRad) * forward;
        double fdz =  Math.cos(yawRad) * forward;
        double rdx =  Math.cos(yawRad) * lateral;
        double rdz =  Math.sin(yawRad) * lateral;
        return new Vector3d(e.getPosX() + fdx + rdx, y, e.getPosZ() + fdz + rdz);
    }

    private Vector2f anglesTo(Vector3d worldPoint) {
        Vector3d eye = mc.player.getEyePosition(1.0F);
        Vector3d v = worldPoint.subtract(eye);
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(v.z, v.x)) - 90);
        float pitch = (float) (-Math.toDegrees(Math.atan2(v.y, hypot(v.x, v.z))));
        return new Vector2f(yaw, pitch);
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (options.getValueByName("Фокусировать одну цель").get() && (target == null || !isValid(target)) || !options.getValueByName("Фокусировать одну цель").get()) {
            updateTarget();
        }

        if (target != null && !(autoPotion.isState() && autoPotion.isActive())) {
            isRotated = false;
            if (mc.player.isOnGround()) attackedThisAir = false;
            if (shouldPlayerFalling()) {
                updateAttack();
                ticks = 2;
            }
            if (type.is("Резкая")) {
                if (ticks > 0) {
                    currentYawSpeed = 180f;
                    currentPitchSpeed = 90f;
                    updateRotation(true, 180, 90);
                    ticks--;
                } else {
                    reset();
                }
            } else {
                if (!isRotated) {
                    if (type.is("Neuro")) {
                        currentYawSpeed = neuroYawSpeed;
                        currentPitchSpeed = neuroPitchSpeed;
                    } else {
                        currentYawSpeed = 80f;
                        currentPitchSpeed = 35f;
                    }
                    updateRotation(false, 80, 35);
                }
            }

            if (neuroTraining && !neuroPaused) {
                sumYawSpeed += currentYawSpeed;
                sumPitchSpeed += currentPitchSpeed;
                samples++;
            }

        } else {
            reset();
        }
    }

    @Subscribe
    private void onWalking(EventMotion e) {
        if (target == null || autoPotion.isState() && autoPotion.isActive()) return;

        long now = System.currentTimeMillis();
        float yaw = rotateVector.x;
        float pitch = rotateVector.y;
        if (now <= attackAimUntilMs) {
            yaw = attackYaw;
            pitch = attackPitch;
        }

        e.setYaw(yaw);
        e.setPitch(pitch);
        mc.player.rotationYawHead = yaw;
        mc.player.renderYawOffset = yaw;
        mc.player.rotationPitchHead = pitch;
    }

    private void updateTarget() {
        List<LivingEntity> targets = new ArrayList<>();

        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof LivingEntity living && isValid(living)) {
                targets.add(living);
            }
        }

        if (targets.isEmpty()) {
            target = null;
            return;
        }

        if (targets.size() == 1) {
            target = targets.get(0);
            return;
        }

        targets.sort(Comparator.comparingDouble(object -> {
            if (object instanceof PlayerEntity player) {
                return -getEntityArmor(player);
            }
            if (object instanceof LivingEntity base) {
                return -base.getTotalArmorValue();
            }
            return 0.0;
        }).thenComparing((object, object2) -> {
            double d2 = getEntityHealth((LivingEntity) object);
            double d3 = getEntityHealth((LivingEntity) object2);
            return Double.compare(d2, d3);
        }).thenComparing((object, object2) -> {
            double d2 = mc.player.getDistance((LivingEntity) object);
            double d3 = mc.player.getDistance((LivingEntity) object2);
            return Double.compare(d2, d3);
        }));

        target = targets.get(0);
    }

    float lastYaw, lastPitch;

    private void updateRotation(boolean attack, float rotationYawSpeed, float rotationPitchSpeed) {
        Vector3d point = getAimPoint();
        Vector3d vec = point.subtract(mc.player.getEyePosition(1.0F));

        isRotated = true;

        if (microEnabled.get()) {
            long nowTs = System.currentTimeMillis();
            float iMin = microIntervalMin.get();
            float iMax = microIntervalMax.get();
            if (iMin > iMax) { float tmp = iMin; iMin = iMax; iMax = tmp; }
            if (nowTs >= microNextAt) {
                microTargetYaw = (rng.nextFloat() - 0.5f) * (microYawRadius.get() * 2.0f);
                microTargetPitch = (rng.nextFloat() - 0.5f) * (microPitchRadius.get() * 2.0f);
                long interval = Math.round(iMin + rng.nextFloat() * (iMax - iMin));
                microNextAt = nowTs + Math.max(50L, interval);
            }
            float mLerp = MathHelper.clamp(microLerp.get(), 0.001f, 1.0f);
            microYaw += (microTargetYaw - microYaw) * mLerp;
            microPitch += (microTargetPitch - microPitch) * mLerp;
        } else {
            microYaw = 0f;
            microPitch = 0f;
        }

        float yawSpeedMul = MathHelper.clamp(jitterYawSpeedMul.get(), 0.001f, 1.0f);
        float pitchSpeedMul = MathHelper.clamp(jitterPitchSpeedMul.get(), 0.001f, 1.0f);
        jitterPhaseYaw += jitterSpeed.get() * yawSpeedMul;
        jitterPhase += jitterSpeed.get() * pitchSpeedMul;
        float yawJitter = (float) Math.sin(jitterPhaseYaw) * (jitterAmp.get() * jitterYawMul.get());
        float pitchJitter = (float) Math.sin(jitterPhase) * (jitterAmp.get() * jitterPitchMul.get());

        float yawToTarget = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90) + microYaw + yawJitter;
        float pitchToTarget = (float) (-Math.toDegrees(Math.atan2(vec.y, hypot(vec.x, vec.z)))) + microPitch + pitchJitter;

        float yawDelta = (wrapDegrees(yawToTarget - rotateVector.x));
        float pitchDelta = (wrapDegrees(pitchToTarget - rotateVector.y));
        int roundedYaw = (int) yawDelta;

        switch (type.get()) {
            case "Плавная" -> {
                float minS = speedMin.get();
                float maxS = Math.max(minS, speedMax.get());
                float desiredYawSpeed = clamp(minS, 10f, maxS);
                float desiredPitchSpeed = clamp(minS * 0.6f, 5f, maxS);
                desiredYawSpeed += (rng.nextFloat() - 0.5f) * 6f;
                desiredPitchSpeed += (rng.nextFloat() - 0.5f) * 4f;
                currentYawSpeed = currentYawSpeed * 0.8f + desiredYawSpeed * 0.2f;
                currentPitchSpeed = currentPitchSpeed * 0.8f + desiredPitchSpeed * 0.2f;

                float capYaw = Math.min(currentYawSpeed, rotationYawSpeed);
                float capPitch = Math.min(currentPitchSpeed, rotationPitchSpeed);

                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 1.0f), capYaw);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 1.0f), capPitch);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 3f;
                }

                if (Math.abs(clampedYaw - this.lastYaw) <= 3.0f) {
                    clampedYaw = this.lastYaw + 3.1f;
                }

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = clamp(rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch), -89.0F, 89.0F);


                rotateVector = new Vector2f(yaw, pitch);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
                if (options.getValueByName("Коррекция движения").get()) {
                    mc.player.rotationYawOffset = yaw;
                }
            }
            case "Резкая" -> {
                float yaw = rotateVector.x + roundedYaw;
                float pitch = clamp(rotateVector.y + pitchDelta, -90, 90);

                rotateVector = new Vector2f(yaw, pitch);

                if (options.getValueByName("Коррекция движения").get()) {
                    mc.player.rotationYawOffset = yaw;
                }
            }
        }
    }


    private void updateAttack() {
        if (options.getValueByName("Только криты").get()) {
            if (mc.player.isOnGround() || mc.player.fallDistance <= 0.05F) return;
            if (attackedThisAir) return;
        }

        Vector3d strikePoint = (sweepCtrl != null && (System.currentTimeMillis() - sweepStartMs) <= sweepDurationMs)
                ? sweepCtrl
                : (multiPoints.get() ? randomBodyPoint(target) : centerPoint(target));
        Vector2f strikeAngles = anglesTo(strikePoint);
        selected = MouseUtil.getMouseOver(target, strikeAngles.x, strikeAngles.y, attackRange.get());

        if (options.getValueByName("Ускорять ротацию при атаке").get()) {
            updateRotation(true, 60, 35);
        }

        if ((selected == null || selected != target) && !mc.player.isElytraFlying()) {
            selected = MouseUtil.getMouseOver(target, rotateVector.x, rotateVector.y, attackRange.get());
            if (selected == null || selected != target) return;
        }

        if (mc.player.isBlocking() && options.getValueByName("Отжимать щит").get()) {
            mc.playerController.onStoppedUsingItem(mc.player);
        }

        attackYaw = strikeAngles.x;
        attackPitch = clamp(strikeAngles.y, -89.0F, 89.0F);
        attackAimUntilMs = System.currentTimeMillis() + 75L;

        mc.playerController.attackEntity(mc.player, target);
        mc.player.swingArm(Hand.MAIN_HAND);
        lastAttackMs = System.currentTimeMillis();
        lookRight = !lookRight;
        if (options.getValueByName("Только криты").get()) {
            attackedThisAir = true;
        }
        sweepStartMs = lastAttackMs;
        sweepFrom = outsidePoint(target, lookRight ? -1 : 1);
        sweepCtrl = rng.nextBoolean() ? centerPoint(target) : randomBodyPoint(target);
        sweepTo = outsidePoint(target, lookRight ? 1 : -1);

        if (target instanceof PlayerEntity player && options.getValueByName("Ломать щит").get()) {
            breakShieldPlayer(player);
        }
    }

    private boolean shouldPlayerFalling() {
        boolean cancelReason = mc.player.isInWater() && mc.player.areEyesInFluid(FluidTags.WATER) || mc.player.isInLava() || mc.player.isOnLadder() || mc.player.isPassenger() || mc.player.abilities.isFlying;

        float attackStrength = mc.player.getCooledAttackStrength(options.getValueByName("Синхронизировать атаку с ТПС").get()
                ? Expensive.getInstance().getTpsCalc().getAdjustTicks() : 1.5f);

        if (attackStrength < 0.92f) {
            return false;
        }

        if (!cancelReason && options.getValueByName("Только криты").get()) {
            return !mc.player.isOnGround() && mc.player.fallDistance > 0;
        }

        return true;
    }

    private boolean isValid(LivingEntity entity) {
        if (entity instanceof ClientPlayerEntity) return false;

        if (entity.ticksExisted < 3) return false;
        if (mc.player.getDistanceEyePos(entity) > attackRange.get()) return false;

        if (entity instanceof PlayerEntity p) {
            if (AntiBot.isBot(entity)) {
                return false;
            }
            if (!targets.getValueByName("Друзья").get() && FriendStorage.isFriend(p.getName().getString())) {
                return false;
            }
            if (p.getName().getString().equalsIgnoreCase(mc.player.getName().getString())) return false;
        }

        if (entity instanceof PlayerEntity && !targets.getValueByName("Игроки").get()) {
            return false;
        }
        if (entity instanceof PlayerEntity && entity.getTotalArmorValue() == 0 && !targets.getValueByName("Голые").get()) {
            return false;
        }
        if (entity instanceof PlayerEntity && entity.isInvisible() && entity.getTotalArmorValue() == 0 && !targets.getValueByName("Голые невидимки").get()) {
            return false;
        }
        if (entity instanceof PlayerEntity && entity.isInvisible() && !targets.getValueByName("Невидимки").get()) {
            return false;
        }

        if (entity instanceof MonsterEntity && !targets.getValueByName("Мобы").get()) {
            return false;
        }
        if (entity instanceof AnimalEntity && !targets.getValueByName("Животные").get()) {
            return false;
        }

        return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStandEntity);
    }

    private void breakShieldPlayer(PlayerEntity entity) {
        if (entity.isBlocking()) {
            int invSlot = InventoryUtil.getInstance().getAxeInInventory(false);
            int hotBarSlot = InventoryUtil.getInstance().getAxeInInventory(true);

            if (hotBarSlot == -1 && invSlot != -1) {
                int bestSlot = InventoryUtil.getInstance().findBestSlotInHotBar();
                mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);

                mc.player.connection.sendPacket(new CHeldItemChangePacket(bestSlot));
                mc.playerController.attackEntity(mc.player, entity);
                mc.player.swingArm(Hand.MAIN_HAND);
                mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));

                mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
            }

            if (hotBarSlot != -1) {
                mc.player.connection.sendPacket(new CHeldItemChangePacket(hotBarSlot));
                mc.playerController.attackEntity(mc.player, entity);
                mc.player.swingArm(Hand.MAIN_HAND);
                mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
            }
        }
    }


    private void reset() {
        if (options.getValueByName("Коррекция движения").get()) {
            mc.player.rotationYawOffset = Integer.MIN_VALUE;
        }
        rotateVector = new Vector2f(mc.player.rotationYaw, mc.player.rotationPitch);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reset();
        target = null;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
        stopWatch.setLastMS(0);
        target = null;
    }

    private double getEntityArmor(PlayerEntity entityPlayer2) {
        double d2 = 0.0;
        for (int i2 = 0; i2 < 4; ++i2) {
            ItemStack is = entityPlayer2.inventory.armorInventory.get(i2);
            if (!(is.getItem() instanceof ArmorItem)) continue;
            d2 += getProtectionLvl(is);
        }
        return d2;
    }

    private double getProtectionLvl(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem i) {
            double damageReduceAmount = i.getDamageReduceAmount();
            if (stack.isEnchanted()) {
                damageReduceAmount += (double) EnchantmentHelper.getEnchantmentLevel(Enchantments.PROTECTION, stack) * 0.25;
            }
            return damageReduceAmount;
        }
        return 0;
    }

    private double getEntityHealth(LivingEntity ent) {
        if (ent instanceof PlayerEntity player) {
            return (double) (player.getHealth() + player.getAbsorptionAmount()) * (getEntityArmor(player) / 20.0);
        }
        return ent.getHealth() + ent.getAbsorptionAmount();
    }

    public long getLastAttackMs() {
        return lastAttackMs;
    }
}
