package dev.fuga.fluxvisuals.modules.visual;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemResorterMatchingTest {
    @Test
    void requiresEveryDistinctConfiguredWantedEnchantment() {
        assertTrue(!ItemResorter.matchesGroup(
                "незеритовый меч\nострота vii",
                List.of("Острота 7", "Яд 2", "Окисление 2", "Вампиризм 2", "Добыча"),
                List.of(),
                false
        ));
    }

    @Test
    void highlightsSwordWhenItHasEveryConfiguredWantedEnchantment() {
        assertTrue(ItemResorter.matchesGroup(
                "незеритовый меч\nострота vii\nяд ii\nокисление ii\nвампиризм ii\nдобыча iii",
                List.of("Острота 7", "Яд 2", "Окисление 2", "Вампиризм 2", "Добыча"),
                List.of(),
                false
        ));
    }

    @Test
    void normalizesCp1251Utf8MojibakeInServerTooltip() {
        String broken = new String("Острота 7".getBytes(StandardCharsets.UTF_8), Charset.forName("windows-1251"));
        assertTrue(ItemResorter.normalize(broken).contains("острота 7"));
    }
}
