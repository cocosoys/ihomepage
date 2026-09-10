package com.github.cocosoys.mc.ihomepages.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Map;

/**
 * 主页内容配置实体（由 home.yml 解析而来）。
 * 通用展示段（page/server/qq/social/...）以原始 {@link YamlConfiguration} 透出供导出器构建 JSON。
 *
 * <p>礼包内容已迁移至 actions.yml（配置动作 {@code claim-gift}，由 action 层承载领取去重/离线补发），
 * home.yml 不再解析 gift 段。</p>
 */
public class HomeConfigEntity {

    private final YamlConfiguration config;

    public HomeConfigEntity(YamlConfiguration config) {
        this.config = config;
    }

    public YamlConfiguration raw() {
        return config;
    }

    /** 整份配置（含通用展示段）的深拷贝 Map，供导出器序列化为前端 JSON。 */
    public Map<String, Object> toDisplayMap() {
        return config.getValues(true);
    }

    // ===== 类型化便捷访问（导出器 / 控制器复用） =====

    public boolean getUIEnable(String key, boolean def) {
        return config.getBoolean("ui." + key, def);
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }
}
