package cucumber.runtime;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Utils {
    public static <T> List<T> listOf(int size, T obj) {
        List<T> list = new ArrayList<T>();
        for (int i = 0; i < size; i++) {
            list.add(obj);
        }
        return list;
    }

    public static boolean isInstantiable(Class<?> clazz) {
        boolean isNonStaticInnerClass = !Modifier.isStatic(clazz.getModifiers()) && clazz.getEnclosingClass() != null;
        return Modifier.isPublic(clazz.getModifiers()) && !Modifier.isAbstract(clazz.getModifiers()) && !isNonStaticInnerClass;
    }

    public static boolean hasConstructor(Class<?> clazz, Class[] paramTypes) {
        try {
            clazz.getConstructor(paramTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static Object invoke(final Object target, final Method method, long timeoutMillis, final Object... args) throws Throwable {
        return Timeout.timeout(new Timeout.Callback<Object>() {
            @Override
            public Object call() throws Throwable {
                try {
                    return method.invoke(target, args);
                } catch (IllegalArgumentException e) {
                    throw new CucumberException("Failed to invoke " + MethodFormat.FULL.format(method), e);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                } catch (IllegalAccessException e) {
                    throw new CucumberException("Failed to invoke " + MethodFormat.FULL.format(method), e);
                }
            }
        }, timeoutMillis);
    }

    public static Type listItemType(Type type) {
        return typeArg(type, List.class, 0);
    }

    public static Type mapKeyType(Type type) {
        return typeArg(type, Map.class, 0);
    }

    public static Type mapValueType(Type type) {
        return typeArg(type, Map.class, 1);
    }

    private static Type typeArg(Type type, Class<?> wantedRawType, int index) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class && wantedRawType.isAssignableFrom((Class) rawType)) {
                return parameterizedType.getActualTypeArguments()[index];
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static void ensureParentDirExists(File file) throws IOException {
        if (file.getParentFile() != null && !file.getParentFile().isDirectory()) {
            boolean ok = file.getParentFile().mkdirs();
            if (!ok) {
                throw new IOException("Failed to create directory " + file.getParentFile().getAbsolutePath());
            }
        }
    }

    public static URL toURL(String pathOrUrl) {
        try {
            if (!pathOrUrl.endsWith("/")) {
                pathOrUrl = pathOrUrl + "/";
            }
            if (pathOrUrl.matches("^(file|http|https):.*")) {
                return new URL(pathOrUrl);
            } else {
                return new URL("file:" + pathOrUrl);
            }
        } catch (MalformedURLException e) {
            throw new CucumberException("Bad URL:" + pathOrUrl, e);
        }
    }
}