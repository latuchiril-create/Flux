package dev.fuga.fluxvisuals.modules.visual;

import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import dev.fuga.fluxvisuals.modules.ModuleCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

public final class NameProtect extends Module {
    private static final Pattern ANARCHY_PATTERN = Pattern.compile("(?iu)((?:\\u0410\\u043d\\u0430\\u0440\\u0445\\u0438\\u044f|anarchy)\\s*(?:[#:\\-\\u2010-\\u2015]?\\s*))(\\d+)");

    private String replacement = "Protected";
    private String anarchyReplacement = "0000";
    private boolean protectAnarchy = true;

    public NameProtect() {
        super("NameProtect", "Replaces your nickname in rendered text.", ModuleCategory.UTILS);
    }

    public String protect(String value) {
        if (!isEnabled() || value == null || value.isEmpty()) {
            return value;
        }

        String result = value;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSession() != null) {
            String username = client.getSession().getUsername();
            if (username != null && !username.isBlank()) {
                result = Pattern.compile(Pattern.quote(username), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(result)
                        .replaceAll(Matcher.quoteReplacement(replacement));
            }
        }
        // Protect every managed bot name as well as the primary account. This
        // is intentionally independent of the currently controlled session so
        // target HUD, nametags and server overlays cannot reveal a bot name.
        if (FluxVisualsClient.MULTI_BOT_MANAGER != null) {
            Set<String> managedNames = FluxVisualsClient.MULTI_BOT_MANAGER.getManagedPlayerNames();
            for (String managedName : managedNames) {
                if (managedName == null || managedName.isBlank()) {
                    continue;
                }
                result = Pattern.compile(Pattern.quote(managedName), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(result)
                        .replaceAll(Matcher.quoteReplacement(replacement));
            }
        }
        if (protectAnarchy) {
            result = replaceAnarchyDigits(result);
        }
        return result;
    }

    private String replaceAnarchyDigits(String value) {
        Matcher matcher = ANARCHY_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        String digits = anarchyDigits();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + digits));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String anarchyDigits() {
        String digits = anarchyReplacement == null ? "" : anarchyReplacement.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return "0000";
        }
        return digits.length() > 8 ? digits.substring(0, 8) : digits;
    }

    public Text protect(Text text) {
        if (text == null) {
            return null;
        }
        if (!isEnabled()) {
            return text;
        }
        MutableText result = Text.empty();
        boolean[] changed = {false};
        text.visit((style, value) -> {
            appendProtected(result, value, style, changed);
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? result : text;
    }

    public StringVisitable protect(StringVisitable visitable) {
        if (visitable == null) {
            return null;
        }
        if (!isEnabled()) {
            return visitable;
        }
        MutableText result = Text.empty();
        boolean[] changed = {false};
        visitable.visit((style, value) -> {
            appendProtected(result, value, style, changed);
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? result : visitable;
    }

    public OrderedText protect(OrderedText text) {
        if (text == null || !isEnabled()) {
            return text;
        }
        StringBuilder raw = new StringBuilder();
        List<StyledPart> parts = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        Style[] segmentStyle = {null};
        text.accept((index, style, codePoint) -> {
            raw.appendCodePoint(codePoint);
            if (segmentStyle[0] == null || !segmentStyle[0].equals(style)) {
                flushPart(parts, segment, segmentStyle[0]);
                segmentStyle[0] = style;
            }
            segment.appendCodePoint(codePoint);
            return true;
        });
        flushPart(parts, segment, segmentStyle[0]);

        MutableText result = Text.empty();
        boolean changed = false;
        for (StyledPart part : parts) {
            String replaced = protect(part.value());
            if (!part.value().equals(replaced)) {
                changed = true;
            }
            result.append(Text.literal(replaced).setStyle(part.style()));
        }

        String replacedRaw = protect(raw.toString());
        if (!changed && !raw.toString().equals(replacedRaw)) {
            return Text.literal(replacedRaw).asOrderedText();
        }
        return changed ? result.asOrderedText() : text;
    }

    private void appendProtected(MutableText result, String value, Style style, boolean[] changed) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String replaced = protect(value);
        if (!value.equals(replaced)) {
            changed[0] = true;
        }
        result.append(Text.literal(replaced).setStyle(style));
    }

    private static void flushPart(List<StyledPart> parts, StringBuilder segment, Style style) {
        if (segment.isEmpty()) {
            return;
        }
        parts.add(new StyledPart(segment.toString(), style == null ? Style.EMPTY : style));
        segment.setLength(0);
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        String next = replacement == null ? "" : replacement;
        if (next.length() > 48) {
            next = next.substring(0, 48);
        }
        if (this.replacement.equals(next)) {
            return;
        }
        this.replacement = next;
        FluxVisualsClient.requestConfigSave();
    }

    public String getAnarchyReplacement() {
        return anarchyReplacement;
    }

    public void setAnarchyReplacement(String anarchyReplacement) {
        String next = anarchyReplacement == null ? "" : anarchyReplacement.replaceAll("\\D+", "");
        if (next.length() > 8) {
            next = next.substring(0, 8);
        }
        if (this.anarchyReplacement.equals(next)) {
            return;
        }
        this.anarchyReplacement = next;
        FluxVisualsClient.requestConfigSave();
    }

    public boolean isProtectAnarchy() {
        return protectAnarchy;
    }

    public void setProtectAnarchy(boolean protectAnarchy) {
        if (this.protectAnarchy == protectAnarchy) {
            return;
        }
        this.protectAnarchy = protectAnarchy;
        FluxVisualsClient.requestConfigSave();
    }

    private record StyledPart(String value, Style style) {
    }
}
