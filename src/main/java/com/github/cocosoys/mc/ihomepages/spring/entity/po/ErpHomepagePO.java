package com.github.cocosoys.mc.ihomepages.spring.entity.po;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * ERP 主页管理持久化对象：围绕「添加主页 / 预览主页」需求，
 * 一条记录代表一个可托管到 SOYSHTTPOverMC {@code web.home} 的页面（按 location 标识位置）。
 *
 * <p>ORM：{@code com.dlz.db} 注解；表 {@code erp_homepage} 由宿主 AutoOps 经
 * {@code DATA.ensureTable} 自动建表（SQL/YAML 双端同构）。</p>
 */
@Data
@TableName("erp_homepage")
public class ErpHomepagePO {

    /** 主键（自增）。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主页名称（前端展示）。 */
    private String name;

    /** 位置标识：对应宿主 pages.yml 的 web.home.<location>，决定页面挂载点。 */
    private String location;

    /** 主页内容：HTML / Markdown / 外链 URL（由宿主前端解析渲染）。 */
    private String content;

    /** 是否启用（默认 true；false 时该主页不参与托管）。 */
    private boolean enabled = true;

    /** 备注。 */
    private String remark;

    /** 创建时间（毫秒时间戳）。 */
    private Long createTime;

    /** 最后更新时间（毫秒时间戳）。 */
    private Long updateTime;
}
