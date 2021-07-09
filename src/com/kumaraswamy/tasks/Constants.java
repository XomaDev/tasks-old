package com.kumaraswamy.tasks;

class Constants {
    protected static final int TASK_CREATE_FUNCTION = 1;
    protected static final int TASK_INVOKE_FUNCTION = 2;
    protected static final int TASK_CREATE_VARIABLE = 5;
    protected static final int TASK_EXECUTE_FUNCTION = 6;
    protected static final int TASK_REGISTER_EVENT = 7;
    protected static final int TASK_DESTROY_COMPONENT = 8;
    protected static final int TASK_DELAY = 3;
    protected static final int TASK_FINISH = 4;

    protected static final String ID_SEPARATOR = "/";

    protected static final String[] intentsPackages =

            new String[] {"com.miui.securitycenter", "com.letv.android.letvsafe",
            "com.huawei.systemmanager", "com.huawei.systemmanager",
            "com.huawei.systemmanager", "com.coloros.safecenter",
            "com.coloros.safecenter", "com.oppo.safe",
            "com.iqoo.secure", "com.iqoo.secure",
            "com.vivo.permissionmanager", "com.htc.pitroad",
            "com.asus.mobilemanager", "com.transsion.phonemanager"};

    protected static final String[] intentSources =

            new String[] {"com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.letv.android.letvsafe.AutobootManageActivity",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.htc.pitroad.landingpage.activity.LandingPageActivity",
            "com.asus.mobilemanager.MainActivity",
            "com.itel.autobootmanager.activity.AutoBootMgrActivity"};
}
