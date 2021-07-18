package com.kumaraswamy.tasks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.util.Log;

import static com.kumaraswamy.tasks.Utils.exists;


public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "StartActivityOnBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive: Received intent");
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
//            showNotification(context, "boot!", "just booted!");

            if (!exists(777, context)) {
                return;
            }


            PersistableBundle bundle = new PersistableBundle();
            bundle.putInt("JOB_ID", 777);
            bundle.putBoolean("PERIODIC", false);
            bundle.putStringArray("FLAGS", new String[0]);

            ComponentName serviceName = new ComponentName(context, ActivityService.class);

            JobInfo.Builder myJobInfo = new JobInfo.Builder(777, serviceName)
                    .setExtras(bundle)
                    .setBackoffCriteria(1, 0)
                    .setMinimumLatency(4000)
                    .setOverrideDeadline(4100);
            // TODO LOOK INTO THIS


            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

            int resultCode = jobScheduler.schedule(myJobInfo.build());
            boolean success = (resultCode == JobScheduler.RESULT_SUCCESS);
            Log.i(TAG, "onReceive: " + success);
        }
    }

    public static void showNotification(Context context, String title, String body) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = 1;
        String channelId = "channel-01";
        String channelName = "Channel Name";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    channelId, channelName, importance);
            notificationManager.createNotificationChannel(mChannel);
        }

        Notification.Builder mBuilder = new Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body);

//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
//        stackBuilder.addNextIntent(intent);
//        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
//                0,
//                PendingIntent.FLAG_UPDATE_CURRENT
//        );
//        mBuilder.setContentIntent(resultPendingIntent);

        notificationManager.notify(notificationId, mBuilder.build());
    }
}