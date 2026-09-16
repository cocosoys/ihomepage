package com.github.cocosoys.mc.ihomepages.action;

import lombok.CustomLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.orm.DATA;
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.ihomepages.action.config.ActionConfigValidator;
import com.github.cocosoys.mc.ihomepages.spring.entity.WebAction;
import com.github.cocosoys.mc.ihomepages.spring.entity.WebActionEffect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 网页动作管理器：加载/校验/缓存网页动作，支持热重载。
 *
 * <p><b>存储模型</b>：动作定义的权威存储为数据库（宿主 SQL 后端，表 {@code homepage_action} /
 * {@code homepage_action_effect}）；{@code actions.yml} 只是提供给服主的一种<em>简易添加方式</em>——
 * 每次加载/重载时读取配置文件、校验后<em>全量同步落库</em>，运行期再从存储读回内存缓存。
 * 存储访问经宿主统一门面 {@link DATA} 自动路由（配置了 SQL 后端则落库，否则回退 YAML 文件存储，
 * 表名即文件名），业务层无需感知后端差异。</p>
 *
 * <p>同步语义：<em>配置为声明源</em>——重载后存储中仅保留当前 actions.yml 中校验通过的动作
 * （yml 中新增/修改的 upsert，yml 中已移除的从存储删除）。后续若引入命令式管理
 * （直接改库），可扩展独立入口并调整同步策略。</p>
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

    /** 加载（或重载）actions.yml 并同步存储。 */
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

        // strict 模式：任一错误 → 全部拒绝，沿用旧配置（不动存储）
        if (newStrict && !errors.isEmpty()) {
            lastErrors = errors;
            strict = newStrict;
            log.warnT("log.action.strict-rejected",
                    "[actions] strict 模式已开启且配置存在 {0} 处错误，本次全部拒绝加载（沿用旧配置）。",
                    errors.size());
            return;
        }
        this.strict = newStrict;

        // 校验通过 → 全量同步落库，再从存储读回内存
        syncStore(parsed);
        this.actions = loadFromStore();
        this.lastErrors = errors;
        log.infoT("log.action.loaded", "[actions] 已加载动作 {0} 个（跳过 {1} 处错误动作，后端 {2}）",
                this.actions.size(), errors.size(), backendName());
    }

    /** 热重载（与 load 等价；由运维命令 / reload 钩子调用）。 */
    public void reload() {
        load();
    }

    // ==================== 存储同步（SQL 优先 / YAML 兜底） ====================

    /** 当前生效后端名（sql / yaml）。 */
    public String backendName() {
        return DATA.sqlEnabled() ? "sql" : (YAML.Pojo.isAvailable() ? "yaml" : "none");
    }

    /**
     * 全量同步：以 yml 声明（parsed）为源，对齐存储。
     * 存储中已存在但本次未声明的动作（含子表效果）将被删除。
     */
    private void syncStore(Map<String, WebAction> parsed) {
        // 1) 读存储现有动作 ID
        Map<String, WebAction> stored = loadFromStore();
        // 2) 删除已移除的动作
        for (String oldId : stored.keySet()) {
            if (!parsed.containsKey(oldId)) {
                deleteAction(oldId);
            }
        }
        // 3) upsert 声明的动作
        for (WebAction act : parsed.values()) {
            saveAction(act);
        }
    }

    /** 从存储读回全量动作（主表 + 子表效果组装）。失败返回空。 */
    private Map<String, WebAction> loadFromStore() {
        Map<String, WebAction> out = new LinkedHashMap<>();
        try {
            List<WebAction> acts = storeSelectActions();
            if (acts == null || acts.isEmpty()) {
                return out;
            }
            // 子表效果按 actionId 分组、sort 排序
            List<WebActionEffect> effects = storeSelectEffects();
            Map<String, List<WebActionEffect>> byAction = new HashMap<>();
            if (effects != null) {
                for (WebActionEffect e : effects) {
                    byAction.computeIfAbsent(e.getActionId(), k -> new ArrayList<>()).add(e);
                }
            }
            for (WebAction act : acts) {
                act.applyDisplayColumns();
                List<WebActionEffect> list = byAction.get(act.getId());
                if (list != null) {
                    list.sort((a, b) -> Integer.compare(a.getSort(), b.getSort()));
                    act.setEffects(list);
                } else {
                    act.setEffects(new ArrayList<>());
                }
                out.put(act.getId(), act);
            }
        } catch (Throwable t) {
            log.warnT("log.action.store-read-fail", "[actions] 从存储读取动作失败: {0}", t);
        }
        return out;
    }

    private List<WebAction> storeSelectActions() {
        return DATA.select(WebAction.class);
    }

    private List<WebActionEffect> storeSelectEffects() {
        return DATA.select(WebActionEffect.class);
    }

    /** upsert 一个动作（主表 + 子表效果重建）。 */
    private void saveAction(WebAction act) {
        if (act == null || act.getId() == null) {
            return;
        }
        // 主表：display 拆列后 upsert
        act.extractDisplayColumns();
        boolean mainOk = storeUpsert(act);
        if (!mainOk) {
            log.warnT("log.action.store-write-fail", "[actions] 动作 {0} 写入主表失败", act.getId());
            return;
        }
        // 子表：删除该动作旧效果后按顺序重建
        deleteEffectsOf(act.getId());
        List<WebActionEffect> list = act.getEffects();
        if (list != null) {
            int i = 0;
            for (WebActionEffect e : list) {
                if (e == null) {
                    i++;
                    continue;
                }
                if (e.getId() == null) {
                    e.setId(UUID.randomUUID().toString());
                }
                e.setActionId(act.getId());
                e.setSort(i);
                storeUpsert(e);
                i++;
            }
        }
    }

    /** 删除一个动作（主表 + 子表效果）。 */
    private void deleteAction(String actionId) {
        deleteEffectsOf(actionId);
        try {
            DATA.deleteById(WebAction.class, actionId);
        } catch (Throwable t) {
            log.warnT("log.action.store-write-fail", "[actions] 删除动作 {0} 失败: {1}",
                    actionId, t.getMessage());
        }
    }

    private void deleteEffectsOf(String actionId) {
        try {
            List<WebActionEffect> olds = DATA.select(WebActionEffect.class,
                    q -> q.eq(WebActionEffect::getActionId, actionId));
            if (olds != null) {
                for (WebActionEffect e : olds) {
                    if (e.getId() != null) {
                        DATA.deleteById(WebActionEffect.class, e.getId());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** upsert 到当前后端（DATA 门面自动路由 SQL 优先 / YAML 兜底；两端均为按主键覆盖）。 */
    private boolean storeUpsert(Object bean) {
        try {
            return DATA.insert(bean);
        } catch (Throwable t) {
            log.warnT("log.action.store-write-fail", "[actions] 动作存储写入失败: {0}",
                    t.getMessage());
            return false;
        }
    }

    // ==================== actions.yml 解析（原有逻辑） ====================

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

    // ==================== 查询（内存缓存） ====================

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
