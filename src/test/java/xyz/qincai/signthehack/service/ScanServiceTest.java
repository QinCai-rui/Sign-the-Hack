package xyz.qincai.signthehack.service;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanServiceTest {

    private final ScanService scanService = new ScanService(null, null, null, null, null);

    @Test
    void exemptPermissionIsCheckedExplicitly() {
        Player exemptPlayer = playerWithPermission(true);
        Player normalPlayer = playerWithPermission(false);

        assertTrue(scanService.isExempt(exemptPlayer));
        assertFalse(scanService.isExempt(normalPlayer));
    }

    private Player playerWithPermission(boolean allowed) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("hasPermission") && args != null && args.length == 1 && args[0] instanceof String permission) {
                return allowed && ScanService.EXEMPT_PERMISSION.equals(permission);
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(boolean.class)) {
                return false;
            }
            if (returnType.equals(int.class)) {
                return 0;
            }
            if (returnType.equals(long.class)) {
                return 0L;
            }
            if (returnType.equals(double.class)) {
                return 0.0d;
            }
            return null;
        };
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, handler);
    }
}