package com.github.cocosoys.mc.ihomepages.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;

import java.util.Map;

/**
 * iHomePages「插件配置」可视化编辑业务接口。
 *
 * <p>读写均落在 owner 插件（ihomepages）自己的数据目录 {@code plugins/ihomepages/}，
 * 不依赖 PluginManager 查找别的插件；保存为全量树写回（注释不保留）。</p>
 */
public interface IHomepagesConfigService {

    /** 可编辑配置文件清单（由 {@link com.github.cocosoys.mc.ihomepages.spring.entity.HomepagesConfigFile} 派生）。 */
    AjaxResult list();

    /** 读取单个配置文件 → 树形 Map（{@code data} 字段返回）。 */
    AjaxResult load(String fileId);

    /** 保存单个配置文件（全量树写回），返回带 {@code tip} 的保存结果。 */
    AjaxResult save(String fileId, Map<String, Object> data);
}
