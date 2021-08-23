package gm.tieba.tabswitch.hooker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import org.jf.baksmali.Adaptors.ClassDefinition;
import org.jf.baksmali.Adaptors.MethodDefinition;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.baksmali.formatter.BaksmaliWriter;
import org.jf.dexlib2.dexbacked.raw.HeaderItem;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.Constants;
import gm.tieba.tabswitch.dao.AcRules;
import gm.tieba.tabswitch.dao.Preferences;
import gm.tieba.tabswitch.util.DisplayUtils;

public class AntiConfusionHelper {
    static List<String> matcherList = new ArrayList<>();

    static {
        Constants.getMatchers().values().forEach(strings -> matcherList.addAll(Arrays.asList(strings)));
    }

    public static List<String> getRulesLost() {
        List<String> list = new ArrayList<>(matcherList);
        list.removeIf(AcRules::isRuleFound);
        return list;
    }

    public static boolean isVersionChanged(Context context) {
        SharedPreferences tsConfig = context.getSharedPreferences("TS_config", Context.MODE_PRIVATE);
        return !tsConfig.getString("anti-confusion_version", "unknown").equals(getTbVersion(context));
    }

    public static boolean isDexChanged(Context context) {
        try {
            ZipFile zipFile = new ZipFile(new File(context.getPackageResourcePath()));
            byte[] signature;
            try (InputStream in = zipFile.getInputStream(zipFile.getEntry("classes.dex"))) {
                signature = calcSignature(in);
            }
            return Arrays.hashCode(signature) != Preferences.getSignature();
        } catch (IOException e) {
            XposedBridge.log(e);
        }
        return false;
    }

    public static byte[] calcSignature(InputStream dataStoreInput) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }

        dataStoreInput.skip(HeaderItem.SIGNATURE_DATA_START_OFFSET);
        byte[] buffer = new byte[4 * 1024];
        int bytesRead = dataStoreInput.read(buffer);
        while (bytesRead >= 0) {
            md.update(buffer, 0, bytesRead);
            bytesRead = dataStoreInput.read(buffer);
        }

        byte[] signature = md.digest();
        if (signature.length != HeaderItem.SIGNATURE_SIZE) {
            throw new RuntimeException("unexpected digest write: " + signature.length + " bytes");
        }
        return signature;
    }

    static void searchAndSave(ClassDef classDef, SQLiteDatabase db) throws IOException {
        for (Method method : classDef.getMethods()) {
            MethodImplementation methodImpl = method.getImplementation();
            if (methodImpl == null) {
                continue;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedWriter bufWriter = new BufferedWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
            try (BaksmaliWriter writer = new BaksmaliWriter(bufWriter, null)) {
                ClassDefinition classDefinition = new ClassDefinition(new BaksmaliOptions(), classDef);
                MethodDefinition methodDefinition = new MethodDefinition(classDefinition, method, methodImpl);
                methodDefinition.writeTo(writer);
                writer.flush();
                for (String matcher : matcherList) {
                    if (baos.toString().contains(matcher)) {
                        String clazz = classDef.getType();
                        clazz = clazz.substring(clazz.indexOf("L") + 1,
                                clazz.indexOf(";")).replace("/", ".");
                        db.execSQL("insert into rules(rule, class, method) values(?, ?, ?)",
                                new Object[]{matcher, clazz, method.getName()});
                    }
                }
            }
        }
    }

    public static String getTbVersion(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo applicationInfo = pm.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            switch ((Integer) applicationInfo.metaData.get("versionType")) {
                case 3:
                    return pm.getPackageInfo(context.getPackageName(), 0).versionName;
                case 2:
                    return String.valueOf(applicationInfo.metaData.get("grayVersion"));
                case 1:
                    return String.valueOf(applicationInfo.metaData.get("subVersion"));
                default:
                    throw new PackageManager.NameNotFoundException("unknown tb version");
            }
        } catch (PackageManager.NameNotFoundException e) {
            XposedBridge.log(e);
            return "unknown";
        }
    }

    @SuppressLint("ApplySharedPref")
    public static void saveAndRestart(Activity activity, String value, Class<?> springboardActivity) {
        SharedPreferences.Editor editor = activity.getSharedPreferences("TS_config", Context.MODE_PRIVATE).edit();
        editor.putString("anti-confusion_version", value);
        editor.commit();
        if (springboardActivity == null) DisplayUtils.restart(activity);
        else {
            XposedHelpers.findAndHookMethod(springboardActivity, "onCreate", Bundle.class, new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    DisplayUtils.restart(activity);
                }
            });
            Intent intent = new Intent(activity, springboardActivity);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
        }
    }
}
