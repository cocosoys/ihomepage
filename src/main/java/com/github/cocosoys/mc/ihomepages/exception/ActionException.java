package com.github.cocosoys.mc.ihomepages.action.exec;

/**
 * 动作效果执行失败异常：携带面向玩家的明确原因，由 WebActionExecutor 聚合为 ActionResult。
 */
public class ActionException extends Exception {

    /** 拒绝码（可选）：not-found / insufficient-funds / rejected-offline / invalid 等。 */
    private final String code;

    public ActionException(String message) {
        super(message);
        this.code = null;
    }

    public ActionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
