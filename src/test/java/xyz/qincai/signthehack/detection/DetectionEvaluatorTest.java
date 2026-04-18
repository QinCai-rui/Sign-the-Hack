package xyz.qincai.signthehack.detection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DetectionEvaluatorTest {

    private final DetectionEvaluator evaluator = new DetectionEvaluator();

    @Test
    void returnsNotDetectedWhenResponseMissing() {
        CheckDefinition check = new CheckDefinition("xray", "XRay", "xray.config.toggle", DetectionMode.KEYBIND, "\u27e6NO_XRAY\u27e7", List.of("xray"));
        assertEquals(CheckStatus.NOT_DETECTED, evaluator.evaluate(check, "", false));
    }

    @Test
    void returnsNotDetectedWhenMeteorFallbackMatches() {
        CheckDefinition check = new CheckDefinition("meteor", "Meteor", "key.meteor-client.open-gui", DetectionMode.METEOR, "\u27e6NO_METEOR\u27e7", List.of("meteor"));
        assertEquals(CheckStatus.NOT_DETECTED, evaluator.evaluate(check, "\u27e6NO_METEOR\u27e7", false));
    }

    @Test
    void returnsProtectedWhenTranslateKeyMatches() {
        CheckDefinition check = new CheckDefinition("liquidbounce", "LiquidBounce", "liquidbounce.module.killaura.name", DetectionMode.TRANSLATE, "\u27e6NO_LIQUIDBOUNCE\u27e7", List.of("liquidbounce"));
        assertEquals(CheckStatus.PROTECTED, evaluator.evaluate(check, "liquidbounce.module.killaura.name", false));
    }

    @Test
    void returnsProtectedWhenKeybindMatchesAndExploitPreventerIsPresent() {
        CheckDefinition check = new CheckDefinition("freecam", "Freecam", "key.freecam.toggle", DetectionMode.KEYBIND, "\u27e6NO_FREECAM\u27e7", List.of("freecam"));
        assertEquals(CheckStatus.PROTECTED, evaluator.evaluate(check, "key.freecam.toggle", true));
    }

    @Test
    void returnsDetectedWhenKeybindDoesNotMatch() {
        CheckDefinition check = new CheckDefinition("freecam", "Freecam", "key.freecam.toggle", DetectionMode.KEYBIND, "\u27e6NO_FREECAM\u27e7", List.of("freecam"));
        assertEquals(CheckStatus.DETECTED, evaluator.evaluate(check, "options.key.jump", false));
    }
}
