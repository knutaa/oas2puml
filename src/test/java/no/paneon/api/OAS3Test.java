package no.paneon.api;

import java.util.List;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import no.paneon.api.diagram.GenerateDiagram;
import no.paneon.api.diagram.app.Args;
import no.paneon.api.model.APIModel;

public class OAS3Test  {

	public OAS3Test() {
	}
	
    static String file = "./src/test/resources/Quote_Management_5.0.0_oas.yaml";
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
        
    @BeforeClass
    public static void runOnceBeforeClass() {        
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
    	    	
    	assert(resources.contains("Quote"));
    	
    }

    @Test
    public void generateDiagrams() {
    	Args         args        = new Args();
		Args.Diagram argsDiagram = args.new Diagram();
    	
		argsDiagram.openAPIFile = file;
		argsDiagram.targetDirectory = folder.toString();

    	GenerateDiagram generator = new GenerateDiagram(argsDiagram);
    	
    	generator.execute();
    	
    	assert(true);
    	
    	folder.delete();
    }
	
	
}
