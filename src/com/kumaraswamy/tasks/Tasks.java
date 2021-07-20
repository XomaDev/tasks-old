package com.kumaraswamy.tasks;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.*;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.AsynchUtil;
import com.google.appinventor.components.runtime.util.YailList;
import com.kumaraswamy.tasks.reflect.ComponentManager;

import java.util.*;
import java.util.concurrent.*;

public class Tasks extends AndroidNonvisibleComponent {

    private int processTaskId = 0;
//    private final String LOG_TAG = "BackgroundTasks";
    private static final String TAG = "Tasks";

    public static final String FLAG_IMPORTANT_FOREGROUND = "FOREGROUND_IMPORTANT";
    public static final String FLAG_ACTIVITY_NO_KILL = "ACTIVITY_NO_KILL";
    public static final String FLAG_IGNORE_FIRST_PERIODIC_RUN = "IGNORE_FIRST_PERIODIC_RUN";

    private boolean isFlagsNull = false;

    private boolean foreground = false;
    private boolean restartAfterKill = false;

    private final HashMap<Integer, Object[]> pendingTasks = new HashMap<>();
    private final HashMap<String, String> componentsList = new HashMap<>();
    private final ArrayList<String> tasksProcessList = new ArrayList<>();
    private final HashMap<String, Object[]> extraFunctions = new HashMap<>();

    private List<String> serviceFlags = null;
    private Object[] extraForeground;
    private TinyDB tinyDB;

    private final Activity activity;
    private JobScheduler jobScheduler;
    private AlarmManager alarmManager;

    public Tasks(ComponentContainer container) {
        super(container.$form());
        activity = container.$context();

        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
            Log.d(TAG, "This Android version is not supported!");
            return;
        }

        ResetTaskList();
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        jobScheduler = (JobScheduler) activity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);

        tinyDB = new TinyDB(container);
        tinyDB.Namespace("TasksInfo11");
    }

    @SimpleFunction(description = "Clears the task lists but do not remove any executing/pending tasks.")
    public void ResetTaskList() {
//        serviceFlags = null;

        foreground = false;
        restartAfterKill = false;
        processTaskId = 0;

        extraForeground = new Object[]{"Foreground service", "Running in the background.", "Tasks", ""};
        pendingTasks.clear();
        componentsList.clear();
        extraFunctions.clear();
        tasksProcessList.clear();
    }

    @SimpleFunction(description = "It's good to use this block when the screen initializes to prevent causing issues "
            + "while starting the service in the background especially on Xiaomi and other devices.")
    public void ResolveActivity() {
        for (int i = 0; i < Constants.intentsPackages.length; i++) {
            final ComponentName component = new ComponentName(Constants.intentsPackages[i], Constants.intentSources[i]);
            final Intent intent = new Intent().setComponent(component);

            if (activity.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                activity.startActivity(intent);
                break;
            }
        }
    }

    private int periodic = 0;

    @SimpleProperty
    public void Periodic(int value) {
        periodic = value;
    }

    @SimpleProperty
    public int Periodic() {
        return periodic;
    }

    @DesignerProperty(defaultValue = "False", editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN)
    @SimpleProperty(description = "Set true if the Job service should restart after being killed.")
    public void RestartAfterKill(boolean bool) {
        restartAfterKill = bool;
    }

    @SimpleProperty(description = "Gets the value for RestartAfterKill.")
    public boolean RestartAfterKill() {
        return restartAfterKill;
    }

    @SimpleFunction(description = "Creates a component. No matter if the app is running in the background or the " +
            "foreground. All you need to do is to specify the component source name and the name which will be used " + "to invoke functions and do other stuff.")
    public void CreateComponent(final Object source, final String name) {
        componentsList.put(name, ComponentManager.getSourceString(source));
    }

    @SimpleFunction(description = "Create a list of components with their id")
    public void CreateComponents(final YailList sources, final YailList names) {
        final Object[] componentsSourceArray = sources.toArray();
        final String[] componentsNameArray = names.toStringArray();

        if (componentsNameArray.length != componentsSourceArray.length) {
            throw new YailRuntimeError("Invalid list input provided!", TAG);
        }

        for (int i = 0; i < componentsSourceArray.length; i++) {
            CreateComponent(componentsSourceArray[i], componentsNameArray[i]);
        }
    }

    @SimpleFunction(description =
            "Creates a function of the component ID specified. Specify the component ID and " + "the" + " values. To "
                    + "access the invoked result use the 'invoke:result' value.")
    public void CreateFunction(String id, String name, String functionName, YailList values) {
        tasksProcessList.add(processTaskId + Constants.ID_SEPARATOR + Constants.TASK_CREATE_FUNCTION);
        pendingTasks.put(processTaskId, Utils.toObjectArray(name, functionName, values, id));
        processTaskId++;
    }

    @SimpleFunction(description = "Calls the created function")
    public void CallFunction(String id) {
        tasksProcessList.add(processTaskId + Constants.ID_SEPARATOR + Constants.TASK_INVOKE_FUNCTION);
        pendingTasks.put(processTaskId, Utils.toObjectArray(id));
        processTaskId++;
    }

    @SimpleFunction(description = "Register for component's events.")
    public void RegisterEvent(String name, String functionId, String eventName) {
        tasksProcessList.add(processTaskId + Constants.ID_SEPARATOR + Constants.TASK_REGISTER_EVENT);
        pendingTasks.put(processTaskId, Utils.toObjectArray(name, functionId, eventName));
        processTaskId++;
    }

    @SimpleFunction(description = "Helps you call the created function multiple times")
    public void ExecuteFunction(String id, int times, int interval) {
        tasksProcessList.add(processTaskId + Constants.ID_SEPARATOR + Constants.TASK_EXECUTE_FUNCTION);
        pendingTasks.put(processTaskId, Utils.toObjectArray(id, times, interval));
        processTaskId++;
    }

    @SimpleFunction(description =
            "Create a variable with the given variable name which can be accessed by " + "[VAR" + ":<NAME>]. For " +
                    "example \"[VAR:Data]\". Use the extra value block and use the value to access the " + "variable.")
    public void CreateVariable(String name, Object value) {
        tasksProcessList.add(processTaskId + Constants.ID_SEPARATOR + Constants.TASK_CREATE_VARIABLE);
        pendingTasks.put(processTaskId, Utils.toObjectArray(name, value));
        processTaskId++;
    }

    @SimpleFunction(description = "Does a delay in the background. You can use it as intervals between function.")
    public void MakeDelay(long millis) {
        tasksProcessList.add(processTaskId + Constants.ID_SEPARATOR + Constants.TASK_DELAY);
        pendingTasks.put(processTaskId, Utils.toObjectArray(millis));
        processTaskId++;
    }

    // Deprecated method

    @SimpleProperty(description = "Should the extension create components on UI thread. This is deprecated method!")
    public void CreateComponentsOnUi(boolean value) {

    }

    // Deprecated method

    @SimpleProperty
    public boolean CreateComponentsOnUi() {
        return false;
    }


    @SimpleProperty
    public void Flags(final YailList flagList) {
        serviceFlags = Arrays.asList(flagList.toStringArray());
    }

    @SimpleProperty
    public YailList Flags() {
        return YailList.makeList(serviceFlags);
    }

    @SimpleFunction(description = "Executes the code and returns the result. You can use it to perform actions using "
            + "Java code, calculate sums and return a value. If the value is null or empty, an empty string or text " + "is " + "returned.")
    public Object Interpret(final String code) {
        return Utils.interpret(code, activity);
    }

    @SimpleFunction(description = "Make the value from code that will be executed at the background service")
    public Object MakeExtra(String text, boolean code) {
        return new String[]{text, String.valueOf(code)};
    }

    @SimpleFunction(description = "Destroys a component and its events.")
    public void DestroyComponent(final String name, long time) {
        tasksProcessList.add(processTaskId + Constants.ID_SEPARATOR + Constants.TASK_DESTROY_COMPONENT);
        pendingTasks.put(processTaskId, Utils.toObjectArray(name, time));
        processTaskId++;
    }

    @SimpleFunction(description = "Starts the service. The app must be alive to call this block or put it in the " +
            "onDestroy or onPause event. This block only helps if the app is compiled and not the companion.")
    public boolean Start(final int id, final long latency, final String requiredNetwork, final boolean foreground) {
        final String packageName = activity.getPackageName();

        if (Arrays.asList(Constants.companionPackages).contains(packageName)) {
            showToast("This extension does not work in the Companion!");
            return false;
        }

        if (componentsList.size() == 0) {
            return false;
        }

        this.foreground = foreground;

        try {
            Log.d(TAG, "Task created");
            return startTask(latency, id, requiredNetwork);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
        return false;
    }

    @SimpleFunction(description = "Sets the title, subtitle and icon for the foreground service. If the icon is " +
            "empty, the app default icon will be set.")
    public void ConfigureForeground(String title, String content, String subtext, String icon) {
        extraForeground = new Object[]{title, content, subtext, icon};
    }

    private void saveTask(int id) {
        final ArrayList<Object> objectArrayList = prepareList(tasksProcessList, pendingTasks, false, componentsList,
                null, foreground, extraForeground, extraFunctions, restartAfterKill, serviceFlags);

        Utils.saveTask(activity, objectArrayList, id);
    }

    private boolean startTask(long time, int id, String requiredNetwork) {
        saveTask(id);
        if(id == 777) {
            return true;
        }

        Log.d(TAG, "Received activity id: " + id);
        isFlagsNull = (serviceFlags == null);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt("JOB_ID", id);
        bundle.putBoolean("PERIODIC", periodic != 0);
        bundle.putStringArray("FLAGS", isFlagsNull ? null : serviceFlags.toArray(new String[0]));

        ComponentName serviceName = new ComponentName(activity, ActivityService.class);

        JobInfo.Builder myJobInfo = new JobInfo.Builder(id, serviceName)
                        .setExtras(bundle).setPersisted(true)
                        .setBackoffCriteria(1, 0);

        // FIXME fix this error when called with minimum latency
        // TODO LOOK INTO THIS

        if (time == 0 && hasFlag(FLAG_IMPORTANT_FOREGROUND)) {
            myJobInfo.setImportantWhileForeground(true);
        } else if (periodic == 0) {
            myJobInfo.setMinimumLatency(time);
        }

        if (periodic != 0) {
            showToast("set periodic");
            myJobInfo.setPeriodic(periodic);
        } else {
            myJobInfo.setOverrideDeadline(time + 10);
        }

        if (!requiredNetwork.isEmpty()) {
            int network_type = Utils.getNetworkInt(requiredNetwork);
            Log.d("Tasks", "startTask: " + network_type);
            myJobInfo.setRequiredNetworkType(network_type);
        }

        int resultCode = jobScheduler.schedule(myJobInfo.build());
        boolean success = (resultCode == JobScheduler.RESULT_SUCCESS);
        if (success) {
            ResetTaskList();
        }
        Log.d(TAG, "Condition: " + success);
        return success;
    }

    public boolean hasFlag(final String flag) {
        return (isFlagsNull || serviceFlags.contains(flag));
    }

    private ArrayList<Object> prepareList(Object... objects) {
        return new ArrayList<>(Arrays.asList(objects));
    }

    @SimpleFunction(description = "Flags the Android system that the task is over. This would help save app " +
            "resources. Call this block when you're done with all you're tasks.")
    public void FinishTask(boolean reschedule) {
        tasksProcessList.add(processTaskId + Constants.ID_SEPARATOR + Constants.TASK_FINISH);
        pendingTasks.put(processTaskId, Utils.toObjectArray(reschedule));
        processTaskId++;
    }

    @SimpleFunction(description = "Stops the given service ID. The service will not be executed.")
    public void CancelTask(int id) {
        try {
            jobScheduler.cancel(id);
            tinyDB.ClearTag(id + "");
            Log.d(TAG, "Cancel status: " + Utils.clearTask(id, activity));
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }

    @SimpleFunction(description = "Gets the pending task IDs")
    public YailList PendingServices() {
        ArrayList<Integer> tasksIds = new ArrayList<>();

        for (JobInfo info : jobScheduler.getAllPendingJobs()) {
            tasksIds.add(info.getId());
        }

        return YailList.makeList(tasksIds);
    }

    @SimpleFunction
    public void Alarm(Calendar time, int id) {
        saveTask(id);

        Intent intent = new Intent(activity, AlarmReceiver.class);
        intent.putExtra("id", id);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, 1, intent, 0);


        if (time.before(Calendar.getInstance())) {
            time.add(Calendar.DATE, 1);
        }
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), pendingIntent);
        ResetTaskList();
    }

    @SimpleFunction
    public void RepeatingAlarm(Calendar time, long interval, int id) {
        saveTask(id);

        Intent intent = new Intent(activity, AlarmReceiver.class);
        intent.putExtra("id", id);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, 1, intent, 0);
        if (time.before(Calendar.getInstance())) {
            time.add(Calendar.DATE, 1);
        }

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), interval, pendingIntent);
        ResetTaskList();
    }

    @SimpleFunction
    public void CancelAlarm() {
        Intent intent = new Intent(activity, AlarmManager.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, 1, intent, 0);
        alarmManager.cancel(pendingIntent);
    }

    @SimpleFunction(description = "Make a functions that can compare things and pass it to a function if the " +
            "condition is true.")
    public void ExtraFunction(final String id, final YailList codes) {
        extraFunctions.put(id, codes.toArray());
    }

    @SimpleFunction(description = "Cancels and stops all the tasks.")
    public void CancelAllTasks() {
        jobScheduler.cancelAll();
        tinyDB.ClearAll();
    }

    private void showToast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
