package com.kumaraswamy.tasks;

import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import androidx.core.content.ContextCompat;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.util.YailList;
import com.kumaraswamy.tasks.external.NotificationStyle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.kumaraswamy.tasks.Utils.findMethod;

public class ActivityService extends JobService {
    private final String TAG = "BackgroundTasks.ActivityService";

    private final HashMap<String, Component> componentsBuilt = new HashMap<>();
    private final HashMap<String, Object[]> createdFunctions = new HashMap<>();
    private final HashMap<String, Object> createdVariables = new HashMap<>();
    private final HashMap<String, Object[]> eventMap = new HashMap<>();

    private boolean createComponentsOnUi = false;

    private ComponentContainer container;
    private AActivity activity;
    private FForm form;

    private Object invokeResult = null;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        try {
            initService(jobParameters);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return true;
    }

    private void initService(JobParameters jobParameters) throws NoSuchFieldException, IllegalAccessException {
        activity = new AActivity();
        activity.init(getApplicationContext());

        form = new FForm();
        form.init(getApplicationContext());
        container = new CComponentContainer(this);


        /*
             What is field and why we're using this?
             For components like player the component needs to access the window
             just to apply some settings, If we do not do this, the window "Activity.getWindow()" maybe null.
             So we're setting the variable "mWindow" to the value of a dummy Window object so the component can do operations on
             it.
             https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/Activity.java
             You could see the Activity source code here. We also do this to the form because form is like activity to say in a simple
             way.
             Refer more about this problem/soln:
             https://community.appinventor.mit.edu/t/create-a-new-custom-component-container-that-works-for-every-component-created-by-reflection/35638?u=kumaraswamy_b.g
             https://developer.android.com/reference/java/lang/reflect/Field
         */

        Field field = activity.getClass().getSuperclass().getDeclaredField("mWindow");
        field.setAccessible(true);
        Window dummyWindowResult = new Dialog(getApplicationContext()).getWindow();
        if(dummyWindowResult == null) {
            Log.d(TAG, "initService: Window result is null and may be Unstable");
        } else {
            field.set(activity, dummyWindowResult);
            field.set(form, dummyWindowResult);
        }
        doBackgroundWork(jobParameters);
    }

    private void doBackgroundWork(final JobParameters jobParameters) {
        Log.d(TAG, "The service is started");
        new Thread(new Runnable() {
            @Override
            public void run() {
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        processTasks(jobParameters);
                    }
                }, 100);
            }
        }).start();
    }

    private ArrayList<String> tasksID;
    private HashMap<String, Object[]> pendingTasks;
    private JobParameters jobParameters;
    private static HashMap<String, String> statusFunctions;

    private void processTasks(JobParameters jobParameters) {
        int jobID = jobParameters.getExtras().getInt("JOB_ID");
        ArrayList<Object> tasksRead = Utils.readTask(getApplicationContext(), jobID);

        Log.d(TAG, "Got data from database: " + tasksRead.toString());

        tasksID = (ArrayList<String>) tasksRead.get(0);
        pendingTasks = (HashMap<String, Object[]>) tasksRead.get(1);
        HashMap<String, String> componentsList = (HashMap<String, String>) tasksRead.get(3);
        statusFunctions = (HashMap<String, String>) tasksRead.get(4);
        boolean foreground = (boolean) tasksRead.get(5);
        Object[] foregroundConfig = (Object[]) tasksRead.get(6);

        if(foreground) {
            processForeground(foregroundConfig, jobID);
        }

        this.jobParameters = jobParameters;
        createComponentsOnUi = (boolean) tasksRead.get(2);

        processComponentList(componentsList);
    }

    private void processForeground(Object[] foregroundConfig, int jobid) {
        Log.i(TAG, "processForeground: " + foregroundConfig[2]);

        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "BackgroundService";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Service is started",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            String icon = foregroundConfig[3].toString().toUpperCase();

            int iconInt;


            if (icon.isEmpty() || icon.equals("DEFAULT")) {
                iconInt = android.R.drawable.ic_menu_info_details;
            } else {
                iconInt = Integer.parseInt(icon);
            }

            Notification notification = new Notification.Builder(this, "BackgroundService")
                    .setSubText(foregroundConfig[0].toString())
                    .setContentTitle(foregroundConfig[1].toString())
                    .setContentText(foregroundConfig[2].toString())
                    .setSmallIcon(iconInt)
                    .build();

            startForeground(1, notification);
        }

//        String icon = foregroundConfig[2].toString();
//        int intIcon = -1;
//
//        if (!icon.isEmpty() && !icon.equals("DEFAULT")) {
//            intIcon = Integer.parseInt(icon);
//        }
//
//        if (Build.VERSION.SDK_INT >= 26) {
//            NotificationStyle notificationStyle = new NotificationStyle(container);
//            startForeground(1, notificationStyle.SimpleNotification(foregroundConfig[0].toString(), foregroundConfig[1].toString(), false, "", 1, intIcon));
//        }
    }

    private void doMainTask() {
        Log.d(TAG, "Total tasks: " + tasksID.size());

        for (String tasks: tasksID) {
            Log.d(TAG, "Processing the task: " + tasks);

            String[] taskData = tasks.split(Tasks.ID_SEPARATOR);

            int taskID = Integer.parseInt(taskData[0]);
            int taskType = Integer.parseInt(taskData[1]);

            Log.d(TAG, "Task ID: " + taskID + ", taskType: " + taskType);

            Object[] taskValues = pendingTasks.get(taskID);
            Log.d(TAG, "Task values: " + Arrays.toString(taskValues));

            if (taskType == Tasks.TASK_CREATE_FUNCTION) {
                Log.d(TAG, "Received task to create a function");
                createFunction(taskValues);
            } else if(taskType == Tasks.TASK_INVOKE_FUNCTION) {
                Log.d(TAG, "Received task to invoke a function");
                invokeFunction(taskValues[0].toString(), null);
            } else if(taskType == Tasks.TASK_DELAY) {
                try {
                    Thread.sleep((Long) taskValues[0]);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }
            } else if(taskType == Tasks.TASK_CREATE_VARIABLE) {
                Log.d(TAG, "Received task to create a variable");
                createVariable(taskValues);
            } else if(taskType == Tasks.TASK_FINISH) {
                Log.d(TAG, "Received task to end the task");
                jobFinished(jobParameters, false);
            } else if(taskType == Tasks.TASK_EXECUTE_FUNCTION) {
                Log.d(TAG, "processTasks: Received task to execute a function");
                executeFunction(taskValues);
            } else if(taskType == Tasks.TASK_REGISTER_EVENT) {
                Log.d(TAG, "processTasks: Received task to register event");
                registerEvent(taskValues);
            } else if (taskType == Tasks.TASK_DESTROY_COMPONENT) {
                Log.d(TAG, "processTasks: Got task to destroy a component");
                destroyComponent(taskValues);
            }
        }
    }

    private boolean processComponentList(HashMap<String, String> componentsList) {
        String[] keySet = componentsList.keySet().toArray(new String[0]);
        String lastKey = keySet[keySet.length - 1];

        Log.d(TAG, "processComponentList: " + Arrays.toString(keySet));
        Log.d(TAG, "processComponentList: " + lastKey);

        for (String key : keySet) {
            String source = componentsList.get(key);
            createComponent(new Object[]{source, key}, lastKey);
        }
        return true;
    }

    private void destroyComponent(final Object[] taskValues) {
        final long time = (long) taskValues[1];
        final long timeRemaining = time - System.currentTimeMillis();

        try {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    final String componentID = String.valueOf(taskValues[0]);

                    invokeComponent(componentID, "onPause", new Object[]{}, new Object[] {});
                    invokeComponent(componentID, "onDestroy", new Object[]{}, new Object[] {});

                    componentsBuilt.remove(componentID);
                }
            }, timeRemaining);

        } catch (Exception exception) {
            Log.i(TAG, "destroyComponent: " + exception.getMessage());
        }
    }

    private void registerEvent(Object[] taskValues) {
        final String componentID = taskValues[0].toString();
        final Object[] otherValues = new Object[] {taskValues[1], taskValues[2]};
        eventMap.put(componentID, otherValues);
        Log.d(TAG, "registerEvent: Register done for event values: " + Arrays.toString(taskValues));
    }

    private void executeFunction(Object[] taskValues) {
        Log.d(TAG, "executeFunction: task values: " + Arrays.toString(taskValues));

        final String function = taskValues[0].toString();

        final int times = (int) taskValues[1];
        final int interval = (int) taskValues[2];

        final int[] timesExecuted = {0};
        final Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(times == timesExecuted[0]) {
                    timer.cancel();
                } else {
                    Log.d(TAG, "run: Executing the function name: " + function);
                    invokeFunction(function, null);
                    timesExecuted[0]++;
                }
            }
        }, 0, interval);
    }

    private void createVariable(Object[] taskValues) {
        Log.d(TAG, "createVariable: " + createdVariables.toString());

        String variableName = taskValues[0].toString();
        Object variableValue = taskValues[1];

        Object result = variableValue instanceof String ? variableValue.toString() : processValue(variableValue);

        Log.d(TAG, "createVariable: Got value: " + result.toString());

        result = replaceVariables(result);

        createdVariables.put(variableName, result);
        Log.d(TAG, "Created a variable of name: " + variableName + ", and value: " + result.toString());
    }

    private Object replaceVariables(Object result) {
        for(String key: createdVariables.keySet()) {
            String madeKey = "data:" + key;
            Log.d(TAG, "replaceVariables: Made key: " + madeKey);
            if(result.toString().contains(madeKey)) {
                result = result.toString().replaceAll(madeKey, createdVariables.get(key).toString());
            }
        }
        return result;
    }

    private void createFunction(Object[] taskValues) {
        Object[] values = new Object[] {taskValues[0], taskValues[1], taskValues[2]};
        String functionName = taskValues[3].toString();

        createdFunctions.put(functionName, values);
    }

    private void createComponent(final Object[] taskValues, final String lastKey) {
        final String componentName = taskValues[0].toString();
        final String componentID = taskValues[1].toString();

        try {
            Class<?> CClass = Class.forName(componentName);
            final Constructor<?> CConstructor = CClass.getConstructor(ComponentContainer.class);

            if (createComponentsOnUi) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            saveComponentToHashmap((Component) CConstructor.newInstance(form),
                                    componentID, componentName);
                            if(lastKey.equals(componentID)) {
                                doMainTask();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                saveComponentToHashmap((Component) CConstructor.newInstance(form),
                        componentID, componentName);
                if(lastKey.equals(componentID)) {
                    doMainTask();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveComponentToHashmap(Component component, String componentID, String componentName) {
        componentsBuilt.put(componentID, component);
        Log.d(TAG, "Component created of source: " + componentName + ", component ID: " + componentID);
        Log.d(TAG, "createComponent: " + eventMap.toString());
    }

    private void invokeFunction(String functionID, Object[] eventValues) {
        Log.d(TAG, createdFunctions.toString());
        final Object[] taskValues = createdFunctions.get(functionID);

        if(taskValues == null || taskValues.length == 0) {
            Log.d(TAG, "Invalid invoke values provided");
            return;
        }

        final String componentID = taskValues[0].toString();
        final String functionName = taskValues[1].toString();
        final Object[] parameters = ((YailList) taskValues[2]).toArray();

        if(componentID.equals("self") && functionName.equals("exit-foreground")) {
            Log.i(TAG, "invokeFunction: Stopping function dynamic");
            stopForeground((Boolean) parameters[0]);
        } else {
            Log.d(TAG, "Invoking function of component ID: " + componentID + ", function name: " + functionName + ", parameters: " + Arrays.toString(parameters));
            Log.d(TAG, "invokeFunction: " + componentsBuilt.toString());
            invokeComponent(componentID, functionName, parameters, eventValues);
        }
    }

    private void invokeComponent(String componentID, String functionName, Object[] parameters, Object[] eventValues) {
        try {
            if (eventValues == null) {
                eventValues = new Object[] {};
            }

            Component component = componentsBuilt.get(componentID);
            Method[] methods = component.getClass().getMethods();

            Method method = findMethod(methods, functionName, parameters.length);

            if(method == null) {
                Log.e(TAG, "Function name: " + functionName + " may not exist");
                return;
            }

            int index = 0;
            for(Object object: parameters) {
                if (object instanceof String) {
                    String code = object.toString();

                    for (int i = 0; i < eventValues.length; i++) {
                        code = code.replace("{%" + i + "}", eventValues[i].toString());
                    }
                    object = code;
                }

                object = replaceVariables(object);
                parameters[index] = processValue(object);
                index++;
            }

            /*
               Taken from: https://github.com/ysfchn/DynamicComponents-AI2
             */

            Class<?>[] mRequestedMethodParameters = method.getParameterTypes();
            ArrayList<Object> mParametersArrayList = new ArrayList<>();

            for (int i = 0; i < mRequestedMethodParameters.length; i++) {
                if ("int".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(Integer.parseInt(parameters[i].toString()));
                } else if ("float".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(Float.parseFloat(parameters[i].toString()));
                } else if ("double".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(Double.parseDouble(parameters[i].toString()));
                } else if ("java.lang.String".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(parameters[i].toString());
                } else if ("boolean".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(Boolean.parseBoolean(parameters[i].toString()));
                } else {
                    mParametersArrayList.add(parameters[i]);
                }
            }

            invokeResult = method.invoke(component, mParametersArrayList.toArray());
            Log.d(TAG, "Invoked method name: " + functionName + ", component ID: " + componentID + ", invoke result: " + invokeResult);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private Object processValue(Object object) {
        if(object instanceof String[]) {
            String[] value = (String[]) object;

            Log.d(TAG, "Found interpret value class: " + Arrays.toString(value));

            // bol parse log try

            String text = value[0];
            boolean isCode = Boolean.parseBoolean(value[1]);

            if(isCode) {
                Object interpretResult = Utils.interpret(text, getApplicationContext());
                Log.d(TAG, "Got interpretResult: " + interpretResult.toString());
                return interpretResult;
            } else if(text.startsWith("invoke:result:[") && text.charAt(text.length() - 1) == ']') {
                String itemIndex = text.substring(15);
                itemIndex = itemIndex.substring(0, itemIndex.length() - 1);

                Log.d(TAG, "Found item index: " + itemIndex);

                try {
                    int indexParsed = Integer.parseInt(itemIndex) - 1;
                    Object resultItem = "";

                    if(invokeResult instanceof YailList) {
                        YailList yailList = (YailList) invokeResult;
                        resultItem = yailList.toArray()[indexParsed];
                    } else if(invokeResult instanceof List) {
                        resultItem = ((List) invokeResult).get(indexParsed);
                    } else {
                        Log.d(TAG, "Unknown list class found: " + invokeResult.getClass());
                    }


                    Log.d(TAG, "processValue: " + resultItem);

                    return resultItem;
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    Log.d(TAG, "The index is not valid");
                }
            } else if(text.equals("invoke:result:empty")) {
                Log.d(TAG, "Received task to check if the invoke result is empty");

                boolean isEmpty = true;

                if(invokeResult instanceof String) {
                    isEmpty = invokeResult.toString().isEmpty();
                } if(invokeResult instanceof YailList) {
                    isEmpty = ((YailList) invokeResult).toArray().length == 0;
                } else if(invokeResult instanceof List) {
                    isEmpty = ((List) invokeResult).toArray().length == 0;
                } else {
                    Log.d(TAG, "Unknown class function received to find if result is empty");
                }

                Log.d(TAG, "Found if the result is empty: " + isEmpty);

                return isEmpty;

            } else if(text.equals("invoke:result:length")) {
                Log.d(TAG, "Found task to find length");

                int invokeLength;

                if(invokeResult instanceof String) {
                    invokeLength = invokeResult.toString().length();
                } else {
                    Object[] arrayList = null;

                    if(invokeResult instanceof YailList) {
                        arrayList = ((YailList) invokeResult).toArray();
                    } else if(invokeResult instanceof List) {
                        arrayList = ((List) invokeResult).toArray();
                    } else {
                        Log.d(TAG, "Unknown class function received to find the length");
                    }

                    Log.d(TAG, Arrays.toString(arrayList));

                    if(arrayList == null) {
                        return -1;
                    } else {
                        invokeLength = arrayList.length;
                    }
                }

                return invokeLength;
            } else if(text.equals("invoke:result")) {
                Log.d(TAG, String.valueOf("Is invoke result null while interpreting due to request: " + invokeResult == null));
                return invokeResult == null ? "" : invokeResult;
            }
        }
        return object;
    }

    public void newEvent(Component component, String eventName, final Object[] array) {
        Log.d(TAG, "newEvent: " + componentsBuilt.toString());
        Log.d(TAG, "newEvent: " + eventName);
        Log.d(TAG, "newEvent: " + Arrays.toString(array));

        String componentID = "";

        for(String key: componentsBuilt.keySet()) {
            if (componentsBuilt.get(key).toString().equals(component.toString())) {
//                Log.i(TAG, "newEvent: Got the key value for the component event: " + key);
                componentID = key;
                break;
            }
        }

        Object[] invokeValues = eventMap.get(componentID);

        if(invokeValues == null) {
//            Log.d(TAG, "newEvent: Dismissed because invoke values is null");
            return;
        }

        Log.i(TAG, "newEvent: contains the component in the built list: " + componentsBuilt.containsValue(component));


        String functionID = invokeValues[0].toString();
        String thisEventName = invokeValues[1].toString();

        if (thisEventName.equals(eventName)) {
            invokeFunction(functionID, array);
        } else {
            Log.i(TAG, "newEvent: Event dismissed as it's not registered");
        }
    }

    public void processStatus(String status) {
        Log.d(TAG, "processStatus: status got :" + status);
        for(String key: statusFunctions.keySet()) {
            if(key.equals(status)) {
                Log.d(TAG, "processStatus: got the status match:" + status);
                String functionId = statusFunctions.get(key);
                invokeFunction(functionId, null);
            }
        }
    }

    class FForm extends Form {
        public void init(Context context) {
            attachBaseContext(context);
        }

        @Override
        public boolean canDispatchEvent(final Component component, final String str) {
            return true;
        }

        @Override
        public boolean dispatchEvent(final Component component, final String eventName, final String str2, final Object[] values) {
            newEvent(component, eventName, values);
            return true;
        }

        @Override
        public void dispatchGenericEvent(final Component component, final String eventName, final boolean z, final Object[] values) {
            newEvent(component, eventName, values);
        }

        @Override
        public void onDestroy() {
            processStatus("KILL");
        }

        @Override
        public void onPause() {
            processStatus("PAUSE");
        }

        @Override
        public void onStop() {
            processStatus("KILL");
        }

        @Override
        public void onResume() {
            processStatus("RESUME");
        }

//        @Override
//        public Window getWindow() {
//            return new Builder(this).setTitle("Title").setMessage("Message").create().getWindow();
//        }
    }

    static class AActivity extends Activity {
        public void init(Context context) {
            attachBaseContext(context);
        }

        @Override
        public String getLocalClassName() {
            return "Screen1";
        }

        @Override
        public void onCreate(Bundle bundle) {
            super.onCreate(bundle);
        }

        @Override
        public ComponentName getComponentName() {
            String applicationPackage = this.getPackageName();
            return new ComponentName(applicationPackage, applicationPackage + "Screen1");
        }

//        @Override
//        public Window getWindow() {
//            return new Builder(this).setTitle("").setMessage("").create().getWindow();
//        }
    }

    static class CComponentContainer implements ComponentContainer {
        private final ActivityService service;

        public CComponentContainer(ActivityService service){
            this.service = service;
        }

        @Override
        public Activity $context() {
            return service.activity;
        }

        @Override
        public Form $form() {
            return service.form;
        }

        @Override
        public void $add(AndroidViewComponent androidViewComponent) {

        }

        @Override
        public void setChildWidth(AndroidViewComponent androidViewComponent, int i) {

        }

        @Override
        public void setChildHeight(AndroidViewComponent androidViewComponent, int i) {

        }

        @Override
        public int Width() {
            return 1;
        }

        @Override
        public int Height() {
            return 1;
        }
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(TAG, "The service stopped");
        for(String componentId: componentsBuilt.keySet()) {
            destroyComponent(new Object[]{componentId, System.currentTimeMillis() + 1});
        }
        jobFinished(jobParameters, false);
        return false;
    }
}