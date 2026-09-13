package com.fieldstory.farm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 传说资格检查结果（C 模块 品质与传说域，P2）。
 *
 * <p>包装 {@code LegendaryService.checkEligibility} 的结论：
 * 是否满足对应传说的全部突破条件，以及未满足条件的可读描述
 * （供 UI 提示与生命故事引用，规则文档 §七十"故事必须能够追溯到真实游戏记录"）。
 *
 * <p>纯值对象：只保存结论，不含判定逻辑（判定在 LegendaryService）。
 */
public class LegendaryCheck {

    /** 是否满足全部传说突破条件 */
    private final boolean eligible;

    /** 目标传说名（如"金色麦穗"，规则文档 §四十二~四十四） */
    private final String legendaryName;

    /** 未满足条件的描述列表；eligible 时为空列表 */
    private final List<String> unmetConditions;

    /**
     * @param eligible         是否满足全部条件
     * @param legendaryName    目标传说名
     * @param unmetConditions  未满足条件描述（可空，作空列表处理）
     */
    public LegendaryCheck(boolean eligible, String legendaryName, List<String> unmetConditions) {
        this.eligible = eligible;
        this.legendaryName = legendaryName;
        this.unmetConditions = unmetConditions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(unmetConditions));
    }

    public boolean isEligible() {
        return eligible;
    }

    public String getLegendaryName() {
        return legendaryName;
    }

    public List<String> getUnmetConditions() {
        return unmetConditions;
    }
}
