package com.github.cocosoys.mc.ihomepages.spring.entity.vo;

/**
 * 配置文件保存结果：文件名 + 热重载提示。
 *
 * <p>字段名 {@code tip} 必须与前端 {@code mixins/configPage.js} 读取的
 * {@code res.data.tip} 对齐，否则保存后的提示弹窗不会出现。</p>
 */
public class HomepagesConfigSaveVo {

    /** 已保存的文件名。 */
    private String file;
    /** 热重载提示。 */
    private String tip;

    public static HomepagesConfigSaveVo of(String file, String tip) {
        HomepagesConfigSaveVo vo = new HomepagesConfigSaveVo();
        vo.file = file;
        vo.tip = tip;
        return vo;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getTip() {
        return tip;
    }

    public void setTip(String tip) {
        this.tip = tip;
    }
}
