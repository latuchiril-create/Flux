package dev.fuga.fluxvisuals.multibot;

import dev.fuga.fluxvisuals.modules.visual.Telegram;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Renders an authentic Minecraft GUI snapshot of a bot's inventory or open container.
 * 100% thread-safe, memory-only, zero OpenGL dependencies, zero network packets.
 */
public final class BotInventoryRenderer {
    private static final int SCALE = 3; // 3x scaling for crisp pixel-art look
    private static final ConcurrentMap<String, BufferedImage> ITEM_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static volatile BufferedImage vanillaInventoryGui;

    // Authentic Minecraft GUI Colors
    private static final Color MC_GUI_BG = new Color(198, 198, 198);
    private static final Color MC_BEVEL_LIGHT = new Color(255, 255, 255);
    private static final Color MC_BEVEL_DARK = new Color(55, 55, 55);
    private static final Color MC_SLOT_BG = new Color(139, 139, 139);
    private static final Color MC_SLOT_BORDER_DARK = new Color(55, 55, 55);
    private static final Color MC_SLOT_BORDER_LIGHT = new Color(255, 255, 255);
    private static final Color HEADER_BG = new Color(20, 22, 26);
    private static final Color GOLD_TEXT = new Color(255, 215, 0);
    private static final Color GREEN_TEXT = new Color(85, 255, 85);
    private static final Color RED_TEXT = new Color(255, 85, 85);
    private static final Color SHADOW_COLOR = new Color(30, 30, 30);

    private BotInventoryRenderer() {
    }

    public static byte[] renderInventoryPng(BotSession session) {
        if (session == null || session.getPlayer() == null) {
            return null;
        }

        Screen screen = session.getScreen();
        boolean hasContainer = screen instanceof HandledScreen<?>;
        ScreenHandler containerHandler = hasContainer ? session.getPlayer().currentScreenHandler : null;
        int containerRows = 0;
        if (hasContainer && containerHandler != null) {
            int nonPlayerSlots = 0;
            for (Slot slot : containerHandler.slots) {
                if (slot.inventory != session.getPlayer().getInventory()) {
                    nonPlayerSlots++;
                }
            }
            containerRows = Math.min(6, Math.max(1, (nonPlayerSlots + 8) / 9));
        }

        int guiWidth = 176 * SCALE;
        int guiHeight = (hasContainer ? (114 + containerRows * 18) : 166) * SCALE;
        int headerHeight = 72;
        int totalWidth = guiWidth;
        int totalHeight = headerHeight + guiHeight;

        BufferedImage image = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. Draw Top Status Banner
        drawHeaderBanner(g, session, totalWidth, headerHeight);

        // 2. Draw Minecraft Inventory / Container GUI
        int guiY = headerHeight;
        if (hasContainer && containerRows > 0) {
            drawContainerGui(g, session, containerHandler, containerRows, 0, guiY);
        } else {
            drawPlayerInventoryGui(g, session, 0, guiY);
        }

        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static void drawHeaderBanner(Graphics2D g, BotSession session, int width, int height) {
        g.setColor(HEADER_BG);
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(60, 60, 70));
        g.drawLine(0, height - 1, width, height - 1);

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(GOLD_TEXT);
        g.drawString("🤖 " + session.getName() + (session.isMain() ? " [Основа]" : " [Бот]"), 12, 22);

        String stateName = session.getState().name();
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(session.getState() == BotSession.State.PLAYING ? GREEN_TEXT : RED_TEXT);
        int stateWidth = g.getFontMetrics().stringWidth("● " + stateName);
        g.drawString("● " + stateName, width - stateWidth - 12, 22);

        float hp = session.getPlayer().getHealth();
        float maxHp = session.getPlayer().getMaxHealth();
        int food = session.getPlayer().getHungerManager().getFoodLevel();
        long balance = dev.fuga.fluxvisuals.FluxVisualsClient.MULTI_BOT_MANAGER.resolveSessionBalance(session);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(Color.WHITE);
        g.drawString(String.format(Locale.ROOT, "❤️ %.1f/%.1f   🍖 %d/20", hp, maxHp, food), 12, 42);

        g.setColor(GOLD_TEXT);
        String balStr = balance >= 0L ? String.format(Locale.ROOT, "%,d$", balance).replace(',', ' ') : "неизвестен";
        g.drawString("💰 Баланс: " + balStr, 12, 60);

        String screenName = Telegram.formatScreenTitle(session.getScreen());
        g.setColor(new Color(180, 210, 255));
        g.drawString("🖥️ " + screenName, width / 2, 60);
    }

    private static void drawPlayerInventoryGui(Graphics2D g, BotSession session, int originX, int originY) {
        BufferedImage bg = getVanillaInventoryGui();
        if (bg != null) {
            g.drawImage(bg, originX, originY, 176 * SCALE, 166 * SCALE, null);
        } else {
            drawFallbackInventoryBackground(g, originX, originY, 176 * SCALE, 166 * SCALE);
        }

        // Draw Player Character / Avatar in preview box (between armor and crafting)
        drawPlayerAvatarBox(g, session, originX + 26 * SCALE, originY + 8 * SCALE, 50 * SCALE, 70 * SCALE);

        PlayerInventory inv = session.getPlayer().getInventory();

        // 4 Armor slots (Helmet: 39, Chest: 38, Legs: 37, Boots: 36)
        int armorBaseX = originX + 8 * SCALE;
        int armorBaseY = originY + 8 * SCALE;
        for (int i = 3; i >= 0; i--) {
            ItemStack stack = inv.getStack(36 + i);
            int slotX = armorBaseX;
            int slotY = armorBaseY + (3 - i) * 18 * SCALE;
            drawItemInSlot(g, stack, slotX, slotY);
        }

        // Offhand slot (slot 40) at (77, 62)
        ItemStack offhand = inv.getStack(40);
        drawItemInSlot(g, offhand, originX + 77 * SCALE, originY + 62 * SCALE);

        // 27 Main Inventory Slots (index 9 to 35) at (8, 84)
        int mainBaseX = originX + 8 * SCALE;
        int mainBaseY = originY + 84 * SCALE;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                ItemStack stack = inv.getStack(slotIndex);
                int slotX = mainBaseX + col * 18 * SCALE;
                int slotY = mainBaseY + row * 18 * SCALE;
                drawItemInSlot(g, stack, slotX, slotY);
            }
        }

        // 9 Hotbar Slots (index 0 to 8) at (8, 142)
        int hotbarBaseX = originX + 8 * SCALE;
        int hotbarBaseY = originY + 142 * SCALE;
        int selectedHotbar = inv.getSelectedSlot();
        for (int col = 0; col < 9; col++) {
            ItemStack stack = inv.getStack(col);
            int slotX = hotbarBaseX + col * 18 * SCALE;
            int slotY = hotbarBaseY;
            if (col == selectedHotbar) {
                g.setColor(new Color(255, 255, 255, 90));
                g.fillRect(slotX, slotY, 16 * SCALE, 16 * SCALE);
            }
            drawItemInSlot(g, stack, slotX, slotY);
        }
    }

    private static void drawContainerGui(Graphics2D g, BotSession session, ScreenHandler handler, int rows, int originX, int originY) {
        int guiH = (114 + rows * 18) * SCALE;
        drawFallbackContainerBackground(g, originX, originY, 176 * SCALE, guiH, rows);

        // Draw Container Slots
        int containerSlotIndex = 0;
        int containerBaseX = originX + 8 * SCALE;
        int containerBaseY = originY + 18 * SCALE;
        for (Slot slot : handler.slots) {
            if (slot.inventory == session.getPlayer().getInventory()) {
                continue;
            }
            int col = containerSlotIndex % 9;
            int row = containerSlotIndex / 9;
            if (row >= rows) break;
            int slotX = containerBaseX + col * 18 * SCALE;
            int slotY = containerBaseY + row * 18 * SCALE;
            drawItemInSlot(g, slot.getStack(), slotX, slotY);
            containerSlotIndex++;
        }

        // Draw Player Inventory inside Container GUI
        PlayerInventory inv = session.getPlayer().getInventory();
        int playerBaseX = originX + 8 * SCALE;
        int playerBaseY = originY + (18 + rows * 18 + 14) * SCALE;

        // Main 3x9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                ItemStack stack = inv.getStack(slotIndex);
                int slotX = playerBaseX + col * 18 * SCALE;
                int slotY = playerBaseY + row * 18 * SCALE;
                drawItemInSlot(g, stack, slotX, slotY);
            }
        }

        // Hotbar
        int hotbarY = playerBaseY + 3 * 18 * SCALE + 4 * SCALE;
        int selectedHotbar = inv.getSelectedSlot();
        for (int col = 0; col < 9; col++) {
            ItemStack stack = inv.getStack(col);
            int slotX = playerBaseX + col * 18 * SCALE;
            int slotY = hotbarY;
            if (col == selectedHotbar) {
                g.setColor(new Color(255, 255, 255, 90));
                g.fillRect(slotX, slotY, 16 * SCALE, 16 * SCALE);
            }
        }
    }

    private static void drawPlayerAvatarBox(Graphics2D g, BotSession session, int x, int y, int w, int h) {
        g.setColor(new Color(35, 38, 45));
        g.fillRect(x, y, w, h);
        g.setColor(MC_BEVEL_DARK);
        g.drawRect(x, y, w, h);

        int centerX = x + w / 2;
        int headSize = 24 * SCALE / 2;
        int headY = y + 10;

        // Head
        g.setColor(new Color(220, 185, 150));
        g.fillRoundRect(centerX - headSize / 2, headY, headSize, headSize, 4, 4);

        // Hair / Helmet
        ItemStack helm = session.getPlayer() != null ? session.getPlayer().getInventory().getStack(39) : null;
        if (helm != null && !helm.isEmpty()) {
            g.setColor(new Color(60, 60, 75));
            g.fillRect(centerX - headSize / 2, headY, headSize, headSize / 2);
        } else {
            g.setColor(new Color(90, 60, 30));
            g.fillRect(centerX - headSize / 2, headY, headSize, 6);
        }

        // Body
        int bodyY = headY + headSize + 2;
        int bodyW = headSize;
        int bodyH = headSize + 6;
        ItemStack chest = session.getPlayer() != null ? session.getPlayer().getInventory().getStack(38) : null;
        if (chest != null && !chest.isEmpty()) {
            g.setColor(new Color(70, 70, 85));
        } else {
            g.setColor(new Color(40, 120, 180));
        }
        g.fillRect(centerX - bodyW / 2, bodyY, bodyW, bodyH);

        // Arms
        int armW = 6;
        int armH = bodyH;
        g.fillRect(centerX - bodyW / 2 - armW - 1, bodyY, armW, armH);
        g.fillRect(centerX + bodyW / 2 + 1, bodyY, armW, armH);

        // Legs
        int legY = bodyY + bodyH + 1;
        int legW = headSize / 2 - 1;
        int legH = 14;
        ItemStack legs = session.getPlayer() != null ? session.getPlayer().getInventory().getStack(37) : null;
        if (legs != null && !legs.isEmpty()) {
            g.setColor(new Color(50, 50, 65));
        } else {
            g.setColor(new Color(50, 60, 120));
        }
        g.fillRect(centerX - bodyW / 2, legY, legW, legH);
        g.fillRect(centerX + 1, legY, legW, legH);

        // Name under avatar
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(190, 210, 235));
        String name = session.getName();
        if (name.length() > 8) name = name.substring(0, 8) + "..";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(name, centerX - fm.stringWidth(name) / 2, y + h - 5);
    }

    private static void drawItemInSlot(Graphics2D g, ItemStack stack, int slotX, int slotY) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        int itemSize = 16 * SCALE;
        BufferedImage itemTexture = getItemTexture(stack);
        if (itemTexture != null) {
            g.drawImage(itemTexture, slotX, slotY, itemSize, itemSize, null);
        } else {
            drawFallbackItemIcon(g, stack, slotX, slotY, itemSize);
        }

        // Enchantment glint effect
        if (stack.hasEnchantments()) {
            g.setColor(new Color(180, 100, 255, 55));
            g.fillRect(slotX, slotY, itemSize, itemSize);
        }

        // Durability bar
        if (stack.isDamageable()) {
            int maxDamage = Math.max(1, stack.getMaxDamage());
            int damage = stack.getDamage();
            int remaining = Math.max(0, maxDamage - damage);
            float ratio = (float) remaining / (float) maxDamage;
            int barWidth = Math.round((itemSize - 4) * ratio);
            int barY = slotY + itemSize - 4;

            g.setColor(Color.BLACK);
            g.fillRect(slotX + 2, barY, itemSize - 4, 3);

            Color barColor = ratio > 0.5f ? new Color(0, 255, 0) : ratio > 0.2f ? new Color(255, 215, 0) : new Color(255, 0, 0);
            g.setColor(barColor);
            g.fillRect(slotX + 2, barY, barWidth, 2);
        }

        // Stack count (white with dark shadow at bottom right)
        if (stack.getCount() > 1) {
            String countText = String.valueOf(stack.getCount());
            g.setFont(new Font("SansSerif", Font.BOLD, 12 * SCALE / 2));
            FontMetrics fm = g.getFontMetrics();
            int tx = slotX + itemSize - fm.stringWidth(countText) - 2;
            int ty = slotY + itemSize - 3;

            g.setColor(SHADOW_COLOR);
            g.drawString(countText, tx + 1, ty + 1);
            g.setColor(Color.WHITE);
            g.drawString(countText, tx, ty);
        }
    }

    private static BufferedImage getItemTexture(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String key = id.getNamespace() + ":" + id.getPath();
        return ITEM_TEXTURE_CACHE.computeIfAbsent(key, k -> loadItemTextureFromGame(id));
    }

    private static BufferedImage loadItemTextureFromGame(Identifier itemId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null) {
            return null;
        }
        String path = itemId.getPath();
        Identifier[] candidates = {
                Identifier.of(itemId.getNamespace(), "textures/item/" + path + ".png"),
                Identifier.of(itemId.getNamespace(), "textures/block/" + path + ".png"),
                Identifier.of(itemId.getNamespace(), "textures/item/" + path + "_front.png")
        };
        for (Identifier cand : candidates) {
            Optional<Resource> res = client.getResourceManager().getResource(cand);
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream()) {
                    return ImageIO.read(in);
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private static BufferedImage getVanillaInventoryGui() {
        if (vanillaInventoryGui != null) return vanillaInventoryGui;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getResourceManager() != null) {
            Optional<Resource> res = client.getResourceManager().getResource(
                    Identifier.of("minecraft", "textures/gui/container/inventory.png")
            );
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream()) {
                    vanillaInventoryGui = ImageIO.read(in);
                    return vanillaInventoryGui;
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private static void drawFallbackInventoryBackground(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(MC_GUI_BG);
        g.fillRect(x, y, w, h);
        draw3DBevel(g, x, y, w, h);

        // Armor slots (4)
        for (int i = 0; i < 4; i++) {
            drawBeveledSlot(g, x + 8 * SCALE, y + (8 + i * 18) * SCALE);
        }
        // Offhand
        drawBeveledSlot(g, x + 77 * SCALE, y + 62 * SCALE);

        // 3x9 Main
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                drawBeveledSlot(g, x + (8 + c * 18) * SCALE, y + (84 + r * 18) * SCALE);
            }
        }
        // Hotbar
        for (int c = 0; c < 9; c++) {
            drawBeveledSlot(g, x + (8 + c * 18) * SCALE, y + 142 * SCALE);
        }
    }

    private static void drawFallbackContainerBackground(Graphics2D g, int x, int y, int w, int h, int rows) {
        g.setColor(MC_GUI_BG);
        g.fillRect(x, y, w, h);
        draw3DBevel(g, x, y, w, h);

        // Container rows
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                drawBeveledSlot(g, x + (8 + c * 18) * SCALE, y + (18 + r * 18) * SCALE);
            }
        }
        // Player 3x9
        int pY = y + (18 + rows * 18 + 14) * SCALE;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                drawBeveledSlot(g, x + (8 + c * 18) * SCALE, pY + r * 18 * SCALE);
            }
        }
        // Hotbar
        int hbY = pY + 3 * 18 * SCALE + 4 * SCALE;
        for (int c = 0; c < 9; c++) {
            drawBeveledSlot(g, x + (8 + c * 18) * SCALE, hbY);
        }
    }

    private static void drawBeveledSlot(Graphics2D g, int x, int y) {
        int s = 18 * SCALE;
        g.setColor(MC_SLOT_BG);
        g.fillRect(x, y, s, s);

        g.setColor(MC_SLOT_BORDER_DARK);
        g.drawLine(x, y, x + s - 1, y);
        g.drawLine(x, y, x, y + s - 1);

        g.setColor(MC_SLOT_BORDER_LIGHT);
        g.drawLine(x + s - 1, y, x + s - 1, y + s - 1);
        g.drawLine(x, y + s - 1, x + s - 1, y + s - 1);
    }

    private static void draw3DBevel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(MC_BEVEL_LIGHT);
        g.drawLine(x, y, x + w - 1, y);
        g.drawLine(x, y, x, y + h - 1);

        g.setColor(MC_BEVEL_DARK);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
    }

    private static void drawFallbackItemIcon(Graphics2D g, ItemStack stack, int x, int y, int size) {
        String name = stack.getName().getString();
        String lower = name.toLowerCase(Locale.ROOT);

        Color itemBg = new Color(48, 50, 58);
        Color accentColor = Color.WHITE;

        if (lower.contains("меч") || lower.contains("sword")) {
            itemBg = new Color(30, 45, 65);
            accentColor = new Color(120, 200, 255);
        } else if (lower.contains("тотем") || lower.contains("totem")) {
            itemBg = new Color(85, 70, 20);
            accentColor = GOLD_TEXT;
        } else if (lower.contains("сфер") || lower.contains("head") || lower.contains("голов")) {
            itemBg = new Color(25, 65, 65);
            accentColor = new Color(80, 255, 210);
        } else if (lower.contains("шлем") || lower.contains("нагруд") || lower.contains("понож") || lower.contains("ботин") || lower.contains("элитр")) {
            itemBg = new Color(42, 42, 52);
            accentColor = new Color(175, 185, 210);
        } else if (lower.contains("зелье") || lower.contains("potion") || lower.contains("пузыр")) {
            itemBg = new Color(70, 25, 70);
            accentColor = new Color(255, 120, 255);
        } else if (lower.contains("яблок") || lower.contains("apple") || lower.contains("гэпл") || lower.contains("чарк")) {
            itemBg = new Color(80, 65, 15);
            accentColor = new Color(255, 230, 80);
        } else if (lower.contains("кристалл") || lower.contains("crystal")) {
            itemBg = new Color(75, 25, 65);
            accentColor = new Color(255, 150, 230);
        } else if (lower.contains("якорь") || lower.contains("anchor")) {
            itemBg = new Color(40, 20, 60);
            accentColor = new Color(190, 100, 255);
        } else if (lower.contains("стрел") || lower.contains("arrow")) {
            itemBg = new Color(55, 45, 35);
            accentColor = new Color(210, 175, 130);
        }

        g.setColor(itemBg);
        g.fillRoundRect(x + 2, y + 2, size - 4, size - 4, 6, 6);
        g.setColor(MC_BEVEL_DARK);
        g.drawRoundRect(x + 2, y + 2, size - 4, size - 4, 6, 6);

        String abbr = name.length() > 4 ? name.substring(0, 4) : name;
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(accentColor);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(abbr, x + (size - fm.stringWidth(abbr)) / 2, y + size / 2 + 4);
    }
}
