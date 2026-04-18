package xyz.qincai.signthehack.service;

import xyz.qincai.signthehack.detection.ScanReason;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownServiceTest {

    @Test
    void cooldownLifecycleWorks() {
        CooldownService service = new CooldownService();
        UUID uuid = UUID.randomUUID();
        Duration duration = Duration.ofSeconds(30);

        assertFalse(service.isCoolingDown(uuid, ScanReason.MANUAL, duration));
        service.apply(uuid, ScanReason.MANUAL, duration);
        assertTrue(service.isCoolingDown(uuid, ScanReason.MANUAL, duration));
    }
}
