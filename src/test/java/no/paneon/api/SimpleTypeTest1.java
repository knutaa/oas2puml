package no.paneon.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import no.paneon.api.diagram.GenerateDiagram;
import no.paneon.api.diagram.app.Args;
import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Node;
import no.paneon.api.graph.complexity.ComplexityAdjustedAPIGraph;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;

public class SimpleTypeTest1  {

	public SimpleTypeTest1() {
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
    	Config.init();
    	
		CoreAPIGraph coreGraph = new CoreAPIGraph();

		Node node = coreGraph.getNode("Quote");
		
		assert(node!=null);
		
		node = coreGraph.getNode("Money");
		
		assert(node.isSimpleType());
		assert(!APIModel.isSimpleType("Money"));

		node = coreGraph.getNode("GcProductStatusType");

		assert(node.isEnumNode());
		assert(!node.isSimpleType());


    }
	
	
}
