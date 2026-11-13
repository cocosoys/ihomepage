package com.github.cocosoys.mc.ihomepages.spring.impl;

import com.github.cocosoys.mc.ihomepages.MyHomePages;
import com.github.cocosoys.mc.ihomepages.spring.entity.HomepagesConfigFile;
import com.github.cocosoys.mc.ihomepages.spring.entity.vo.HomepagesConfigFileVo;
import com.github.cocosoys.mc.ihomepages.spring.entity.vo.HomepagesConfigSaveVo;
import com.github.cocosoys.mc.ihomepages.spring.service.IHomepagesConfigService;
import com.github.cocosoys.mc.soyshttpovermc.platform.PlatformYaml;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.ConfigUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * iHomePages 配置文件编辑实现：目标目录 = owner 插件（ihomepages）自己的数据目录。
 *
 * <p><b>自包含</b>：构造器注入 owner 实例，按 {@code plugin.getDataFolder()} 解析，
 * 不写死任何插件名、不依赖 Bukkit PluginManager 去查别的插件目录。这与参考实现
 * {@code SoysConfigServiceImpl}（网关 ERP 编辑 SOYSHTTPOverMC 的配置）不同。</p>
 *
 * <p>读写全部复用平台层：{@link PlatformYaml#load}/{@link PlatformYaml#save}
 * （统一接管编码，低版本 UTF-8 不乱码）+ {@link ConfigUtil#toMap}/{@link ConfigUtil#toYaml}
 * （YAML ↔ Map 递归转换）。全量写回，注释不保留。所有方法 try/catch(Throwable)，
 * 把异常转成 {@code AjaxResult.error(msg)}，不让堆栈穿透到 HTTP。</p>
 */
public class HomepagesConfigServiceImpl implements IHomepagesConfigService {

    private final MyHomePages plugin;

    public HomepagesConfigServiceImpl(MyHomePages plugin) {
        this.plugin = plugin;
    }

    @Override
    public AjaxResult list() {
        try {
            List<HomepagesConfigFileVo> rows = new ArrayList<>();
            for (HomepagesConfigFile f : HomepagesConfigFile.values()) {
                HomepagesConfigFileVo vo = new HomepagesConfigFileVo();
                vo.setId(f.getId());
                vo.setName(f.getName());
                vo.setFile(f.getFile());
                vo.setDesc(f.getDesc());
                rows.add(vo);
            }
            return AjaxResult.success(rows);
        } catch (Throwable t) {
            return AjaxResult.error("配置清单查询失败：" + t.getMessage());
        }
    }

    @Override
    public AjaxResult load(String fileId) {
        try {
            HomepagesConfigFile f = requireFile(fileId);
            File file = resolve(f);
            if (!file.isFile()) {
                return AjaxResult.error("配置文件不存在，请先启动一次插件生成默认配置: "
                        + file.getPath());
            }
            return AjaxResult.success(ConfigUtil.toMap(PlatformYaml.load(file)));
        } catch (Throwable t) {
            return AjaxResult.error("配置读取失败：" + t.getMessage());
        }
    }

    @Override
    public AjaxResult save(String fileId, Map<String, Object> data) {
        try {
            HomepagesConfigFile f = requireFile(fileId);
            File file = resolve(f);
            if (!file.isFile()) {
                return AjaxResult.error("配置文件不存在，请先启动一次插件生成默认配置: "
                        + file.getPath());
            }
            if (data == null) {
                return AjaxResult.error("配置内容为空");
            }
            PlatformYaml.save(ConfigUtil.toYaml(data), file);
            return AjaxResult.success(HomepagesConfigSaveVo.of(f.getFile(),
                    "已保存。执行 /soyshttp homepage config reload 热重载生效"
                            + "（enabled 开关、新增/删除主页映射需重启服务器）"));
        } catch (Throwable t) {
            return AjaxResult.error("配置保存失败：" + t.getMessage());
        }
    }

    // ===== 内部 =====

    private static HomepagesConfigFile requireFile(String fileId) {
        HomepagesConfigFile f = HomepagesConfigFile.byId(fileId);
        if (f == null) {
            throw new IllegalArgumentException("未知配置文件 id: " + fileId);
        }
        return f;
    }

    /** owner 插件自己的数据目录下解析目标文件。 */
    private File resolve(HomepagesConfigFile f) {
        if (plugin == null) {
            throw new IllegalStateException("owner 插件实例未注入");
        }
        return new File(plugin.getDataFolder(), f.getFile());
    }
}
