package cucumber.runtime.xstream;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

class DoubleConverter extends ConverterWithNumberFormat<Double> {

    public DoubleConverter(Locale locale) {
        super(locale, new Class[]{Double.class, Double.TYPE});
        
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance( Locale.US );
		symbols.setExponentSeparator( "e" );
		
		DecimalFormat scientificNotificationAwareDecimalFormat = new DecimalFormat("#.#", symbols);
		
        getFormats().add( 0, scientificNotificationAwareDecimalFormat );
    }

    @Override
    protected Double downcast(Number argument) {
        return argument.doubleValue();
    }

}
