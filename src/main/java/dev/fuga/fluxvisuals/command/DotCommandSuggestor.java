package dev.fuga.fluxvisuals.command;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.fuga.fluxvisuals.FluxVisualsClient;
import dev.fuga.fluxvisuals.modules.Module;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Autocomplete for the client's dot commands and their dynamic arguments. */
public final class DotCommandSuggestor {
    private static final List<Entry> ROOT_COMMANDS = List.of(
            entry(".bind", "Привязать клавишу к функции или модулю"),
            entry(".b", "Короткий алиас команды привязки"),
            entry(".bot", "Управление мульти-ботами")
    );

    private static final Map<String, String> BOT_COMMANDS = orderedMap(
            "add", "Добавить бота", "random", "Создать бота с нормальным свободным ником", "remove", "Удалить бота", "removeall", "Удалить всех подключённых ботов",
            "list", "Показать список ботов", "switch", "Переключить активного бота", "main", "Вернуть основной аккаунт",
            "command", "Отправить команду выбранному боту", "tp", "Телепортировать бота к игроку", "play", "Игровой режим",
            "drop", "Выбросить предметы бота", "pay", "Перевести деньги боту или всем ботам", "an", "Отправить ботов на анархию",
            "baritone", "Выполнить команду Baritone", "resell", "Автореселл", "antiafk", "Анти-AFK", "anti-afk", "Алиас анти-AFK",
            "preset", "Сохранить или загрузить пресет", "lineup", "Выстроить ботов рядом", "goto", "Переместить бота к игроку",
            "proxy", "Настроить прокси", "chat", "Настроить трансляцию чата", "function", "Управление функциями",
            "debug", "Режим отладки", "help", "Полная помощь по ботам",
            "добавить", "Русский алиас add", "удалить", "Русский алиас remove", "удалитьвсех", "Русский алиас removeall",
            "список", "Русский алиас list", "переключить", "Русский алиас switch", "основной", "Русский алиас main",
            "команда", "Русский алиас command", "тп", "Русский алиас tp", "играть", "Русский алиас play",
            "помощь", "Русский алиас help", "прокси", "Русский алиас proxy", "чат", "Русский алиас chat",
            "функция", "Русский алиас function", "дебаг", "Русский алиас debug"
    );

    private static final List<String> STATES = List.of("on", "off", "вкл", "выкл");
    private static final List<String> FUNCTION_STATES = List.of("on", "off", "включить", "выключить");
    private static final List<String> PRESET_ACTIONS = List.of("save", "remove", "load");

    private DotCommandSuggestor() {
    }

    public static CompletableFuture<Suggestions> getSuggestions(String fullText, int cursor) {
        if (fullText == null || !fullText.startsWith(".")) return null;
        int safeCursor = Math.max(0, Math.min(cursor, fullText.length()));
        String input = fullText.substring(0, safeCursor);
        List<Token> tokens = tokenize(input);
        boolean trailing = input.endsWith(" ");

        if (tokens.isEmpty() || tokens.size() == 1 && !trailing) {
            int start = tokens.isEmpty() ? 0 : tokens.get(0).start;
            return complete(ROOT_COMMANDS, tokens.isEmpty() ? "." : tokens.get(0).text, start, safeCursor);
        }

        String root = tokens.get(0).text.toLowerCase(Locale.ROOT);
        if (root.equals(".bind") || root.equals(".b")) return suggestBind(tokens, input, safeCursor);
        if (root.equals(".bot")) return suggestBot(tokens, input, safeCursor);
        if (!input.contains(" ")) return complete(ROOT_COMMANDS, input, 0, safeCursor);
        return empty(safeCursor);
    }

    private static CompletableFuture<Suggestions> suggestBind(List<Token> tokens, String input, int cursor) {
        boolean trailing = input.endsWith(" ");
        if (tokens.size() == 1 && trailing || tokens.size() == 2 && !trailing) {
            int start = trailing ? cursor : tokens.get(1).start;
            return complete(bindFunctions(), trailing ? "" : tokens.get(1).text, start, cursor);
        }
        if (tokens.size() == 2 && trailing || tokens.size() == 3 && !trailing) {
            int start = trailing ? cursor : tokens.get(2).start;
            return complete(entries(BindCommand.COMMON_KEY_SUGGESTIONS, "Доступная клавиша"), trailing ? "" : tokens.get(2).text, start, cursor);
        }
        return empty(cursor);
    }

    private static CompletableFuture<Suggestions> suggestBot(List<Token> tokens, String input, int cursor) {
        boolean trailing = input.endsWith(" ");
        if (tokens.size() >= 2 && alias(tokens.get(1).text, "add", "добавить")) {
            return suggestAdd(tokens, input, cursor);
        }
        if (tokens.size() == 1 && trailing || tokens.size() == 2 && !trailing) {
            List<Entry> values = new ArrayList<>();
            for (Map.Entry<String, String> command : BOT_COMMANDS.entrySet()) values.add(entry(command.getKey(), command.getValue()));
            for (String name : botNames(false)) values.add(entry(name, "Подключённый бот"));
            int start = trailing ? cursor : tokens.get(1).start;
            return complete(values, trailing ? "" : tokens.get(1).text, start, cursor);
        }

        String second = tokens.get(1).text.toLowerCase(Locale.ROOT);
        if (isBotName(tokens.get(1).text)) return suggestScopedBot(tokens, input, cursor);
        if (alias(second, "remove", "удалить", "switch", "переключить", "drop", "tp", "тп")) return completeNames(tokens, input, cursor, false);
        if (alias(second, "goto")) return suggestGoto(tokens, input, cursor);
        if (alias(second, "chat", "чат")) return suggestChat(tokens, input, cursor);
        if (alias(second, "function", "функция")) return suggestFunction(tokens, input, cursor);
        if (alias(second, "preset")) return suggestPreset(tokens, input, cursor);
        if (alias(second, "resell", "antiafk", "anti-afk", "play", "играть", "debug", "дебаг")) {
            return suggestState(tokens, input, cursor, alias(second, "debug", "дебаг") ? List.of("on", "off", "вкл", "выкл", "status") : STATES);
        }
        if (alias(second, "baritone")) return suggestLast(tokens, input, cursor, baritoneNames());
        return empty(cursor);
    }

    private static CompletableFuture<Suggestions> suggestAdd(List<Token> tokens, String input, int cursor) {
        boolean trailing = input.endsWith(" ");
        if (tokens.size() == 2 && !trailing) {
            int start = tokens.get(1).start;
            return complete(List.of(entry("add random", "Создать одного бота со свободным именем")),
                    tokens.get(1).text, start, cursor);
        }
        if (tokens.size() == 2 && trailing || tokens.size() == 3 && !trailing) {
            int start = trailing ? cursor : tokens.get(2).start;
            return complete(List.of(entry("random", "Создать один или несколько ботов со свободными именами")),
                    trailing ? "" : tokens.get(2).text, start, cursor);
        }
        if (tokens.size() == 3 && trailing || tokens.size() == 4 && !trailing) {
            List<String> counts = new ArrayList<>();
            for (int count = 1; count <= 20; count++) counts.add(Integer.toString(count));
            int start = trailing ? cursor : tokens.get(3).start;
            return complete(entries(counts, "Количество ботов"), trailing ? "" : tokens.get(3).text, start, cursor);
        }
        return empty(cursor);
    }

    private static CompletableFuture<Suggestions> suggestScopedBot(List<Token> tokens, String input, int cursor) {
        boolean trailing = input.endsWith(" ");
        if (tokens.size() == 2 && trailing || tokens.size() == 3 && !trailing) {
            int start = trailing ? cursor : tokens.get(2).start;
            return complete(entries(List.of("goto", "drop", "chat", "proxy", "function"), "Команда для этого бота"), trailing ? "" : tokens.get(2).text, start, cursor);
        }
        String sub = tokens.get(2).text.toLowerCase(Locale.ROOT);
        if (sub.equals("chat")) return suggestState(tokens, input, cursor, STATES);
        if (sub.equals("function")) return suggestFunction(tokens, input, cursor);
        if (sub.equals("goto")) {
            return tokens.size() >= 4 && tokens.get(3).text.equalsIgnoreCase("me")
                    ? empty(cursor)
                    : complete(List.of(entry("me", "Переместить к игроку")), input.endsWith(" ") ? "" : tokens.get(tokens.size() - 1).text,
                    input.endsWith(" ") ? cursor : tokens.get(tokens.size() - 1).start, cursor);
        }
        if (sub.equals("proxy")) return suggestLast(tokens, input, cursor, List.of("off", "clear"));
        return empty(cursor);
    }

    private static CompletableFuture<Suggestions> suggestGoto(List<Token> tokens, String input, int cursor) {
        boolean trailing = input.endsWith(" ");
        if (tokens.size() == 2 && trailing || tokens.size() == 3 && !trailing) {
            int start = trailing ? cursor : tokens.get(2).start;
            return complete(List.of(entry("me", "Переместить к игроку")), trailing ? "" : tokens.get(2).text, start, cursor);
        }
        if (tokens.size() >= 3 && tokens.get(2).text.equalsIgnoreCase("me")) return completeNames(tokens, input, cursor, false);
        return empty(cursor);
    }

    private static CompletableFuture<Suggestions> suggestChat(List<Token> tokens, String input, int cursor) {
        boolean trailing = input.endsWith(" ");
        if (tokens.size() == 2 && trailing || tokens.size() == 3 && !trailing) {
            List<Entry> values = new ArrayList<>();
            for (String name : botNames(false)) values.add(entry(name, "Бот для трансляции чата"));
            values.add(entry("all", "Все подключённые боты"));
            values.add(entry("on", "Включить трансляцию"));
            values.add(entry("off", "Выключить трансляцию"));
            int start = trailing ? cursor : tokens.get(2).start;
            return complete(values, trailing ? "" : tokens.get(2).text, start, cursor);
        }
        return suggestState(tokens, input, cursor, STATES);
    }

    private static CompletableFuture<Suggestions> suggestFunction(List<Token> tokens, String input, int cursor) {
        boolean trailing = input.endsWith(" ");
        int index = isBotName(tokens.get(1).text) ? 3 : 2;
        if (tokens.size() == index && trailing || tokens.size() == index + 1 && !trailing) {
            int start = trailing ? cursor : tokens.get(index).start;
            return complete(functions(), trailing ? "" : tokens.get(index).text, start, cursor);
        }
        return suggestState(tokens, input, cursor, FUNCTION_STATES);
    }

    private static CompletableFuture<Suggestions> suggestPreset(List<Token> tokens, String input, int cursor) {
        boolean trailing = input.endsWith(" ");
        if (tokens.size() == 2 && trailing || tokens.size() == 3 && !trailing) {
            int start = trailing ? cursor : tokens.get(2).start;
            return complete(entries(PRESET_ACTIONS, "Действие с пресетом"), trailing ? "" : tokens.get(2).text, start, cursor);
        }
        if (tokens.size() >= 3 && (tokens.get(2).text.equalsIgnoreCase("load") || tokens.get(2).text.equalsIgnoreCase("remove"))) return suggestLast(tokens, input, cursor, presetNames());
        return empty(cursor);
    }

    private static CompletableFuture<Suggestions> suggestState(List<Token> tokens, String input, int cursor, List<String> states) {
        boolean trailing = input.endsWith(" ");
        int start = trailing ? cursor : tokens.get(tokens.size() - 1).start;
        return complete(entries(states, "Состояние команды"), trailing ? "" : tokens.get(tokens.size() - 1).text, start, cursor);
    }

    private static CompletableFuture<Suggestions> completeNames(List<Token> tokens, String input, int cursor, boolean includeMain) {
        boolean trailing = input.endsWith(" ");
        int start = trailing ? cursor : tokens.get(tokens.size() - 1).start;
        List<Entry> names = new ArrayList<>();
        for (String name : botNames(includeMain)) names.add(entry(name, "Подключённый бот"));
        return complete(names, trailing ? "" : tokens.get(tokens.size() - 1).text, start, cursor);
    }

    private static CompletableFuture<Suggestions> suggestLast(List<Token> tokens, String input, int cursor, List<String> values) {
        boolean trailing = input.endsWith(" ");
        int start = trailing ? cursor : tokens.get(tokens.size() - 1).start;
        return complete(entries(values, "Доступный вариант"), trailing ? "" : tokens.get(tokens.size() - 1).text, start, cursor);
    }

    private static List<Entry> bindFunctions() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ModernGui", "Современное меню FLUX");
        values.put("ModernGui2", "Заготовка нового меню FLUX");
        values.put("PremiumGui", "Классическое меню FLUX");
        values.put("list", "Список всех привязок");
        if (FluxVisualsClient.MODULE_MANAGER != null) {
            for (Module module : FluxVisualsClient.MODULE_MANAGER.getModules()) {
                if (module != null && module.getName() != null) values.putIfAbsent(module.getName().replaceAll("\\s+", ""), module.getDescription());
            }
        }
        List<Entry> result = new ArrayList<>();
        values.forEach((name, description) -> result.add(entry(name, description == null || description.isBlank() ? "Функция клиента" : description)));
        return result;
    }

    private static List<Entry> functions() {
        List<Entry> result = new ArrayList<>();
        if (FluxVisualsClient.MULTI_BOT_MANAGER != null) {
            for (String name : FluxVisualsClient.MULTI_BOT_MANAGER.getCommandFunctionNames()) result.add(entry(name, "Функция, доступная для управления ботом"));
        }
        return result;
    }

    private static List<String> botNames(boolean includeMain) {
        return FluxVisualsClient.MULTI_BOT_MANAGER == null ? List.of() : FluxVisualsClient.MULTI_BOT_MANAGER.getCommandBotNames(includeMain);
    }

    private static List<String> presetNames() {
        return FluxVisualsClient.MULTI_BOT_MANAGER == null ? List.of() : FluxVisualsClient.MULTI_BOT_MANAGER.getCommandPresetNames();
    }

    private static List<String> baritoneNames() {
        return FluxVisualsClient.MULTI_BOT_MANAGER == null ? List.of() : FluxVisualsClient.MULTI_BOT_MANAGER.getCommandBaritoneNames();
    }

    private static boolean isBotName(String value) {
        for (String name : botNames(true)) if (name.equalsIgnoreCase(value)) return true;
        return false;
    }

    private static boolean alias(String value, String... names) {
        for (String name : names) {
            if (name.equalsIgnoreCase(value) || decodeMojibake(name).equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static List<Entry> entries(List<String> values, String description) {
        List<Entry> result = new ArrayList<>();
        for (String value : values) result.add(entry(value, description));
        return result;
    }

    private static CompletableFuture<Suggestions> complete(List<Entry> values, String current, int start, int cursor) {
        String lower = current == null ? "" : current.toLowerCase(Locale.ROOT);
        List<Suggestion> result = new ArrayList<>();
        for (Entry value : values) {
            if (lower.isEmpty() || value.value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(new Suggestion(StringRange.between(start, cursor), value.value, Text.literal(value.description)));
            }
        }
        return CompletableFuture.completedFuture(new Suggestions(StringRange.between(start, cursor), result));
    }

    private static CompletableFuture<Suggestions> empty(int cursor) {
        return CompletableFuture.completedFuture(new Suggestions(StringRange.at(cursor), List.of()));
    }

    private static Entry entry(String value, String description) {
        return new Entry(decodeMojibake(value), decodeMojibake(description));
    }

    /**
     * Older generated copies of this source contained UTF-8 text decoded as
     * Windows-1251 ("Рџ..."), which made Russian aliases and descriptions
     * unusable. Normalize only that unmistakable three-character prefix so
     * normal Cyrillic module names remain untouched.
     */
    private static String decodeMojibake(String value) {
        if (value == null || value.length() < 3) return value;
        char first = value.charAt(0);
        char third = value.charAt(2);
        if ((first != 'Р' && first != 'С') || (third != 'Р' && third != 'С')) return value;
        try {
            String decoded = new String(value.getBytes(StandardCharsets.UTF_8), Charset.forName("windows-1251"));
            return decoded.indexOf('\uFFFD') >= 0 ? value : decoded;
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private static Map<String, String> orderedMap(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) result.put(values[i], values[i + 1]);
        return result;
    }

    private static List<Token> tokenize(String input) {
        List<Token> result = new ArrayList<>();
        int i = 0;
        while (i < input.length()) {
            while (i < input.length() && Character.isWhitespace(input.charAt(i))) i++;
            if (i >= input.length()) break;
            int start = i;
            while (i < input.length() && !Character.isWhitespace(input.charAt(i))) i++;
            result.add(new Token(input.substring(start, i), start));
        }
        return result;
    }

    private record Entry(String value, String description) {
    }

    private record Token(String text, int start) {
    }
}
