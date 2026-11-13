package com.github.cocosoys.mc.ihomepages.spring.impl;

import com.github.cocosoys.mc.ihomepages.spring.entity.po.ErpHomepagePO;
import com.github.cocosoys.mc.ihomepages.spring.service.IErpHomepageService;
import com.github.cocosoys.mc.soyshttpovermc.orm.DATA;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * ERP 主页管理业务实现：直接调用宿主 {@code DATA} ORM 门面（同 WebActionManager）。
 */
public class ErpHomepageServiceImpl implements IErpHomepageService {

    @SuppressWarnings("unused")
    private final JavaPlugin plugin;

    public ErpHomepageServiceImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        DATA.ensureTable(ErpHomepagePO.class);
    }

    @Override
    public List<ErpHomepagePO> list() {
        return DATA.select(ErpHomepagePO.class);
    }

    @Override
    public ErpHomepagePO get(Long id) {
        if (id == null) {
            return null;
        }
        return DATA.get(ErpHomepagePO.class, id);
    }

    @Override
    public boolean save(ErpHomepagePO po) {
        if (po == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (po.getId() == null) {
            po.setCreateTime(now);
            po.setUpdateTime(now);
            return DATA.insert(po);
        }
        po.setUpdateTime(now);
        return DATA.updateById(po);
    }

    @Override
    public boolean remove(Long id) {
        if (id == null) {
            return false;
        }
        return DATA.deleteById(ErpHomepagePO.class, id);
    }

    @Override
    public long count() {
        return DATA.count(ErpHomepagePO.class);
    }
}
