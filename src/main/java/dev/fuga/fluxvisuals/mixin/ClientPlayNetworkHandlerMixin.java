package dev.fuga.fluxvisuals.mixin;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.multibot.BotDebug;
import dev.fuga.fluxvisuals.multibot.BotSession;
import dev.fuga.fluxvisuals.multibot.MultiBotManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.world.GameMode;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.MathHelper;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Unique
    private static final Map<UUID, Long> FLUXVISUALS$BOSSBAR_LAST_UPDATE = new HashMap<>();

    @Unique
    private static final ThreadLocal<Boolean> FLUXVISUALS$HANDLING_BOT_POSITION = ThreadLocal.withInitial(() -> false);

    @Unique
    private int fluxvisuals$flightChunkSamples;

    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void fluxvisuals$traceWorldAfterGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$traceFlightWorldBoundary("DEBUG_FLY_GAME_JOIN_V1", null);
    }

    @Inject(method = "onPlayerRespawn", at = @At("TAIL"))
    private void fluxvisuals$traceWorldAfterRespawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$traceFlightWorldBoundary("DEBUG_FLY_RESPAWN_V1", null);
    }

    @Inject(method = "onChunkData", at = @At("TAIL"))
    private void fluxvisuals$traceWorldAfterChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        if (!MinecraftClient.getInstance().isOnThread()) {
            return;
        }
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        BotSession session = manager == null ? null
                : manager.findSession((ClientPlayNetworkHandler) (Object) this);
        if (session == null || session.isMain()) {
            return;
        }
        ClientPlayerEntity player = session.getPlayer();
        boolean playerChunk = player != null
                && player.getBlockX() >> 4 == packet.getChunkX()
                && player.getBlockZ() >> 4 == packet.getChunkZ();
        if (fluxvisuals$flightChunkSamples++ < 16 || playerChunk) {
            fluxvisuals$traceFlightWorldBoundary(
                    "DEBUG_FLY_CHUNK_V1",
                    "chunk=" + packet.getChunkX() + "," + packet.getChunkZ()
                            + ", player_chunk=" + playerChunk
            );
        }
    }

    @Unique
    private void fluxvisuals$traceFlightWorldBoundary(String event, String prefix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !client.isOnThread()) {
            return;
        }
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        BotSession session = manager == null ? null
                : manager.findSession((ClientPlayNetworkHandler) (Object) this);
        if (session == null || session.isMain()) {
            return;
        }
        ClientWorld handlerWorld = ((ClientPlayNetworkHandler) (Object) this).getWorld();
        ClientWorld sessionWorld = session.getWorld();
        ClientPlayerEntity sessionPlayer = session.getPlayer();
        ClientPlayerEntity clientPlayer = client.player;
        String details = (prefix == null ? "" : prefix + ", ")
                + "handler_world=" + fluxvisuals$worldIdentity(handlerWorld)
                + ", session_world=" + fluxvisuals$worldIdentity(sessionWorld)
                + ", session_player_world=" + fluxvisuals$worldIdentity(
                sessionPlayer == null ? null : sessionPlayer.getWorld())
                + ", client_world=" + fluxvisuals$worldIdentity(client.world)
                + ", client_player_world=" + fluxvisuals$worldIdentity(
                clientPlayer == null ? null : clientPlayer.getWorld())
                + ", same_handler_session=" + (handlerWorld == sessionWorld)
                + ", same_handler_session_player="
                + (sessionPlayer != null && handlerWorld == sessionPlayer.getWorld())
                + ", same_handler_client_player="
                + (clientPlayer != null && handlerWorld == clientPlayer.getWorld())
                + ", session_player_registered="
                + (handlerWorld != null && sessionPlayer != null && handlerWorld.hasEntity(sessionPlayer))
                + ", client_player_registered="
                + (handlerWorld != null && clientPlayer != null && handlerWorld.hasEntity(clientPlayer))
                + ", loaded_chunks=" + (handlerWorld == null ? -1 : handlerWorld.getChunkManager().getLoadedChunkCount())
                + ", session_player_pos=" + (sessionPlayer == null ? "null"
                : sessionPlayer.getX() + "," + sessionPlayer.getY() + "," + sessionPlayer.getZ())
                + ", client_player_pos=" + (clientPlayer == null ? "null"
                : clientPlayer.getX() + "," + clientPlayer.getY() + "," + clientPlayer.getZ());
        BotDebug.info(event, session, details);
    }

    @Unique
    private static String fluxvisuals$worldIdentity(net.minecraft.world.World world) {
        if (world == null || world.getRegistryKey() == null) {
            return "null";
        }
        return world.getRegistryKey().getValue() + "@"
                + Integer.toHexString(System.identityHashCode(world));
    }

    @Unique
    private boolean fluxvisuals$isCurrentVisibleHandler() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        ClientPlayNetworkHandler current = client.getNetworkHandler();
        if (current == null || (Object) this == current) {
            return !MultiBotManager.isBackgroundContext();
        }
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        if (manager != null) {
            BotSession active = manager.getActiveSession();
            if (active != null && active.getNetworkHandler() == (Object) this) {
                return !MultiBotManager.isBackgroundContext();
            }
        }
        return false;
    }

    @Inject(method = "onGameStateChange", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateGameStateChange(GameStateChangeS2CPacket packet, CallbackInfo ci) {
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        if (manager != null && manager.isManagedBotHandler((net.minecraft.network.listener.PacketListener) (Object) this)) {
            // The manager normally reaches this handler from the client
            // thread. Cancelling that already-ordered call and enqueueing it
            // again lets Join/Respawn overtake the mode packet during a world
            // transfer, leaving the visible session frozen in the old world.
            if (MinecraftClient.getInstance().isOnThread()) {
                return;
            }
            // When the packet is reapplied below, the session context is
            // already installed and vanilla must be allowed to handle it.
            if (MultiBotManager.isBackgroundContext()) {
                return;
            }
            ci.cancel();
            BotSession session = manager.findSession((ClientPlayNetworkHandler) (Object) this);
            if (session != null) {
                // Do not reconstruct a game-mode packet by hand. During a
                // reconfiguration the server deliberately sends intermediate
                // states; overwriting them with Adventure desynchronizes the
                // player from its destination world. Run the vanilla handler
                // on the client thread with this bot installed instead.
                MinecraftClient.getInstance().execute(() -> session.runWithContext(() -> {
                    ((ClientPlayNetworkHandler) (Object) this).onGameStateChange(packet);
                }));
            }
        }
    }

    @Inject(method = "onPlayerList", at = @At("TAIL"))
    private void fluxvisuals$syncBotGameModeFromPlayerList(PlayerListS2CPacket packet, CallbackInfo ci) {
        // Vanilla owns the tab-list game mode. Applying a second local mode
        // here was racing the reconfiguration packets and could pin a bot to
        // the hub state after it had changed worlds.
    }

    @Inject(method = "onPlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolatePlayerAbilities(PlayerAbilitiesS2CPacket packet, CallbackInfo ci) {
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        if (manager != null && manager.isManagedBotHandler((net.minecraft.network.listener.PacketListener) (Object) this)) {
            if (MinecraftClient.getInstance().isOnThread()) {
                return;
            }
            if (MultiBotManager.isBackgroundContext()) {
                return;
            }
            ci.cancel();
            BotSession session = manager.findSession((ClientPlayNetworkHandler) (Object) this);
            if (session != null) {
                MinecraftClient.getInstance().execute(() -> session.runWithContext(
                        () -> ((ClientPlayNetworkHandler) (Object) this).onPlayerAbilities(packet)));
            }
        }
    }

    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolatePlayerPositionLook(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        if (manager != null && manager.isManagedBotHandler((net.minecraft.network.listener.PacketListener) (Object) this)) {
            BotSession diagnosticSession = manager.findSession((ClientPlayNetworkHandler) (Object) this);
            if (diagnosticSession != null) {
                // [FLIGHT-POSITION] The hover has no local flying flags. Keep
                // the raw authoritative correction in the Desktop bot log to
                // establish whether the server is pinning Y every tick.
                BotDebug.info("FLIGHT_POSITION_PACKET", diagnosticSession,
                        "thread=" + Thread.currentThread().getName() + ", packet=" + packet);
            }
            if (MinecraftClient.getInstance().isOnThread()) {
                return;
            }
            if (MultiBotManager.isBackgroundContext() || FLUXVISUALS$HANDLING_BOT_POSITION.get()) {
                return;
            }
            ci.cancel();
            BotSession session = manager.findSession((ClientPlayNetworkHandler) (Object) this);
            if (session != null) {
                // Network handlers run on Netty. Applying a position/teleport
                // here can make installAsActive rebuild WorldRenderer sections
                // from that thread, which disconnects the client with
                // "createSections called from wrong thread". Re-enter vanilla
                // only from the client thread, while retaining the bot context.
                MinecraftClient.getInstance().execute(() -> session.runWithContext(() -> {
                    FLUXVISUALS$HANDLING_BOT_POSITION.set(true);
                    try {
                        ((ClientPlayNetworkHandler) (Object) this).onPlayerPositionLook(packet);
                    } finally {
                        FLUXVISUALS$HANDLING_BOT_POSITION.remove();
                    }
                }));
            }
        }
    }

    @Inject(method = "onBossBar", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateBossBar(BossBarS2CPacket packet, CallbackInfo ci) {
        MultiBotManager botManager = FluxVisualsClient.MULTI_BOT_MANAGER;
        if (botManager == null || !botManager.hasManagedSessions()) {
            return; // no bots: boss bars stay 100% vanilla, untouched
        }
        BotSession packetSession = botManager.findSession((ClientPlayNetworkHandler) (Object) this);
        BotSession contextSession = MultiBotManager.currentContextSession();
        BotSession activeSession = botManager.getActiveSession();
        // Never let a bot's packet mutate whichever HUD happens to be installed
        // globally during a switch. It may only update its own detached HUD,
        // or the HUD of the currently visible session.
        if (packetSession != null && packetSession != activeSession && packetSession != contextSession) {
            fluxvisuals$rerouteUiPacket(packet, ci);
            ci.cancel();
            return;
        }
        boolean detachedBossBarContext = contextSession != null
                && (contextSession == packetSession || contextSession.getNetworkHandler() == (Object) this);
        if (!detachedBossBarContext && !fluxvisuals$isCurrentVisibleHandler()) {
            fluxvisuals$rerouteUiPacket(packet, ci);
            ci.cancel();
            return;
        }
        fluxvisuals$rerouteUiPacket(packet, ci);
        if (ci.isCancelled()) {
            return;
        }
        BossBarPacketAccessor access = (BossBarPacketAccessor) (Object) packet;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            BossBarHud bossBarHud = client.inGameHud.getBossBarHud();
            Map<UUID, BossBar> bars = ((BossBarHudAccessor) (Object) bossBarHud).fluxvisuals$getBossBars();
            UUID packetUuid = access.fluxvisuals$getUuid();
            boolean hasBar = bars.containsKey(packetUuid);
            boolean isAdd = fluxvisuals$isBossBarAdd(packet);

            long now = System.currentTimeMillis();
            FLUXVISUALS$BOSSBAR_LAST_UPDATE.put(packetUuid, now);

            if (isAdd) {
                if (hasBar) {
                    bars.remove(packetUuid);
                }
            } else if (!hasBar) {
                // Servers may send a late update/remove during a world transfer.
                // Vanilla assumes the id is still in the HUD map and throws NPE,
                // disconnecting the whole client. A stale update is harmless.
                ci.cancel();
                return;
            }
        }
    }

    @Unique
    private static boolean fluxvisuals$isBossBarAdd(BossBarS2CPacket packet) {
        boolean[] add = {false};
        packet.accept(new BossBarS2CPacket.Consumer() {
            @Override
            public void add(UUID id, Text name, float percent, BossBar.Color color, BossBar.Style style,
                            boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
                add[0] = true;
            }

            @Override public void remove(UUID id) { }
            @Override public void updateProgress(UUID id, float percent) { }
            @Override public void updateName(UUID id, Text name) { }
            @Override public void updateStyle(UUID id, BossBar.Color color, BossBar.Style style) { }
            @Override public void updateProperties(UUID id, boolean darkenSky, boolean dragonMusic, boolean thickenFog) { }
        });
        return add[0];
    }

    @Inject(method = "onOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateOverlayMessage(OverlayMessageS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    /**
     * Vanilla shows "Press SHIFT to dismount" (mount.onboard overlay) and
     * mutates vehicle ridership directly from this handler. Without an
     * explicit hook a bot seated into a boat by an antibot check leaks the
     * hint onto the visible account's HUD (and can ride/dismount the wrong
     * player) whenever the packet is applied outside the session context.
     */
    @Inject(method = "onEntityPassengersSet", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateEntityPassengersSet(EntityPassengersSetS2CPacket packet, CallbackInfo ci) {
        MultiBotManager manager = FluxVisualsClient.MULTI_BOT_MANAGER;
        if (manager != null && manager.isManagedBotHandler((net.minecraft.network.listener.PacketListener) (Object) this)) {
            BotSession session = manager.findSession((ClientPlayNetworkHandler) (Object) this);
            if (session != null) {
                // [FLIGHT-PASSENGERS] Captures both the verification mount and
                // its removal, including packets delivered during a transfer.
                BotDebug.info("FLIGHT_PASSENGERS_PACKET", session,
                        "thread=" + Thread.currentThread().getName() + ", packet=" + packet);
            }
        }
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Inject(method = "onTitle", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateTitle(TitleS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Inject(method = "onSubtitle", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateSubtitle(SubtitleS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Inject(method = "onTitleFade", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateTitleFade(TitleFadeS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Inject(method = "onTitleClear", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateTitleClear(ClearTitleS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Inject(method = "onPlayerListHeader", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolatePlayerListHeader(PlayerListHeaderS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateChatMessage(ChatMessageS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Inject(method = "onProfilelessChatMessage", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateProfilelessChatMessage(ProfilelessChatMessageS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Inject(method = "onAdvancements", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$hideBackgroundAdvancementToasts(AdvancementUpdateS2CPacket packet, CallbackInfo ci) {
        fluxvisuals$rerouteUiPacket(packet, ci);
    }

    @Unique
    private void fluxvisuals$rerouteUiPacket(Packet<?> packet, CallbackInfo ci) {
        if (FluxVisualsClient.MULTI_BOT_MANAGER.rerouteBackgroundUiPacket(
                packet, (ClientPlayNetworkHandler) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "onGameMessage", at = @At("TAIL"))
    private void fluxvisuals$autoBuyReadGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        Text formattedMessage = packet.content();
        String message = formattedMessage.getString();
        boolean botMessage = FluxVisualsClient.MULTI_BOT_MANAGER.handleGameMessage(
                (ClientPlayNetworkHandler) (Object) this,
                formattedMessage
        );
        if (!botMessage) {
            FluxVisualsClient.MODULE_MANAGER.getAutoBuy().onChatMessage(message);
        }
        if (!botMessage && FluxVisualsClient.MULTI_BOT_MANAGER.isTelegramTargetListener(
                (ClientPlayNetworkHandler) (Object) this
        )) {
            FluxVisualsClient.MODULE_MANAGER.getTelegram().handleAutoResellPurchaseMessage(message);
            FluxVisualsClient.MODULE_MANAGER.getTelegram().onGameChatMessage(message);
        }
    }

    @Inject(method = "onChatMessage", at = @At("TAIL"))
    private void fluxvisuals$syncChatMessage(ChatMessageS2CPacket packet, CallbackInfo ci) {
        Text formattedMessage = packet.unsignedContent();
        if (formattedMessage == null) {
            formattedMessage = packet.serializedParameters()
                    .applyChatDecoration(Text.literal(packet.body().content()));
        }
        String message = formattedMessage.getString();
        boolean botMessage = FluxVisualsClient.MULTI_BOT_MANAGER.handleChatMessage(
                (ClientPlayNetworkHandler) (Object) this, formattedMessage);
        if (!botMessage) {
            FluxVisualsClient.MODULE_MANAGER.getAutoBuy().onChatMessage(message);
            FluxVisualsClient.MODULE_MANAGER.getTelegram().onGameChatMessage(message);
        }
    }

    @Inject(method = "onProfilelessChatMessage", at = @At("TAIL"))
    private void fluxvisuals$syncProfilelessChatMessage(ProfilelessChatMessageS2CPacket packet, CallbackInfo ci) {
        Text formattedMessage = packet.message();
        String message = formattedMessage.getString();
        boolean botMessage = FluxVisualsClient.MULTI_BOT_MANAGER.handleChatMessage(
                (ClientPlayNetworkHandler) (Object) this, formattedMessage);
        if (!botMessage) {
            FluxVisualsClient.MODULE_MANAGER.getAutoBuy().onChatMessage(message);
            FluxVisualsClient.MODULE_MANAGER.getTelegram().onGameChatMessage(message);
        }
    }

    /**
     * FunTime-style captchas often arrive as title/subtitle/actionbar instead
     * of chat. Forward their text through the same per-session pipeline so
     * CaptchaSolver arms even when there is no chat line.
     */
    @Inject(method = "onTitle", at = @At("TAIL"))
    private void fluxvisuals$captchaReadTitle(TitleS2CPacket packet, CallbackInfo ci) {
        try {
            FluxVisualsClient.MULTI_BOT_MANAGER.handleChatMessage(
                    (ClientPlayNetworkHandler) (Object) this, packet.text());
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "onSubtitle", at = @At("TAIL"))
    private void fluxvisuals$captchaReadSubtitle(SubtitleS2CPacket packet, CallbackInfo ci) {
        try {
            FluxVisualsClient.MULTI_BOT_MANAGER.handleChatMessage(
                    (ClientPlayNetworkHandler) (Object) this, packet.text());
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "onOverlayMessage", at = @At("TAIL"))
    private void fluxvisuals$captchaReadOverlayMessage(OverlayMessageS2CPacket packet, CallbackInfo ci) {
        try {
            FluxVisualsClient.MULTI_BOT_MANAGER.handleChatMessage(
                    (ClientPlayNetworkHandler) (Object) this, packet.text());
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void fluxvisuals$spawnTotemParticles(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (MultiBotManager.isBackgroundContext()) {
            return;
        }
        if (packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        Entity entity = packet.getEntity(client.world);
        if (entity != null) {
            FluxVisualsClient.MODULE_MANAGER.getParticles().spawnTotemBurst(entity);
        }
    }

    @Inject(method = "onPlaySound", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolatePlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        if (!fluxvisuals$isCurrentVisibleHandler()) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlaySoundFromEntity", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolatePlaySoundFromEntity(PlaySoundFromEntityS2CPacket packet, CallbackInfo ci) {
        if (!fluxvisuals$isCurrentVisibleHandler()) {
            ci.cancel();
        }
    }

    @Inject(method = "onStopSound", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateStopSound(StopSoundS2CPacket packet, CallbackInfo ci) {
        if (!fluxvisuals$isCurrentVisibleHandler()) {
            ci.cancel();
        }
    }

    @Inject(method = "onParticle", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateParticle(ParticleS2CPacket packet, CallbackInfo ci) {
        if (!fluxvisuals$isCurrentVisibleHandler()) {
            ci.cancel();
        }
    }

    @Inject(method = "onItemPickupAnimation", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateItemPickupAnimation(ItemPickupAnimationS2CPacket packet, CallbackInfo ci) {
        if (!fluxvisuals$isCurrentVisibleHandler()) {
            ci.cancel();
        }
    }

    @Inject(method = "onWorldEvent", at = @At("HEAD"), cancellable = true)
    private void fluxvisuals$isolateWorldEvent(WorldEventS2CPacket packet, CallbackInfo ci) {
        if (!fluxvisuals$isCurrentVisibleHandler()) {
            ci.cancel();
        }
    }
}
