
package com.vstory.apperrors.utils.tool;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;


public class ModuleServiceHolder {

    private static volatile XposedService service;

    
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
