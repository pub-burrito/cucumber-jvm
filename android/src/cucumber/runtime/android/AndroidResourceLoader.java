package cucumber.runtime.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import cucumber.api.android.CucumberInstrumentation;
import cucumber.runtime.io.Resource;
import cucumber.runtime.io.ResourceLoader;

public class AndroidResourceLoader implements ResourceLoader
{
	private static final String TAG = "AndroidResourceLoader";
	
	private Context mContext;
	
	private AssetIndex assetIndex;
	private AssetManager assetManager;
	
	public AndroidResourceLoader( Context context )
	{
		mContext = context;
		
		assetIndex = new AssetIndex( context );
	}
	
	@Override
	public Iterable<Resource> resources( String path, String suffix )
	{
		
		Log.v( TAG, "Loading resources for path: " + path + " (suffix: " + suffix + ")" );
		
		List<Resource> resources = new ArrayList<Resource>();
		
		load( path, suffix, resources );
		
		Log.v( TAG, "Loaded: " + resources.size() + " resources" );
		
		return resources;
	}
	
	protected void load( String path, String suffix, List<Resource> resources )
	{
		try
		{
			addResourceRecursive( resources, path, suffix );
		}
		catch ( IOException e )
		{
			Log.e( CucumberInstrumentation.TAG, "Error loading resources.", e );
		}
	}
	
	private void addResourceRecursive( List<Resource> res, String path, String suffix ) throws IOException
	{
		Log.v( TAG, "- Looking for resources under: " + path );
		
		if ( path.endsWith( suffix ) )
		{
			res.add( new AndroidResource( mContext, path ) );
			
		}
		else
		{
			List<String> list = new ArrayList<String>();
			boolean listIncludesPath = false;
			
			if ( assetIndex.size() > 0 )
			{
				list = assetIndex.list( path );
				listIncludesPath = true;
			}
			else
			{
				list = Arrays.asList( assetManager.list( path ) );
			}
			
			Log.v( TAG, "- Candidates in path: " + list.size() );
			
			for ( String name : list )
			{
				String fullName = listIncludesPath ? name : String.format( "%s/%s", path, name );
				boolean hasExtension = fullName.substring( fullName.lastIndexOf( "/" ) ).contains( "." );
				
				Resource resource = new AndroidResource( mContext, fullName );
				
				if ( name.endsWith( suffix ) )
				{
					res.add( resource );
				}
				else if ( !hasExtension )
				{
					addResourceRecursive( res, fullName, suffix );
				}
			}
		}
	}
}
