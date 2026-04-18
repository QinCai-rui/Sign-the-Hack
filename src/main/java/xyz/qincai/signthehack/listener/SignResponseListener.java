package xyz.qincai.signthehack.listener;

import xyz.qincai.signthehack.service.ScanService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.util.ArrayList;
import java.util.List;

public final class SignResponseListener implements Listener {
    private final ScanService scanService;

    public SignResponseListener(ScanService scanService) {
        this.scanService = scanService;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            lines.add(event.getLine(i));
        }
        scanService.handleSignResponse(event.getPlayer(), event.getBlock().getLocation(), lines);
    }
}
