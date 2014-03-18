package cucumber.runtime.model;

import gherkin.I18n;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cucumber.runtime.CucumberException;
import cucumber.runtime.FeatureBuilder;
import cucumber.runtime.PathWithPredicates.PathPredicateFilter;
import cucumber.runtime.Runtime;
import cucumber.runtime.io.Resource;
import cucumber.runtime.io.ResourceLoader;

public class CucumberFeature {
    private final String uri;
    private final Feature feature;
    private CucumberBackground cucumberBackground;
    private StepContainer currentStepContainer;
    private final List<CucumberTagStatement> cucumberTagStatements = new ArrayList<CucumberTagStatement>();
    private I18n i18n;
    private CucumberScenarioOutline currentScenarioOutline;

    public static List<CucumberFeature> load(ResourceLoader resourceLoader, List<String> featurePaths, final List<Object> filters) {    	
    	final List<CucumberFeature> cucumberFeatures = new ArrayList<CucumberFeature>();
        final FeatureBuilder builder = new FeatureBuilder(cucumberFeatures);
        
        List<Object> otherFilters = new ArrayList<Object>();
        
        Map<String, PathPredicateFilter> pathFilters = new LinkedHashMap<String, PathPredicateFilter>();
        for ( Object filter : filters )
		{
			if ( filter instanceof PathPredicateFilter )
			{
				PathPredicateFilter pathFilter = (PathPredicateFilter) filter;
				pathFilters.put( pathFilter.getOriginalPath(), pathFilter );
			}
			else
			{
				otherFilters.add( filter );
			}
		}
        
        try
        {
	        boolean resourceFound = false;
	        for (String featurePath : featurePaths) 
	        {
	        	PathPredicateFilter pathFilter = pathFilters.get( featurePath );
	        	
	            String rootPath = pathFilter != null ? pathFilter.getPath() : featurePath;
	            
				Iterable<Resource> resources = resourceLoader.resources( rootPath, ".feature");
				
	            for (Resource resource : resources) {
	            	if ( pathFilter == null || pathFilter.matches( resource ) )
	            	{
		                resourceFound = true;
		                builder.parse(resource, otherFilters);
	            	}
	            }
	        }
	        if (cucumberFeatures.isEmpty()) {
	            if (resourceFound) {
	                throw new CucumberException(String.format("None of the features at %s matched the filters: %s", featurePaths, filters));
	            } else {
	                throw new CucumberException(String.format("No features found at %s", featurePaths));
	            }
	        }
	        Collections.sort(cucumberFeatures, new CucumberFeatureUriComparator());
	        return cucumberFeatures;
        }
        finally
        {
        	if ( builder != null) builder.close();
        }
    }


    public CucumberFeature(Feature feature, String uri) {
        this.feature = feature;
        this.uri = uri;
    }

    public void background(Background background) {
        cucumberBackground = new CucumberBackground(this, background);
        currentStepContainer = cucumberBackground;
    }

    public void scenario(Scenario scenario) {
        CucumberTagStatement cucumberTagStatement = new CucumberScenario(this, cucumberBackground, scenario);
        currentStepContainer = cucumberTagStatement;
        cucumberTagStatements.add(cucumberTagStatement);
    }

    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        CucumberScenarioOutline cucumberScenarioOutline = new CucumberScenarioOutline(this, cucumberBackground, scenarioOutline);
        currentScenarioOutline = cucumberScenarioOutline;
        currentStepContainer = cucumberScenarioOutline;
        cucumberTagStatements.add(cucumberScenarioOutline);
    }

    public void examples(Examples examples) {
        currentScenarioOutline.examples(examples);
    }

    public void step(Step step) {
        currentStepContainer.step(step);
    }

    public Feature getGherkinFeature() {
        return feature;
    }

    public List<CucumberTagStatement> getFeatureElements() {
        return cucumberTagStatements;
    }

    public void setI18n(I18n i18n) {
        this.i18n = i18n;
    }

    public I18n getI18n() {
        return i18n;
    }

    public String getUri() {
        return uri;
    }

    public void run(Formatter formatter, Reporter reporter, Runtime runtime) {
        formatter.uri(getUri());
        formatter.feature(getGherkinFeature());

        for (CucumberTagStatement cucumberTagStatement : getFeatureElements()) {
            //Run the scenario, it should handle before and after hooks
            cucumberTagStatement.run(formatter, reporter, runtime);
        }
        formatter.eof();

    }

    private static class CucumberFeatureUriComparator implements Comparator<CucumberFeature> {
        @Override
        public int compare(CucumberFeature a, CucumberFeature b) {
            return a.getUri().compareTo(b.getUri());
        }
    }
}
