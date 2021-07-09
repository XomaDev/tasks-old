package com.kumaraswamy.tasks;

import android.app.job.JobInfo;
import android.content.Context;
import android.util.Log;
import bsh.EvalError;
import bsh.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

public class Utils {
    private static final String TAG = "BackgroundTasks.Utils";

    public static Object[] toObjectArray(Object... array) {
        return new ArrayList<>(Arrays.asList(array)).toArray();
    }

    /*
       Taken from: https://github.com/ysfchn/DynamicComponents-AI2
     */

    public static Method findMethod(Method[] methods, String name, int parameterCount) {
        name = name.replaceAll("[^a-zA-Z0-9]", "");
        for (Method method : methods) {
            int methodParameterCount = method.getParameterTypes().length;
            if (method.getName().equals(name) && methodParameterCount == parameterCount) {
                return method;
            }
        }

        return null;
    }

    protected static int getNetworkInt(String requiredNetwork) {
        requiredNetwork = requiredNetwork.toUpperCase();
        int network_type = JobInfo.NETWORK_TYPE_NONE;
        if(requiredNetwork.equals("ANY")) {
            network_type = JobInfo.NETWORK_TYPE_ANY;
        } else if(requiredNetwork.equals("CELLULAR")) {
            network_type = JobInfo.NETWORK_TYPE_CELLULAR;
        } else if(requiredNetwork.equals("UNMETERED")) {
            network_type = JobInfo.NETWORK_TYPE_UNMETERED;
        } else if(requiredNetwork.equals("NOT_ROAMING")) {
            network_type = JobInfo.NETWORK_TYPE_NOT_ROAMING;
        }
        return network_type;
    }

    public static void saveTask(Context context, ArrayList<Object> tasks, int jobID) {
        try {
            FileOutputStream outputStream = new FileOutputStream(getTaskFileName(context, jobID));
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(tasks);

            outputStream.close();
            objectOutputStream.close();
            Log.d(TAG, "Saved the task to database");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static ArrayList<Object> readTask(Context context, int jobID) {
        ArrayList<Object> result = null;

        try {
            FileInputStream inputStream = new FileInputStream(getTaskFileName(context, jobID));
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            result = (ArrayList<Object>) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        if(result == null) {
            Log.e(TAG, "Task object is null");
            return null;
        } else {
            return result;
        }
    }

    protected static boolean clearTask(final int taskID, final Context context) {
        File file = getTaskFileName(context, taskID);
        if(file.exists()) {
            return file.delete();
        }
        return true;
    }

    private static File getTaskFileName(final Context context, final int jobID) {
        return new File(getInternalPath(context), "BackgroundTasksTask" + jobID + ".txt");
    }

    private static String getInternalPath(final Context context) {
        File file = new File(context.getExternalFilesDir(null).getPath(), "BackgroundTasks");
        Log.d(TAG, "getInternalPath: " + file.mkdir());
        return file.getPath();
    }

    protected static Object interpret(final String code, final Context activity) {
        Interpreter interpreter = new Interpreter();

        try {
            interpreter.set("context", activity);
            Object result = interpreter.eval(code);

            return result == null
                    ? "" :
                    result;
        } catch (EvalError evalError) {
            evalError.printStackTrace();
        }
        return "";
    }


    public static class CodeParser {
        public static Object processCode(final String code, final Object[] parameters) {
            final StringBuilder conditionConstructor = new StringBuilder();

            boolean firstRun = false;
            boolean secondRun = false;

            for(Character character: code.toCharArray()) {
                System.out.println("processing char: " + character);
                if(character == ':' && !firstRun) {
                    firstRun = true;
                    System.out.println("Ignored first character ':'");
                } else if (character == ':' && !secondRun){
                    secondRun = true;
                    System.out.println("Ignored second character ':'");
                } else {
                    if (!firstRun || !secondRun) {
                        conditionConstructor.append(character);
                    }
                }
            }

            String execute = code.substring(code.lastIndexOf("::") + 3);
            String condition = conditionConstructor.toString();

            if(execute.charAt(0) == ' ') {
                execute = execute.substring(1);
            }

            if(condition.charAt(condition.length() - 1) == ' ') {
                condition = condition.substring(0, condition.length() - 1);
            }

            Object result = false;
            final Interpreter interpreter = new Interpreter();

            try {
                for (int i = 0; i < parameters.length; i++) {
                    interpreter.set("val" + i, parameters[i]);
                }
                result = interpreter.eval("return " + condition + ";");
            } catch (EvalError evalError) {
                Log.e("Interpreter error", evalError.getMessage());
            }

            Log.i(TAG, "processCode: The custom function result: " + result);

            if(result instanceof Boolean && (boolean) result) {
                final String[] splitCurve = execute.split("\\(");
                final String functionType = splitCurve[0];
                final String functionID = execute.substring(functionType.length() + 1, execute.length() - 1);
                return new String[] {functionType, functionID};
            } else {
                return "none";
            }
        }
    }
}
