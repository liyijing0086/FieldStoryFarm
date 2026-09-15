package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.OfflineLog;
import com.fieldstory.farm.model.OfflineSimulationResult;
import com.fieldstory.farm.service.LogService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineLogControllerTest {

    @Test
    void delegatesToLogServiceWithoutWorldLogic() {
        OfflineLog expected = new OfflineLog(30L, List.of());
        LogService stub = result -> Optional.of(expected);
        OfflineLogController controller = new OfflineLogController(stub);

        Optional<OfflineLog> actual = controller.buildLog(
                new OfflineSimulationResult(30L, 30L, 30L, List.of()));

        assertTrue(actual.isPresent());
        assertEquals(expected, actual.orElseThrow());
    }
}
