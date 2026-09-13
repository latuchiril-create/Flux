package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.Locale;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class TrapTracker extends Module {
    private static final long NORMAL_TRAP_DURATION_MS = 15_000L;
    private static final long DRAGON_TRAP_DURATION_MS = 30_000L;
    private static final int COOLDOWN_CONFIRMATION_WINDOW_TICKS = 30;
    private static final ItemStack TRACKED_STACK = Items.NETHERITE_SCRAP.getDefaultStack();

    private int currentTick;
    private int pendingCooldownUntilTick = -1;
    private ActiveTrap activeTrap;

    public TrapTracker() {
        super("TrapTracker", "Tracks the remaining trap time from netherite scrap use.", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(MinecraftClient client) {
        currentTick++;

        if (!isEnabled()) {
            return;
        }

        if (client.player == null || client.world == null) {
            pendingCooldownUntilTick = -1;
            activeTrap = null;
            return;
        }

        ClientPlayerEntity player = client.player;
        updateTrapState(player);
        renderActionBar(client);
    }

    @Override
    protected void onDisable(MinecraftClient client) {
        pendingCooldownUntilTick = -1;
        activeTrap = null;
    }

    public ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (isEnabled() && world.isClient() && isTrackedTrapItem(player.getStackInHand(hand))) {
            pendingCooldownUntilTick = currentTick + COOLDOWN_CONFIRMATION_WINDOW_TICKS;
        }

        return ActionResult.PASS;
    }

    private void updateTrapState(ClientPlayerEntity player) {
        boolean isCoolingDown = player.getItemCooldownManager().isCoolingDown(TRACKED_STACK);

        if (pendingCooldownUntilTick >= currentTick && isCoolingDown) {
            startTrap(player);
            pendingCooldownUntilTick = -1;
        }

        if (pendingCooldownUntilTick < currentTick) {
            pendingCooldownUntilTick = -1;
        }

        if (activeTrap != null && activeTrap.isExpired()) {
            activeTrap = null;
        }
    }

    private void startTrap(ClientPlayerEntity player) {
        activeTrap = new ActiveTrap(System.currentTimeMillis(), hasNearbyNetheriteBlock(player));
    }

    private void renderActionBar(MinecraftClient client) {
        if (activeTrap == null) {
            return;
        }

        double secondsLeft = Math.max(0.0D, activeTrap.getRemainingMillis() / 1000.0D);
        String timer = String.format(Locale.US, "%.1fs", secondsLeft);
        client.inGameHud.setOverlayMessage(
                Text.literal(activeTrap.getPrefix()).formatted(Formatting.WHITE)
                        .append(Text.literal(timer).formatted(Formatting.AQUA)),
                false
        );
    }

    private static boolean isTrackedTrapItem(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(Items.NETHERITE_SCRAP);
    }

    private static boolean hasNearbyNetheriteBlock(ClientPlayerEntity player) {
        BlockPos center = player.getBlockPos();

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (player.getWorld().getBlockState(center.add(x, y, z)).isOf(Blocks.NETHERITE_BLOCK)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static final class ActiveTrap {
        private final long endsAtMillis;
        private final boolean dragon;

        private ActiveTrap(long startedAtMillis, boolean dragon) {
            this.dragon = dragon;
            this.endsAtMillis = startedAtMillis + (dragon ? DRAGON_TRAP_DURATION_MS : NORMAL_TRAP_DURATION_MS);
        }

        private String getPrefix() {
            return dragon
                    ? "\u0414\u043e \u043a\u043e\u043d\u0446\u0430 \u0434\u0440\u0430\u043a\u043e\u043d\u044c\u0435\u0439 \u0442\u0440\u0430\u043f\u043a\u0438 "
                    : "\u0414\u043e \u043a\u043e\u043d\u0446\u0430 \u043e\u0431\u044b\u0447\u043d\u043e\u0439 \u0442\u0440\u0430\u043f\u043a\u0438 ";
        }

        private long getRemainingMillis() {
            return endsAtMillis - System.currentTimeMillis();
        }

        private boolean isExpired() {
            return getRemainingMillis() <= 0L;
        }
    }
}
