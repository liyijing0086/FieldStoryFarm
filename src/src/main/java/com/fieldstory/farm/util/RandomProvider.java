package com.fieldstory.farm.util;

import java.util.Random;

/**
 * 统一随机源（D 模块 P0：世界环境）。
 *
 * <p>依据《D模块 P0 接口与类设计文档》§四、《P0-P4功能实现与验收规范》§7。
 *
 * <p>约束：任何 Service 不得自行 {@code new Random()}；随机必须经本类，以便固定 seed
 * 进行测试与离线复现。P0 阶段无随机业务需求，本类仅在单元测试中调用。
 */
public final class RandomProvider {

    private static final Random RANDOM = new Random();

    private RandomProvider() {
        // 静态工具类禁止实例化
    }

    /**
     * 返回 [0, bound) 的随机整数。
     *
     * @param bound 上界（不含），必须为正
     * @return 随机整数
     */
    public static int nextInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    /**
     * 返回 [0.0, 1.0) 的随机浮点数。
     *
     * @return 随机浮点数
     */
    public static double nextDouble() {
        return RANDOM.nextDouble();
    }

    /**
     * 返回随机布尔值。
     *
     * @return 随机布尔值
     */
    public static boolean nextBoolean() {
        return RANDOM.nextBoolean();
    }

    /**
     * 设置随机种子（测试用）。
     *
     * <p>溯源：验收规范 §143 属 <b>P4</b> 阶段（固定随机测试），非 P0 需求；
     * P0 阶段仅预置该入口，供后续阶段与单元测试使用。
     *
     * @param seed 随机种子
     */
    public static void setSeed(long seed) {
        RANDOM.setSeed(seed);
    }
}
