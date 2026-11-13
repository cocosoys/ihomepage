package com.github.cocosoys.mc.ihomepages.spring.service;

import com.github.cocosoys.mc.ihomepages.spring.entity.po.ErpHomepagePO;

import java.util.List;

/**
 * ERP 主页管理业务接口（围绕「添加主页 / 预览主页」）。
 */
public interface IErpHomepageService {

    /** 主页列表。 */
    List<ErpHomepagePO> list();

    /** 按主键获取主页（不存在返回 null）。 */
    ErpHomepagePO get(Long id);

    /** 保存主页：id 为空则新增，否则按 id 更新。 */
    boolean save(ErpHomepagePO po);

    /** 按主键删除主页。 */
    boolean remove(Long id);

    /** 主页总数。 */
    long count();
}
