package com.github.cocosoys.mc.ihomepages.spring.controller;

import com.github.cocosoys.mc.ihomepages.spring.entity.po.ErpHomepagePO;
import com.github.cocosoys.mc.ihomepages.spring.service.IErpHomepageService;
import com.github.cocosoys.mc.soyshttpovermc.annotations.*;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ERP 主页管理控制器（双 Expansion 方案 P 的 ERP 实例）：
 * 经 ErpExpansion.buildControllers() 注册，自动获得 {@code /api/plugins/ihomepages-erp/homepage/...} 命名空间。
 *
 * <p>围绕「添加主页 / 预览主页」需求提供：列表 / 详情 / 保存 / 删除 / 预览。
 * 端点沿用 {@code @ApiPublic}（公开，无需会话凭证）。</p>
 */
@RequestMapping("/homepage")
public class ErpHomepageController {

    private final IErpHomepageService svc;

    public ErpHomepageController(IErpHomepageService svc) {
        this.svc = svc;
    }

    @ApiName("主页列表")
    @ApiPublic
    @GetMapping("/list")
    public AjaxResult list() {
        return AjaxResult.success(svc.list());
    }

    @ApiName("主页详情")
    @ApiPublic
    @GetMapping("/get")
    public AjaxResult get(@RequestParam(name = "id", required = true) Long id) {
        ErpHomepagePO po = svc.get(id);
        if (po == null) {
            return AjaxResult.error(404, "主页不存在");
        }
        return AjaxResult.success(po);
    }

    @ApiName("保存主页")
    @ApiPublic
    @PostMapping("/save")
    public AjaxResult save(
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "location", required = false) String location,
            @RequestParam(name = "content", required = false) String content,
            @RequestParam(name = "enabled", defaultValue = "true") boolean enabled,
            @RequestParam(name = "remark", required = false) String remark) {
        ErpHomepagePO po = new ErpHomepagePO();
        po.setId(id);
        po.setName(name);
        po.setLocation(location);
        po.setContent(content);
        po.setEnabled(enabled);
        po.setRemark(remark);
        boolean ok = svc.save(po);
        return ok ? AjaxResult.success("保存成功") : AjaxResult.error(500, "保存失败");
    }

    @ApiName("删除主页")
    @ApiPublic
    @PostMapping("/remove")
    public AjaxResult remove(@RequestParam(name = "id", required = true) Long id) {
        boolean ok = svc.remove(id);
        return ok ? AjaxResult.success("删除成功") : AjaxResult.error(500, "删除失败");
    }

    @ApiName("主页预览")
    @ApiPublic
    @GetMapping("/preview")
    public AjaxResult preview(@RequestParam(name = "id", required = true) Long id) {
        ErpHomepagePO po = svc.get(id);
        if (po == null) {
            return AjaxResult.error(404, "主页不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", po.getId());
        m.put("name", po.getName());
        m.put("location", po.getLocation());
        m.put("content", po.getContent());
        m.put("enabled", po.isEnabled());
        // 预览地址占位：实际按宿主页面托管规则拼装（web.home.<location>）
        m.put("previewUrl", "/web/plugins/ihomepages/?loc=" + (po.getLocation() == null ? "" : po.getLocation()));
        return AjaxResult.success(m);
    }
}
