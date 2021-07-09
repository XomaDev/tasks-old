package com.kumaraswamy.tasks;

import android.app.Activity;
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
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.YailList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class Tasks extends AndroidNonvisibleComponent {

  private int taskID = 0;
  private final String LOG_TAG = "BackgroundTasks";

  protected static boolean createComponentsOnUi = false;
  protected static boolean foreground = false;
  protected static boolean restartAfterKill = false;

  protected static HashMap<Integer, Object[]> pendingTasks = new HashMap<>();
  private static HashMap<String, String> componentsList = new HashMap<>();
  protected static ArrayList<String> tasksID = new ArrayList<>();
  protected static HashMap<String, String> statusFunctions = new HashMap<>();
  private static HashMap<String, Object[]> extraFunctions = new HashMap<>();

  private static Object[] foregroundConfig;

  protected static Activity activity;
  protected static ComponentContainer componentContainer;

  public static JobScheduler jobScheduler;

  @SimpleFunction(description = "Clears the task lists but do not remove any executing/pending tasks.")
  public void ResetTaskList() {
    taskID = 0;
    createComponentsOnUi = false;
    foreground = false;
    restartAfterKill = false;

    pendingTasks = new HashMap<>();
    componentsList = new HashMap<>();
    tasksID = new ArrayList<>();
    statusFunctions = new HashMap<>();
    extraFunctions = new HashMap<>();

    foregroundConfig = new Object[] {"Foreground service", "The task is running", "Running!", ""};
  }

  public Tasks(ComponentContainer container) {
    super(container.$form());
    componentContainer = container;
    activity = container.$context();

    if(!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
      Log.d(LOG_TAG, "This android version is not supported");
      return;
    }

    ResetTaskList();

    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    jobScheduler = (JobScheduler) activity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
  }

  @SimpleFunction(description = "It's good to use this block when the screen initializes to prevent causing issues while starting the service in the background especially on Xiaomi and other devices.")
  public void ResolveActivity() {
    for (int i = 0; i < Constants.intentsPackages.length; i++) {
      ComponentName component = new ComponentName(Constants.intentsPackages[i],
              Constants.intentSources[i]);
      Intent intent = new Intent().setComponent(component);

      if (activity.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY) != null) {
        activity.startActivity(intent);
        break;
      }
    }
  }

  @DesignerProperty(
  defaultValue = "False",
  editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN)
  @SimpleProperty(description = "Set true if the Job service should restart after being killed.")
  public void RestartAfterKill(boolean bool) {
    restartAfterKill = bool;
  }

  @SimpleProperty(description = "Gets the value for RestartAfterKill.")
  public boolean RestartAfterKill() {
    return restartAfterKill;
  }

  @SimpleFunction(description = "Creates a component. No matter if the app is running in the background or the foreground. All you need to do is to specify the component source name and the name which will be used to invoke functions and do other stuff.")
  public void CreateComponent(Object source, String name) {
    String sourceClass = null;

    if(source instanceof Component) {
      sourceClass = source.getClass().getName();
    } else if(source instanceof String) {
      String sourceString = source.toString();

      if(sourceString.contains(".")) {
        sourceClass = sourceString;
      } else {
        sourceClass = "com.google.appinventor.components.runtime" + sourceString;
      }
    }

    Log.d(LOG_TAG, "The source class found is: " + sourceClass);

    try {
      Class.forName(sourceClass);
    } catch( ClassNotFoundException e ) {
      Log.d(LOG_TAG, "The source class is not valid and does not exists: " + sourceClass);
      throw new YailRuntimeError("The component source name does not exists: " + sourceClass, LOG_TAG);
    }
    componentsList.put(name, sourceClass);
  }

  @SimpleFunction(description = "Create a list of components with their id")
  public void CreateComponents(YailList sources, YailList names) {
    Object[] componentsSourceArray = sources.toArray();
    String[] componentsNameArray = names.toStringArray();

    if (componentsNameArray.length != componentsSourceArray.length) {
      Toast.makeText(activity, "Invalid list input provided!", Toast.LENGTH_SHORT).show();
    } else {
      for (int i = 0; i < componentsSourceArray.length; i++) {
        CreateComponent(componentsSourceArray[i], componentsNameArray[i]);
      }
    }
  }

  @SimpleFunction(description = "Creates a function of the component ID specified. Specify the component ID and the values. To access the invoked result use the 'invoke:result' value.")
  public void CreateFunction(String id, String name, String functionName, YailList values) {
    tasksID.add(taskID + Constants.ID_SEPARATOR + Constants.TASK_CREATE_FUNCTION);
    pendingTasks.put(taskID, Utils.toObjectArray(name, functionName, values, id));
    taskID++;
  }

  @SimpleFunction(description = "Calls the created function")
  public void CallFunction(String id) {
    tasksID.add(taskID + Constants.ID_SEPARATOR + Constants.TASK_INVOKE_FUNCTION);
    pendingTasks.put(taskID, Utils.toObjectArray(id));
    taskID++;
  }

  @SimpleFunction(description = "Register for component's events.")
  public void RegisterEvent(String name, String functionId, String eventName) {
    tasksID.add(taskID + Constants.ID_SEPARATOR + Constants.TASK_REGISTER_EVENT);
    pendingTasks.put(taskID, Utils.toObjectArray(name, functionId, eventName));
    taskID++;
  }

  @SimpleFunction(description = "Helps you call the created function multiple times")
  public void ExecuteFunction(String id, int times, int interval) {
    tasksID.add(taskID + Constants.ID_SEPARATOR + Constants.TASK_EXECUTE_FUNCTION);
    pendingTasks.put(taskID, Utils.toObjectArray(id, times, interval));
    taskID++;
  }

  @SimpleFunction(description = "Create a variable with the given variable name which can be accessed by [VAR:<NAME>]. For example \"[VAR:Data]\". Use the extra value block and use the value to access the variable.")
  public void CreateVariable(String name, Object value) {
    tasksID.add(taskID + Constants.ID_SEPARATOR + Constants.TASK_CREATE_VARIABLE);
    pendingTasks.put(taskID, Utils.toObjectArray(name, value));
    taskID++;
  }

  @SimpleFunction(description = "Does a delay in the background. You can use it as intervals between function.")
  public void MakeDelay(long millis) {
    tasksID.add(taskID + Constants.ID_SEPARATOR + Constants.TASK_DELAY);
    pendingTasks.put(taskID, Utils.toObjectArray(millis));
    taskID++;
  }

  @DesignerProperty(
  defaultValue = "False",
  editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN)
  @SimpleProperty(description = "Should the extension create components on UI thread.")
  public void CreateComponentsOnUi(boolean value) {
    createComponentsOnUi = value;
  }

  @SimpleProperty
  public boolean CreateComponentsOnUi() {
    return createComponentsOnUi;
  }

  @SimpleFunction(description = "Executes the code and returns the result. You can use it to perform actions using Java code, calculate sums and return a value. If the value is null or empty, an empty string or text is returned.")
  public Object Interpret(String code) {
    return Utils.interpret(code, activity);
  }

  @SimpleFunction(description = "Make the value from code that will be executed at the background service")
  public Object MakeExtra(String text, boolean code) {
    return new String[] {text, String.valueOf(code)};
  }

  @SimpleFunction(description = "Destroys a component and it's events.")
  public void DestroyComponent(String name, long time) {
    tasksID.add(taskID + Constants.ID_SEPARATOR + Constants.TASK_DESTROY_COMPONENT);
    pendingTasks.put(taskID, Utils.toObjectArray(name, time));
    taskID++;
  }

  @SimpleFunction(description = "Starts the service. The app must be alive to call this block or put it in the onDestroy or onPause event. This block only helps if the app is compiled and not the companion.")
  public boolean Start(int id, long latency, String requiredNetwork, boolean foreground) {
    String packageName = activity.getPackageName();

    if(packageName.equals("edu.mit.appinventor.aicompanion3") ||
            packageName.equals("io.makeroid.companion")) {
      showToast("This extension does not work in Companion!");
      return false;
    }

    if(componentsList.size() == 0) {
      showToast("Invalid input of tasks.");
      return false;
    }

    Tasks.foreground = foreground;

    saveFunctions(id);

    try {
      Log.d(LOG_TAG, "Task created");
      return startTask(latency, id, requiredNetwork);
    } catch (Exception e) {
      Log.d(LOG_TAG, e.getMessage());
      return false;
    }
  }

  @SimpleFunction(description = "Sets the title, subtitle and icon for the foreground service. If the icon is empty, the app default icon will be set.")
  public void ConfigureForeground(String title, String content, String subtext, String icon) {
    foregroundConfig = new Object[] {title, content, subtext, icon};
  }

  private boolean startTask(long time, int id, String requiredNetwork) {
    Log.d(LOG_TAG, "Received activity id: " + id);

    PersistableBundle bundle = new PersistableBundle();
    bundle.putInt("JOB_ID", id);

    ComponentName serviceName = new ComponentName(activity, ActivityService.class);

    JobInfo.Builder myJobInfo = new JobInfo.Builder(id, serviceName)
            .setExtras(bundle)
            .setMinimumLatency(time)
            .setOverrideDeadline(time + 10)
            .setPersisted(true)
            .setBackoffCriteria(1, 0);


    if(!requiredNetwork.isEmpty()) {
      int network_type = Utils.getNetworkInt(requiredNetwork);
      Log.d("Tasks", "startTask: " + network_type);
      myJobInfo.setRequiredNetworkType(network_type);
    }

    int resultCode = jobScheduler.schedule(myJobInfo.build());
    boolean success = (resultCode == JobScheduler.RESULT_SUCCESS);
    Log.d(LOG_TAG, "Condition: " + success);
    return success;
  }

  private void saveFunctions(int id) {
    ArrayList<Object> task = new ArrayList<>();

    task.add(tasksID); task.add(pendingTasks);
    task.add(createComponentsOnUi); task.add(componentsList);
    task.add(statusFunctions); task.add(foreground);
    task.add(foregroundConfig); task.add(extraFunctions);
    task.add(restartAfterKill);

    Utils.saveTask(activity, task, id);
  }


  @SimpleFunction(description = "Flags the Android system that the task is over. This would help save app resources. Call this block when you're done with all you're tasks.")
  public void FinishTask(boolean reschedule) {
    tasksID.add(taskID + Constants.ID_SEPARATOR + Constants.TASK_FINISH);
    pendingTasks.put(taskID, Utils.toObjectArray(reschedule));
    taskID++;
  }

  @SimpleFunction(description = "Stops the given service ID. The service will not be executed.")
  public void CancelTask(int id) {
    try {
      jobScheduler.cancel(id);
      Log.d(LOG_TAG, "Cancel status: " + Utils.clearTask(id, activity));
    } catch (Exception e) {
      Log.d(LOG_TAG, e.getMessage());
    }
  }

  @SimpleFunction(description = "Gets the pending task IDs")
  public YailList PendingServices() {
    List<JobInfo> pendingTasks = jobScheduler.getAllPendingJobs();

    ArrayList<Integer> tasksIds = new ArrayList<>();

    for(JobInfo info: pendingTasks) {
      tasksIds.add(info.getId());
    }

    return YailList.makeList(tasksIds);
  }

  @SimpleFunction(description = "Make a functions that can compare things and pass it to a function if the condition is true.")
  public void ExtraFunction(String id, YailList codes) {
    extraFunctions.put(id, codes.toArray());
  }

  @SimpleFunction(description = "Cancels and stops all the tasks.")
  public void CancelAllTasks() {
    jobScheduler.cancelAll();
  }

  private void showToast(String message) {
    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
  }
}
