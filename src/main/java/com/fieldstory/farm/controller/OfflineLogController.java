package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.OfflineLog;
import com.fieldstory.farm.model.OfflineSimulationResult;
import com.fieldstory.farm.service.LogService;

import java.util.Objects;
import java.util.Optional;

/** B 模块 P2 离线日志 Controller。 */
public final class OfflineLogController {

    private final LogService logService;

    public OfflineLogController(LogService logService) {
        this.logService = Objects.requireNonNull(logService, "logService");
    }

    public Optional<OfflineLog> buildLog(OfflineSimulationResult result) {
        return logService.buildOfflineLog(result);
    }
}
