
package io.github.sky.apperrors.utils.factory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ThreadPoolFactoryKt {

    private ThreadPoolFactoryKt() {}

    
    private static ExecutorService currentThreadPool() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    
    public static void newThread(Runnable block) {
        currentThreadPool().execute(() -> {
            try {
                block.run();
            } finally {
                
            }
        });
    }
}
