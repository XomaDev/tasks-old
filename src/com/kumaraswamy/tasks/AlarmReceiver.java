package com.kumaraswamy.tasks;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive: Received alarm");
//        BootReceiver.showNotification(context, "Alarm Receiver", "Intent received!");
        int id = intent.getIntExtra("id", 0);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt("JOB_ID", id);
        bundle.putBoolean("PERIODIC", false);
        bundle.putStringArray("FLAGS", new String[0]);

        ComponentName serviceName = new ComponentName(context, ActivityService.class);

        JobInfo.Builder myJobInfo = new JobInfo.Builder(777, serviceName)
                .setExtras(bundle)
                .setBackoffCriteria(1, 0)
                .setMinimumLatency(100)
                .setOverrideDeadline(200);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        int resultCode = jobScheduler.schedule(myJobInfo.build());
        boolean success = (resultCode == JobScheduler.RESULT_SUCCESS);
        Log.i(TAG, "onReceive: " + success);
    }
}
