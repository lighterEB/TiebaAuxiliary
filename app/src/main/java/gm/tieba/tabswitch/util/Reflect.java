package gm.tieba.tabswitch.util;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.XposedHelpers;

public class Reflect {
    public static Object getObjectField(Object instance, String className) throws Throwable {
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.get(instance) != null && Objects.equals(field.get(instance).getClass().getName(), className))
                return field.get(instance);
        }
        return null;
    }

    public static String pbContentParser(Object instance, String fieldName) throws Throwable {
        List<?> contents = (List<?>) XposedHelpers.getObjectField(instance, fieldName);
        StringBuilder pbContent = new StringBuilder();
        for (int i = 0; i < contents.size(); i++)
            pbContent.append(XposedHelpers.getObjectField(contents.get(i), "text"));
        return pbContent.toString();
    }
}