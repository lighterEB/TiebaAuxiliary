package gm.tieba.tabswitch.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import de.robv.android.xposed.XposedBridge;
import gm.tieba.tabswitch.BaseHooker;
import gm.tieba.tabswitch.BuildConfig;
import gm.tieba.tabswitch.dao.Preferences;
import gm.tieba.tabswitch.hooker.TSPreference;
import gm.tieba.tabswitch.hooker.TSPreferenceHelper;
import gm.tieba.tabswitch.widget.TbToast;

public class TraceChecker extends BaseHooker {
    private int mCount;
    private final TSPreferenceHelper.PreferenceLayout mPreferenceLayout;
    private final String JAVA = "java";
    private final String C = "c";
    private final String S = "syscall";

    public TraceChecker(TSPreferenceHelper.PreferenceLayout preferenceLayout) {
        mPreferenceLayout = preferenceLayout;
    }

    public void checkAll() {
        mCount = 0;
        while (mPreferenceLayout.getChildAt(3) != null) {
            mPreferenceLayout.removeViewAt(3);
        }
        getContext().getExternalFilesDir(null).mkdirs();
        files();
        maps();
        mounts();
        modules();
        classloader();
        stackTrace();
        preferences();
        TbToast.showTbToast(mCount > 0 ? String.format(Locale.CHINA, "%s\n检测出%d处痕迹",
                randomToast(), mCount) : "未检测出痕迹", TbToast.LENGTH_SHORT);
    }

    private class ResultBuilder {
        StringBuilder mResult;
        private static final String INDENT = "　 ";

        private ResultBuilder(String text) {
            mResult = new StringBuilder("检测" + text + " -> ");
        }

        private void addTrace(String tag, String msg) {
            if (msg == null) return;
            mResult.append("\n").append(INDENT).append(tag).append(": ").append(msg);
            mCount++;
        }

        private void show() {
            String result = mResult.toString();
            XposedBridge.log(result);
            mPreferenceLayout.addView(TSPreferenceHelper.createTextView(result));
        }
    }

    private void files() {
        ResultBuilder result = new ResultBuilder("文件");
        String[] paths = new String[]{getContext().getFilesDir().getParent()
                .replace(getContext().getPackageName(), BuildConfig.APPLICATION_ID),
                getContext().getExternalFilesDir(null).getParent()
                        .replace(getContext().getPackageName(), BuildConfig.APPLICATION_ID),
                getContext().getDatabasePath("Rules.db").getPath(),
                getContext().getFilesDir().getParent() + File.separator + "shared_prefs"
                        + File.separator + "TS_preferences.xml",
                getContext().getFilesDir().getParent() + File.separator + "shared_prefs"
                        + File.separator + "TS_config.xml"};
        for (String path : paths) {
            if (new File(path).exists()) result.addTrace(JAVA, path);
            if (Native.access(path) == 0) result.addTrace(C, path);
            if (Native.sysaccess(path) == 0) result.addTrace(S, path);
        }
        result.show();
    }

    private void maps() {
        ResultBuilder result = new ResultBuilder(" /proc/self/maps");
        try {
            BufferedReader br = new BufferedReader(new FileReader(String.format(Locale.CHINA,
                    "/proc/%d/maps", Process.myPid())));
            String line;
            do {
                line = br.readLine();
                if (line != null && (line.contains(BuildConfig.APPLICATION_ID)
                        || line.contains("/data/app") && !line.contains("com.google.android")
                        && !line.contains(getContext().getPackageName()))) {
                    result.addTrace(JAVA, line);
                }
            } while (line != null);
        } catch (IOException e) {
            XposedBridge.log(e);
            result.addTrace(JAVA, e.getMessage());
        }
        result.show();
    }

    private void mounts() {
        ResultBuilder result = new ResultBuilder("挂载");
        try {
            BufferedReader br = new BufferedReader(new FileReader(String.format(Locale.CHINA,
                    "/proc/%d/mountinfo", Process.myPid())));
            List<String> paths = new ArrayList<>();
            String lastPath = getContext().getExternalFilesDir(null).getPath();
            while (!paths.contains(Environment.getExternalStorageDirectory().getPath())) {
                lastPath = IO.getParent(lastPath);
                if (!lastPath.endsWith("/data")) paths.add(lastPath);
            }

            String line;
            do {
                line = br.readLine();
                for (String path : paths) {
                    if (line != null && line.contains(String.format(" %s ", path))) {
                        result.addTrace(JAVA, line);
                    }
                }
            } while (line != null);
        } catch (IOException e) {
            XposedBridge.log(e);
            result.addTrace(JAVA, e.getMessage());
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                for (File f : getContext().getExternalFilesDir(null).getParentFile()
                        .getParentFile().listFiles()) {
                    result.addTrace(JAVA, f.getPath());
                }
            } catch (NullPointerException ignored) {
            }
        }
        result.show();
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void modules() {
        ResultBuilder result = new ResultBuilder("包管理器");
        List<String> modules = new ArrayList<>();
        PackageManager pm = getContext().getPackageManager();

        for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_META_DATA)) {
            ApplicationInfo app = pkg.applicationInfo;
            if (app.metaData != null && app.metaData.containsKey("xposedmodule")) {
                modules.add(pm.getApplicationLabel(pkg.applicationInfo).toString());
            }
        }

        Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
        intentToResolve.addCategory("de.robv.android.xposed.category.MODULE_SETTINGS");
        List<ResolveInfo> ris = pm.queryIntentActivities(intentToResolve, 0);
        for (ResolveInfo ri : ris) {
            String name = ri.loadLabel(pm).toString();
            if (!modules.contains(name)) modules.add(name);
        }

        if (modules.size() > 0) {
            result.addTrace(JAVA, modules.toString());
        }
        result.show();
    }

    private void classloader() {
        ResultBuilder result = new ResultBuilder("类加载器");
        String[] classes = new String[]{"de.robv.android.xposed.XposedBridge",
                "gm.tieba.tabswitch.XposedInit", "gm.tieba.tabswitch.util.Native"};
        for (String clazz : classes) {
            try {
                Class.forName(clazz);
                result.addTrace(JAVA, clazz);
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (Native.findXposed()) result.addTrace(C, "FOUND_XPOSED");
        result.show();
    }

    private void stackTrace() {
        ResultBuilder result = new ResultBuilder("堆栈");
        for (String st : TSPreference.sStes) {
            result.addTrace(JAVA, st);
        }
        if (!Preferences.getBoolean("hide") || BuildConfig.DEBUG) result.show();
    }

    private void preferences() {
        ResultBuilder result = new ResultBuilder("偏好");
        String[] sps = new String[]{"TS_preferences", "TS_config"};
        for (String sp : sps) {
            if (getContext().getSharedPreferences(sp, Context.MODE_PRIVATE)
                    .getAll().keySet().size() != 0) result.addTrace(JAVA, sp);
        }
        result.show();
    }

    private String randomToast() {
        switch (new Random().nextInt(9)) {
            case 0:
                return "没收尾巴球";
            case 1:
                return "尾巴捏捏";
            case 2:
                return "没收尾巴";
            case 3:
                return "点燃尾巴";
            case 4:
                return "捏尾巴";
            case 5:
                return "若要人不知，除非己莫为";
            case 6:
                return "哼！你满身都是破绽";
            case 7:
                return "checkmate";
            case 8:
                return "Xposed 无处可逃";
            default:
                return "";
        }
    }
}
