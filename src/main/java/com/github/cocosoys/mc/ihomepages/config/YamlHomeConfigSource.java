package com.github.cocosoys.mc.ihomepages.config;


import org.bukkit.plugin.java.JavaPlugin;

/**
 * 内容配置源实现：读取 {@code home.yml} → {@link HomeConfigEntity}。
 */
public class YamlHomeConfigSource extends AbstractConfigSource implements IHomeConfigSource {

    /** 会被 HTTP 线程（controller）与 reload/onEnable 并发访问，volatile 保证可见性。 */
    private volatile HomeConfigEntity entity;

    public YamlHomeConfigSource(JavaPlugin plugin) {
        super(plugin, "home.yml", "home.yml");
    }

    @Override
    public void load() {
        entity = new HomeConfigEntity(loadConfig());
    }

    @Override
    public void refresh() {
        entity = new HomeConfigEntity(loadConfig());
    }

    @Override
    public HomeConfigEntity get() {
        HomeConfigEntity e = entity;
        if (e == null) {
            synchronized (this) {
                e = entity;
                if (e == null) {
                    entity = e = new HomeConfigEntity(loadConfig());
                }
            }
        }
        return e;
    }
}
