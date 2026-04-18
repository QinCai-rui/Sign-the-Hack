package xyz.qincai.signthehack.detection;

public enum ScanReason {
    MANUAL("manual"),
    JOIN("join"),
    ANTICHEAT("anticheat");

    private final String key;

    ScanReason(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
