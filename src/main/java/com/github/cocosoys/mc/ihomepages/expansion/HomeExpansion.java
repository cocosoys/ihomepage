package com.github.cocosoys.mc.ihomepages.expansion;

import com.github.cocosoys.mc.ihomepages.spring.controller.HomeApiController;
import com.github.cocosoys.mc.soyshttpovermc.api.SoysExpansion;
import lombok.CustomLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 主页 SoysExpansion（双 Expansion 方案 P 的主页实例）：
 * <ul>
 *   <li>托管手写 HTML 前端（{@code dist/}）到 {@code /web/plugins/ihomepages/}，
 *       磁盘 {@code plugins/ihomepages/dist/} 优先（惰性登记、支持热替换），jar 回退；</li>
 *   <li>收编 {@link HomeApiController} 端点到 {@code /api/plugins/ihomepages/...}
 *       （原 {@code registerController} 正常登记语义，宿主自动补插件前缀）；</li>
 *   <li>页面规则（indexRule/spaFallback）<b>唯一所有者</b>——另一实例（ErpExpansion）
 *       不得调用 super.registerPages() / 修改这两项规则，否则按插件名互覆。</li>
 * </ul>
 *
 * <p>手写多页 HTML 非 SPA：{@code spaFallback()=false}（无扩展名路径未命中保持 404，
 * 避免被吞成 index.html）。</p>
 */
@CustomLog
public class HomeExpansion extends SoysExpansion {

    private final HomeApiController homeApiController;

    public HomeExpansion(HomeApiController homeApiController) {
        this.homeApiController = homeApiController;
    }

    @Override
    public String getIdentifier() {
        return "ihomepages";
    }

    /** 主页前端资源目录（磁盘 plugins/ihomepages/dist/ 优先，jar 回退）。 */
    @Override
    protected String resourceRoot() {
        return "dist";
    }

    /** 手写多页 HTML，非 SPA → 关闭回退。 */
    @Override
    protected boolean spaFallback() {
        return false;
    }

    // indexFile() 默认 "index"：/web/plugins/ihomepages → index.html，无需覆写

    /** 主页 API：正常登记自动补 /api/plugins/ihomepages/ 前缀。 */
    @Override
    protected List<Object> buildControllers() {
        List<Object> list = new ArrayList<>();
        list.add(homeApiController);
        return list;
    }
}
