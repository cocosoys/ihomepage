package com.github.cocosoys.mc.ihomepages.action.exec;

import com.github.cocosoys.mc.ihomepages.action.model.ActionContext;
import com.github.cocosoys.mc.ihomepages.action.model.WebActionEffect;

/**
 * 效果执行器 SPI —— 动作效果类型的扩展点。
 *
 * <p>内置实现：{@link CommandEffectExecutor}（type=command）、{@link EconomyEffectExecutor}（type=economy.take / economy.give）。
 * 开发者新增效果类型只需实现本接口并在 {@code WebActionExecutor} 注册，服主即可在 actions.yml 直接使用。</p>
 */
public interface EffectExecutor {

    /** 效果类型标识（与 actions.yml 中 {@code effects[].type} 对应）。 */
    String type();

    /**
     * 执行该效果。
     *
     * @param effect 效果定义（type/command/target/amount）
     * @param ctx    执行上下文
     * @return 执行结果：true=已立即执行；false=已离线入队（待补发）；抛异常=执行失败
     * @throws ActionException 执行失败（含明确原因，供上层聚合与审计）
     */
    boolean execute(WebActionEffect effect, ActionContext ctx) throws ActionException;
}
