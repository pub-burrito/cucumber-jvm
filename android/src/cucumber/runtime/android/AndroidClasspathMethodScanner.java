package cucumber.runtime.android;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.runtime.CucumberException;
import cucumber.runtime.Utils;
import cucumber.runtime.java.StepDefAnnotation;
import ext.android.test.ClassPathPackageInfoSource;
import ext.com.google.android.collect.Sets;

public class AndroidClasspathMethodScanner {
    private final Collection<Class<? extends Annotation>> mCucumberAnnotationClasses;
    private static ClassPathPackageInfoSource mSource;

    AndroidClasspathMethodScanner(Context context) {
        classPathPackageInfoSource(context);
        mCucumberAnnotationClasses = findCucumberAnnotationClasses();
    }

    public static ClassPathPackageInfoSource classPathPackageInfoSource(Context context) {
        if (mSource == null) {
            String apkPath = context.getPackageCodePath();
            ClassPathPackageInfoSource.setApkPaths(new String[]{apkPath});
            mSource = new ClassPathPackageInfoSource();
        }
        mSource.setClassLoader(context.getClassLoader());
        return mSource;
    }

    /**
     * Registers step definitions and hooks.
     *
     * @param androidBackend the backend where stepdefs and hooks will be registered
     * @param gluePaths      where to look
     */
    void scan(AndroidBackend androidBackend, List<String> gluePaths) {
        for (String gluePath : gluePaths) {
            for (Class<?> glueCodeClass : mSource.getPackageInfo(gluePath).getTopLevelClassesRecursive()) {
                while (glueCodeClass != null && glueCodeClass != Object.class && !Utils.isInstantiable(glueCodeClass)) {
                    // those can't be instantiated without container class present
                    glueCodeClass = glueCodeClass.getSuperclass();
                }
                if (glueCodeClass != null) {
                    for (Method method : glueCodeClass.getMethods()) {
                        scan(androidBackend, method, glueCodeClass);
                    }
                }
            }
        }
    }

    /**
     * Registers step definitions and hooks.
     *
     * @param androidBackend the backend where stepdefs and hooks will be registered.
     * @param method         a candidate for being a stepdef or hook.
     * @param glueCodeClass  the class where the method is declared.
     */
    void scan(AndroidBackend androidBackend, Method method, Class<?> glueCodeClass) {
        for (Class<? extends Annotation> cucumberAnnotationClass : mCucumberAnnotationClasses) {
            Annotation annotation = method.getAnnotation(cucumberAnnotationClass);
            if (annotation != null) {
                if (!method.getDeclaringClass().isAssignableFrom(glueCodeClass)) {
                    throw new CucumberException(String.format("%s isn't assignable from %s", method.getDeclaringClass(), glueCodeClass));
                }
                if (!glueCodeClass.equals(method.getDeclaringClass())) {
                    throw new CucumberException(String.format("You're not allowed to extend classes that define Step Definitions or hooks. %s extends %s", glueCodeClass, method.getDeclaringClass()));
                }
                if (isHookAnnotation(annotation)) {
                    androidBackend.addHook(annotation, method);
                } else if (isStepdefAnnotation(annotation)) {
                    androidBackend.addStepDefinition(annotation, method);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<Class<? extends Annotation>> findCucumberAnnotationClasses() {
        List<Class<? extends Annotation>> annotationClasses = new ArrayList<Class<? extends Annotation>>();
        
        String topLevelPackage = "cucumber.api.java";
        String localizedPackage = String.format( "%s.%s", topLevelPackage, Locale.getDefault().getLanguage() );
        
        Set<Class<?>> classes = Sets.newHashSet();
        classes.addAll( mSource.getPackageInfo(topLevelPackage).getTopLevelClasses() );
        classes.addAll( mSource.getPackageInfo(localizedPackage).getTopLevelClassesRecursive() );
        
        for (Class<?> clazz : classes) {
        	if ( clazz.isAnnotation() ) 
        	{
        		annotationClasses.add( (Class<? extends Annotation>) clazz );
        	}
        }
        
        return annotationClasses;
    }

    private boolean isHookAnnotation(Annotation annotation) {
        Class<? extends Annotation> annotationClass = annotation.annotationType();
        return annotationClass.equals(Before.class) || annotationClass.equals(After.class);
    }

    private boolean isStepdefAnnotation(Annotation annotation) {
        Class<? extends Annotation> annotationClass = annotation.annotationType();
        return annotationClass.getAnnotation(StepDefAnnotation.class) != null;
    }
}
