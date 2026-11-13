package com.github.cocosoys.mc.ihomepages.spring.controller;

import com.github.cocosoys.mc.ihomepages.spring.service.IHomepagesConfigService;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPermission;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestParam;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;

import java.util.Map;

/**
 * ERP 管理面 —— 「插件配置」菜单的后端端点。
 *
 * <p>注册前缀由宿主按 <b>owner 插件名</b>（ihomepages）而非 identifier 计算，
 * 因此实际路径为 {@code /api/plugins/ihomepages/erp/config/*}，正好与
 * {@code web/src/api/erp.js} 中已有的 {@code listConfigFiles/loadConfigFile/saveConfigFile}
 * 三个函数对齐（它们拼的是 {@code /erp/config/files|load|save}）。</p>
 *
 * <p>鉴权（与用户确认）：不再沿用 {@code @ApiPublic}，改用 {@code @ApiPermission}，
 * 三个节点 {@code ihomepages.erp.config.list/query/edit} 在 {@code plugin.yml} 中声明
 * 为 {@code default: op}。这些节点同时被 MCERP 的 {@code ControllerPermScanner}
 * 投影为该菜单下的 F 按钮（用于前端按钮级显隐）。</p>
 *
 * <p>本类只做参数转发，文件解析、YAML 读写、异常兜底全部在
 * {@link IHomepagesConfigService} 实现中。</p>
 */
@RequestMapping("/erp/config")
public class ErpConfigController {

    private final IHomepagesConfigService configService;

    public ErpConfigController(IHomepagesConfigService configService) {
        this.configService = configService;
    }

    @ApiName("配置文件清单")
    @ApiPermission("ihomepages.erp.config.list")
    @GetMapping("/files")
    public AjaxResult files() {
        return configService.list();
    }

    @ApiName("配置文件读取")
    @ApiPermission("ihomepages.erp.config.query")
    @GetMapping("/load")
    public AjaxResult load(@RequestParam(name = "file", required = true) String file) {
        return configService.load(file);
    }

    @ApiName("配置文件保存")
    @ApiPermission("ihomepages.erp.config.edit")
    @PostMapping("/save")
    public AjaxResult save(@RequestParam(name = "file", required = true) String file,
                           @RequestBody Map<String, Object> data) {
        return configService.save(file, data);
    }
}
