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
import android.os.PowerManager;
import android.util.Log;
import android.view.Window;

import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.util.YailList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.kumaraswamy.tasks.Utils.findMethod;

public class ActivityService extends JobService {
    private final String TAG = "BackgroundTasks.ActivityService";

    private final HashMap<String, Component> componentsBuilt = new HashMap<>();
    private final HashMap<String, Object[]> createdFunctions = new HashMap<>();
    private final HashMap<String, Object> createdVariables = new HashMap<>();
    private final HashMap<String, Object[]> eventMap = new HashMap<>();
    private HashMap<String, Object[]> extraFunctions = new HashMap<>();

    private ArrayList<String> tasksID;
    private HashMap<String, Object[]> pendingTasks;
    private JobParameters jobParameters;

    private boolean createComponentsOnUi = false;
    private boolean restartAfterKill = false;

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

    private void initService(final JobParameters jobParameters) throws NoSuchFieldException, IllegalAccessException {
        activity = new AActivity();
        activity.init(getApplicationContext());

        form = new FForm();
        form.init(getApplicationContext());

        Field field = activity.getClass().getSuperclass().getDeclaredField("mWindow");
        field.setAccessible(true);
        Window dummyWindowResult = new Dialog(getApplicationContext()).getWindow();

        if(dummyWindowResult == null) {
            Log.d(TAG, "initService: Window result is null and may be Unstable");
        } else {
            field.set(activity, dummyWindowResult);
            field.set(form, dummyWindowResult);
        }

        field = activity.getClass().getSuperclass().getDeclaredField("mComponent");
        field.setAccessible(true);

        ComponentName componentName = new ComponentName(activity.getPackageName(), activity.getPackageName() + ".Screen1");

        field.set(activity, componentName);
        field.set(form, componentName);

        doBackgroundWork(jobParameters);
    }


    private void doBackgroundWork(final JobParameters jobParameters) {
        Log.d(TAG, "The service is started");
        new Thread(new Runnable() {
            @Override
            public void run() {
                processTasks(jobParameters);
            }
        }).start();
    }

    private void processTasks(JobParameters jobParameters) {
        int jobID = jobParameters.getExtras().getInt("JOB_ID");
        ArrayList<Object> tasksRead = Utils.readTask(getApplicationContext(), jobID);

        if(tasksRead == null) {
            Log.i(TAG, "processTasks: Tasks are null are invalid");
            return;
        }

        Log.d(TAG, "Got data from database: " + tasksRead);

        tasksID = (ArrayList<String>) tasksRead.get(0);
        pendingTasks = (HashMap<String, Object[]>) tasksRead.get(1);
        HashMap<String, String> componentsList = (HashMap<String, String>) tasksRead.get(3);
        boolean foreground = (boolean) tasksRead.get(5);
        Object[] foregroundConfig = (Object[]) tasksRead.get(6);

        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ActivityServiceWakeLock");
        wakeLock.acquire();

        extraFunctions = (HashMap<String, Object[]>) tasksRead.get(7);

        if(foreground) {
            processForeground(foregroundConfig);
        }

        this.jobParameters = jobParameters;
        createComponentsOnUi = (boolean) tasksRead.get(2);
        restartAfterKill = (boolean) tasksRead.get(8);

        processComponentList(componentsList);
    }

    private void processForeground(Object[] foregroundConfig) {
        Log.i(TAG, "processForeground: " + foregroundConfig[2]);

        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "BackgroundService";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Service is started",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            String icon = foregroundConfig[3].toString().toUpperCase();

            int iconInt = (icon.isEmpty() || icon.equals("DEFAULT"))
                    ? android.R.drawable.ic_menu_info_details
                    : Integer.parseInt(icon.replaceAll(" ", ""));

            Notification notification = new Notification.Builder(this, "BackgroundService")
                    .setSubText(foregroundConfig[0].toString())
                    .setContentTitle(foregroundConfig[1].toString())
                    .setContentText(foregroundConfig[2].toString())
                    .setSmallIcon(iconInt)
                    .build();

            startForeground(1, notification);
        }
    }

    private void processMainTasks() {
        Log.d(TAG, "Total tasks: " + tasksID.size());

        for (String tasks: tasksID) {
            Log.d(TAG, "Processing the task: " + tasks);

            String[] taskData = tasks.split(Constants.ID_SEPARATOR);

            int taskID = Integer.parseInt(taskData[0]);
            int taskType = Integer.parseInt(taskData[1]);

            Log.d(TAG, "Task ID: " + taskID + ", taskType: " + taskType);

            Object[] taskValues = pendingTasks.get(taskID);
            Log.d(TAG, "Task values: " + Arrays.toString(taskValues));

            if (taskType == Constants.TASK_CREATE_FUNCTION) {
                Log.d(TAG, "Received task to create a function");
                createFunction(taskValues);
            } else if(taskType == Constants.TASK_INVOKE_FUNCTION) {
                Log.d(TAG, "Received task to invoke a function");
                invokeFunction(taskValues[0].toString(), null);
            } else if(taskType == Constants.TASK_DELAY) {
                try {
                    Thread.sleep((Long) taskValues[0]);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }
            } else if(taskType == Constants.TASK_CREATE_VARIABLE) {
                Log.d(TAG, "Received task to create a variable");
                createVariable(taskValues);
            } else if(taskType == Constants.TASK_FINISH) {
                Log.d(TAG, "Received task to end the task");
                jobFinished(jobParameters, (Boolean) taskValues[0]);
            } else if(taskType == Constants.TASK_EXECUTE_FUNCTION) {
                Log.d(TAG, "processTasks: Received task to execute a function");
                executeFunction(taskValues);
            } else if(taskType == Constants.TASK_REGISTER_EVENT) {
                Log.d(TAG, "processTasks: Received task to register event");
                registerEvent(taskValues);
            } else if (taskType == Constants.TASK_DESTROY_COMPONENT) {
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

                    invokeComponent(componentID, "onPause", null, null);
                    invokeComponent(componentID, "onDestroy", null, null);

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
                    Log.d(TAG, "executeFunction: Executing the function name: " + function);
                    invokeFunction(function, null);
                    timesExecuted[0]++;
                }
            }
        }, 0, interval);
    }

    private void createVariable(Object[] taskValues) {
        Log.d(TAG, "createVariable: " + createdVariables);

        final String variableName = taskValues[0].toString();
        final Object variableValue = taskValues[1];
        Object result = variableValue instanceof String ?
                variableValue.toString() :
                processValue(variableValue, null);

        Log.d(TAG, "createVariable: Got value: " + result.toString());

        result = replaceVariables(result);

        createdVariables.put(variableName, result);
        Log.d(TAG, "Created a variable of name: " + variableName + ", and value: " + result.toString());
    }

    private Object replaceVariables(Object result) {
        if(result instanceof String) {
            for(String key: createdVariables.keySet()) {
                final String madeKey = "data:" + key;
                Log.d(TAG, "replaceVariables: Made key: " + madeKey);
                if(result.toString().contains(madeKey)) {
                    result = result.toString().replaceAll(madeKey, createdVariables.get(key).toString());
                }
            }
        }
        return result;
    }

    private void createFunction(final Object[] taskValues) {
        final Object[] values = new Object[] {taskValues[0], taskValues[1], taskValues[2]};
        final String functionName = taskValues[3].toString();

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
                                processMainTasks();
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
                    processMainTasks();
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

    private Object invokeFunction(String functionID, Object[] eventValues) {
        Log.d(TAG, createdFunctions.toString());
        final Object[] taskValues = createdFunctions.get(functionID);

        if(taskValues == null || taskValues.length == 0) {
            Log.d(TAG, "Invalid invoke values provided");
            return "";
        }

        final String componentID = taskValues[0].toString();
        final String functionName = taskValues[1].toString();
        final Object[] parameters = ((YailList) taskValues[2]).toArray();

        if(componentID.equals("self") && functionName.equals("exit-foreground")) {
            Log.i(TAG, "invokeFunction: Stopping function dynamic");
            if(parameters.length > 0) {
                stopForeground((Boolean) parameters[0]);
            }
        } else if(componentID.equals("self") && functionName.equals("exit")){
            Log.i(TAG, "invokeFunction: Stopping the service");
            jobFinished(jobParameters, (Boolean) parameters[0]);
        } else {
            Log.d(TAG, "Invoking function of component ID: " + componentID + ", function name: " + functionName + ", parameters: " + Arrays.toString(parameters)  + " of compo objects: " + componentsBuilt.toString());
            return invokeComponent(componentID, functionName, parameters, eventValues);
        }
        return null;
    }

    private Object invokeComponent(final String componentID, final String functionName, Object[] parameters, Object[] eventValues) {
        try {
            if (eventValues == null) {
                eventValues = new Object[] {};
            }
            if(parameters == null) {
                parameters = new Object[] {};
            }

            final Component component = componentsBuilt.get(componentID);
            final Method[] methods = component.getClass().getMethods();
            final Method method = findMethod(methods, functionName, parameters.length);

            if(method == null) {
                Log.e(TAG, "Function name: " + functionName + " may not exist");
                return null;
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
                parameters[index] = processValue(object, eventValues);
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
            return invokeResult;
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Object processValue(final Object object, final Object[] eventValues) {
        if(object instanceof String[]) {
            String[] value = (String[]) object;

            Log.d(TAG, "Found interpret value class: " + Arrays.toString(value));

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
                return invokeResult == null ? "" : invokeResult;
            } else if(text.startsWith("invoke:[") && text.charAt(text.length() - 1) == ']') {
                String functionId = text.substring(8);
                functionId = functionId.substring(0, functionId.length() - 1);
                Log.i(TAG, "processValue: Processing extra value of executing function value: " + functionId);

                Object result = invokeFunction(functionId, eventValues);
                return result == null ? "" : result;
            }
        }
        return object;
    }

    private void newEvent(final Component component, final String eventName, final Object[] array) {
        Log.d(TAG, "newEvent: Component " + componentsBuilt + " eventName " + eventName + " values " + Arrays.toString(array));

        String componentID = "";

        for(String key: componentsBuilt.keySet()) {
            if (componentsBuilt.get(key).toString().equals(component.toString())) {
                componentID = key;
                break;
            }
        }

        final Object[] invokeValues = eventMap.get(componentID);

        if(invokeValues == null) {
            return;
        }

        Log.i(TAG, "newEvent: contains the component in the built list: " + componentsBuilt.containsValue(component));

        String functionID = invokeValues[0].toString();
        final String thisEventName = invokeValues[1].toString();

        if (thisEventName.equals(eventName)) {
            if(functionID.startsWith("$")) {
                Log.i(TAG, "Function has an extra function Id");
                final Object[] values = extraFunctions.get(functionID.substring(1));
                Log.i(TAG, "Extra function values : " + Arrays.toString(values));
                final ArrayList<String[]> results = new ArrayList<>();

                for(Object object: values) {
                    Object result = Utils.CodeParser.processCode(object.toString(), array);

                    if(!result.toString().equals("none")) {
                        Log.i(TAG, "newEvent: The parsed result is: " + Arrays.toString(((String[]) result)));
                        results.add((String[]) result);
                    }
                }

                for(final String[] stringArray: results) {
                    final String functionName = stringArray[0];
                    final String functionId = stringArray[1];

                    if(functionName.equalsIgnoreCase("function")) {
                        invokeFunction(functionId, array);
                    }
                }

                Log.i(TAG, "newEvent: The values received and prepared: " + results);
            } else {
                invokeFunction(functionID, array);
            }
        } else {
            Log.i(TAG, "newEvent: Event dismissed");
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
            // do nothing
        }

        @Override
        public void onPause() {
            // do nothing
        }

        @Override
        public void onStop() {
            // do nothing
        }

        @Override
        public void onResume() {
            // do nothing
        }
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
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(TAG, "The service stopped");
        for(String componentId: componentsBuilt.keySet()) {
            destroyComponent(new Object[]{componentId, System.currentTimeMillis() + 1});
        }
        jobFinished(jobParameters, restartAfterKill);
        return restartAfterKill;
    }
}
