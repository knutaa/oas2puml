package no.paneon.api;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import no.paneon.api.diagram.app.Args;
import no.paneon.api.extensions.ExtractExtensions;
import no.paneon.api.utils.Utils;

public class OverlayExtract extends TestCase {

	static String targetDir;

    public OverlayExtract( String testName )
    {
        super( testName );
        try {
        	// targetDir = createTempDir();
        	String path = System.getProperty("user.home");
        	targetDir = path + "/test-overlay-extract";
        	
    		System.out.println("... target directory: " + targetDir);

        } catch(Exception ex) {
        	assert(false);
        }
        
    }

    public static Test suite()
    {
        return new TestSuite( OverlayExtract.class );
    }

    
    public void test1()
    {
        	
    	try {
    				    	
			String prev = "Test_API_1_v4.json";
			String current = "Test_API_1_v4_1.json";
			String output = "extract_4_diff.yaml";

			String tmpDir = test(prev,current,targetDir, output);
			
			File file = FileUtils.getFile(targetDir,"Resource_ProductOrder.puml");
			
			assert(file!=null);
			
    	} catch(Exception ex) {
    		System.out.println("Exception: " + ex.getLocalizedMessage());
    		assert(false);
    	}

    }
    
    public void test1_1()
    {
        	
    	try {
    				    	
			String prev = "Test_API_1_v4_1.json";
			String current = "Test_API_1_v4_2.json";
			String output = "extract_4_2_diff.yaml";

			test(prev,current,targetDir, output);
			
			File file = FileUtils.getFile(targetDir,output);
			
			assert(file!=null);
			
			JSONObject diff = Utils.readJSONOrYaml(new java.io.FileInputStream(file)); // FileUtils.readFileToString(file, Charset.defaultCharset());
			
			Object obj = diff.query("/extensions/resourceAttributeExtension");
			
			if(obj instanceof JSONArray) {
				JSONArray arr = (JSONArray)obj;
				
				boolean check = arr.toList().stream()
					.filter(o -> o instanceof JSONObject)
					.map(o -> (JSONObject)o)
					.map(o -> o.optJSONArray("attributesExtension"))
					.filter(Objects::nonNull)
					.map(JSONArray::toList)
					.filter(o -> o instanceof JSONObject)
					.map(o -> (JSONObject)o)					
					.map(o -> o.optString("name"))
					.anyMatch(o -> o.contentEquals("newDescription"));
				
				
				assert(check);
			}
			
			
    	} catch(Exception ex) {
    		System.out.println("Exception: " + ex.getLocalizedMessage());
    		assert(false);
    	}

    }
    
    public void test2()
    {
    	
    	try {
    		 			    	
			String prev = "Test_API_1_v5_0.yaml";
			String current = "Test_API_1_v5_1.yaml";
			
			String output = "extract_5_diff.yaml";
			
			test(prev,current,targetDir, output);

			File file = FileUtils.getFile(targetDir,output);
			
			assert(file.exists());
			
			
    	} catch(Exception ex) {
    		System.out.println("Exception: " + ex.getLocalizedMessage());
    		assert(false);
    	}

    }
    
    
 
    public String test(String prev, String current, String targetDir, String filename)
    {
    	
      	try {

	    	Args args = new Args();
			
	    	Args.ExtractExtensions argsExtension = args.new ExtractExtensions();
	    	
	    	argsExtension.baseSpecification = prev;
	    	argsExtension.openAPIFile = current;
	    	argsExtension.targetDirectory = targetDir;
	    	argsExtension.outputFileName = filename;
					
	    	ExtractExtensions extract = new ExtractExtensions(argsExtension);
			
			extract.execute();
			
			
    	} catch(Exception ex) {
    		System.out.println("Exception: " + ex.getLocalizedMessage());
    		assert(false);
    	}
		
      	return "";
      	
	}

    private static String createTempDir() throws Exception {
    	Path path = Paths.get(FileUtils.getTempDirectory().getAbsolutePath(), UUID.randomUUID().toString());
    	String tmpdir = Files.createDirectories(path).toFile().getAbsolutePath();

    	return tmpdir;
    }
    
    
}
