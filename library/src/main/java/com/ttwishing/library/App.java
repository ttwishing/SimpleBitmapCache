package com.ttwishing.library;

import android.app.Application;

/**
 * Created by kurt on 8/13/15.
 */
public abstract class App extends Application {

    private static App sApp;

    public App() {
        sApp = this;
    }

    public static <T extends App> T getInstance() {
        return (T) sApp;
    }

}
