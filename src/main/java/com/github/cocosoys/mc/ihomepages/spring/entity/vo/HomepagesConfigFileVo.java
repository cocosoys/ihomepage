package com.github.cocosoys.mc.ihomepages.spring.entity.vo;

/**
 * 配置文件清单行：id / 名称 / 物理文件名 / 描述。
 * 用于 {@code GET /erp/config/files} 响应。
 */
public class HomepagesConfigFileVo {

    /** 配置文件内部 ID（枚举 id，前端 fileId）。 */
    private String id;
    /** 显示名称。 */
    private String name;
    /** 实际文件名（相对 owner 插件数据目录）。 */
    private String file;
    /** 描述。 */
    private String desc;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
