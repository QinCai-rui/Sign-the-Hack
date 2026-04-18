package xyz.qincai.signthehack.detection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DetectionEvaluatorTest {

    private final DetectionEvaluator evaluator = new DetectionEvaluator();

    @Test
    void returnsProtectedWhenResponseMissing() {
        CheckDefinition check = new CheckDefinition("xray", "XRay", "xray.config.toggle", DetectionMode.KEYBIND, List.of("xray"));
        assertEquals(CheckStatus.PROTECTED, evaluator.evaluate(check, ""));
    }

    @Test
    void returnsDetectedWhenSignatureMatches() {
        CheckDefinition check = new CheckDefinition("meteor", "Meteor", "key.meteor-client.open-gui", DetectionMode.METEOR, List.of("meteor"));
        assertEquals(CheckStatus.DETECTED, evaluator.evaluate(check, "Meteor Client"));
    }

    @Test
    void returnsNotDetectedWhenNoSignatureMatch() {
        CheckDefinition check = new CheckDefinition("freecam", "Freecam", "key.freecam.toggle", DetectionMode.KEYBIND, List.of("freecam"));
        assertEquals(CheckStatus.NOT_DETECTED, evaluator.evaluate(check, "options.key.jump"));
    }
}
