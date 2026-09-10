package com.github.cocosoys.mc.ihomepages.action.exec;

/**
 * 命令类别 —— 「网页命令及时执行」的第一性分类：
 * 决定一条命令在玩家离线时能否立即执行。
 *
 * <ul>
 *   <li>{@link #CONSOLE} —— 操作服务器本身（broadcast/time/weather 等），与玩家实体无关，任何时刻可执行；</li>
 *   <li>{@link #ACCOUNT} —— 操作玩家「账号数据」（经济 eco / 权限 lp / 点券等，按用户名读写数据库），
 *       离线也可立即执行；</li>
 *   <li>{@link #ENTITY} —— 操作「在线实体」（背包/位置/效果/生命，如 give/clear/tp/effect），
 *       离线必失败，需按动作 offline 策略（run/queue/reject）处理。</li>
 * </ul>
 */
public enum CommandClass {
    CONSOLE,
    ACCOUNT,
    ENTITY
}
