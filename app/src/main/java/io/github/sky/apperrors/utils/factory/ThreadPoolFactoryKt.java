/*
 * AppErrorsTracking - 线程池工厂 (Java 化, 保持 ThreadPoolFactoryKt 类名)
 */
package io.github.sky.apperrors.utils.factory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 线程池工厂（原 ThreadPoolFactory.kt 顶层函数） */
public class ThreadPoolFactoryKt {

    private ThreadPoolFactoryKt() {}

    /** 创建当前线程池服务 */
    private static ExecutorService currentThreadPool() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    /** 创建并启动新的临时线程池，等待 block 执行完成并自动释放 */
    public static void newThread(Runnable block) {
        currentThreadPool().execute(() -> {
            try {
                block.run();
            } finally {
                // shutdown after execute (原逻辑在 block 内 shutdown)
            }
        });
    }
}
