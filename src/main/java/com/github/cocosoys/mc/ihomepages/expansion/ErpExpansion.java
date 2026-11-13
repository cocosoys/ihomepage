package com.github.cocosoys.mc.ihomepages.expansion;

import com.github.cocosoys.mc.ihomepages.spring.controller.ErpConfigController;
import com.github.cocosoys.mc.ihomepages.spring.controller.ErpHomepageController;
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
 *   <li><b>ERP 前端单独实现</b>：{@link #erpResourceRoot()} = {@code "erp-dist"}
 *       （由 {@code web/} 的 Vue2 工程构建产物复制而来），挂 {@code /erp} 子路径；
 *       因 {@code HomeExpansion.spaFallback()=false} 只能走 hash 路由，故采用
 *       {@link #wujie()} = false 的 IFRAME 模式；</li>
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

    /**
     * 模块访问权限：与 {@code plugin.yml} 的 {@code ihomepages.erp} 对应。
     * {@code MenuRouteServiceImpl.moduleRoute} 会先按此节点过滤整个一级模块。
     */
    @Override
    protected String permission() {
        return "ihomepages.erp";
    }

    /**
     * 采用 <b>IFRAME</b> 而非默认的 wujie。原因（经核对 MCERP 主应用 WujieLink 组件实现）：
     * <ul>
     *   <li>wujie 把 {@code meta.link} 整个当子应用入口 URL，并把
     *       {@code /web/plugins/<名>} 之后的部分作为 {@code props.path} / bus
     *       {@code router-push} 推给子应用。带 {@code #/...} 的 hash 会被算成
     *       {@code /erp/#/ihomepageserp/config/config} 这种非法 path；</li>
     *   <li>不带 hash 的深层 URL 又要求 SPA 回退，而 {@code HomeExpansion.spaFallback()=false}
     *       （手写多页 HTML 不能被吞成 index.html），且该规则按 owner 插件名唯一，
     *       两个 Expansion 无法各设一份；</li>
     *   <li>IFRAME 直接把完整 hash URL 作为 iframe src，服务器只收到 {@code /erp/}
     *       并命中 index.html，由子应用 vue-router 自己解析 hash，链路最短。</li>
     * </ul>
     * 前端已相应把「嵌入态」识别扩展为 wujie 或 iframe（router/permission.js），
     * 使主应用统一鉴权、子应用不再走自身登录守卫。
     */
    @Override
    protected boolean wujie() {
        return false;
    }

    /**
     * 页面 URL 前缀 = <b>owner 插件名</b>的命名空间（{@code /web/plugins/ihomepages}），
     * 而非 identifier。父类默认用 identifier（{@code ihomepages-erp}）会指向
     * {@code /web/plugins/ihomepages-erp} —— 该命名空间从未托管任何资源，菜单必然白屏。
     * 服务器可改 page 前缀，故经 toolkit 求值（与 GiftLoft 参考实现一致）。
     */
    @Override
    public String pageRoot() {
        SoysHttpOverMcApi a = getApi();
        Plugin o = getOwner();
        String base = (a == null || o == null) ? "/web/plugins/ihomepages"
                : a.getToolkit().pageFullPrefix(o);
        return base == null || base.isEmpty() ? "/web/plugins/ihomepages" : base;
    }

    /** ERP 前端入口（沿用既有 {@code /erp} 子路径方案）。 */
    private String erpPageRoot() {
        return pageRoot() + "/erp/";
    }

    /** ERP 菜单入口：/web/plugins/ihomepages/erp/（hash 模式，刷新不 404）。 */
    @Override
    protected String homeUrl() {
        return erpPageRoot();
    }

    /**
     * 菜单叶子页面地址。
     *
     * <p>父模块路由 path 由 {@code MenuRouteServiceImpl.moduleRoute} 合成为
     * {@code "/" + safeName(identifier)} = {@code /ihomepageserp}；子应用 SPA 的
     * moduleRoot 必须与之同源，否则 {@code router/menuAdapter.js} 裁不出子树 → 白屏。
     * 因 {@link HomeExpansion#spaFallback()} 为 false（手写多页 HTML 不能吞深层 404），
     * ERP 子应用只能走 <b>hash 路由</b>：服务器只收到 {@code /erp/} 命中 index.html，
     * {@code #/...} 由前端 vue-router 解析。</p>
     */
    private String erpPage(String route) {
        return erpPageRoot() + "#/ihomepageserp/" + route;
    }

    /**
     * 菜单树（修正了旧实现的两个结构性错误）：
     * <ol>
     *   <li>旧代码 {@code .dir("erp","ERP 控制台","erp")} 是<b>无 lambda</b> 重载，
     *       会压栈且不弹栈 → 后续 {@code menu} 全部嵌进这个多余目录，且模块自身已被
     *       MCERP 渲染成一级节点，等于多套了一层；</li>
     *   <li>{@code .menu(id, name, "erp/homepage", Controller.class)} 第三个位置参数是
     *       <b>icon</b> 不是 path/component，页面地址被写成了图标字符串。</li>
     * </ol>
     *
     * <p>现在：根级 M 目录「插件配置」下两片叶子（config.yml / home.yml）。
     * {@code componentFullURL} 不加任何前缀，直接指向 {@code /erp} 子路径下的 hash 路由；
     * {@code @ApiPermission} 端点由 {@code ControllerPermScanner} 自动投影为 F 按钮
     * （仅挂在 config 叶子，两个叶子共用同一组端点，重复挂会产生重复 menuId）。</p>
     *
     * <p><b>「主页管理」暂不挂菜单</b>：{@link ErpHomepageController} 的
     * {@code /homepage/list|get|save|remove|preview} 端点已注册且可直连，但前端尚无对应
     * 视图与路由绑定。后端菜单一旦下发而前端 {@code moduleRoutes} 缺绑定，
     * {@code menuAdapter.buildMenuTree} 会直接抛错（点开必然白屏）——与 GiftLoft 参考实现
     * 「玩家查询端点已注册、前端无页面故不挂菜单」的处理一致。等前端补页面后加回即可。</p>
     */
    @Override
    protected ErpMenus menus() {
        return ErpMenus.create()
                .dir("config", "插件配置", "edit", d -> d
                        .menu("config", "核心配置", "input", ErpConfigController.class)
                            .componentFullURL(erpPage("config/config"))
                            .orderNum(10)
                        .menu("home", "首页内容", "documentation")
                            .componentFullURL(erpPage("config/home"))
                            .orderNum(20));
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
     * ERP 前端资源根：{@code web/} 的 {@code npm run build:prod} 产物复制到
     * {@code src/main/resources/erp-dist/} 后随 jar 发布。
     *
     * <p>磁盘 {@code plugins/ihomepages/erp-dist/} 若存在则优先（便于运营热替换），
     * 否则 {@link #registerPages()} 回退到 jar 内资源目录。返回 null 会导致页面托管空操作
     * —— 曾是本模块 ERP 菜单点开 404 的根因。</p>
     */
    protected String erpResourceRoot() {
        return "erp-dist";
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
