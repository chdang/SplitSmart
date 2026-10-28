package edu.usc.splitsmart;

import android.app.Application;

/**
 * Runs before any screen does. Its only job is to hand {@link AppServices} the context the real
 * handlers need for the database and the saved session.
 */
public final class SplitSmartApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppServices.init(this);
    }
}
