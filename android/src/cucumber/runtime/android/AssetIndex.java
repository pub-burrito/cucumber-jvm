package cucumber.runtime.android;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import android.content.Context;
import android.util.Log;

/**
 * uses asset.index file (which is pregenerated) since asset-list()s take very long
 *
 */
public final class AssetIndex {

	private static final String TAG = AssetIndex.class.getSimpleName();

	public static final String FILE = "asset.index";
    private final List<String> files;

    public AssetIndex(final Context contextWrapper) {

        Log.d(TAG, "creating index from assets");

        files = new ArrayList<String>();
        
        InputStream in  = null;
        Scanner scanner = null;
        try {
            in          = contextWrapper.getAssets().open(FILE);
            scanner     = new Scanner(new BufferedInputStream(in));

            while (scanner.hasNextLine()) {
                files.add(scanner.nextLine());
            }

            scanner.close();
            in.close();

        } catch (final IOException e) {
            Log.e(TAG, e.getMessage(), e);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (final IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }

    /* returns the files in a directory */
    public List<String> list(final String dir) {
        String directory = dir;
        if (directory.endsWith(File.separator)) {
            directory = directory.substring(0, directory.length() - 1);
        }

        List<String> matches = new ArrayList<String>();
        
        for (final String file : this.files) {
            if (file.startsWith(directory)) {

                String rest = file.substring(directory.length());
                if (rest.charAt(0) == File.separatorChar) {
                    //if (rest.indexOf(File.separator, 1) == -1) {
                    	matches.add( file );
                    //}
                }
            }
        }

        return matches;
    }
    
    /* returns the number of files in a directory */
    public int numFiles(final String dir) {

        String directory = dir;
        if (directory.endsWith(File.separator)) {
            directory = directory.substring(0, directory.length() - 1);
        }

        int num = 0;
        for (final String file : this.files) {
            if (file.startsWith(directory)) {

                String rest = file.substring(directory.length());
                if (rest.charAt(0) == File.separatorChar) {
                    if (rest.indexOf(File.separator, 1) == -1) {
                        num = num + 1;
                    }
                }
            }
        }

        return num;
    }
    
    public int size()
    {
    	return files.size();
    }
    
}