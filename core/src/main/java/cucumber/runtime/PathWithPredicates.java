/**
 * 
 */
package cucumber.runtime;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import cucumber.runtime.io.Resource;


public class PathWithPredicates
{
	private static final Pattern PREDICATED_PATH = Pattern.compile( "(.*)\\[(.*)\\](?:\\{(.*)\\})?" );
	
	private final String originalPath;
	private final String path;
	private final String namePredicate;
	private final String contentPredicate;
	private final boolean hasPredicate;
	
	public PathWithPredicates( String predicatedResourcePath )
	{
		originalPath = predicatedResourcePath;
		
		Matcher predicateMatcher = PREDICATED_PATH.matcher( predicatedResourcePath );
		hasPredicate = predicateMatcher.matches();
		
		path = hasPredicate ? predicateMatcher.group( 1 ) : predicatedResourcePath;
		namePredicate = hasPredicate ? predicateMatcher.group( 2 ) : null;
		contentPredicate = hasPredicate ? predicateMatcher.group( 3 ) : null;
	}
	
	public String getOriginalPath()
	{
		return originalPath;
	}
	
	public String getPath()
	{
		return path;
	}

	public boolean hasPredicate()
	{
		return hasPredicate;
	}
	
	public PathPredicateFilter filter()
	{
		return new PathPredicateFilter( this );
	}
	
	public static class PathPredicateFilter
	{
		private final PathWithPredicates pathWithPredicates;
		
		public PathPredicateFilter( PathWithPredicates pathWithFilters )
		{
			this.pathWithPredicates = pathWithFilters;
		}
		
		public String getOriginalPath()
		{
			return pathWithPredicates.getOriginalPath();
		}
		
		public String getPath()
		{
			return pathWithPredicates.getPath();
		}
		
		public boolean matches( Resource resource )
		{
			return matchesName( resource ) && matchesContent( resource ); 
		}
		
		public boolean matchesName( Resource resource )
		{
			String resourcePath = String.format( "%s%s", pathWithPredicates.getPath(), resource.getPath() );
			
			boolean matches = pathWithPredicates.hasPredicate && resourcePath.matches( pathWithPredicates.namePredicate );
			
			System.out.println( "- Matching '" + resourcePath + "' to " + pathWithPredicates.namePredicate + " = " + matches );
			
			return matches;
		}
		
		public boolean matchesContent( Resource resource )
		{
			try
			{
				return 
					pathWithPredicates.hasPredicate 
					&& 
					( 
						pathWithPredicates.contentPredicate == null 
						|| 
						IOUtils.toString( resource.getInputStream() )
							.contains( pathWithPredicates.contentPredicate ) 
					);
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
			
			return false;
		}
		
		@Override
		public String toString()
		{
			return 
				String.format( 
					"%s[namePredicate=%s, contentPredicate=%s]", 
					super.toString(), 
					pathWithPredicates.namePredicate, 
					pathWithPredicates.contentPredicate 
				);
		}
	}
}