package cucumber.runtime.java;

import static java.util.Arrays.asList;
import gherkin.TagExpression;
import gherkin.formatter.model.Tag;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import cucumber.api.Scenario;
import cucumber.runtime.CucumberException;
import cucumber.runtime.HookDefinition;
import cucumber.runtime.MethodFormat;
import cucumber.runtime.Utils;

class JavaHookDefinition implements HookDefinition {

	private final static Logger LOG = Logger.getLogger( JavaHookDefinition.class.getSimpleName() );
	static
	{
		LOG.setLevel( Level.FINE );
	}
	
    private final Method method;
    private final int timeout;
    private final TagExpression tagExpression;
    private final int order;
    private final ObjectFactory objectFactory;

    public JavaHookDefinition(Method method, String[] tagExpressions, int order, int timeout, ObjectFactory objectFactory) {
        this.method = method;
        this.timeout = timeout;
        tagExpression = new TagExpression(asList(tagExpressions));
        this.order = order;
        this.objectFactory = objectFactory;
    }

    Method getMethod() {
        return method;
    }

    @Override
    public String getLocation(boolean detail) {
        MethodFormat format = detail ? MethodFormat.FULL : MethodFormat.SHORT;
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

        final Object hookInstance = objectFactory.getInstance(method.getDeclaringClass());
        
        LOG.log( Level.FINE, "\t - Executing hook definition " + hookInstance + "." + method.getName() );

        Utils.invoke(hookInstance, method, timeout, args);
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
