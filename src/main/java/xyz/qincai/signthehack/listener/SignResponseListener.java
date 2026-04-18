package xyz.qincai.signthehack.listener;

import xyz.qincai.signthehack.service.ScanService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.util.ArrayList;
import java.util.List;

public final class SignResponseListener implements Listener {
    private final ScanService scanService;

    public SignResponseListener(ScanService scanService) {
        this.scanService = scanService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSignChange(SignChangeEvent event) {
        if (!scanService.isChecking(event.getPlayer().getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Component line = event.line(i);
            lines.add(line != null ? PlainTextComponentSerializer.plainText().serialize(line) : "");
        }
        scanService.handleSignResponse(event.getPlayer(), lines);
    }
}
