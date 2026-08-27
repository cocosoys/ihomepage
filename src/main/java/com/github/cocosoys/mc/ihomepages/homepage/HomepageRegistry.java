package com.github.cocosoys.mc.ihomepages.homepage;
import lombok.CustomLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页登记表（轻量）：仅记录“主页名 → 位置（相对路径 / 绝对路径 / 网络 URL）”的映射，
 * <b>不持有任何网页字节内容</b>。实际伺服由 SOYSHTTPOverMC 的 {@code web.home} 机制完成——
 * 切换主页时把对应位置写入 soyshttpovermc 的 {@code config.yml} 的 {@code web.home} 并 reload 即可，
 * 无需把 HTML 字节塞进 {@code GET /} 路由。
 *
 * <p>当前首页名持久化在 {@code config.yml} 的 {@code homepage.current}
 * （由 {@link HomepageState} 负责读写），服务器重启后自动恢复。</p>
 */
@CustomLog
public class HomepageRegistry {

    /** {@code specs} 可能被命令线程 / HTTP 线程 / 第三方插件线程并发访问，须全部经同步进入。 */
    private final Map<String, String> specs = new LinkedHashMap<>();
    private volatile String currentName;

    /** 记录一个主页位置（不自动切换）。 */
    public synchronized void register(String name, String spec) {
        if (name == null || name.isEmpty() || spec == null || spec.trim().isEmpty()) {
            return;
        }
        specs.put(name, spec.trim());
        log.infoT("log.homepage.register", "记录主页位置: {0} -> {1}", name, spec.trim());
    }

    /** 读取指定主页的位置描述（可能为 null）。 */
    public synchronized String getSpec(String name) {
        return specs.get(name);
    }

    /** 列出所有已注册主页名称（按注册顺序）。 */
    public synchronized List<String> list() {
        return new ArrayList<>(specs.keySet());
    }

    /** 当前主页名称（可能为 null；volatile 保证跨线程可见性）。 */
    public String getCurrentName() {
        return currentName;
    }

    /** 设置当前主页名称（仅供切换/恢复时使用，不触发任何伺服动作）。 */
    public void setCurrentName(String name) {
        this.currentName = name;
    }

    /** 注销指定主页。 */
    public synchronized boolean unregister(String name) {
        return specs.remove(name) != null;
    }

    /** 注销全部主页，返回注销数量。 */
    public synchronized int unregisterAll() {
        int n = specs.size();
        specs.clear();
        currentName = null;
        return n;
    }
}
