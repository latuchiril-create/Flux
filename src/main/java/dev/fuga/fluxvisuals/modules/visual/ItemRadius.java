package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class ItemRadius extends Module {
    private static final int ALERT_COLOR = 0xFF9AFF4D;
    private static final int WHITE_COLOR = 0xFFFFFFFF;
    private static final double LINE_LIFT = 0.035D;
    private static final double PLAST_THICKNESS = 1.5D;
    private static final double PLAST_RANGE = 4.0D;
    private static final double FREEZE_RANGE = 32.0D;
    private static final double SNOWBALL_SPEED = 1.5D;
    private static final double SNOWBALL_DRAG = 0.99D;
    private static final double SNOWBALL_GRAVITY = 0.03D;
    private static final int SNOWBALL_SIMULATION_TICKS = 80;
    private static final int CIRCLE_SEGMENTS = 128;

    private boolean dezka = true;
    private boolean yavka = true;
    private boolean fireCharge = true;
    private boolean godAura = true;
    private boolean trap = true;
    private boolean plast = true;
    private boolean freezeSnowball = true;
    private boolean draconicTrap;
    private float alertProgress;
    private long lastRenderNanos;

    public ItemRadius() {
        super("ItemRadius", "Draws selected held item radius previews.", ModuleCategory.VISUALS);
    }

    public void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client == null || client.world == null || client.player == null
                || context.matrixStack() == null || context.consumers() == null || context.camera() == null) {
            resetAnimationClock();
            return;
        }

        RadiusItem active = activeItem(client.player);
        if (active == null) {
            resetAnimationClock();
            return;
        }

        float tickDelta = context.tickCounter().getTickProgress(false);
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        RenderLayer layer = RenderLayer.getLines();
        VertexConsumer consumer = context.consumers().getBuffer(layer);

        boolean playersNearby = switch (active) {
            case DEZKA, YAVKA, FIRE_CHARGE, GOD_AURA -> hasPlayerNear(client, client.player.getLerpedPos(tickDelta), active.radius);
            case TRAP -> hasPlayerInBox(client, trapBox(client.player.getLerpedPos(tickDelta)), tickDelta);
            case PLAST -> {
                PlaneBox box = plastBox(client.player, tickDelta);
                yield box != null && hasPlayerNear(client, box.center, Math.max(box.halfA, box.halfB) + 0.75D);
            }
            case FREEZE_SNOWBALL -> {
                PlaneTarget target = freezeTarget(client.player, tickDelta);
                yield target != null && hasPlayerNear(client, target.center, active.radius);
            }
        };
        int color = currentColor(active.color, playersNearby);

        switch (active) {
            case DEZKA, YAVKA, FIRE_CHARGE, GOD_AURA -> drawGroundCircle(matrices, consumer,
                    client.player.getLerpedPos(tickDelta), camera, active.radius, color, false);
            case TRAP -> drawBox(matrices, consumer, trapBox(client.player.getLerpedPos(tickDelta)).offset(-camera.x, -camera.y, -camera.z), color);
            case PLAST -> {
                PlaneBox box = plastBox(client.player, tickDelta);
                if (box != null) {
                    drawOrientedBox(matrices, consumer, box, camera, color);
                }
            }
            case FREEZE_SNOWBALL -> {
                PlaneTarget target = freezeTarget(client.player, tickDelta);
                if (target != null) {
                    drawPlaneCircle(matrices, consumer, target.center, target.axisA, target.axisB, camera, active.radius, color, true);
                }
            }
        }
        drawLayer(context, layer);
    }

    private RadiusItem activeItem(ClientPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        ItemStack offhand = player.getOffHandStack();
        for (RadiusItem item : RadiusItem.values()) {
            if (isItemEnabled(item) && (isHolding(main, item.item) || isHolding(offhand, item.item))) {
                return item;
            }
        }
        return null;
    }

    private boolean isItemEnabled(RadiusItem item) {
        return switch (item) {
            case DEZKA -> dezka;
            case YAVKA -> yavka;
            case FIRE_CHARGE -> fireCharge;
            case GOD_AURA -> godAura;
            case TRAP -> trap;
            case PLAST -> plast;
            case FREEZE_SNOWBALL -> freezeSnowball;
        };
    }

    public void setItemEnabled(RadiusItem item, boolean enabled) {
        if (item == null || isItemEnabled(item) == enabled) {
            return;
        }
        switch (item) {
            case DEZKA -> dezka = enabled;
            case YAVKA -> yavka = enabled;
            case FIRE_CHARGE -> fireCharge = enabled;
            case GOD_AURA -> godAura = enabled;
            case TRAP -> trap = enabled;
            case PLAST -> plast = enabled;
            case FREEZE_SNOWBALL -> freezeSnowball = enabled;
        }
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isItemEnabledPublic(RadiusItem item) {
        return item != null && isItemEnabled(item);
    }

    public boolean isDraconicTrap() {
        return draconicTrap;
    }

    public void setDraconicTrap(boolean draconicTrap) {
        if (this.draconicTrap == draconicTrap) {
            return;
        }
        this.draconicTrap = draconicTrap;
        FluxVisualsClient.requestConfigSave();
    }

    public java.util.Set<String> getSelectedLabels() {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (RadiusItem item : RadiusItem.values()) {
            if (isItemEnabled(item)) {
                set.add(item.label());
            }
        }
        return set;
    }

    public void setSelectedLabels(java.util.Set<String> labels) {
        if (labels == null) return;
        for (RadiusItem item : RadiusItem.values()) {
            boolean active = labels.contains(item.label()) || labels.contains(item.name());
            setItemEnabled(item, active);
        }
    }

    public String enabledItemNames() {
        StringBuilder builder = new StringBuilder();
        for (RadiusItem item : RadiusItem.values()) {
            if (isItemEnabled(item)) {
                if (!builder.isEmpty()) {
                    builder.append(',');
                }
                builder.append(item.name());
            }
        }
        return builder.toString();
    }

    public void setEnabledItemNames(String value) {
        if (value == null) {
            return;
        }
        for (RadiusItem item : RadiusItem.values()) {
            setItemEnabledSilently(item, false);
        }
        if (value.isBlank()) {
            return;
        }
        for (String name : value.split(",")) {
            try {
                setItemEnabledSilently(RadiusItem.valueOf(name.trim().toUpperCase(Locale.ROOT)), true);
            } catch (IllegalArgumentException ignored) {
                // Ignore stale config values from older builds.
            }
        }
    }

    private void setItemEnabledSilently(RadiusItem item, boolean enabled) {
        switch (item) {
            case DEZKA -> dezka = enabled;
            case YAVKA -> yavka = enabled;
            case FIRE_CHARGE -> fireCharge = enabled;
            case GOD_AURA -> godAura = enabled;
            case TRAP -> trap = enabled;
            case PLAST -> plast = enabled;
            case FREEZE_SNOWBALL -> freezeSnowball = enabled;
        }
    }

    private int currentColor(int baseColor, boolean playersNearby) {
        long now = System.nanoTime();
        float dt = lastRenderNanos == 0L ? 1.0F / 60.0F
                : Math.min(0.08F, Math.max(0.0F, (now - lastRenderNanos) / 1_000_000_000.0F));
        lastRenderNanos = now;
        float target = playersNearby ? 1.0F : 0.0F;
        alertProgress += (target - alertProgress) * (1.0F - (float) Math.exp(-10.5F * dt));
        return mixColor(baseColor, ALERT_COLOR, alertProgress);
    }

    private void resetAnimationClock() {
        lastRenderNanos = 0L;
        alertProgress = 0.0F;
    }

    private Box trapBox(Vec3d playerPos) {
        double size = draconicTrap ? 5.4D : 4.0D;
        double half = size * 0.5D;
        double x = Math.floor(playerPos.x) + 0.5D;
        double y = Math.floor(playerPos.y) + 0.1D + half;
        double z = Math.floor(playerPos.z) + 0.5D;
        return new Box(x - half, y - half, z - half, x + half, y + half, z + half);
    }

    private PlaneBox plastBox(ClientPlayerEntity player, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }

        double size = draconicTrap ? 5.0D : 4.0D;
        Vec3d start = player.getCameraPosVec(tickDelta);
        Vec3d look = player.getRotationVec(tickDelta);
        BlockHitResult hit = raycast(player, start, look, PLAST_RANGE);
        Direction side = hit.getType() == HitResult.Type.BLOCK ? hit.getSide() : null;
        Vec3d center;
        Vec3d axisA;
        Vec3d axisB;
        Vec3d axisC;

        if (side != null && side.getAxis().isHorizontal()) {
            Vec3d normal = directionVec(side);
            Vec3d tangent = side.getAxis() == Direction.Axis.X ? new Vec3d(0.0D, 0.0D, 1.0D) : new Vec3d(1.0D, 0.0D, 0.0D);
            BlockPos pos = hit.getBlockPos();
            center = new Vec3d(
                    pos.getX() + 0.5D + normal.x * (PLAST_THICKNESS * 0.5D + 0.01D),
                    Math.floor(hit.getPos().y) + 0.1D + size * 0.5D,
                    pos.getZ() + 0.5D + normal.z * (PLAST_THICKNESS * 0.5D + 0.01D)
            );
            axisA = tangent.multiply(size * 0.5D);
            axisB = new Vec3d(0.0D, size * 0.5D, 0.0D);
            axisC = normal.multiply(PLAST_THICKNESS * 0.5D);
        } else if (side != null) {
            Vec3d normal = directionVec(side);
            Vec3d hitPos = hit.getPos();
            center = new Vec3d(
                    Math.floor(hitPos.x) + 0.5D,
                    hitPos.y + normal.y * (PLAST_THICKNESS * 0.5D + 0.01D),
                    Math.floor(hitPos.z) + 0.5D
            );
            axisA = new Vec3d(size * 0.5D, 0.0D, 0.0D);
            axisB = new Vec3d(0.0D, 0.0D, size * 0.5D);
            axisC = new Vec3d(0.0D, PLAST_THICKNESS * 0.5D, 0.0D);
        } else {
            Vec3d raw = start.add(look.multiply(PLAST_RANGE));
            boolean horizontal = Math.abs(look.y) > 0.55D;
            if (horizontal) {
                center = new Vec3d(Math.floor(raw.x) + 0.5D, Math.floor(raw.y) + 0.08D + PLAST_THICKNESS * 0.5D, Math.floor(raw.z) + 0.5D);
                axisA = new Vec3d(size * 0.5D, 0.0D, 0.0D);
                axisB = new Vec3d(0.0D, 0.0D, size * 0.5D);
                axisC = new Vec3d(0.0D, PLAST_THICKNESS * 0.5D, 0.0D);
            } else {
                Vec3d normal = new Vec3d(look.x, 0.0D, look.z);
                if (normal.lengthSquared() < 1.0E-5D) {
                    normal = new Vec3d(0.0D, 0.0D, 1.0D);
                } else {
                    normal = normal.normalize();
                }
                Vec3d tangent = new Vec3d(-normal.z, 0.0D, normal.x);
                center = new Vec3d(Math.floor(raw.x) + 0.5D, Math.floor(raw.y) + 0.1D + size * 0.5D, Math.floor(raw.z) + 0.5D);
                axisA = tangent.multiply(size * 0.5D);
                axisB = new Vec3d(0.0D, size * 0.5D, 0.0D);
                axisC = normal.multiply(PLAST_THICKNESS * 0.5D);
            }
        }
        return new PlaneBox(center, axisA, axisB, axisC, size * 0.5D, size * 0.5D);
    }

    private PlaneTarget freezeTarget(ClientPlayerEntity player, float tickDelta) {
        Vec3d look = player.getRotationVec(tickDelta).normalize();
        Vec3d pos = player.getCameraPosVec(tickDelta).add(look.multiply(0.18D));
        Vec3d velocity = look.multiply(SNOWBALL_SPEED).add(player.getVelocity());
        Vec3d lastPos = pos;

        for (int tick = 0; tick < SNOWBALL_SIMULATION_TICKS; tick++) {
            Vec3d nextPos = pos.add(velocity);
            BlockHitResult hit = raycastSegment(player, pos, nextPos);
            if (hit.getType() == HitResult.Type.BLOCK) {
                return freezeTargetFromHit(hit);
            }

            lastPos = nextPos;
            pos = nextPos;
            velocity = velocity.multiply(SNOWBALL_DRAG).add(0.0D, -SNOWBALL_GRAVITY, 0.0D);
            if (player.getCameraPosVec(tickDelta).squaredDistanceTo(pos) > FREEZE_RANGE * FREEZE_RANGE) {
                break;
            }
        }

        Vec3d horizontal = new Vec3d(look.x, 0.0D, look.z);
        if (horizontal.lengthSquared() < 1.0E-5D) {
            horizontal = Vec3d.fromPolar(0.0F, player.getYaw(tickDelta));
            horizontal = new Vec3d(horizontal.x, 0.0D, horizontal.z);
        }
        horizontal = horizontal.normalize();
        Vec3d fallback = player.getLerpedPos(tickDelta).add(horizontal.multiply(12.0D));
        Vec3d center = Double.isFinite(lastPos.x) && Double.isFinite(lastPos.y) && Double.isFinite(lastPos.z)
                ? new Vec3d(lastPos.x, Math.floor(lastPos.y) + LINE_LIFT, lastPos.z)
                : fallback.add(0.0D, LINE_LIFT, 0.0D);
        return new PlaneTarget(center, new Vec3d(1.0D, 0.0D, 0.0D), new Vec3d(0.0D, 0.0D, 1.0D));
    }

    private PlaneTarget freezeTargetFromHit(BlockHitResult hit) {
        Direction side = hit.getSide();
        Vec3d normal = directionVec(side);
        Vec3d center = hit.getPos().add(normal.multiply(LINE_LIFT));
        if (side.getAxis().isHorizontal()) {
            Vec3d axisA = side.getAxis() == Direction.Axis.X ? new Vec3d(0.0D, 0.0D, 1.0D) : new Vec3d(1.0D, 0.0D, 0.0D);
            Vec3d axisB = new Vec3d(0.0D, 1.0D, 0.0D);
            return new PlaneTarget(center, axisA, axisB);
        }
        return new PlaneTarget(center, new Vec3d(1.0D, 0.0D, 0.0D), new Vec3d(0.0D, 0.0D, 1.0D));
    }

    private BlockHitResult raycast(ClientPlayerEntity player, Vec3d start, Vec3d look, double range) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d end = start.add(look.multiply(range));
        return raycastSegment(player, start, end);
    }

    private BlockHitResult raycastSegment(ClientPlayerEntity player, Vec3d start, Vec3d end) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
    }

    private boolean hasPlayerNear(MinecraftClient client, Vec3d center, double radius) {
        double radiusSquared = radius * radius;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || player.isRemoved() || !player.isAlive()) {
                continue;
            }
            if (player.getPos().squaredDistanceTo(center) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPlayerInBox(MinecraftClient client, Box box, float tickDelta) {
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || player.isRemoved() || !player.isAlive()) {
                continue;
            }
            if (box.expand(0.25D).contains(player.getLerpedPos(tickDelta))) {
                return true;
            }
        }
        return false;
    }

    private void drawGroundCircle(MatrixStack matrices, VertexConsumer consumer, Vec3d playerPos, Vec3d camera, double radius,
                                  int color, boolean detailed) {
        Vec3d center = new Vec3d(playerPos.x, Math.floor(playerPos.y) + LINE_LIFT, playerPos.z);
        drawPlaneCircle(matrices, consumer, center, new Vec3d(1.0D, 0.0D, 0.0D), new Vec3d(0.0D, 0.0D, 1.0D),
                camera, radius, color, detailed);
    }

    private void drawPlaneCircle(MatrixStack matrices, VertexConsumer consumer, Vec3d center, Vec3d axisA, Vec3d axisB,
                                 Vec3d camera, double radius, int color, boolean detailed) {
        Vec3d a = axisA.normalize();
        Vec3d b = axisB.normalize();
        Vec3d previous = circlePoint(center, a, b, radius, 0.0D);
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            double angle = Math.PI * 2.0D * i / CIRCLE_SEGMENTS;
            Vec3d next = circlePoint(center, a, b, radius, angle);
            drawLine(matrices, consumer, previous.subtract(camera), next.subtract(camera), color);
            previous = next;
        }

        if (!detailed) {
            return;
        }

        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D;
            Vec3d outer = circlePoint(center, a, b, radius, angle);
            Vec3d inner = circlePoint(center, a, b, radius * 0.82D, angle);
            drawLine(matrices, consumer, inner.subtract(camera), outer.subtract(camera), color);
        }
        double innerRadius = radius * 0.58D;
        Vec3d innerPrevious = circlePoint(center, a, b, innerRadius, 0.0D);
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            double angle = Math.PI * 2.0D * i / CIRCLE_SEGMENTS;
            Vec3d next = circlePoint(center, a, b, innerRadius, angle);
            drawLine(matrices, consumer, innerPrevious.subtract(camera), next.subtract(camera), withAlpha(color, 190));
            innerPrevious = next;
        }
    }

    private Vec3d circlePoint(Vec3d center, Vec3d axisA, Vec3d axisB, double radius, double angle) {
        return center.add(axisA.multiply(Math.cos(angle) * radius)).add(axisB.multiply(Math.sin(angle) * radius));
    }

    private void drawBox(MatrixStack matrices, VertexConsumer consumer, Box box, int color) {
        Vec3d[] vertices = {
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.maxZ)
        };
        drawEdges(matrices, consumer, vertices, color);
    }

    private void drawOrientedBox(MatrixStack matrices, VertexConsumer consumer, PlaneBox box, Vec3d camera, int color) {
        Vec3d a = box.axisA;
        Vec3d b = box.axisB;
        Vec3d c = box.axisC;
        Vec3d center = box.center.subtract(camera);
        Vec3d[] vertices = {
                center.subtract(a).subtract(b).subtract(c),
                center.add(a).subtract(b).subtract(c),
                center.add(a).add(b).subtract(c),
                center.subtract(a).add(b).subtract(c),
                center.subtract(a).subtract(b).add(c),
                center.add(a).subtract(b).add(c),
                center.add(a).add(b).add(c),
                center.subtract(a).add(b).add(c)
        };
        drawEdges(matrices, consumer, vertices, color);
    }

    private void drawEdges(MatrixStack matrices, VertexConsumer consumer, Vec3d[] vertices, int color) {
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            drawLine(matrices, consumer, vertices[edge[0]], vertices[edge[1]], color);
        }
    }

    private void drawLine(MatrixStack matrices, VertexConsumer consumer, Vec3d from, Vec3d to, int color) {
        Vec3d normal = to.subtract(from);
        if (normal.lengthSquared() < 1.0E-8D) {
            normal = new Vec3d(0.0D, 1.0D, 0.0D);
        } else {
            normal = normal.normalize();
        }
        MatrixStack.Entry entry = matrices.peek();
        int alpha = color >>> 24;
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        consumer.vertex(entry, (float) from.x, (float) from.y, (float) from.z)
                .color(red, green, blue, alpha)
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.vertex(entry, (float) to.x, (float) to.y, (float) to.z)
                .color(red, green, blue, alpha)
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void drawLayer(WorldRenderContext context, RenderLayer layer) {
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }

    private static Vec3d directionVec(Direction direction) {
        return new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }

    private static boolean isHolding(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.isOf(item);
    }

    private static int mixColor(int from, int to, float delta) {
        float t = Math.max(0.0F, Math.min(1.0F, delta));
        int fa = from >>> 24;
        int fr = (from >> 16) & 255;
        int fg = (from >> 8) & 255;
        int fb = from & 255;
        int ta = to >>> 24;
        int tr = (to >> 16) & 255;
        int tg = (to >> 8) & 255;
        int tb = to & 255;
        int a = Math.round(fa + (ta - fa) * t);
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private record PlaneTarget(Vec3d center, Vec3d axisA, Vec3d axisB) {
    }

    private record PlaneBox(Vec3d center, Vec3d axisA, Vec3d axisB, Vec3d axisC, double halfA, double halfB) {
    }

    public enum RadiusItem {
        DEZKA("Дезка", Items.ENDER_EYE, 10.0D, WHITE_COLOR),
        YAVKA("Явка", Items.SUGAR, 10.0D, WHITE_COLOR),
        FIRE_CHARGE("Огненый Заряд", Items.FIRE_CHARGE, 10.0D, WHITE_COLOR),
        GOD_AURA("Божья Аура", Items.PHANTOM_MEMBRANE, 2.0D, WHITE_COLOR),
        TRAP("Трапка", Items.NETHERITE_SCRAP, 4.0D, WHITE_COLOR),
        PLAST("Пласт", Items.DRIED_KELP, 4.0D, WHITE_COLOR),
        FREEZE_SNOWBALL("Снежок Заморозка", Items.SNOWBALL, 3.5D, WHITE_COLOR);

        private final String label;
        private final Item item;
        private final double radius;
        private final int color;

        RadiusItem(String label, Item item, double radius, int color) {
            this.label = label;
            this.item = item;
            this.radius = radius;
            this.color = color;
        }

        public String label() {
            return label;
        }
    }
}
