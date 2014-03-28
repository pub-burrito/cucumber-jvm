package cucumber.api.android;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface RunWithCucumber {
	/**
	 * 
	 * @return default is ""
	 */
    String[] glue() default "";

    /**
     * Set the path to the feature files that you want to run. Path must be relative to the assets folder
     * @return default is "features"
     */
    String[] features() default "features";
    
    /**
     * Set the tags that will filter out and run specific feature files. Use & to separate tags to be applies in conjunction and , for disjunctions.
     * @return default is ""
     */
    String[] tags() default "";
    
    /**
     * Set a regular expression that will filter out and run specific matched features/scenarios.
     * @return default is ""
     */
    String filter() default "";
}
