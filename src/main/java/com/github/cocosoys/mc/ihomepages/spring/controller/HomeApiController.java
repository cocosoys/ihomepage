package com.github.cocosoys.mc.ihomepages.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.ihomepages.config.HomeConfigEntity;
import com.github.cocosoys.mc.ihomepages.spring.service.IGiftService;
import com.github.cocosoys.mc.ihomepages.config.IHomeConfigExporter;
import com.github.cocosoys.mc.ihomepages.config.IHomeConfigSource;
import com.github.cocosoys.mc.ihomepages.spring.entity.LiveData;

/**
 * 自定义主页接口控制器（仿 Spring MVC 注解式）：
 * <ul>
 *   <li>{@code GET /api/homepage/config} —— 返回 home.yml 的「仅展示」安全 JSON（前端渲染用）；</li>
 *   <li>{@code GET /api/homepage/live} —— 返回实时数据（在线人数等，独立端点可缓存）；</li>
 *   <li>{@code POST /api/homepage/gift/claim} —— 领取礼包（需登录凭证）；</li>
 *   <li>{@code GET /api/homepage/gift/status} —— 查询礼包领取状态（需登录凭证）。</li>
 * </ul>
 * config / live 标注 {@code @ApiPublic} 且已在 auth.yml exempt 中豁免，浏览器无需凭证即可访问；
 * gift/claim / gift/status 受网关保护（需玩家会话凭证）。
 */
@com.github.cocosoys.mc.soyshttpovermc.annotations.RequestMapping("/homepage")
public class HomeApiController {

    private final IHomeConfigSource home;
    private final IHomeConfigExporter exporter;
    private final IGiftService gift;
    private final JavaPlugin plugin;

    public HomeApiController(IHomeConfigSource home, IHomeConfigExporter exporter,
                             IGiftService gift, JavaPlugin plugin) {
        this.home = home;
        this.exporter = exporter;
        this.gift = gift;
        this.plugin = plugin;
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("首页配置")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping("/config")
    public AjaxResult config() {
        HomeConfigEntity hc = home.get();
        if (hc == null) {
            return AjaxResult.errorT(500, "gift.config.no-config", "主页配置未加载");
        }
        return AjaxResult.success(exporter.export(hc));
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("首页实时数据")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping("/live")
    public AjaxResult live() {
        int online = Bukkit.getOnlinePlayers().size();
        return AjaxResult.success(new LiveData(online, Bukkit.getMaxPlayers()));
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("首页礼包领取")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping("/gift/claim")
    public AjaxResult claim(ApiRequestContext ctx) {
        return gift.claim(ctx.getSyncPlayer());
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("礼包状态查询")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @GetMapping("/gift/status")
    public AjaxResult giftStatus(ApiRequestContext ctx) {
        return gift.status(ctx.getSyncPlayer());
    }
}
