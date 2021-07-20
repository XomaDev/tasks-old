package com.kumaraswamy.tasks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.util.YailList;
import com.kumaraswamy.tasks.reflect.ActivityListener;
import com.kumaraswamy.tasks.reflect.ComponentManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.kumaraswamy.tasks.Utils.emptyIfNull;
import static com.kumaraswamy.tasks.Utils.findMethod;

public class ActivityService extends JobService {
//    private final String TAG = "BackgroundTasks.ActivityService";
    private static final String TAG = "ActivityService";

    private final HashMap<String, Object[]> createdFunctions = new HashMap<>();
    private HashMap<String, Object> createdVariables = new HashMap<>();
    private final HashMap<String, ArrayList<Object>> eventMap = new HashMap<>();
    private HashMap<String, Object[]> extraFunctions = new HashMap<>();

    private ComponentManager componentManager;
    private boolean serviceStarted = false;

    private ArrayList<String> tasksID;
    private HashMap<String, Object[]> pendingTasks;
    private JobParameters jobParameters;

    private boolean restartAfterKill = false;
    private Object invokeResult = null;

    private boolean activityNoKill = false;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        doBackgroundWork(jobParameters);
        return true;
    }

    private void doBackgroundWork(final JobParameters jobParameters) {
        final int jobID = jobParameters.getExtras().getInt("JOB_ID");
        final boolean periodic = jobParameters.getExtras().getBoolean("PERIODIC");
        final String[] flags = jobParameters.getExtras().getStringArray("FLAGS");
        System.out.println(periodic + " ppp");

        Log.d(TAG, "The service is started");

        if(flags != null) {
            if (Arrays.asList(flags).contains(Tasks.FLAG_IGNORE_FIRST_PERIODIC_RUN)) {
                if(isFirstRun(jobID)) {
                    markFirstRunComplete(jobID);
                    System.out.println("Ignoring service");
                    jobFinished(jobParameters, false);
                    return;
                }
            }
        }
        serviceStarted = true;
        processTasks(jobParameters, jobID);
    }


    private boolean isFirstRun(int job) {
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("TasksInfo11", 0);
        return sharedPreferences.getBoolean(job + "", true);
    }

    private void markFirstRunComplete(int job) {
        Log.d(TAG, "mark first run complete");
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("TasksInfo11", 0);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(job + "", false);
        editor.apply();
    }

    private void processTasks(final JobParameters jobParameters, int jobID) {
        ArrayList<Object> tasksRead = Utils.readTask(getApplicationContext(), jobID);

        if (tasksRead == null) {
            Log.i(TAG, "processTasks: Tasks are null are invalid");
            return;
        }

        Log.d(TAG, "Got data from database: " + tasksRead);

        tasksID = (ArrayList<String>) tasksRead.get(0);
        pendingTasks = (HashMap<String, Object[]>) tasksRead.get(1);
        HashMap<String, String> componentsList = (HashMap<String, String>) tasksRead.get(3);
        boolean foreground = (boolean) tasksRead.get(5);
        Object[] foregroundConfig = (Object[]) tasksRead.get(6);


        List<String> serviceFlags = (List<String>) tasksRead.get(9);

        if (serviceFlags != null) {
            activityNoKill = serviceFlags.contains(Tasks.FLAG_ACTIVITY_NO_KILL);
        }
        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ActivityServiceWakeLock");
        wakeLock.acquire();

        extraFunctions = (HashMap<String, Object[]>) tasksRead.get(7);

        if (foreground)
            processForeground(foregroundConfig);

        this.jobParameters = jobParameters;
        restartAfterKill = (boolean) tasksRead.get(8);

        final ActivityListener.ComponentsCreated componentsCreatedListener =
                new ActivityListener.ComponentsCreated() {
                    @Override
                    public void componentsCreated() {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                processMainTasks();
                            }
                        }).start();
                    }
                };

        final ActivityListener.EventRaised eventRaisedListener = new ActivityListener.EventRaised() {
            @Override
            public void eventRaised(Component component, String eventName, Object[] parameters) {
                handleNewEvent(component, eventName, parameters);
            }
        };

        componentManager = new ComponentManager(getApplicationContext(), componentsList,
                componentsCreatedListener, eventRaisedListener);
    }

    private void processForeground(final Object[] foregroundConfig) {
        Log.i(TAG, "processForeground: " + foregroundConfig[2]);

        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "BackgroundService";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Task",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).
                    createNotificationChannel(channel);

            final String icon = foregroundConfig[3].toString();

            final int iconInt = (icon.isEmpty() || icon.equalsIgnoreCase("DEFAULT"))
                    ? android.R.drawable.ic_menu_info_details
                    : Integer.parseInt(icon.replaceAll(" ", ""));

            final Notification notification = new Notification.Builder(this, "BackgroundService")
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

        for (final String tasks : tasksID) {
            Log.d(TAG, "Processing the task: " + tasks);

            final String[] taskData = tasks.split(Constants.ID_SEPARATOR);

            final int taskID = Integer.parseInt(taskData[0]);
            final int taskType = Integer.parseInt(taskData[1]);

            Log.d(TAG, "Task ID: " + taskID + ", taskType: " + taskType);

            Object[] taskValues = pendingTasks.get(taskID);
            Log.d(TAG, "Task values: " + Arrays.toString(taskValues));

            if (taskType == Constants.TASK_CREATE_FUNCTION) {
                Log.d(TAG, "Received task to create a function");
                createFunction(taskValues);
            } else if (taskType == Constants.TASK_INVOKE_FUNCTION) {
                Log.d(TAG, "Received task to invoke a function");
                invokeFunction(taskValues[0].toString(), null);
            } else if (taskType == Constants.TASK_DELAY) {
                try {
                    Thread.sleep((Long) taskValues[0]);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }
            } else if (taskType == Constants.TASK_CREATE_VARIABLE) {
                Log.d(TAG, "Received task to create a variable");
                createVariable(taskValues);
            } else if (taskType == Constants.TASK_FINISH) {
                Log.d(TAG, "Received task to end the task");
                jobFinished(jobParameters, (Boolean) taskValues[0]);
            } else if (taskType == Constants.TASK_EXECUTE_FUNCTION) {
                Log.d(TAG, "processTasks: Received task to execute a function");
                executeFunction(taskValues);
            } else if (taskType == Constants.TASK_REGISTER_EVENT) {
                Log.d(TAG, "processTasks: Received task to register event");
                registerEvent(taskValues);
            } else if (taskType == Constants.TASK_DESTROY_COMPONENT) {
                Log.d(TAG, "processTasks: Got task to destroy a component");
                destroyComponentFromTime(taskValues);
            }
        }
    }

    private void destroyComponentFromTime(final Object[] values) {
        final String componentId = values[0].toString();
        final long time = (long) values[1];
        final long timeRemaining = time - System.currentTimeMillis();

        try {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    destroyComponent(componentId);
                }
            }, timeRemaining);
        } catch (IllegalArgumentException exception) {
            destroyComponent(componentId);
        }
    }

    private void destroyComponent(final String componentId) {
        componentManager.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final String[] functionNames = new String[]{"onPause", "onDestroy"};
                for (final String functionName : functionNames) {
                    invokeComponent(componentId, functionName, null, null);
                }
                componentManager.clearComponents();
            }
        });
    }

    private void registerEvent(Object[] taskValues) {
        final String componentID = taskValues[0].toString();

        final Object[] otherValues = new Object[]{taskValues[1], taskValues[2]};

        final ArrayList<Object> events = eventMap.getOrDefault(componentID, new ArrayList<>());
        events.add(otherValues);

        eventMap.put(componentID, events);

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
                if (times == timesExecuted[0]) {
                    timer.cancel();
                } else {
                    Log.d(TAG, "executeFunction: Executing the function name: " + function);
                    invokeFunction(function, null);
                    timesExecuted[0]++;
                }
            }
        }, 0, interval);
    }

    private void createVariable(final Object[] taskValues) {
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
        if (result instanceof String) {
            for (String key : createdVariables.keySet()) {
                final String madeKey = "data:" + key;
                Log.d(TAG, "replaceVariables: Made key: " + madeKey);
                if (result.toString().contains(madeKey)) {
                    result = result.toString().replaceAll(madeKey, createdVariables.get(key).toString());
                }
            }
        }
        return result;
    }

    private void createFunction(final Object[] taskValues) {
        final Object[] values = new Object[]{taskValues[0], taskValues[1], taskValues[2]};
        final String functionName = taskValues[3].toString();

        createdFunctions.put(functionName, values);
    }

    private Object invokeFunction(final String functionID, final Object[] eventValues) {
        Log.d(TAG, createdFunctions.toString());
        final Object[] taskValues = createdFunctions.get(functionID);

        if (taskValues == null || taskValues.length == 0) {
            Log.d(TAG, "Invalid invoke values provided");
            return "";
        }

        final String componentId = taskValues[0].toString();
        final String functionName = taskValues[1].toString();
        final Object[] parameters = ((YailList) taskValues[2]).toArray();

        if (componentId.equals("self")) {
            final boolean restartOrRemoveNotification = getExitBoolean(parameters);
            if (functionName.equalsIgnoreCase("exit-foreground")) {
                Log.i(TAG, "invokeFunction: " + "Stopping foreground function");
                stopForeground(restartOrRemoveNotification);
                return null;
            } else if (functionName.equalsIgnoreCase("exit")) {
                jobFinished(jobParameters, restartOrRemoveNotification);
                return null;
            } else if (functionName.equalsIgnoreCase("exit-force")) {
                destroyAllComponents();
                jobFinished(jobParameters, restartOrRemoveNotification);
                return null;
            }
        }
        Log.d(TAG, "Invoking function of component ID: " + componentId + ", function name: " + functionName + ", parameters: " + Arrays.toString(parameters));
        return invokeComponent(componentId, functionName, parameters, eventValues);
    }

    private boolean getExitBoolean(final Object[] array) {
        return (array.length <= 0
                || Boolean.parseBoolean(array[0].toString()));
    }

    private Object invokeComponent(final String componentKey, final String functionName, Object[] parameters, Object[] eventValues) {
        try {
            if (eventValues == null) {
                eventValues = new Object[]{};
            }
            if (parameters == null) {
                parameters = new Object[]{};
            }

            final Component component = componentManager.getComponent(componentKey);
            final Method[] methods = component.getClass().getMethods();
            final Method method = findMethod(methods, functionName, parameters.length);

            if (method == null) {
                Log.e(TAG, "Function name: " + functionName + " may not exist");
                return null;
            }

            int index = 0;
            for (Object object : parameters) {
                if (object instanceof String) {
                    String code = object.toString();

                    for (int i = 0; i < eventValues.length; i++) {
                        code = code.replace("{%" + i + "}", eventValues[i].toString());
                    }
                    object = code;
                }

                object = replaceVariables(object);
                Log.i(TAG, "invokeComponent: replaces ----------------: " + object);
                parameters[index] = processValue(object, eventValues);
                index++;
            }

            /*
               Taken from: https://github.com/ysfchn/DynamicComponents-AI2
             */

            final Class<?>[] mRequestedMethodParameters = method.getParameterTypes();
            final ArrayList<Object> mParametersArrayList = new ArrayList<>();

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
            Log.d(TAG, "Invoked method name: " + functionName + ", component ID: " + componentKey + ", invoke result: " + invokeResult);
            return invokeResult;
        } catch (IllegalAccessException | InvocationTargetException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Object processValue(final Object object, final Object[] eventValues) {
        if (object instanceof String[]) {
            final String[] value = (String[]) object;

            Log.d(TAG, "Found interpret value class: " + Arrays.toString(value));

            String text = value[0];
            boolean isCode = Boolean.parseBoolean(value[1]);

            if (isCode) {
                Object interpretResult = Utils.interpret(text, getApplicationContext());
                Log.d(TAG, "Got interpretResult: " + interpretResult.toString());
                return interpretResult;
            } else {
                return invokeReplacement(text, eventValues);
            }
        }
        return object;
    }

    private Object invokeReplacement(String text, Object[] eventValues) {
        if (text.startsWith("invoke:result:[") && text.charAt(text.length() - 1) == ']') {
            String itemIndex = text.substring(15);
            itemIndex = itemIndex.substring(0, itemIndex.length() - 1);

            Log.d(TAG, "Found item index: " + itemIndex);

            try {
                int indexParsed = Integer.parseInt(itemIndex) - 1;
                Object resultItem = "";

                if (invokeResult instanceof YailList) {
                    resultItem = ((YailList) invokeResult).toArray()[indexParsed];
                } else if (invokeResult instanceof List) {
                    resultItem = ((List<?>) invokeResult).get(indexParsed);
                } else {
                    Log.d(TAG, "Unknown list class found: " + invokeResult.getClass());
                }

                Log.d(TAG, "processValue: " + resultItem);

                return resultItem;
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                Log.d(TAG, "The index is not valid");
            }
        } else if (text.equals("invoke:result:empty")) {
            Log.d(TAG, "Received task to check if the invoke result is empty");
            if (invokeResult instanceof String) {
                return invokeResult.toString().isEmpty();
            }

            if (invokeResult instanceof YailList) {
                return ((YailList) invokeResult).toArray().length == 0;
            } else if (invokeResult instanceof List) {
                return ((List<?>) invokeResult).toArray().length == 0;
            }

            Log.e(TAG, "Invalid argument to find the invoke result!");
            return -1;
        } else if (text.equals("invoke:result:length")) {
            Log.d(TAG, "Found task to find length");

            int invokeLength;

            if (invokeResult instanceof String) {
                invokeLength = invokeResult.toString().length();
            } else {
                Object[] arrayList = null;

                if (invokeResult instanceof YailList) {
                    arrayList = ((YailList) invokeResult).toArray();
                } else if (invokeResult instanceof List) {
                    arrayList = ((List<?>) invokeResult).toArray();
                } else {
                    Log.d(TAG, "Unknown class function received to find the length");
                }

                Log.d(TAG, Arrays.toString(arrayList));

                if (arrayList == null) {
                    return -1;
                }
                invokeLength = arrayList.length;
            }

            return invokeLength;
        } else if (text.equals("invoke:result")) {
            return emptyIfNull(invokeResult);
        } else if (text.startsWith("invoke:[") && text.charAt(text.length() - 1) == ']') {
            String functionId = text.substring(8);
            functionId = functionId.substring(0, functionId.length() - 1);
            Log.i(TAG, "processValue: Processing extra value of executing function value: " + functionId);
            return emptyIfNull(invokeFunction(functionId, eventValues));
        }
        return text;
    }

    private void handleNewEvent(final Component component, final String eventName, final Object[] eventParameters) {
        Log.d(TAG, "eventName " + eventName + " values " + Arrays.toString(eventParameters));
        final String componentId = componentManager.getKeyOfComponent(component);

        if (componentId == null) {
            return;
        }

        final ArrayList<Object> valuesList = eventMap.get(componentId);

        if (valuesList == null) {
            return;
        }

        for (int i = 0; i < valuesList.size(); i++) {
            Object[] invokeValues = (Object[]) valuesList.get(i);

            if (invokeValues != null) {
                final String functionID = invokeValues[0].toString();
                final String thisEventName = invokeValues[1].toString();

                if (thisEventName.equals(eventName)) {
                    if (functionID.startsWith("$")) {
                        Log.i(TAG, "Function has an extra function Id");
                        final Object[] values = extraFunctions.get(functionID.substring(1));
                        Log.i(TAG, "Extra function values : " + Arrays.toString(values));
                        final ArrayList<Object[]> results = new ArrayList<>();

                        for (Object object : values) {
                            Object result = Utils.CodeParser.processCode(object.toString(), eventParameters, createdVariables);

                            Log.i(TAG, "handleNewEvent: " + result.toString());

                            if (!(result instanceof Boolean)) {
                                Log.i(TAG, "newEvent: The parsed result is: " + Arrays.toString(((Object[]) result)));
                                results.add((Object[]) result);
                            }
                        }

                        Log.i(TAG, "newEvent: The values received and prepared: " + results);

                        for (final Object[] object : results) {
                            processExtraFunction(object, eventParameters);
                        }
//                        return;
                    } else {
                        invokeFunction(functionID, eventParameters);
                    }
                } else {
                    Log.i(TAG, "newEvent: Event dismissed");
                }
            }
        }
    }

    private void processExtraFunction(final Object[] object, Object[] eventValues) {
        final String functionName = object[0].toString();
        final String input = object[1].toString();
        final Object[] objectParameters = (Object[]) object[2];
        this.createdVariables = (HashMap<String, Object>) object[3];
        Log.i(TAG, "processExtraFunction: variables: " + createdVariables);

        if (functionName.equalsIgnoreCase("function")) {
            invokeFunction(input, objectParameters);
            return;
        } else if (functionName.equalsIgnoreCase("destroy")) {
            destroyComponent(input);
            return;
        } else if(functionName.equalsIgnoreCase("variable")) {
            Object[] vals = new Object[2];
            String[] inputString = input.replaceAll(" ", "").split(",");
            Log.i(TAG, "processExtraFunction: Split result " + Arrays.toString(inputString));
            vals[0] = inputString[0];

            Object varValue = inputString[1];
//            varValue = varValue.replaceAll("invoke:result", String.valueOf(invokeResult));
            varValue = invokeReplacement(varValue.toString(), eventValues);

            vals[1] = varValue;

            createVariable(vals);
            return;
        }
        Log.e(TAG, "Invalid extra function type name received!");
    }

    private void destroyAllComponents() {
        if(componentManager != null) {
            for (final String componentId : componentManager.getKeySet()) {
                destroyComponent(componentId);
            }
        }
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(TAG, "The service stopped");
        Log.d(TAG, "onStopJob: should protect" + activityNoKill);
        if(!activityNoKill && serviceStarted) {
            destroyAllComponents();
        }
        jobFinished(jobParameters, restartAfterKill);
        return restartAfterKill;
    }
}
