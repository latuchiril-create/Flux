# Beautiful GUI Blur — post-effect для Minecraft 1.21.8 (Fabric)

Формат проверен по ванильным ассетам 1.21.8 (`post_effect` + `#version 150`,
UBO `Globals` / `Projection` / `SamplerInfo`).

## Что внутри

```
assets/mymod/post_effect/gui_blur.json     <- ваша собственная цепочка
assets/mymod/shaders/post/gaussian_blur.vsh|fsh
assets/mymod/shaders/post/blur_composite.vsh|fsh
assets/minecraft/post_effect/blur.json     <- переопределение ванильного блюра
```

## Установка

1. Скопируйте папку `assets/` в `src/main/resources/` вашего мода.
2. Замените `mymod` на ваш mod id (в путях папок И в json — поля
   `vertex_shader` / `fragment_shader`).
3. Если НЕ хотите трогать ванильный блюр — удалите
   `assets/minecraft/post_effect/blur.json`.

## Как включить

Вариант A (0 строк Java): оставьте `assets/minecraft/post_effect/blur.json`.
Любой экран, вызывающий `Screen#renderBackground`, получит ваш блюр.

Вариант B: в своём Screen явно попросите движок размыть всё, что нарисовано ниже:

```java
@Override
public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    context.nextStratum();             // всё, что ниже — отдельный слой
    context.blurBeforeThisStratum();   // применить post_effect "minecraft:blur"
    context.fill(0, 0, this.width, this.height, 0x30000000); // затемнение сверху
}
```

## Настройка

Все параметры — в `gui_blur.json`, перекомпиляция не нужна (F3+T перезагружает ресурсы).

| Uniform | Где | Смысл |
|---|---|---|
| `Radius` | BlurConfig | радиус в текселях (1..64) |
| `Spread` | BlurConfig | шаг между семплами; >1 = шире и дешевле |
| `Tint` | BlurStyle | rgb = цвет, a = сила тонирования |
| `Brightness` | BlurStyle | 1.0 = без изменений |
| `Saturation` | BlurStyle | 1.0 = без изменений, 0.0 = ч/б |
| `Vignette` | BlurStyle | 0.0..1.0 |
| `Grain` | BlurStyle | ~0.015-0.03, убирает бандинг |

Сильнее размыть: поднимите `Spread` второй пары проходов (3.0 -> 5.0).
Дешевле: удалите первую пару проходов и подайте `minecraft:main` сразу во вторую.
