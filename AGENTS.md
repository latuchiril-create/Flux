# Задача для Codex: установить post-effect блюр в мод Minecraft 1.21.8 (Fabric)

Ты работаешь в репозитории мода Minecraft для **Fabric, версия игры 1.21.8**.
Рядом лежит распакованный пакет `beautiful-gui-blur/` с готовым шейдером.
Твоя задача — корректно встроить его в проект.

## Контекст, который нельзя проигнорировать

- Начиная с **1.21.5** Mojang полностью переписали систему шейдеров.
  Старые гайды («intarget/outtarget», `uniform float Time`, `blit.json`,
  `shaders/program/*.json`) **несовместимы** с 1.21.8. Не «чини» файлы по ним.
- Актуальный формат: `assets/<namespace>/post_effect/<name>.json`,
  поля `vertex_shader` / `fragment_shader` / `inputs` / `output` / `uniforms`,
  GLSL `#version 150`, юниформы только через `layout(std140) uniform <Block>`,
  инклуды через `#moj_import <minecraft:globals.glsl>`.
- Файлы в пакете уже написаны под этот формат и **проверены**. Правь их только
  если тебя об этом явно попросили.

## Шаги

1. Определи корень Gradle-проекта (файл `build.gradle` или `build.gradle.kts`).
2. Найди `src/main/resources/fabric.mod.json` и возьми из него значение `id`.
   Это mod id. Если файла нет — спроси пользователя.
3. Запусти установщик:

   ```bash
   python3 beautiful-gui-blur/install.py --project . --mod-id <MOD_ID>
   ```

   Скрипт сам скопирует ассеты, заменит namespace `mymod` на `<MOD_ID>`
   и проверит, что все ссылки на шейдеры разрешаются. Он идемпотентен —
   повторный запуск безопасен.

   Если пользователь **не хочет** менять внешний вид ванильных экранов,
   добавь флаг `--no-vanilla-override`.

4. Проверь результат:

   ```bash
   find src/main/resources/assets -path '*post_effect*' -o -path '*shaders/post*' | sort
   python3 -c "import json,glob;[json.load(open(f)) for f in glob.glob('src/main/resources/assets/*/post_effect/*.json')]"
   ```

   Ожидаемые файлы:

   ```
   src/main/resources/assets/<MOD_ID>/post_effect/gui_blur.json
   src/main/resources/assets/<MOD_ID>/shaders/post/gaussian_blur.vsh
   src/main/resources/assets/<MOD_ID>/shaders/post/gaussian_blur.fsh
   src/main/resources/assets/<MOD_ID>/shaders/post/blur_composite.vsh
   src/main/resources/assets/<MOD_ID>/shaders/post/blur_composite.fsh
   src/main/resources/assets/minecraft/post_effect/blur.json   (если без --no-vanilla-override)
   ```

5. Собери проект, чтобы убедиться, что ничего не сломано:

   ```bash
   ./gradlew build
   ```

   Java-код не менялся, так что сборка обязана пройти. Если она падала и до
   твоих правок — сообщи об этом, но не пытайся чинить чужие ошибки.

6. Сообщи пользователю итог: какие файлы добавлены, какой namespace,
   и как включить эффект (см. ниже).

## Как эффект включается в игре

**Если ванильный override установлен (по умолчанию)** — ничего писать не надо.
Любой экран, вызывающий `Screen#renderBackground`, получает новый блюр.

**Если стоял `--no-vanilla-override`**, либо пользователь хочет вызывать блюр
вручную из своего экрана — добавь в его `Screen`:

```java
@Override
public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    context.nextStratum();
    context.blurBeforeThisStratum();
    context.fill(0, 0, this.width, this.height, 0x30000000);
}
```

Замечания:
- В Mojang-маппингах класс называется `GuiGraphics`, в Yarn — `DrawContext`.
  Посмотри, какие маппинги в `build.gradle`, и используй соответствующее имя.
- Методы `nextStratum()` и `blurBeforeThisStratum()` появились в 1.21.6.
  На 1.21.5 и ниже их нет — там был `minecraft.gameRenderer.processBlurEffect()`.
- `blurBeforeThisStratum()` запускает цепочку `minecraft:blur`, а не
  `<MOD_ID>:gui_blur`. Именно поэтому по умолчанию мы переопределяем ванильный
  `blur.json`. Собственную цепочку `<MOD_ID>:gui_blur` оставь как эталон/бэкап.

## Чего делать НЕ надо

- Не добавляй зависимости в `build.gradle` — шейдер их не требует.
- Не создавай миксины: `blurBeforeThisStratum()` это штатный публичный API.
- Не переписывай GLSL под `#version 330` / `layout(location=...)` — этот стиль
  появился только в снапшотах 25w31a+ (после 1.21.8) и здесь сломает загрузку.
- Не удаляй `#moj_import` — это препроцессор Minecraft, а не ошибка.
- Не трогай `assets/minecraft/post_effect/blur.json.bak`, если установщик его
  создал: это резервная копия.

## Если что-то пошло не так

- **Игра падает при открытии экрана / ресурспак отключается.** Смотри в
  `logs/latest.log` строку с `Failed to load shader` или `ChainedJsonException`.
  Там будет указан конкретный файл и строка GLSL.
- **Блюра не видно.** Проверь в настройках: Специальные возможности →
  «Размытие фона меню» должно быть больше 0.
- **Установлен Iris/OptiFine.** Они перехватывают пайплайн и отключают
  ванильные post-effect'ы. Это ожидаемое поведение, не баг.
