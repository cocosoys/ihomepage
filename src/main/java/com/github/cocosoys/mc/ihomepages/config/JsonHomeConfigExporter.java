package com.github.cocosoys.mc.ihomepages.config;
import lombok.CustomLog;

import java.util.Map;

/**
 * 配置导出实现：构建「仅展示字段」的安全 JSON Map。
 *
 * <p>礼包内容已迁移至 actions.yml（由 action 层承载，前端经 /api/plugins/ihomepages/homepage/action/list
 * 获取展示信息），本类仅透出 home.yml 的通用展示段；动作列表的安全视图由 HomeApiController.actionList 单独构建。</p>
 */
@CustomLog
public class JsonHomeConfigExporter implements IHomeConfigExporter {

    public JsonHomeConfigExporter() {
    }

    @Override
    public Map<String, Object> export(HomeConfigEntity cfg) {
        return new java.util.LinkedHashMap<>(cfg.toDisplayMap());
    }
}
