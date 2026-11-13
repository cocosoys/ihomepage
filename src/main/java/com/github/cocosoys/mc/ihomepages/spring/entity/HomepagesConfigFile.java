package com.github.cocosoys.mc.ihomepages.spring.entity;

/**
 * iHomePages ERP「插件配置」菜单可编辑的配置文件清单（数据目录 = {@code plugins/ihomepages/}）。
 *
 * <p><b>数据驱动的唯一事实源</b>：菜单叶子、HTTP 端点、文件读写全部从本枚举派生；
 * 新增一个可编辑文件只需在此加一项 + 加一个前端 .vue，其余链路自动打通。</p>
 *
 * <p>范围（与用户确认）：仅 {@code config.yml}（逻辑开关 / 当前主页 / 主页位置映射）
 * 与 {@code home.yml}（首页展示内容）。以下文件<b>不纳入</b>：
 * <ul>
 *   <li>{@code actions.yml} —— 由专门的 {@code /soyshttp homepage actions reload} 与严格校验器维护，
 *       且含 effects 嵌套列表，通用 Map 编辑器全量写回会丢注释、易破坏结构；</li>
 *   <li>{@code language/*.yml} —— 语言包，由独立语言源管理；</li>
 *   <li>{@code data/*.yml} / {@code sql/init.sql} —— ORM 表结构与 DDL，由宿主 AutoOps 自动运维，
 *       手改会破坏 ensureTable 建表；</li>
 *   <li>{@code dist/} —— 玩家侧手写 HTML 主页，不属于配置。</li>
 * </ul></p>
 *
 * <p>保存后执行 {@code /soyshttp homepage config reload} 热重载；
 * {@code enabled} 开关与新增/删除主页映射需重启服务器才完全生效（见 HomepageConfigReloader）。</p>
 */
public enum HomepagesConfigFile {

    CONFIG("config", "核心配置", "config.yml",
            "功能开关、缓存秒数、当前主页与主页位置映射（homepage.current / homepage.pages）"),

    HOME("home", "首页内容", "home.yml",
            "标题 / 服务器地址 / QQ 群 / 社交链接 / 公告 / 主题 / SEO / 页脚 / 展示开关");

    private final String id;
    private final String name;
    private final String file;
    private final String desc;

    HomepagesConfigFile(String id, String name, String file, String desc) {
        this.id = id;
        this.name = name;
        this.file = file;
        this.desc = desc;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFile() {
        return file;
    }

    public String getDesc() {
        return desc;
    }

    /** 按内部 id 查找；未知或 null 返回 null。 */
    public static HomepagesConfigFile byId(String id) {
        if (id == null) {
            return null;
        }
        for (HomepagesConfigFile f : values()) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }
}
