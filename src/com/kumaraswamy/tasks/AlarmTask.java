package com.kumaraswamy.tasks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmTask extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
//        Log.d("BackgroundTasks.alaramtask", "onReceive: Alarm task received");
//
//        int jobId = intent.getIntExtra("id", 0);
//        String network = intent.getStringExtra("network");
//
//        PersistableBundle bundle = new PersistableBundle();
//        bundle.putInt("JOB_ID", jobId);
//
//        ComponentName serviceName = new ComponentName(context, ActivityService.class);
//
//        JobInfo.Builder myJobInfo = new JobInfo.Builder(jobId, serviceName)
//                .setExtras(bundle)
//                .setOverrideDeadline(0)
//                .setBackoffCriteria(1, 0);
//
//        if(!network.isEmpty()) {
//            int network_type = getNetworkInt(network);
//            Log.d("Tasks", "startTask: " + network_type);
//            myJobInfo.setRequiredNetworkType(network_type);
//        }

//        int resultCode = ((JobScheduler)  context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(myJobInfo.build());
//        boolean success = (resultCode == JobScheduler.RESULT_SUCCESS);
//        Log.d("BackgroundTasks.alaramtask", "Condition: " + success);

        Log.i("BackgroundTasks.alaramtask", "onReceive: Alarm task just started up");
    }
}
