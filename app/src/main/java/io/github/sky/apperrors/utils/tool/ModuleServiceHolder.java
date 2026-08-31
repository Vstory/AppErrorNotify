/*
 * AppErrorsTracking (api102 重构版) - XposedService 全局持有器 (Java 化)
 */
package io.github.sky.apperrors.utils.tool;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;

/** XposedService 全局持有器（替代 YukiHookAPI.Status） */
public class ModuleServiceHolder {

    private static volatile XposedService service;

    /** 模块是否已连接框架（= LSPosed 激活 + 作用域已勾选） */
    public static boolean isActive() {
        return service != null;
    }

    public static XposedService getService() {
        return service;
    }

    private static final Set<ServiceStateListener> listeners = new CopyOnWriteArraySet<>();

    public interface ServiceStateListener {
        void onServiceStateChanged(XposedService service);
    }

    public static void addServiceStateListener(ServiceStateListener listener, boolean notifyImmediately) {
        listeners.add(listener);
        if (notifyImmediately) listener.onServiceStateChanged(service);
    }

    public static void removeServiceStateListener(ServiceStateListener listener) {
        listeners.remove(listener);
    }

    public static void onServiceBind(XposedService service) {
        ModuleServiceHolder.service = service;
        for (ServiceStateListener l : listeners) l.onServiceStateChanged(service);
    }

    public static void onServiceDied(XposedService service) {
        ModuleServiceHolder.service = null;
        for (ServiceStateListener l : listeners) l.onServiceStateChanged(null);
    }

    private ModuleServiceHolder() {}
}
