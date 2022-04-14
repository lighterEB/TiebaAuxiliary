package gm.tieba.tabswitch.hooker.eliminate;

import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.Constants;
import gm.tieba.tabswitch.XposedContext;
import gm.tieba.tabswitch.dao.AcRules;
import gm.tieba.tabswitch.hooker.IHooker;
import gm.tieba.tabswitch.util.ReflectUtils;

public class PurgeMy extends XposedContext implements IHooker {
    public void hook() throws Throwable {
        XposedHelpers.findAndHookMethod("com.baidu.tieba.flutter.base.view.FlutterDelegateStatic", sClassLoader,
                "createFragmentTabStructure", XC_MethodReplacement.returnConstant(null));
        AcRules.findRule(Constants.getMatchers().get(PurgeMy.class), (AcRules.Callback) (matcher, clazz, method) -> {
            switch (matcher) {
                case "Lcom/baidu/tieba/R$drawable;->icon_pure_topbar_store44_svg:I": // 商店
                    XposedHelpers.findAndHookMethod(clazz, sClassLoader, method, int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // R.id.person_navigation_dressup_img
                            var imageView = (ImageView) ReflectUtils.getObjectField(param.thisObject, 4);
                            imageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case "Lcom/baidu/tieba/R$drawable;->person_center_red_tip_shape:I": // 分割线
                    if ("com.baidu.tieba.post.PersonPostActivity".equals(clazz)) {
                        break;
                    }
                    for (var md : XposedHelpers.findClass(clazz, sClassLoader).getDeclaredMethods()) {
                        if (Arrays.toString(md.getParameterTypes()).equals("[interface com.baidu.tbadk.TbPageContext, int]")) {
                            XposedBridge.hookMethod(md, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    // R.id.function_item_bottom_divider
                                    var view = (View) ReflectUtils.getObjectField(param.thisObject, 10);
                                    view.setVisibility(View.GONE);
                                }
                            });
                        }
                    }
                    break;
                case "\"https://tieba.baidu.com/mo/q/duxiaoman/index?noshare=1\"": // 我的ArrayList
                    for (var md : XposedHelpers.findClass(clazz, sClassLoader).getDeclaredMethods()) {
                        if (Arrays.toString(md.getParameterTypes()).equals("[class tbclient.Profile.ProfileResIdl]")) {
                            XposedBridge.hookMethod(md, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    var list = (ArrayList<?>) ReflectUtils.getObjectField(param.thisObject, ArrayList.class);
                                    list.removeIf(o -> {
                                        try {
                                            ReflectUtils.getObjectField(o, "com.baidu.tbadk.core.data.UserData");
                                        } catch (NoSuchFieldError e) {
                                            return true;
                                        }
                                        return Arrays.stream(o.getClass().getDeclaredFields()).anyMatch(field -> {
                                            field.setAccessible(true);
                                            try {
                                                var obj = field.get(o);
                                                if (obj instanceof String) {
                                                    var type = (String) obj;
                                                    if (!type.startsWith("http")
                                                            && !type.equals("我的收藏")
                                                            && !type.equals("浏览历史")
                                                            && !type.equals("服务中心")) {
                                                        return true;
                                                    }
                                                }
                                            } catch (IllegalAccessException e) {
                                                XposedBridge.log(e);
                                            }
                                            return false;
                                        });
                                    });
                                }
                            });
                        }
                    }
                    break;
            }
        });
    }
}