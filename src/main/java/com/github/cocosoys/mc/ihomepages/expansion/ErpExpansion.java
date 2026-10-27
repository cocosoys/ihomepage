package com.github.cocosoys.mc.ihomepages.expansion;

import com.github.cocosoys.mc.ihomepages.spring.controller.HomeApiController;
import com.github.cocosoys.mc.ihomepages.spring.entity.WebAction;
import com.github.cocosoys.mc.ihomepages.spring.entity.WebActionEffect;
import com.github.cocosoys.mc.mcerp.ErpMenus;
import com.github.cocosoys.mc.mcerp.McerpExpansion;
import com.github.cocosoys.mc.soyshttpovermc.api.SoysHttpOverMcApi;
import com.github.cocosoys.mc.soyshttpovermc.web.WebRegistry;
import lombok.CustomLog;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ERP SoysExpansion（双 Expansion 方案 P 的 ERP 实例，McerpExpansion 子类）：
 * <ul>
 *   <li><b>ERP API 自包含</b>：{@link #buildControllers()} → 正常登记自动补
 *       {@code /api/plugins/ihomepages/} 前缀（控制器类级前缀如 {@code /erp} 决定子路径）；</li>
 *   <li><b>ERP 数据自包含</b>：{@link #dataRoots()}={@code {"data"}} + {@link #sqlRoots()}={@code {"sql"}}
 *       → 宿主 AutoOps 复制 jar 内 {@code data/} 4 张实体表初始化 yml 并（MySQL 方言下）
 *       执行 {@code sql/init.sql}；{@link #seedData()} 提供默认动作种子（表空才插入，幂等）；</li>
 *   <li><b>ERP 前端单独实现</b>：{@link #erpResourceRoot()} 待用户指定前端后填入
 *       （如 {@code "erp-dist"}）；返回 null → 页面托管空操作，模块/API/数据照常注册；</li>
 *   <li><b>ERP 模块登记</b>：onRegister → 默认实现登记到 MCERP {@code ErpRegistry}
 *       （菜单入口 {@link #homeUrl()} = {@code /web/plugins/ihomepages/erp/}，hash 模式；
 *       MCERP 未安装/未就绪时静默跳过，不阻断）。</li>
 * </ul>
 *
 * <p><b>关键约束</b>：
 * <ol>
 *   <li>{@code registerPages()} 不调用 super —— super 会 setIndexRule/setSpaFallback
 *       （按插件名存键，重复以最后一次为准），会把主页实例的规则顶掉；</li>
 *   <li>{@code unregisterPages()} 覆写为只卸载自身 tag —— 默认实现会
 *       removeIndexRule/removeSpaFallback({@code "ihomepages"})，连带清掉主页规则。</li>
 * </ol>
 * 两个实例 owner 都是 ihomepages，页面 URL 前缀同名，靠 basePath（{@code /erp}）错开命名空间。</p>
 */
@CustomLog
public class ErpExpansion extends McerpExpansion {

    private final List<Object> erpControllers;

    public ErpExpansion(List<Object> erpControllers) {
        this.erpControllers = erpControllers == null ? new ArrayList<>() : erpControllers;
    }

    // ===== 元信息 =====

    @Override
    public String getIdentifier() {
        return "ihomepages-erp";
    }

    /** 必填：缺失 → onRegister 返回 false → 整体注册回滚。 */
    @Override
    protected String displayName() {
        return "iHomePages";
    }

    @Override
    protected String icon() {
        return "erp";
    }

    @Override
    protected int sortOrder() {
        return 10;
    }

    /** ERP 菜单入口：/web/plugins/ihomepages/erp/（hash 模式，刷新不 404）。 */
    @Override
    protected String homeUrl() {
        SoysHttpOverMcApi a = getApi();
        Plugin o = getOwner();
        String base = (a == null || o == null) ? "/web/plugins/ihomepages"
                : a.getToolkit().pageFullPrefix(o);
        return base + "/erp/";
    }

    @Override
    protected ErpMenus menus() {
        return ErpMenus.create()
                .dir("erp", "ERP 控制台", "erp");
        // 待 ERP 控制器就位后按实际菜单补全（.menu/perm 叶子）
    }

    // ===== ERP API（决策 A：自包含）=====

    @Override
    protected List<Object> buildControllers() {
        return erpControllers;
    }

    // ===== ERP 数据（决策 A：自包含）=====

    /** 默认数据目录：jar 内 {@code data/}（4 张实体表初始化 yml），AutoOps 复制到宿主数据目录。 */
    @Override
    protected String[] dataRoots() {
        return new String[]{"data"};
    }

    /** 初始化 SQL 目录：jar 内 {@code sql/}（{@code init.sql}，仅 MySQL 方言执行；YAML/SQLite 自动跳过）。 */
    @Override
    protected String[] sqlRoots() {
        return new String[]{"sql"};
    }

    /**
     * 种子数据：4 个默认动作 + 9 条效果（与 {@code data/homepage_action.yml}、
     * {@code actions.yml} 默认模板三源一致）。宿主 AutoOps 仅在<b>表为空</b>时插入
     * （表非空跳过，幂等）；同时经 mergedTableClasses 声明归属表用于 ensureTables 建表。
     * 领取记录/离线任务为运行期数据表，不提供种子。
     */
    @Override
    protected List<Object> seedData() {
        List<Object> seeds = new ArrayList<>();
        seeds.add(seedAction("daily-bonus", "每日礼包", 50D, "queue", 86400, true, null,
                "🎁", "消耗 50 金币：10 铁锭 + 捐赠者权限（离线购买物品上线到账，权限立即生效）", "日常", 1));
        seeds.add(seedAction("unlock-vip", "解锁VIP", 500D, "run", 0, false, null,
                "👑", "500 金币解锁 VIP 权限组，离线购买立即生效", "特权", 2));
        seeds.add(seedAction("sell-wheat", "出售小麦", 0D, "reject", 0, false, null,
                "🌾", "出售背包中的小麦：每次 5 金币（需在线操作）", "商城", 3));
        seeds.add(seedAction("claim-gift", "新人礼包", 0D, "queue", 0, false, "once",
                "🎁", "新玩家专属礼包：16 钻石 + 32 铁锭（离线领取上线自动到账）", "新人", 4));
        seeds.add(seedEffect(1L, "daily-bonus", 0, "economy.take", null, null, 0D));
        seeds.add(seedEffect(2L, "daily-bonus", 1, "command",
                "give {player} iron_ingot 10", "entity", 0D));
        seeds.add(seedEffect(3L, "daily-bonus", 2, "command",
                "lp user {player} parent add donor", "account", 0D));
        seeds.add(seedEffect(4L, "unlock-vip", 0, "command",
                "lp user {player} parent add vip", null, 0D));
        seeds.add(seedEffect(5L, "sell-wheat", 0, "command",
                "clear {player} wheat", "entity", 0D));
        seeds.add(seedEffect(6L, "sell-wheat", 1, "economy.give",
                null, null, 5D));
        seeds.add(seedEffect(7L, "claim-gift", 0, "command",
                "give {player} DIAMOND 16", "entity", 0D));
        seeds.add(seedEffect(8L, "claim-gift", 1, "command",
                "give {player} IRON_INGOT 32", "entity", 0D));
        seeds.add(seedEffect(9L, "claim-gift", 2, "command",
                "say 欢迎新玩家 {player} 加入服务器！", "console", 0D));
        return seeds;
    }

    /** 构造默认动作种子（display 拆列后落库，与 data yml 同构）。 */
    private WebAction seedAction(String id, String name, double price, String offline, int cooldown,
                                 boolean balanceCheck, String claimPeriod, String icon, String desc,
                                 String group, int sort) {
        WebAction a = new WebAction();
        a.setId(id);
        a.setName(name);
        a.setPrice(price);
        a.setOffline(offline);
        a.setCooldownSeconds(cooldown);
        a.setBalanceCheck(balanceCheck);
        a.setClaimPeriod(claimPeriod);
        WebAction.DisplayConfig d = new WebAction.DisplayConfig();
        d.setIcon(icon);
        d.setDesc(desc);
        d.setGroup(group);
        d.setSort(sort);
        a.setDisplay(d);
        a.extractDisplayColumns();
        return a;
    }

    /** 构造默认效果种子（自增数字主键 1..n，与 data/homepage_action_effect.yml 一致）。 */
    private WebActionEffect seedEffect(Long id, String actionId, int sort, String type,
                                       String command, String target, double amount) {
        WebActionEffect e = new WebActionEffect(id, actionId, sort);
        e.setType(type);
        e.setCommand(command);
        e.setTarget(target);
        e.setAmount(amount);
        return e;
    }

    // ===== ERP 前端（待用户指定）=====

    /**
     * ERP 前端资源根：待用户指定前端后填入（如 {@code "erp-dist"}）。
     * 返回 null → 页面托管空操作（模块/API/数据照常注册）。
     */
    protected String erpResourceRoot() {
        return null;
    }

    /** 挂 /erp/ 子路径（方案 P）；不调 super.registerPages()（防规则互覆）。 */
    @Override
    protected boolean registerPages() {
        SoysHttpOverMcApi a = getApi();
        Plugin o = getOwner();
        if (a == null || o == null) {
            return false;
        }
        String root = erpResourceRoot();
        if (root == null || root.trim().isEmpty()) {
            return true; // 前端未指定 → 空操作视为成功
        }
        try {
            Set<WebRegistry.Entry> reg = new HashSet<>();
            String t = getTag();
            File disk = new File(o.getDataFolder(), root.trim());
            Set<WebRegistry.Entry> es = disk.isDirectory()
                    ? a.getWebPage().registerDirectory(o, "/erp", disk)
                    : a.getWebPage().registerResourceDirectory(o, "/erp",
                            o.getClass().getClassLoader(), root.trim());
            if (es != null) {
                for (WebRegistry.Entry e : es) {
                    e.tags.add(t); // "expansion:ihomepages-erp"，unregisterByTag 精确卸载
                    reg.add(e);
                }
            }
            return true;
        } catch (Exception ex) {
            log.warnT("ihomepages.erp.expansion.pages-register-fail", "ErpExpansion 页面托管失败: {0}", ex.getMessage());
            return false;
        }
    }

    /** 只卸载自身页面；不删主页的 index/spa 规则（默认实现会按插件名误删）。 */
    @Override
    protected void unregisterPages() {
        SoysHttpOverMcApi a = getApi();
        String t = getTag();
        if (a == null || t == null) {
            return;
        }
        try {
            a.getWebPage().unregisterByTag(t);
        } catch (Exception ex) {
            log.warnT("ihomepages.erp.expansion.pages-unregister-fail", "ErpExpansion 页面反注册失败: {0}", ex.getMessage());
        }
    }
}
