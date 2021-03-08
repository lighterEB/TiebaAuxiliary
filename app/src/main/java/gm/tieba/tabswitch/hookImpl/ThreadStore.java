package gm.tieba.tabswitch.hookImpl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.Hook;
import gm.tieba.tabswitch.R;
import gm.tieba.tabswitch.util.DisplayHelper;

public class ThreadStore extends Hook {
    private static volatile String regex = "";

    public static void hook(ClassLoader classLoader) throws Throwable {
        XposedHelpers.findAndHookMethod("com.baidu.tieba.myCollection.CollectTabActivity", classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                TextView edit = activity.findViewById(classLoader.loadClass("com.baidu.tieba.R$id").getField("right_textview").getInt(null));
                TextView textView = new TextView(activity);
                textView.setTextSize(DisplayHelper.px2Dip(activity, edit.getTextSize()));
                textView.setTextColor(edit.getTextColors());
                textView.setText("搜索");
                LinearLayout naviRightButton = activity.findViewById(classLoader.loadClass("com.baidu.tieba.R$id").getField("navi_right_button").getInt(null));
                naviRightButton.addView(textView, 0);
                textView.setOnClickListener(v -> showRegexDialog(activity));
            }
        });
        for (int i = 0; i < ruleMapList.size(); i++) {
            Map<String, String> map = ruleMapList.get(i);
            if (Objects.equals(map.get("rule"), "\"c/f/post/threadstore\""))
                XposedHelpers.findAndHookMethod(map.get("class"), classLoader, map.get("method"), Boolean[].class, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Field[] fields = param.getResult().getClass().getDeclaredFields();
                        for (Field field : fields) {
                            field.setAccessible(true);
                            if (field.get(param.getResult()) instanceof ArrayList) {
                                ArrayList<?> arrayList = (ArrayList<?>) field.get(param.getResult());
                                if (arrayList == null) return;
                                for (int j = 0; j < arrayList.size(); j++) {
                                    // com.baidu.tbadk.baseEditMark.MarkData
                                    Field mTitle = arrayList.get(j).getClass().getDeclaredField("mTitle");
                                    Field mForumName = arrayList.get(j).getClass().getDeclaredField("mForumName");
                                    Field mAuthorName = arrayList.get(j).getClass().getDeclaredField("mAuthorName");
                                    mTitle.setAccessible(true);
                                    mForumName.setAccessible(true);
                                    mAuthorName.setAccessible(true);
                                    String title = (String) mTitle.get(arrayList.get(j));
                                    String forumName = (String) mForumName.get(arrayList.get(j));
                                    String authorName = (String) mAuthorName.get(arrayList.get(j));
                                    if (!Pattern.compile(regex).matcher(title).find() &&
                                            !Pattern.compile(regex).matcher(forumName).find() &&
                                            !Pattern.compile(regex).matcher(authorName).find()) {
                                        arrayList.remove(j);
                                        j--;
                                    }
                                }
                                return;
                            }
                        }
                    }
                });
        }
    }

    private static void showRegexDialog(Activity activity) {
        EditText editText = new EditText(activity);
        editText.setHint("请输入正则表达式，如.*");
        editText.setText(regex);
        editText.selectAll();
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setTextSize(18);
        editText.requestFocus();
        AlertDialog alertDialog;
        if (DisplayHelper.isLightMode(activity)) {
            alertDialog = new AlertDialog.Builder(activity, AlertDialog.THEME_HOLO_LIGHT)
                    .setTitle("搜索").setView(editText).setCancelable(false)
                    .setNeutralButton("|", (dialogInterface, i) -> {
                    }).setNegativeButton("取消", (dialogInterface, i) -> {
                    }).setPositiveButton("确定", (dialogInterface, i) -> {
                    }).create();
        } else {
            alertDialog = new AlertDialog.Builder(activity, AlertDialog.THEME_HOLO_DARK)
                    .setTitle("搜索").setView(editText).setCancelable(false)
                    .setNeutralButton("|", (dialogInterface, i) -> {
                    }).setNegativeButton("取消", (dialogInterface, i) -> {
                    }).setPositiveButton("确定", (dialogInterface, i) -> {
                    }).create();
            editText.setTextColor(Hook.modRes.getColor(R.color.colorPrimary, null));
            editText.setHintTextColor(Hook.modRes.getColor(R.color.colorPrimary, null));
        }
        alertDialog.show();
        alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            int selectionStart = editText.getSelectionStart();
            int selectionEnd = editText.getSelectionEnd();
            String sub1 = editText.getText().toString().substring(0, selectionEnd);
            String sub2 = editText.getText().toString().substring(selectionEnd);
            editText.setText(String.format("%s|%s", sub1, sub2));
            if (selectionStart == selectionEnd) editText.setSelection(selectionEnd + 1);
            else editText.setSelection(selectionStart, selectionEnd + 1);
        });
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                Pattern.compile(editText.getText().toString());
                regex = editText.getText().toString();
                activity.finish();
                Intent intent = new Intent().setClassName(activity, "com.baidu.tieba.myCollection.CollectTabActivity");
                activity.startActivity(intent);
                alertDialog.dismiss();
            } catch (PatternSyntaxException e) {
                Toast.makeText(activity, Log.getStackTraceString(e), Toast.LENGTH_SHORT).show();
            }
        });
    }
}