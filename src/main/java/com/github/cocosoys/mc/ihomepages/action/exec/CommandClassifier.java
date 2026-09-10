package com.github.cocosoys.mc.ihomepages.action.exec;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 命令类别分类器：按命令首个单词（子命令前）匹配内置规则表，自动识别 CONSOLE / ACCOUNT / ENTITY。
 *
 * <p>规则表是<b>可配置起点</b>：actions.yml 中每个 command 效果可用 {@code target:} 显式覆盖，
 * 本类还提供 {@link #registerEntity(String)} / {@link #registerAccount(String)} 供开发者按服扩展。</p>
 */
public class CommandClassifier {

    /** 实体类命令前缀（需在线 Player 实体才能生效）。 */
    private final Set<String> entityPrefixes = new HashSet<>(Arrays.asList(
            "give", "clear", "tp", "teleport", "effect", "spawnpoint", "heal", "feed",
            "gamemode", "kill", "sethealth", "setfood", "inventory", "enderchest", "workbench",
            "rename", "enchant", "repair", "home", "sethome", "warp", "spawn", "tpa", "tpahere",
            "back", "rtp", "fly", "vanish", "god", "kit", "ec", "vault", "items"
    ));

    /** 账号类命令前缀（按玩家名操作账号数据，离线可执行）。 */
    private final Set<String> accountPrefixes = new HashSet<>(Arrays.asList(
            "eco", "money", "balance", "bal", "lp", "luckperms", "pex", "permissionsex",
            "points", "baltop", "pay", "cmi", "essentials"
    ));

    /** 追加实体类前缀（开发者扩展，按服定制）。 */
    public void registerEntity(String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            entityPrefixes.add(prefix.toLowerCase(Locale.ROOT));
        }
    }

    /** 追加账号类前缀（开发者扩展，按服定制）。 */
    public void registerAccount(String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            accountPrefixes.add(prefix.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * 自动分类命令类别。
     *
     * @param command 完整命令（可含参数，如 {@code give Steve diamond 5} / {@code lp user Steve parent add vip}）
     * @return 命令类别；无法识别时按 CONSOLE 处理（保守：不与玩家实体绑定）
     */
    public CommandClass classify(String command) {
        if (command == null || command.trim().isEmpty()) {
            return CommandClass.CONSOLE;
        }
        String first = firstWord(command);
        if (first == null) {
            return CommandClass.CONSOLE;
        }
        if (entityPrefixes.contains(first)) {
            return CommandClass.ENTITY;
        }
        if (accountPrefixes.contains(first)) {
            return CommandClass.ACCOUNT;
        }
        return CommandClass.CONSOLE;
    }

    /** 取命令首个单词（小写，去前导斜杠）。 */
    private static String firstWord(String command) {
        String s = command.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int sp = s.indexOf(' ');
        String w = sp < 0 ? s : s.substring(0, sp);
        return w.trim().isEmpty() ? null : w.toLowerCase(Locale.ROOT);
    }
}
