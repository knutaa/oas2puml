package no.paneon.api;

import java.util.List;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import no.paneon.api.diagram.GenerateDiagram;
import no.paneon.api.diagram.app.Args;
import no.paneon.api.diagram.app.args.Diagram;
import no.paneon.api.model.APIModel;

public class OAS2Test  {

	public OAS2Test() {
	}
	
    static String file = "./src/test/resources/TMF620-ProductCatalog-v4.1.0.swagger.json";
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
        
    @BeforeClass
    public static void runOnceBeforeClass() {  
    	APIModel.clean();
        APIModel.setSwaggerSource(file);
        APIModel.loadAPI(file);

    }

    @AfterClass
    public static void runOnceAfterClass() {
        APIModel.clean();
    }

    @Before
    public void runBeforeTestMethod() {
    }

    @After
    public void runAfterTestMethod() {
    }

    @Test
    public void checkResource() {
    	List<String> resources = APIModel.getAllDefinitions();
    	    	
    	assert(resources.contains("Catalog"));
    	
    }

    @Test
    public void generateDiagrams() {
		Diagram argsDiagram = new Diagram();
    	
		argsDiagram.openAPIFile = file;
		argsDiagram.targetDirectory = folder.toString();

    	GenerateDiagram generator = new GenerateDiagram(argsDiagram);
    	
    	generator.execute();
    	
    	assert(true);
    	
    	folder.delete();
    }
	
	
}
