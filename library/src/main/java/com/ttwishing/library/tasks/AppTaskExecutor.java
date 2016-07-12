package com.ttwishing.library.tasks;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppTaskExecutor extends ExecutorWithPerfTracking {

    private static AppTaskExecutor sExecutor;

    private AppTaskExecutor(ExecutorService executorService) {
        super(executorService, "app_bg_task");
    }

    public static AppTaskExecutor getInstanse() {
        if (sExecutor == null)

            sExecutor = new AppTaskExecutor(Executors.newCachedThreadPool(new NamedThreadFactory("app-task")));
        return sExecutor;
    }
}