package com.minnallab.alexaiconhider;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Runs when alexa_app_in_foreground changes; restores it and re-arms the trigger. */
public final class ResetJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        Hider.apply(this, "trigger");
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
