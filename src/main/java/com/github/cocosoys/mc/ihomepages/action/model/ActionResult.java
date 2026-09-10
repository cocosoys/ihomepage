package com.github.cocosoys.mc.ihomepages.action.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动作执行结果（门面返回值，也是 HTTP 响应体）。
 *
 * <p>{@code mode} 说明：</p>
 * <ul>
 *   <li>{@code executed} —— 全部效果已立即执行；</li>
 *   <li>{@code queued} —— 至少一个效果离线入队（如给物品），其余已立即执行；</li>
 *   <li>{@code rejected} —— 因离线策略/前置校验被整体拒绝；</li>
 *   <li>{@code failed} —— 执行中途异常（部分效果可能已生效，需审计）。</li>
 * </ul>
 */
public class ActionResult {

    private final boolean success;
    private final String mode;
    private final String message;
    private final Map<String, Object> data;

    private ActionResult(boolean success, String mode, String message, Map<String, Object> data) {
        this.success = success;
        this.mode = mode;
        this.message = message;
        this.data = data;
    }

    public static ActionResult success(String mode, String message) {
        return new ActionResult(true, mode, message, new LinkedHashMap<String, Object>());
    }

    public static ActionResult success(String mode, String message, Map<String, Object> data) {
        return new ActionResult(true, mode, message, data);
    }

    public static ActionResult reject(String mode, String message) {
        return new ActionResult(false, mode, message, new LinkedHashMap<String, Object>());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMode() {
        return mode;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
