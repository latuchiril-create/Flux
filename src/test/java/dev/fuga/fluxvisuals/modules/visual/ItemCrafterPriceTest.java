package dev.fuga.fluxvisuals.modules.visual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ItemCrafterPriceTest {
    @Test
    void usesActualSlotCountWhenAuctionTooltipDoesNotExposeQuantity() {
        assertEquals(16, ItemCrafter.extractQuantityFromLot(
                16, List.of("Цена: 800 000", "Продавец: Test")
        ));
    }

    @Test
    void keepsExplicitTooltipQuantityWhenTheAuctionIconIsSingleItem() {
        assertEquals(7, ItemCrafter.extractQuantityFromLot(
                1, List.of("Количество: 7", "Цена: 400 000")
        ));
    }
}
