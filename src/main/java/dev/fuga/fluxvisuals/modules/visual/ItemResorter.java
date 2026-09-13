package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;

import java.awt.Color;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.item.ItemStack;

public final class ItemResorter extends Module {
    private static final HighlightMatch NO_MATCH = new HighlightMatch(false, false, 0);
    private static final List<String> EXPLOSIVE_MARKERS = List.of("взрыв", "explosive", "blast", "boom");
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    private final List<String> enchantNeeded = new ArrayList<>();
    private final List<String> enchantIgnored = new ArrayList<>();
    private final List<String> buffNeeded = new ArrayList<>();
    private final List<String> buffIgnored = new ArrayList<>();

    private static final Pattern PRICE_PATTERN = Pattern.compile("([0-9][0-9\\s,._]{2,})");
    private static final Pattern DURABILITY_PATTERN = Pattern.compile("([0-9][0-9\\s,._]*)\\s*/\\s*([0-9][0-9\\s,._]*)");
    private boolean buffExplosiveOnly;
    private boolean priceFilterEnabled;
    private long maxPrice = 3_000_000L;
    private boolean durabilityFilterEnabled;
    private int minDurabilityPercent = 80;
    private float enchantHue = 0.76F;
    private float enchantSaturation = 0.52F;
    private float enchantValue = 1.0F;
    private float buffHue = 0.08F;
    private float buffSaturation = 0.78F;
    private float buffValue = 1.0F;

    public ItemResorter() {
        super("ItemResorter", "Highlights inventory items that match configured enchant or buff rules.", ModuleCategory.UTILS);
    }

    public HighlightMatch matchTooltip(ItemStack stack, List<String> tooltipLines) {
        if (!isEnabled()) {
            return NO_MATCH;
        }
        return matchConfiguredTooltip(stack, tooltipLines);
    }

    public HighlightMatch matchConfiguredTooltip(ItemStack stack, List<String> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            return NO_MATCH;
        }

        List<String> normalizedLines = new ArrayList<>();
        StringBuilder joinedBuilder = new StringBuilder();
        for (String line : tooltipLines) {
            String normalized = normalize(line);
            if (normalized.isEmpty()) {
                continue;
            }
            normalizedLines.add(normalized);
            if (!joinedBuilder.isEmpty()) {
                joinedBuilder.append('\n');
            }
            joinedBuilder.append(normalized);
        }

        if (normalizedLines.isEmpty()) {
            return NO_MATCH;
        }

        String joined = joinedBuilder.toString();
        boolean enchantMatch = matchesGroup(joined, enchantNeeded, enchantIgnored, false);
        boolean buffMatch = matchesGroup(joined, buffNeeded, buffIgnored, buffExplosiveOnly);
        if (!enchantMatch && !buffMatch) {
            return NO_MATCH;
        }
        if (priceFilterEnabled) {
            Long price = extractPrice(tooltipLines);
            if (price == null || price > maxPrice) {
                return NO_MATCH;
            }
        }
        if (durabilityFilterEnabled) {
            Float durabilityPercent = durabilityPercent(stack, tooltipLines);
            if (durabilityPercent == null || durabilityPercent < minDurabilityPercent) {
                return NO_MATCH;
            }
        }
        if (enchantMatch && buffMatch) {
            return new HighlightMatch(true, true, blendRgb(enchantColorRgb(), buffColorRgb()));
        }
        return new HighlightMatch(enchantMatch, buffMatch, enchantMatch ? enchantColorRgb() : buffColorRgb());
    }

    public HighlightMatch matchTooltip(List<String> tooltipLines) {
        return matchTooltip(ItemStack.EMPTY, tooltipLines);
    }

    public List<String> getEnchantNeeded() {
        return List.copyOf(enchantNeeded);
    }

    public List<String> getEnchantIgnored() {
        return List.copyOf(enchantIgnored);
    }

    public List<String> getBuffNeeded() {
        return List.copyOf(buffNeeded);
    }

    public List<String> getBuffIgnored() {
        return List.copyOf(buffIgnored);
    }

    public void setEnchantNeeded(List<String> values) {
        replaceEntries(enchantNeeded, values);
    }

    public void setEnchantIgnored(List<String> values) {
        replaceEntries(enchantIgnored, values);
    }

    public void setBuffNeeded(List<String> values) {
        replaceEntries(buffNeeded, values);
    }

    public void setBuffIgnored(List<String> values) {
        replaceEntries(buffIgnored, values);
    }

    public boolean addEnchantNeeded(String value) {
        return addEntry(enchantNeeded, value);
    }

    public boolean addEnchantIgnored(String value) {
        return addEntry(enchantIgnored, value);
    }

    public boolean addBuffNeeded(String value) {
        return addEntry(buffNeeded, value);
    }

    public boolean addBuffIgnored(String value) {
        return addEntry(buffIgnored, value);
    }

    public void removeEnchantNeeded(String value) {
        removeEntry(enchantNeeded, value);
    }

    public void removeEnchantIgnored(String value) {
        removeEntry(enchantIgnored, value);
    }

    public void removeBuffNeeded(String value) {
        removeEntry(buffNeeded, value);
    }

    public void removeBuffIgnored(String value) {
        removeEntry(buffIgnored, value);
    }

    public boolean isBuffExplosiveOnly() {
        return buffExplosiveOnly;
    }

    public void setBuffExplosiveOnly(boolean buffExplosiveOnly) {
        if (this.buffExplosiveOnly == buffExplosiveOnly) {
            return;
        }
        this.buffExplosiveOnly = buffExplosiveOnly;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isPriceFilterEnabled() {
        return priceFilterEnabled;
    }

    public void setPriceFilterEnabled(boolean priceFilterEnabled) {
        if (this.priceFilterEnabled == priceFilterEnabled) {
            return;
        }
        this.priceFilterEnabled = priceFilterEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public long getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(long maxPrice) {
        long next = Math.max(0L, maxPrice);
        if (this.maxPrice == next) {
            return;
        }
        this.maxPrice = next;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isDurabilityFilterEnabled() {
        return durabilityFilterEnabled;
    }

    public void setDurabilityFilterEnabled(boolean durabilityFilterEnabled) {
        if (this.durabilityFilterEnabled == durabilityFilterEnabled) {
            return;
        }
        this.durabilityFilterEnabled = durabilityFilterEnabled;
        FluxVisualsClient.requestConfigSave();
    }

    public int getMinDurabilityPercent() {
        return minDurabilityPercent;
    }

    public void setMinDurabilityPercent(int minDurabilityPercent) {
        int next = Math.max(1, Math.min(100, minDurabilityPercent));
        if (this.minDurabilityPercent == next) {
            return;
        }
        this.minDurabilityPercent = next;
        FluxVisualsClient.requestConfigSave();
    }

    public float getEnchantHue() {
        return enchantHue;
    }

    public float getEnchantSaturation() {
        return enchantSaturation;
    }

    public float getEnchantValue() {
        return enchantValue;
    }

    public float getBuffHue() {
        return buffHue;
    }

    public float getBuffSaturation() {
        return buffSaturation;
    }

    public float getBuffValue() {
        return buffValue;
    }

    public void setEnchantColor(float hue, float saturation, float value) {
        float nextHue = clamp(hue);
        float nextSaturation = clamp(saturation);
        float nextValue = clamp(value);
        if (Float.compare(enchantHue, nextHue) == 0
                && Float.compare(enchantSaturation, nextSaturation) == 0
                && Float.compare(enchantValue, nextValue) == 0) {
            return;
        }
        enchantHue = nextHue;
        enchantSaturation = nextSaturation;
        enchantValue = nextValue;
        FluxVisualsClient.requestConfigSave();
    }

    public void setBuffColor(float hue, float saturation, float value) {
        float nextHue = clamp(hue);
        float nextSaturation = clamp(saturation);
        float nextValue = clamp(value);
        if (Float.compare(buffHue, nextHue) == 0
                && Float.compare(buffSaturation, nextSaturation) == 0
                && Float.compare(buffValue, nextValue) == 0) {
            return;
        }
        buffHue = nextHue;
        buffSaturation = nextSaturation;
        buffValue = nextValue;
        FluxVisualsClient.requestConfigSave();
    }

    public int enchantColorRgb() {
        return Color.HSBtoRGB(enchantHue, enchantSaturation, enchantValue) & 0x00FFFFFF;
    }

    public int buffColorRgb() {
        return Color.HSBtoRGB(buffHue, buffSaturation, buffValue) & 0x00FFFFFF;
    }

    static boolean matchesGroup(String joinedTooltip, List<String> needed, List<String> ignored, boolean explosiveOnly) {
        if (needed.isEmpty()) {
            return false;
        }
        if (explosiveOnly && !containsExplosive(joinedTooltip)) {
            return false;
        }
        for (String ignoredEntry : ignored) {
            if (matchesEntry(joinedTooltip, ignoredEntry)) {
                return false;
            }
        }
        // Entries with the same base name are level alternatives (e.g.
        // "Острота 7" or "Острота 8"). Different entries are distinct
        // required effects and must all be present on the item.
        for (List<String> alternatives : groupedAlternatives(needed).values()) {
            boolean matchedAny = false;
            for (String neededEntry : alternatives) {
                if (matchesEntry(joinedTooltip, neededEntry)) {
                    matchedAny = true;
                    break;
                }
            }
            if (!matchedAny) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsExplosive(String joinedTooltip) {
        for (String marker : EXPLOSIVE_MARKERS) {
            if (joinedTooltip.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesEntry(String joinedTooltip, String entry) {
        for (String variant : variants(entry)) {
            if (!variant.isEmpty() && joinedTooltip.contains(variant)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<String>> groupedAlternatives(List<String> entries) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String entry : entries) {
            groups.computeIfAbsent(entryBaseKey(entry), ignored -> new ArrayList<>()).add(entry);
        }
        return groups;
    }

    private static String entryBaseKey(String raw) {
        String normalized = normalize(raw);
        int split = normalized.lastIndexOf(' ');
        if (split <= 0 || split >= normalized.length() - 1) {
            return normalized;
        }
        String suffix = normalized.substring(split + 1);
        if (parsePositiveInt(suffix) != null || fromRoman(suffix) > 0) {
            return normalized.substring(0, split).trim();
        }
        return normalized;
    }

    private static List<String> variants(String raw) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return List.of();
        }

        Set<String> variants = new LinkedHashSet<>();
        variants.add(normalized);

        int split = normalized.lastIndexOf(' ');
        if (split > 0 && split < normalized.length() - 1) {
            String prefix = normalized.substring(0, split + 1);
            String suffix = normalized.substring(split + 1);
            Integer arabic = parsePositiveInt(suffix);
            if (arabic != null) {
                String roman = toRoman(arabic);
                if (!roman.isEmpty()) {
                    variants.add(prefix + roman.toLowerCase(Locale.ROOT));
                }
            } else {
                int romanValue = fromRoman(suffix);
                if (romanValue > 0) {
                    variants.add(prefix + romanValue);
                }
            }
        }

        return List.copyOf(variants);
    }

    private boolean addEntry(List<String> target, String value) {
        String cleaned = cleanEntry(value);
        if (cleaned.isEmpty()) {
            return false;
        }
        String normalized = normalize(cleaned);
        for (String existing : target) {
            if (normalize(existing).equals(normalized)) {
                return false;
            }
        }
        target.add(cleaned);
        FluxVisualsClient.requestConfigSave();
        return true;
    }

    private void removeEntry(List<String> target, String value) {
        String normalized = normalize(value);
        boolean removed = target.removeIf(existing -> normalize(existing).equals(normalized));
        if (removed) {
            FluxVisualsClient.requestConfigSave();
        }
    }

    private void replaceEntries(List<String> target, List<String> values) {
        target.clear();
        if (values != null) {
            for (String value : values) {
                String cleaned = cleanEntry(value);
                if (cleaned.isEmpty()) {
                    continue;
                }
                boolean duplicate = false;
                String normalized = normalize(cleaned);
                for (String existing : target) {
                    if (normalize(existing).equals(normalized)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    target.add(cleaned);
                }
            }
        }
        FluxVisualsClient.requestConfigSave();
    }

    private static String cleanEntry(String value) {
        return value == null ? "" : value.trim();
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String repaired = repairMojibake(value);
        return repaired.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Some server lore and the old Premium GUI text can arrive as UTF-8 bytes
     * decoded as Windows-1251 ("РћСЃС‚СЂ..." instead of "Острота...").
     * Recover only when round-tripping through CP1251 yields valid UTF-8;
     * ordinary Russian text would produce replacement characters and is kept.
     */
    private static String repairMojibake(String value) {
        if (value.isEmpty()) {
            return value;
        }
        String candidate = new String(value.getBytes(WINDOWS_1251), StandardCharsets.UTF_8);
        return candidate.indexOf('\uFFFD') < 0 && !candidate.equals(value) ? candidate : value;
    }

    private static int blendRgb(int first, int second) {
        int r = (((first >> 16) & 255) + ((second >> 16) & 255)) / 2;
        int g = (((first >> 8) & 255) + ((second >> 8) & 255)) / 2;
        int b = ((first & 255) + (second & 255)) / 2;
        return (r << 16) | (g << 8) | b;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static Long extractPrice(List<String> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            return null;
        }
        long best = -1L;
        for (String line : tooltipLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String normalized = normalize(line);
            if (!normalized.contains("цен") && !normalized.contains("price") && !line.contains("$")) {
                continue;
            }
            Matcher matcher = PRICE_PATTERN.matcher(line);
            while (matcher.find()) {
                String digits = matcher.group(1).replaceAll("[^0-9]", "");
                if (digits.isEmpty()) {
                    continue;
                }
                try {
                    long value = Long.parseLong(digits);
                    if (value > best) {
                        best = value;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed values.
                }
            }
        }
        return best < 0L ? null : best;
    }

    private static Float durabilityPercent(ItemStack stack, List<String> tooltipLines) {
        Float tooltipValue = tooltipDurabilityPercent(tooltipLines);
        if (tooltipValue != null) {
            return tooltipValue;
        }
        if (stack == null || stack.isEmpty() || !stack.isDamageable()) {
            return null;
        }
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return null;
        }
        int remaining = Math.max(0, max - stack.getDamage());
        return remaining * 100.0F / max;
    }

    private static Float tooltipDurabilityPercent(List<String> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            return null;
        }
        for (String line : tooltipLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String normalized = normalize(line);
            if (!normalized.contains("\u043f\u0440\u043e\u0447\u043d\u043e\u0441\u0442") && !normalized.contains("durability")) {
                continue;
            }
            Matcher matcher = DURABILITY_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            try {
                long current = Long.parseLong(matcher.group(1).replaceAll("[^0-9]", ""));
                long max = Long.parseLong(matcher.group(2).replaceAll("[^0-9]", ""));
                if (max > 0L) {
                    return Math.max(0.0F, Math.min(100.0F, current * 100.0F / max));
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed durability lines.
            }
        }
        return null;
    }

    private static Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String toRoman(int value) {
        if (value <= 0 || value > 50) {
            return "";
        }
        int remaining = value;
        int[] numbers = {50, 40, 10, 9, 5, 4, 1};
        String[] romans = {"L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                builder.append(romans[i]);
                remaining -= numbers[i];
            }
        }
        return builder.toString();
    }

    private static int fromRoman(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        int total = 0;
        int previous = 0;
        for (int i = upper.length() - 1; i >= 0; i--) {
            int current = romanDigit(upper.charAt(i));
            if (current <= 0) {
                return -1;
            }
            if (current < previous) {
                total -= current;
            } else {
                total += current;
                previous = current;
            }
        }
        return total;
    }

    private static int romanDigit(char c) {
        return switch (c) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            default -> -1;
        };
    }

    public record HighlightMatch(boolean enchantMatch, boolean buffMatch, int colorRgb) {
        public boolean matches() {
            return enchantMatch || buffMatch;
        }
    }
}
