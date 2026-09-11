package com.github.cocosoys.mc.ihomepages.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.ihomepages.action.WebActionExecutor;
import com.github.cocosoys.mc.ihomepages.action.WebActionManager;
import com.github.cocosoys.mc.ihomepages.action.model.ActionClaimRecord;
import com.github.cocosoys.mc.ihomepages.action.model.ActionResult;
import com.github.cocosoys.mc.ihomepages.action.model.WebAction;
import com.github.cocosoys.mc.ihomepages.action.queue.ActionClaimStore;
import com.github.cocosoys.mc.ihomepages.action.queue.OfflineTaskQueue;
import com.github.cocosoys.mc.ihomepages.config.HomeConfigEntity;
import com.github.cocosoys.mc.ihomepages.config.IHomeConfigExporter;
import com.github.cocosoys.mc.ihomepages.config.IHomeConfigSource;
import com.github.cocosoys.mc.ihomepages.spring.entity.LiveData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义主页接口控制器（仿 Spring MVC 注解式）。
 *
 * <p><b>地址前缀说明</b>：按宿主 1.4.0 API 注册规范，非主插件控制器经 {@code registerController} 注册时
 * 自动获得 {@code /plugins/ihomepages} 命名空间前缀（位于全局 {@code /api} 前缀之后），故下列端点的
 * <b>实际访问地址</b>均为 {@code /api/plugins/ihomepages/homepage/...}（注解内只写相对路径，不写前缀）：</p>
 * <ul>
 *   <li>{@code GET /api/plugins/ihomepages/homepage/config} —— 返回 home.yml 的「仅展示」安全 JSON（前端渲染用）；</li>
 *   <li>{@code GET /api/plugins/ihomepages/homepage/live} —— 返回实时数据（在线人数等，独立端点可缓存）；</li>
 *   <li>{@code GET /api/plugins/ihomepages/homepage/action/list} —— 网页动作列表（安全视图，不含命令模板，含领取型动作的展示信息）；</li>
 *   <li>{@code POST /api/plugins/ihomepages/homepage/action/execute} —— 执行网页动作（actions.yml 配置驱动：扣款/指令/离线分流/领取去重）；</li>
 *   <li>{@code GET /api/plugins/ihomepages/homepage/action/status} —— 查询本人待补发的离线任务数 + 各领取型动作的领取状态。</li>
 * </ul>
 * config / live 标注 {@code @ApiPublic} 且已在 auth.yml exempt 中豁免，浏览器无需凭证即可访问；
 * action/* 需玩家会话凭证（在线/离线令牌均可，凭证解析出的玩家名即领取主体）。
 */
@RequestMapping("/homepage")
public class HomeApiController {

    private final IHomeConfigSource home;
    private final IHomeConfigExporter exporter;
    private final WebActionManager actionManager;
    private final WebActionExecutor actionExecutor;
    private final OfflineTaskQueue taskQueue;
    private final ActionClaimStore claimStore;
    private final JavaPlugin plugin;

    public HomeApiController(IHomeConfigSource home, IHomeConfigExporter exporter,
                             WebActionManager actionManager,
                             WebActionExecutor actionExecutor, OfflineTaskQueue taskQueue,
                             ActionClaimStore claimStore, JavaPlugin plugin) {
        this.home = home;
        this.exporter = exporter;
        this.actionManager = actionManager;
        this.actionExecutor = actionExecutor;
        this.taskQueue = taskQueue;
        this.claimStore = claimStore;
        this.plugin = plugin;
    }

    @ApiName("首页配置")
    @ApiPublic
    @GetMapping("/config")
    public AjaxResult config() {
        HomeConfigEntity hc = home.get();
        if (hc == null) {
            return AjaxResult.errorT(500, "homepage.config.no-config", "主页配置未加载");
        }
        return AjaxResult.success(exporter.export(hc));
    }

    @ApiName("首页实时数据")
    @ApiPublic
    @GetMapping("/live")
    public AjaxResult live() {
        int online = Bukkit.getOnlinePlayers().size();
        return AjaxResult.success(new LiveData(online, Bukkit.getMaxPlayers()));
    }

    @ApiName("网页动作列表")
    @ApiPublic
    @GetMapping("/action/list")
    public AjaxResult actionList() {
        // 安全视图：只返回展示所需字段，不包含命令模板/效果明细（前端不可见执行细节）
        List<Map<String, Object>> list = new ArrayList<>();
        for (WebAction a : actionManager.visible()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("name", a.getName());
            m.put("price", a.getPrice());
            m.put("cooldownSeconds", a.getCooldownSeconds());
            if (a.isClaimAction()) {
                m.put("claimPeriod", a.getClaimPeriod());
            }
            if (a.getDisplay() != null) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("icon", a.getDisplay().getIcon());
                d.put("desc", a.getDisplay().getDesc());
                d.put("group", a.getDisplay().getGroup());
                d.put("sort", a.getDisplay().getSort());
                m.put("display", d);
            }
            list.add(m);
        }
        return AjaxResult.success(list);
    }

    @ApiName("网页动作执行")
    @ApiPublic
    @PostMapping("/action/execute")
    public AjaxResult actionExecute(ApiRequestContext ctx,
                                    @RequestParam(name = "action", required = true) String action,
                                    @RequestParam(name = "amount", required = false, defaultValue = "1") int amount) {
        ActionResult r = actionExecutor.execute(action, ctx.getPlayerName(), amount);
        if (!r.isSuccess()) {
            return AjaxResult.error(500, r.getMessage());
        }
        return AjaxResult.success(r.getMessage(), r.getData());
    }

    @ApiName("动作状态查询")
    @ApiPublic
    @GetMapping("/action/status")
    public AjaxResult actionStatus(ApiRequestContext ctx) {
        String player = ctx.getPlayerName();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("player", player);
        m.put("pending", taskQueue.pendingCount(player));
        // 领取型动作的领取状态（前端用于展示「已领取/可领取/待补发」）
        List<Map<String, Object>> claims = new ArrayList<>();
        for (WebAction a : actionManager.all().values()) {
            if (!a.isClaimAction() || !a.isVisible()) {
                continue;
            }
            Map<String, Object> c = new LinkedHashMap<>();
            ActionClaimRecord rec = claimStore.findLatest(player, a.getId());
            boolean claimed = claimStore.isClaimed(rec, a.getClaimPeriod(), System.currentTimeMillis());
            c.put("action", a.getId());
            c.put("claimed", claimed);
            c.put("claimedAt", rec == null ? null : rec.getClaimedAt());
            c.put("canClaim", !claimed);
            claims.add(c);
        }
        m.put("claims", claims);
        return AjaxResult.success(m);
    }
}
