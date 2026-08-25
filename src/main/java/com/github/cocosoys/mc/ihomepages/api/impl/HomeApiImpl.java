package com.github.cocosoys.mc.ihomepages.api.impl;

import com.github.cocosoys.mc.ihomepages.api.HomeApi;
import com.github.cocosoys.mc.ihomepages.homepage.HomepageRegistry;
import com.github.cocosoys.mc.ihomepages.homepage.HomepageState;
import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link HomeApi} 实现：
 * <ul>
 *   <li>记录——{@link HomepageRegistry}（主页名 → 位置）；</li>
 *   <li>持久化——{@link HomepageState}（当前选择写入 ihomepage {@code config.yml}）；</li>
 *   <li>切换——写 homepage.current + 同步 soyshttpovermc {@code web.home} + reload（由宿主完成实际伺服）。</li>
 * </ul>
 */
public class HomeApiImpl implements HomeApi {

    private final HttpOverMcPlugin plugin;
    private final HomepageRegistry registry;
    private final HomepageState state;

    /** 串行化 homepage 切换的整套副作用序列，避免并发切换交错导致 current 与 web.home 状态不一致。 */
    private final ReentrantLock switchLock = new ReentrantLock();

    public HomeApiImpl(HttpOverMcPlugin plugin, HomepageRegistry registry, HomepageState state) {
        this.plugin = plugin;
        this.registry = registry;
        this.state = state;
    }

    @Override
    public void registerPage(String name, String spec) {
        registry.register(name, spec);
    }

    @Override
    public boolean switchTo(String name) {
        switchLock.lock();
        try {
            String spec = registry.getSpec(name);
            if (spec == null || spec.isEmpty()) {
                return false;
            }
            // 1) 记录当前主页到 ihomepage 自身 config.yml 的 homepage.current
            state.saveCurrent(name);
            // 2) 同步 soyshttpovermc pages.yml 的 web.home（页面位置：相对路径按数据目录解析为绝对路径；URL/绝对路径原样）
            String value = com.github.cocosoys.mc.ihomepages.MyHomePages.resolveWebHomeSpec(plugin, spec);
            plugin.setWebHome(value);
            // 3) reload 使 web.home 生效（reloadHttpConfig 末尾会把 web.home 应用到运行中的 WebFrontendHandler，
            //    并触发已注册的 ReloadHttpConfigHandler / HttpConfigReloadEvent 让其它模块一起刷新）。
            //    注意：reload 内部会再次调用本模块注册的钩子，但钩子只做 setHomeSpec 不递归 reload。
            plugin.reloadHttpConfig();
            registry.setCurrentName(name);
            return true;
        } finally {
            switchLock.unlock();
        }
    }

    @Override
    public boolean unregister(String name) {
        return registry.unregister(name);
    }

    @Override
    public int unregisterAll() {
        return registry.unregisterAll();
    }

    @Override
    public List<String> list() {
        return registry.list();
    }

    @Override
    public String getCurrent() {
        return registry.getCurrentName();
    }
}
