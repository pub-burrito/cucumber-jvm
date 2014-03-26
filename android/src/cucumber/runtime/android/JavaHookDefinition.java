package cucumber.runtime.android;

import static java.util.Arrays.asList;
import gherkin.TagExpression;
import gherkin.formatter.model.Tag;

import java.lang.reflect.Method;
import java.util.Collection;

import android.util.Log;
import cucumber.api.Scenario;
import cucumber.api.android.CucumberInstrumentation;
import cucumber.runtime.CucumberException;
import cucumber.runtime.HookDefinition;
import cucumber.runtime.Utils;
import cucumber.runtime.java.ObjectFactory;

class JavaHookDefinition implements HookDefinition {

    private final Method method;
    private final long timeout;
    private final TagExpression tagExpression;
    private final int order;
    private final ObjectFactory objectFactory;

    public JavaHookDefinition(Method method, String[] tagExpressions, int order, long timeout, ObjectFactory objectFactory) {
        this.method = method;
        this.timeout = timeout;
        tagExpression = new TagExpression(asList(tagExpressions));
        this.order = order;
        this.objectFactory = objectFactory;
    }

    @Override
    public String getLocation(boolean detail) {
        AndroidMethodFormat format = detail ? AndroidMethodFormat.FULL : AndroidMethodFormat.SHORT;
        return format.format(method);
    }

    @Override
    public void execute(Scenario scenario) throws Throwable {
    	Object[] args;
        switch (method.getParameterTypes().length) {
            case 0:
                args = new Object[0];
                break;
            case 1:
                if (!Scenario.class.equals(method.getParameterTypes()[0])) {
                    throw new CucumberException("When a hook declares an argument it must be of type " + Scenario.class.getName() + ". " + method.toString());
                }
                args = new Object[]{scenario};
                break;
            default:
                throw new CucumberException("Hooks must declare 0 or 1 arguments. " + method.toString());
        }

    	if (!CucumberInstrumentation.skip) {
    		final Object hookInstance = objectFactory.getInstance(method.getDeclaringClass());
    		
    		Log.v(CucumberInstrumentation.TAG, "\t - Executing hook definition " + hookInstance + "." + method.getName());

    		Utils.invoke(hookInstance, method, timeout, args);
    	}
    }

    @Override
    public boolean matches(Collection<Tag> tags) {
        return tagExpression.evaluate(tags);
    }

    @Override
    public int getOrder() {
        return order;
    }
}
