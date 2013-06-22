package cucumber.runtime.android;

import gherkin.formatter.Formatter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;

import java.util.List;

import android.util.Log;

public class AndroidFormatter implements Formatter {
    private final String mLogtag;
    private String mUri;

    public AndroidFormatter(String logtag) {
        mLogtag = logtag;
    }

    @Override
    public void uri(String uri) {
        mUri = uri;
    }

    @Override
    public void feature(Feature feature) {
        Log.d(mLogtag, String.format("%s: %s (%s)%n%s", feature.getKeyword(), feature.getName(), mUri, feature.getDescription()));
    }

    @Override
    public void background(Background background) {
        Log.d(mLogtag, background.getName());
    }

    @Override
    public void scenario(Scenario scenario) {
        Log.d(mLogtag, String.format("%s: %s", scenario.getKeyword(), scenario.getName()));
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        Log.d(mLogtag, String.format("%s: %s", scenarioOutline.getKeyword(), scenarioOutline.getName()));
    }

    @Override
    public void examples(Examples examples) {
        Log.d(mLogtag, String.format("%s: %s (#%s)", examples.getKeyword(), examples.getName(), examples.getRows() != null ? examples.getRows().size() : 0));
    }

    @Override
    public void step(Step step) {
        Log.d(mLogtag, String.format("%s%s", step.getKeyword(), step.getName()));
    }

    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
        Log.e(mLogtag, String.format("syntax error '%s' %s:%d", event, uri, line));
    }

    @Override
    public void eof() {
    }

    @Override
    public void done() {
    }

    @Override
    public void close() {
    }
}
