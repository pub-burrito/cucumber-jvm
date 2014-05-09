package cucumber.runtime;

import gherkin.formatter.Argument;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JdkPatternArgumentMatcher {
    private final Pattern pattern;
    private final Matcher matcher;

    public JdkPatternArgumentMatcher(Pattern pattern) {
        this.pattern = pattern;
        this.matcher = pattern.matcher( "" );
    }

    public List<Argument> argumentsFrom(String stepName) {
        matcher.reset(stepName);
        
        if (matcher.lookingAt()) {
            List<Argument> arguments = new ArrayList<Argument>(matcher.groupCount());
            for (int i = 1; i <= matcher.groupCount(); i++) {
                arguments.add(new Argument(matcher.start(i), matcher.group(i)));
            }
            return arguments;
        } else {
            return null;
        }
    }

}
