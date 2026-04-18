package xyz.qincai.signthehack.detection;

public final class DetectionEvaluator {

    public CheckStatus evaluate(CheckDefinition check, String clientResponse, boolean exploitPreventer) {
        String response = clientResponse == null ? "" : clientResponse.strip();
        if (response.isEmpty()) {
            return CheckStatus.NOT_DETECTED;
        }

        return switch (check.mode()) {
            case METEOR -> evaluateMeteor(check, response);
            case TRANSLATE -> evaluateTranslate(check, response);
            case KEYBIND -> evaluateKeybind(check, response, exploitPreventer);
        };
    }

    private CheckStatus evaluateMeteor(CheckDefinition check, String response) {
        if (response.equalsIgnoreCase(check.key())) {
            return CheckStatus.DETECTED;
        }
        if (startsWithIgnoreCase(response, check.fallback())) {
            return CheckStatus.NOT_DETECTED;
        }
        return CheckStatus.DETECTED;
    }

    private CheckStatus evaluateTranslate(CheckDefinition check, String response) {
        if (startsWithIgnoreCase(response, check.fallback())) {
            return CheckStatus.NOT_DETECTED;
        }
        if (response.equalsIgnoreCase(check.key())) {
            return CheckStatus.PROTECTED;
        }
        return CheckStatus.DETECTED;
    }

    private CheckStatus evaluateKeybind(CheckDefinition check, String response, boolean exploitPreventer) {
        if (exploitPreventer && response.equalsIgnoreCase(check.key())) {
            return CheckStatus.PROTECTED;
        }
        if (response.equalsIgnoreCase(check.key())) {
            return CheckStatus.NOT_DETECTED;
        }
        return CheckStatus.DETECTED;
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
