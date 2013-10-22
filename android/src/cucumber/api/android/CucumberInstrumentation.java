package cucumber.api.android;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.test.InstrumentationTestRunner;
import android.text.TextUtils;
import android.util.Log;
import cucumber.deps.difflib.StringUtills;
import cucumber.runtime.Backend;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.android.AndroidBackend;
import cucumber.runtime.android.AndroidClasspathMethodScanner;
import cucumber.runtime.android.AndroidFormatter;
import cucumber.runtime.android.AndroidResourceLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberExamples;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberScenario;
import cucumber.runtime.model.CucumberScenarioOutline;
import cucumber.runtime.model.CucumberTagStatement;
import ext.android.test.ClassPathPackageInfoSource;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;
import gherkin.formatter.model.TagStatement;

public class CucumberInstrumentation extends InstrumentationTestRunner {
    public static final String ARGUMENT_TEST_CLASS = "class";
    public static final String ARGUMENT_TEST_PACKAGE = "package";
    public static final String REPORT_VALUE_ID = "InstrumentationTestRunner";
    public static final String REPORT_KEY_NUM_TOTAL = "numtests";
    public static final String REPORT_KEY_NUM_CURRENT = "current";
    public static final String REPORT_KEY_NAME_CLASS = "class";
    public static final String REPORT_KEY_NAME_TEST = "test";
    public static final String REPORT_KEY_NAME_WORD = "keyword";
    public static final String REPORT_KEY_EXAMPLES = "examples";
    public static final String REPORT_KEY_STEPS = "steps";
    public static final int REPORT_VALUE_RESULT_START = 1;
    public static final int REPORT_VALUE_RESULT_ERROR = -1;
    public static final int REPORT_VALUE_RESULT_FAILURE = -2;
    public static final String REPORT_KEY_STACK = "stack";
    public static final String TAG = "cucumber-android";
    private RuntimeOptions mRuntimeOptions;
    private ResourceLoader mResourceLoader;
    private ClassLoader mClassLoader;
    private Runtime mRuntime;
    private String mPackageOfTests;
    private String mFeatures;
    private String[] mTags;
    private String mFilter;
    
    public static boolean skip = false;
    
    private SharedPreferences preferences() {
    	return getContext().getSharedPreferences( getClass().getName(), Context.MODE_MULTI_PROCESS );

    }
    private boolean shouldSkip() {
    	return 
    		preferences()
    		.getBoolean( "skip", false );
    }
    
    private void markSkip(boolean skip) {
    	if (skip)
    	{
    		CucumberInstrumentation.skip = skip;
    	}
    	
    	preferences()
    		.edit()
    		.putBoolean( "skip", skip )
    		.commit();
    }

    @Override
    public void onCreate(Bundle arguments) {
    	skip = arguments != null && "true".equals(arguments.getString("log"));
    	
    	if (!skip) 
    	{
    		skip = shouldSkip();
    	}
    	
    	if (skip)
    	{
    		markSkip( false ); //reset it
    	}
    	
        Context context = getContext();
        mClassLoader = context.getClassLoader();
        
		Set<String> keySet = arguments.keySet();
		for (String string : keySet) {
			Log.i(TAG, "Key: " + string + ": " + arguments.getString(string));
		}
		
		Log.i(TAG, "Skip execution: " + skip);

        // For glue and features either use the provided arguments or try to find a RunWithCucumber annotated class.
        // If nothing works, default values will be used instead.
        if (arguments != null &&
                (arguments.containsKey(ARGUMENT_TEST_CLASS) || arguments.containsKey(ARGUMENT_TEST_PACKAGE))) {
        	
            String testClass = arguments.getString(ARGUMENT_TEST_CLASS);
            testClass = testClass != null ? testClass : "null";
            mPackageOfTests = arguments.getString(ARGUMENT_TEST_PACKAGE);

            if (testClass.indexOf("#") != -1) { //executing a particular method/feature
            	String method = testClass.substring(testClass.indexOf("#") + 1);
            	
            	mFeatures = method.replaceAll("__", "/") + ".feature";
            	testClass = testClass.substring(0, testClass.indexOf("#"));
            }

            try {
                Class<?> clazz = mClassLoader.loadClass(testClass);
                boolean annotationWasPresent = readRunWithCucumberAnnotation(clazz);

                // If the class is not RunWithCucumber annotated, maybe it's Cucumber annotated?
                if (!annotationWasPresent) {
                    SEARCH_ANNOTATION:
                    for (Method m : clazz.getMethods()) {
                        for (Annotation a : m.getAnnotations()) {
                            if (a.annotationType().getName().startsWith("cucumber") && mPackageOfTests == null) {
                                mPackageOfTests = testClass.substring(0, testClass.lastIndexOf("."));
                                break SEARCH_ANNOTATION;
                            }
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                Log.w(TAG, e.toString());
            }
        } else {
        	TreeMap<String, Class<?>> cucumberRunners = new TreeMap<String, Class<?>>();
        	
            ClassPathPackageInfoSource source = AndroidClasspathMethodScanner.classPathPackageInfoSource(context);
            for (Class<?> clazz : source.getPackageInfo(context.getPackageName()).getTopLevelClassesRecursive()) {
                if (readRunWithCucumberAnnotation(clazz)) {
                	cucumberRunners.put( clazz.getName(), clazz );
                }
            }
            
            mFeatures = null;
            
            Log.i(TAG, "Runners found: " + cucumberRunners.keySet());
            
            if (!cucumberRunners.isEmpty()) {
            	readRunWithCucumberAnnotation(cucumberRunners.values().iterator().next());
            }
        }

        Properties properties = new Properties();
        mPackageOfTests = mPackageOfTests != null ? mPackageOfTests : defaultGlue();
        mFeatures = mFeatures != null ? mFeatures : defaultFeatures();
        mTags = mTags != null ? mTags : defaultTags();
        
        //Command line arguments to runtime
        StringBuffer cmdLineArgs = new StringBuffer();
        
        if (skip) {
        	cmdLineArgs.append(" --dry-run ");
        }
        
        if (!TextUtils.isEmpty( mFilter )) {
        	cmdLineArgs.append(String.format(" --name %s ", mFilter.replaceAll(" ", "\\\\s")));
        } else {
	        for (String tag : mTags) {
	        	cmdLineArgs.append(String.format(" --tags %s ", tag.replaceAll(" ", "\\\\s")));
			}
        }
	        
        cmdLineArgs.append(String.format(" --glue %s %s ", mPackageOfTests, mFeatures));
        
        properties.setProperty("cucumber.options", cmdLineArgs.toString());
        
        Log.i(TAG, "RuntimeOptions: " + properties);
        
        mRuntimeOptions = new RuntimeOptions(properties);

        mResourceLoader = new AndroidResourceLoader(context);
        List<Backend> backends = new ArrayList<Backend>();
        backends.add(new AndroidBackend(this));
        mRuntime = new Runtime(mResourceLoader, mClassLoader, backends, mRuntimeOptions);

    	super.onCreate(arguments);
        //start();
    }

    /**
     * @return true if the class is RunWithCucumber annotated, false otherwise
     */
    private boolean readRunWithCucumberAnnotation(Class<?> clazz) {
        RunWithCucumber annotation = clazz.getAnnotation(RunWithCucumber.class);
        if (annotation != null) {
            // isEmpty() only available in Android API 9+
            mPackageOfTests = annotation.glue().equals("") ? defaultGlue() : annotation.glue();
            mFeatures = annotation.features().equals("") ? defaultFeatures() : annotation.features() + (mFeatures != null ? "/" + mFeatures : "");
            mTags = annotation.tags().equals("") ? defaultTags() : annotation.tags().split( "\\s*&\\s*" );
            mFilter = annotation.filter();
            return true;
        }
        return false;
    }

    private String defaultFeatures() {
        return "features";
    }

    private String defaultGlue() {
        return getContext().getPackageName();
    }
    
    private String[] defaultTags() {
    	return new String[]{ };
    }

    @Override
    public void onStart() {
    	Looper.prepare();

        List<CucumberFeature> cucumberFeatures = mRuntimeOptions.cucumberFeatures(mResourceLoader);
        int numScenarios = 0;

        for (CucumberFeature feature : cucumberFeatures) {
            for (CucumberTagStatement statement : feature.getFeatureElements()) {
                if (statement instanceof CucumberScenario) {
                    numScenarios++;
                } else if (statement instanceof CucumberScenarioOutline) {
                    for (CucumberExamples examples : ((CucumberScenarioOutline) statement).getCucumberExamplesList()) {
                    	numScenarios += examples.getExamples().getRows().size();
                    }
                    numScenarios--; // subtract table header
                }
            }
        }

        AndroidReporter reporter = new AndroidReporter(numScenarios);
        mRuntimeOptions.formatters.clear();
        mRuntimeOptions.formatters.add(reporter);

        for (CucumberFeature cucumberFeature : cucumberFeatures) {
            Formatter formatter = mRuntimeOptions.formatter(mClassLoader);
            	cucumberFeature.run(formatter, reporter, mRuntime);
        }
        
        Formatter formatter = mRuntimeOptions.formatter(mClassLoader);

        formatter.done();
        printSummary();
        formatter.close();

        finish(Activity.RESULT_OK, new Bundle());
    }

    private void printSummary() {
    	Log.i(TAG, "Summary:");
    	Log.i(TAG, "- Errors: " + mRuntime.getErrors().size());
    	Log.i(TAG, "- Snippets: " + mRuntime.getSnippets().size());
    	
        for (Throwable t : mRuntime.getErrors()) {
            Log.e(TAG, "Error running instrumentation", t);
        }
        
        for (String s : mRuntime.getSnippets()) {
            Log.w(TAG, s);
        }
    }
    
    public void sendStatus(int resultCode, Bundle results)
	{
		//Log.v(TAG, "Status=" + resultCode + ", Bundle: " + results.getInt( "current" ));
		
		super.sendStatus( resultCode, results );
	}

    /**
     * This class reports the current test-state back to the framework.
     * It also wraps the AndroidFormatter to intercept important callbacks.
     */
    private class AndroidReporter implements Formatter, Reporter {
        private final AndroidFormatter mFormatter;
        private final Bundle mResultTemplate;
        private Bundle mTestResult;
        private Bundle mParentBundle;
        private Bundle mLastTestResult;
        private int mScenarioNum;
        private int mTestResultCode;
        private Feature mFeature;
        private Step mStep;
        private String mUri;
        private String mPackage;

        public AndroidReporter(int numTests) {
            mFormatter = new AndroidFormatter(TAG);
            mResultTemplate = new Bundle();
            mResultTemplate.putString(Instrumentation.REPORT_KEY_IDENTIFIER, REPORT_VALUE_ID);
            mResultTemplate.putInt(REPORT_KEY_NUM_TOTAL, numTests);
        }

        @Override
        public void uri(String uri) {
            mFormatter.uri(uri);
            mUri = uri.substring( 0, uri.lastIndexOf( "." ) ).replaceAll( "/", "." );
        }

        @Override
        public void feature(Feature feature) {
            mFeature = feature;
            mFormatter.feature(feature);
        }

        @Override
        public void background(Background background) {
            mFormatter.background(background);
        }

        @Override
        public void scenario(Scenario scenario) {
            reportLastResult();
            mFormatter.scenario(scenario);
            beginScenario(scenario);
        }

        @Override
        public void scenarioOutline(ScenarioOutline scenarioOutline) {
            reportLastResult();
            
            //a new scenario has been started - reset the parent bundle
            mParentBundle = null;
            mFormatter.scenarioOutline(scenarioOutline);
            beginScenario(scenarioOutline);
            mParentBundle = mTestResult;
            
            Log.i(TAG, "Outline map: " + scenarioOutline.toMap());
        }

        @Override
        public void examples(Examples examples) {
            mFormatter.examples(examples);
            
            if (mParentBundle != null) {
            	mParentBundle.putInt(REPORT_KEY_EXAMPLES, mParentBundle.getInt(REPORT_KEY_EXAMPLES, 0) + examples.getRows().size() - 1); //discounting header
            }
        }

		@Override
        public void step(Step step) {
			stopStepTracking();
			
            mStep = step;
            mFormatter.step(step);
            
            startStepTracking();
        }

		protected void startStepTracking()
		{
			if ( mTestResult != null )
            {
            	trackStep( 0.0 );
            }
		}

		protected void stopStepTracking()
		{
			if ( mTestResult != null && mStep != null )
            {
            	trackStep( -1 );
            }
		}

        protected Calendar stepTracker = Calendar.getInstance();
        
		protected void trackStep(double duration)
		{
			if ( duration == 0) 
			{
				stepTracker = Calendar.getInstance();
			}
			else if ( duration == -1 )
			{
				duration = ( Calendar.getInstance().getTimeInMillis() - stepTracker.getTimeInMillis() ) / 1000.0;
			}
			
			LinkedHashMap<String, Double> steps = (LinkedHashMap<String, Double>) unFormat( mTestResult.getString( REPORT_KEY_STEPS ) );
			
			if ( steps == null )
			{
				steps = new LinkedHashMap<String, Double>();
			}
			
			String stepId = stepId();
			
			if ( steps.containsKey( stepId ) )
			{
				duration+= steps.get( stepId );
			}
			
			steps.put( stepId, duration );
			
			mTestResult.putString( REPORT_KEY_STEPS, prettyFormat( steps ) );
		}

		private String prettyFormat( Map<String, Double> map )
		{
			StringBuilder builder = new StringBuilder("{\n");
			
			for ( String key : map.keySet() )
			{
				builder.append( String.format( "%s\t-\t%s\n", key, map.get( key ) ) );
			}
			
			builder.append( "}" );
			
			return builder.toString();
		}
		
		private Map<String, Double> unFormat(String prettyString)
		{
			LinkedHashMap<String, Double> map = new LinkedHashMap<String, Double>();
			
			if ( TextUtils.isEmpty( prettyString ) )
			{
				return map;
			}
			
			String[] steps = prettyString.split( "\n" );
			for ( String step : steps )
			{
				if ( TextUtils.isEmpty( step ) || step.equals( "{" ) || step.equals( "}" ) ) 
				{
					continue;
				}
		
				int indexOf = step.lastIndexOf( "-" );
				String key = step.substring( 0, indexOf ).replace( "\t", "");
				Double value = Double.valueOf( step.substring( indexOf + 1 ).replace( "\t", "" ).replace( "\n", "" ) );
				map.put( key, value );
			}
			
			return map;
		}
		
		protected String stepId()
		{
			return String.format("%s %s", mStep.getKeyword(), mStep.getName() );
		}

        @Override
        public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
            mFormatter.syntaxError(state, event, legalEvents, uri, line);
        }

        @Override
        public void eof() {
            reportLastResult();
            mFormatter.eof();
        }

        @Override
        public void done() {
            mFormatter.done();
        }

        @Override
        public void close() {
            mFormatter.close();
        }

        @Override
        public void embedding(String mimeType, byte[] data) {
        }

        @Override
        public void write(String text) {
        }

        @Override
        public void before(Match match, Result result) {
        }

        @Override
        public void after(Match match, Result result) {
        }

        @Override
        public void match(Match match) {
        }

        private void beginScenario(TagStatement scenario) {
            String testClass = String.format("%s: %s", mFeature.getKeyword(), mFeature.getName()).replaceAll( " - |_", ". " );
            String testName = String.format("%s: %s", scenario.getKeyword(), scenario.getName());

            if (mParentBundle != null){
            	if( !scenario.getName().contains(scenarioNameFromParent()) )
            	{
            		mParentBundle = null;
            	}
            }
            
            mTestResult = new Bundle(mResultTemplate);
            mTestResult.putString(REPORT_KEY_NAME_CLASS, testClass);
            mTestResult.putString(REPORT_KEY_NAME_TEST, testName);
            mTestResult.putInt(REPORT_KEY_NUM_CURRENT, ++mScenarioNum);
            mTestResult.putString(REPORT_KEY_NAME_WORD, scenario.getKeyword());
            mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT, String.format("\n%s:", testClass));

            sendStatus(REPORT_VALUE_RESULT_START, mTestResult);
            mTestResultCode = 0;
        }
        
		protected String scenarioNameFromParent()
		{
			return mParentBundle.getString(REPORT_KEY_NAME_TEST).replace("Scenario Outline: ", "").trim();
		}

        List<String> lastSnippets = null;
        
        @Override
        public void result(Result result) {
        	
        	/*
        	 * Reporting errors or missing step definitions
        	 */
            if (result.getError() != null) {
                mTestResult.putString(REPORT_KEY_STACK, result.getErrorMessage());
                mTestResultCode = REPORT_VALUE_RESULT_FAILURE;
                mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT, result.getErrorMessage());
                
            } else if (result.getStatus().equals("undefined")) {
                // There was a missing step definition, report an error.
                List<String> snippets = mRuntime.getSnippets();
                
                if (lastSnippets != null) 
                {
                	List<String> newSnippets = new ArrayList<String>();

                    for (String snippet : snippets) {
                        if ( !lastSnippets.contains(snippet) ) {
                            newSnippets.add( snippet );
                        }
                    }
                    
                    snippets = newSnippets;
                }
                
                lastSnippets = new ArrayList<String>( snippets );
                
                String report = 
                	String.format(
                		"\n/*\n* Missing step-definition%s: \n* Feature: '%s'\n*\n* Step: \n* %s \n*/\n\n%s",
                		
                		snippets.size() == 1 ? "" : "s",
                        mFeature.getName(),
                        mStep.getStackTraceElement( "" ),
                        StringUtills.join( snippets, "\n" )
                    );
                
                mTestResult.putString(REPORT_KEY_STACK, report);
                mTestResultCode = REPORT_VALUE_RESULT_ERROR;
                mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                        String.format(
                        	"Missing step-definition: %s", 
                        	mStep.getName()
                        )
                );
                
                markSkip( true ); //make sure all tests are skipped when trying to execute them
            }
        }

        private void reportLastResult() {
            if (mScenarioNum != 0 && mTestResult != mLastTestResult) {
                if (mTestResultCode == 0) {
                    mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT, ".");
                }
                
                stopStepTracking();
                
                mLastTestResult = mTestResult;
                
                String keyword = mTestResult.getString(REPORT_KEY_NAME_WORD);
                //mTestResult.remove(REPORT_KEY_NAME_WORD);
                
				boolean isIndividualScenario = keyword.equalsIgnoreCase("scenario");
				boolean isOutlineScenario = keyword.equalsIgnoreCase(CucumberScenarioOutline.OUTLINE_CHILD_KEYWORD);
				
				int current = mTestResult.getInt(REPORT_KEY_NUM_CURRENT);
				int parentNum = mParentBundle != null ? mParentBundle.getInt(REPORT_KEY_NUM_CURRENT) : -1;
				int parentLastChildNum = mParentBundle != null ? parentNum + mParentBundle.getInt(REPORT_KEY_EXAMPLES) : -1;

				if (skip) 
				{
					mTestResult.putBoolean( "Skipped", true );
				}
				
				//Log.v( TAG, "Result for " + current + " (parent: " + parentNum + ", lastChild: " + parentLastChildNum + "): " + mTestResultCode );
				
				if (isIndividualScenario || isOutlineScenario){
                	sendStatus(mTestResultCode, mTestResult);
                }
				
				//report child scenario status onto parent outline
				if (mParentBundle != null && mParentBundle != mTestResult) 
				{
					if (mTestResultCode == REPORT_VALUE_RESULT_FAILURE) {
	                	mParentBundle.putString(REPORT_KEY_STACK, mTestResult.getString(REPORT_KEY_NAME_TEST) + ".\n Error - " + mTestResult.getString(REPORT_KEY_STACK));
	                	
	                	sendStatus(REPORT_VALUE_RESULT_OK, mParentBundle);
		                
					} else if (parentLastChildNum == current) {
						sendStatus(mTestResultCode, mParentBundle);
					}
				}
            }
        }
    }
}
