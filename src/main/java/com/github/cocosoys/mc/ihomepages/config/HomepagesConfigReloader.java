package com.github.cocosoys.mc.ihomepages.config;

import com.github.cocosoys.mc.ihomepages.MyHomePages;
import com.github.cocosoys.mc.ihomepages.homepage.HomepageRegistry;
import com.github.cocosoys.mc.ihomepages.homepage.HomepageState;
import com.github.cocosoys.mc.ihomepages.spring.impl.MainConfigReader;
import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import lombok.CustomLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /soyshttp homepage config reload} 的实际执行体：把 ERP「插件配置」页保存后的
 * {@code config.yml} / {@code home.yml} 重新读进<b>运行期已存在的那些对象</b>。
 *
 * <p><b>为什么需要独立类</b>：{@code MyHomePages.onEnable()} 里的
 * {@code MainConfigReader / HomepageRegistry / HomepageState / YamlHomeConfigSource}
 * 都是局部变量，而 {@code HomeApiController} 与 {@code HomeApiImpl} 持有的是它们的
 * <b>实例引用</b>。新读一份配置毫无用处，必须原地刷新同一批实例：</p>
 * <ol>
 *   <li>{@link MainConfigReader#load()} —— enabled / cache-seconds；</li>
 *   <li>{@link HomepageState#loadConfig()} 重读 → 重建 {@link HomepageRegistry} 的
 *       homepage.pages 映射与 current（原地 unregisterAll + register，不换对象）；</li>
 *   <li>按新的 current 重新应用宿主 {@code web.home}（setWebHome + setHomeSpec，
 *       <b>不</b>调用 reloadHttpConfig，避免递归触发本模块已注册的 reload 钩子）；</li>
 *   <li>{@link YamlHomeConfigSource#refresh()} —— home.yml 展示内容。</li>
 * </ol>
 *
 * <p><b>边界</b>：{@code enabled: false} 只能在 onEnable 阶段决定是否注册 Expansion，
 * 运行期无法安全反注册已登记的端点/页面，故仅提示重启；新增/删除主页映射虽然已重建
 * 到 registry，但 dist/ 静态资源、Expansion 托管的页面规则也在启动时固定，同样建议重启
 * 以完全生效。保存后的提示文案见 {@code HomepagesConfigServiceImpl#save}。</p>
 */
@CustomLog
public class HomepagesConfigReloader {

    private final HttpOverMcPlugin host;
    private final HomepageRegistry registry;
    private final HomepageState state;
    private final MainConfigReader cfgReader;
    private final YamlHomeConfigSource homeSource;

    public HomepagesConfigReloader(HttpOverMcPlugin host,
                                   HomepageRegistry registry, HomepageState state,
                                   MainConfigReader cfgReader, YamlHomeConfigSource homeSource) {
        this.host = host;
        this.registry = registry;
        this.state = state;
        this.cfgReader = cfgReader;
        this.homeSource = homeSource;
    }

    /** 执行热重载；任何单步失败都记录到 {@link Report#notes}，不抛出、不中断后续步骤。 */
    public Report reload() {
        Report r = new Report();
        try {
            // 1) 逻辑配置
            cfgReader.load();
            r.enabled = cfgReader.isEnabled();
            r.cacheSeconds = cfgReader.cacheSeconds();
        } catch (Throwable t) {
            r.notes.add("config.yml 逻辑配置重载失败: " + t.getMessage());
        }

        // 2+3) homepage.pages / current + web.home
        try {
            YamlConfiguration cfg = state.loadConfig();
            Map<String, String> specs = new LinkedHashMap<>();
            ConfigurationSection pages = cfg.getConfigurationSection("homepage.pages");
            if (pages != null) {
                for (String key : pages.getKeys(false)) {
                    String v = pages.getString(key);
                    if (v != null && !v.trim().isEmpty()) {
                        specs.put(key, v.trim());
                    }
                }
            }
            if (!specs.containsKey("default")) {
                specs.put("default", "dist/index.html");
            }
            String current = cfg.getString("homepage.current", "");
            if (current == null || current.isEmpty() || !specs.containsKey(current)) {
                current = "default";
            }

            registry.unregisterAll();
            for (Map.Entry<String, String> e : specs.entrySet()) {
                registry.register(e.getKey(), e.getValue());
            }
            registry.setCurrentName(current);
            r.pageCount = specs.size();
            r.current = current;

            String spec = registry.getSpec(current);
            r.appliedSpec = spec;
            if (spec != null) {
                String value = MyHomePages.resolveWebHomeSpec(spec);
                host.getDelegate().setWebHome(value);
                host.getWebFrontend().setHomeSpec(value);
                r.homeApplied = true;
            }
        } catch (Throwable t) {
            r.notes.add("主页映射/web.home 重载失败: " + t.getMessage());
        }

        // 4) 首页展示内容
        try {
            homeSource.refresh();
            r.homeYamlReloaded = true;
        } catch (Throwable t) {
            r.notes.add("home.yml 重载失败: " + t.getMessage());
        }

        if (!r.enabled) {
            r.notes.add("enabled=false 只在插件启动时生效，请重启服务器以停用本模块");
        }
        r.notes.add("新增/删除主页映射、dist 静态资源变更建议重启服务器以完全生效");

        log.infoT("ihomepages.config.reload-done",
                "配置热重载完成: 主页 {0} 个 (current={1}), home.yml={2}, web.home={3}, enabled={4}",
                r.pageCount, r.current, r.homeYamlReloaded, r.homeApplied, r.enabled);
        return r;
    }

    /** 重载结果摘要（供命令回显，也便于日后给 ERP 端点复用）。 */
    public static final class Report {
        public boolean enabled = true;
        public int cacheSeconds;
        public int pageCount;
        public String current = "";
        public String appliedSpec = "";
        public boolean homeApplied;
        public boolean homeYamlReloaded;
        public final List<String> notes = new ArrayList<>();
    }
}
