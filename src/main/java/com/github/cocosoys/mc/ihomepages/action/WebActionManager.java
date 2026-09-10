package com.github.cocosoys.mc.ihomepages.action;

import lombok.CustomLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.ihomepages.action.config.ActionConfigValidator;
import com.github.cocosoys.mc.ihomepages.action.model.WebAction;
import com.github.cocosoys.mc.ihomepages.action.model.WebActionEffect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网页动作配置管理器：加载/校验/缓存 {@code actions.yml}，支持热重载。
 *
 * <p>服主编辑 actions.yml 后执行 {@code /soyshttp homepage actions reload}（或宿主 reload）即生效，
 * 无需重启。加载策略：默认宽容（坏动作跳过并告警，好动作照常）；配置顶部 {@code strict: true}
 * 时任一错误全部拒绝并沿用旧配置。已入队的离线任务不受重载影响（快照独立）。</p>
 *
 * <p>日志与 lastErrors 均经 {@link I18n#t} 翻译（key 见 language/*.yml），未命中回退代码内模板。</p>
 */
@CustomLog
public class WebActionManager {

    private final JavaPlugin plugin;
    private final ActionConfigValidator validator;

    private volatile Map<String, WebAction> actions = new LinkedHashMap<>();
    private volatile List<String> lastErrors = new ArrayList<>();
    private volatile boolean strict = false;

    public WebActionManager(JavaPlugin plugin, ActionConfigValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    /** 加载（或重载）actions.yml。 */
    public void load() {
        // 首次启动释放内置模板到磁盘（不覆盖已存在的用户副本）
        if (!new File(plugin.getDataFolder(), "actions.yml").isFile()) {
            plugin.saveResource("actions.yml", false);
        }
        File file = new File(plugin.getDataFolder(), "actions.yml");
        if (!file.isFile()) {
            lastErrors = Collections.singletonList(
                    I18n.t("action.validate.file-missing", "actions.yml 不存在（未释放模板？）"));
            log.warnT("log.action.load-fail", "[actions] {0}", lastErrors.get(0));
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        boolean newStrict = y.getBoolean("strict", false);

        Map<String, WebAction> parsed = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        ConfigurationSection sec = y.getConfigurationSection("actions");
        if (sec == null) {
            lastErrors = Collections.singletonList(
                    I18n.t("action.validate.no-actions-section", "actions.yml 缺少顶层 actions: 段"));
            log.warnT("log.action.load-fail", "[actions] {0}", lastErrors.get(0));
            return;
        }
        for (String id : sec.getKeys(false)) {
            WebAction act = parseAction(y, id);
            List<String> errs = validator.validate(id, act);
            if (errs.isEmpty()) {
                parsed.put(id, act);
            } else {
                errors.addAll(errs);
            }
        }
        for (String e : errors) {
            log.warnT("log.action.parse-fail", "[actions] 动作解析失败: {0}", e);
        }

        // strict 模式：任一错误 → 全部拒绝，沿用旧配置
        if (newStrict && !errors.isEmpty()) {
            lastErrors = errors;
            strict = newStrict;
            log.warnT("log.action.strict-rejected",
                    "[actions] strict 模式已开启且配置存在 {0} 处错误，本次全部拒绝加载（沿用旧配置）。",
                    errors.size());
            return;
        }
        this.strict = newStrict;
        this.actions = parsed;
        this.lastErrors = errors;
        log.infoT("log.action.loaded", "[actions] 已加载动作 {0} 个（跳过 {1} 处错误动作）",
                parsed.size(), errors.size());
    }

    /** 热重载（与 load 等价；由运维命令 / reload 钩子调用）。 */
    public void reload() {
        load();
    }

    private WebAction parseAction(YamlConfiguration y, String id) {
        String base = "actions." + id + ".";
        WebAction act = new WebAction();
        act.setId(id);
        act.setName(y.getString(base + "name", id));
        act.setPrice(y.getDouble(base + "price", 0D));
        act.setOffline(y.getString(base + "offline", "queue").toLowerCase());
        act.setCooldownSeconds(y.getInt(base + "cooldown-seconds", 0));
        act.setBalanceCheck(y.getBoolean(base + "balance-check", false));
        act.setClaimPeriod(lowerTrimToNull(y.getString(base + "claim-period", "")));
        act.setClaimWindowStart(trimToNull(y.getString(base + "claim-window-start", "")));
        act.setClaimWindowEnd(trimToNull(y.getString(base + "claim-window-end", "")));

        // effects 列表
        List<WebActionEffect> effects = new ArrayList<>();
        List<?> raw = y.getList(base + "effects");
        if (raw != null) {
            for (Object o : raw) {
                if (!(o instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                WebActionEffect e = new WebActionEffect();
                e.setType(str(m.get("type")));
                e.setCommand(str(m.get("command")));
                e.setTarget(str(m.get("target")));
                e.setAmount(num(m.get("amount")));
                effects.add(e);
            }
        }
        act.setEffects(effects);

        // display 段
        ConfigurationSection disp = y.getConfigurationSection(base + "display");
        if (disp != null) {
            WebAction.DisplayConfig d = new WebAction.DisplayConfig();
            d.setIcon(disp.getString("icon", ""));
            d.setDesc(disp.getString("desc", ""));
            d.setGroup(disp.getString("group", ""));
            d.setSort(disp.getInt("sort", 0));
            d.setHidden(disp.getBoolean("hidden", false));
            act.setDisplay(d);
        }
        return act;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String lowerTrimToNull(String s) {
        String t = trimToNull(s);
        return t == null ? null : t.toLowerCase();
    }

    private static double num(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o instanceof String) {
            try {
                return Double.parseDouble((String) o);
            } catch (NumberFormatException e) {
                return 0D;
            }
        }
        return 0D;
    }

    /** 按 ID 查动作（不存在返回 null）。 */
    public WebAction get(String id) {
        return id == null ? null : actions.get(id);
    }

    /** 全部动作（含 hidden）。 */
    public Map<String, WebAction> all() {
        return actions;
    }

    /** 前端可见动作（display.hidden != true），按 display.sort 升序。 */
    public List<WebAction> visible() {
        List<WebAction> out = new ArrayList<>();
        for (WebAction a : actions.values()) {
            if (a.isVisible()) {
                out.add(a);
            }
        }
        out.sort((a, b) -> {
            int sa = a.getDisplay() == null ? 0 : a.getDisplay().getSort();
            int sb = b.getDisplay() == null ? 0 : b.getDisplay().getSort();
            return Integer.compare(sa, sb);
        });
        return out;
    }

    /** 最近一次加载的校验错误列表（空 = 全部通过）。 */
    public List<String> lastErrors() {
        return lastErrors;
    }

    /** 当前是否 strict 模式。 */
    public boolean isStrict() {
        return strict;
    }

    /** 是否已加载出至少一个动作。 */
    public boolean hasActions() {
        return !actions.isEmpty();
    }
}
