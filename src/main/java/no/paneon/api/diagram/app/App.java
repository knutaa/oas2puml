package no.paneon.api.diagram.app;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.beust.jcommander.JCommander;

import no.paneon.api.diagram.GenerateDiagram;
import no.paneon.api.diagram.app.args.Version;
import no.paneon.api.diagram.app.args.Diagram;
import no.paneon.api.diagram.app.args.ExtractExtension;
import no.paneon.api.diagram.app.args.GQLGraph;
import no.paneon.api.diagram.app.args.Usage;

import no.paneon.api.extensions.ExtractExtensions;
import no.paneon.api.gql.GenerateGQLGraph;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Timestamp;

	
public class App {

    static final Logger LOG = LogManager.getLogger(App.class);
		
	JCommander commandLine;

	Version				argsVersion;
	Diagram            	argsDiagram;
	GQLGraph  	        argsGQLGraph;
	Usage  	        	argsUsage;
	ExtractExtension  	argsExtractExtensions;
	
	App(String ... argv) {
		     	
		argsDiagram           = new Diagram();
		argsGQLGraph          = new GQLGraph();
		argsUsage          	  = new Usage();
		argsExtractExtensions = new ExtractExtension();
		
		argsVersion           = new Version();

		commandLine = JCommander.newBuilder()
		    .addCommand("diagrams",            argsDiagram )
		    .addCommand("gqlgraph",            argsGQLGraph )
		    .addCommand("extract-extensions",  argsExtractExtensions )
		    .addCommand("--help",              argsUsage )
		    .addCommand("help",                argsUsage )
		    .addCommand("--version",           argsVersion )
		    .addCommand("version",             argsVersion )

		    .build();

		try {
			commandLine.parse(argv);						
		} catch(Exception ex) {
			Out.println(ex.getMessage());
			Out.println("Use option --help or -h for usage information");
			
			System.exit(1);
		}	
		
	}
	
	public static void main(String ... args) {
		App app = new App(args);
				
		System.setProperty("java.awt.headless", "true");
		
		try {			
			app.run();
		} catch(Exception ex) {
			Out.println("app error: " + ex.getLocalizedMessage());	
			// ex.printStackTrace();
			System.exit(1);			
		}
		
		Timestamp.timeStamp("finished", Timestamp.FROM_START);
		
	}


	void run() {
						
		try {
			final Properties properties = new Properties();
			properties.load(this.getClass(). getClassLoader().getResourceAsStream("project.properties"));		
			String version = properties.getProperty("version");
			String artifactId = properties.getProperty("artifactId");
			
			String command = commandLine.getParsedCommand()!=null ? commandLine.getParsedCommand() : "";
			
			String javaVersion = System.getProperty("java.version");
			
			Out.printAlways("{} {} {} (java version {})", artifactId, version, command, javaVersion);
			
		} catch(Exception e) {
			Out.printAlways("... version information not available: {}", e.getLocalizedMessage());
		}

		
		if (commandLine.getParsedCommand()==null) {
            commandLine.usage();
            return;
        }
		
		// Timestamp.timeStamp("arguments available");

    	switch(commandLine.getParsedCommand()) {
    
    	case "--version":
    	case "version":
    		break;
    		
    	case "--help":
    	case "help":
    		commandLine.usage();
    		break;
    		
    	case "diagrams":
    		GenerateDiagram diagram = new GenerateDiagram(argsDiagram);
    		diagram.execute();
    		break;

 
       	case "gqlgraph":
       		if(argsGQLGraph.outputFileName==null) {
    			Out.println("... missing output file argument");
    		} else {
    			GenerateGQLGraph gengraph = new GenerateGQLGraph(argsGQLGraph);
    			gengraph.execute();
    		}
    		break;
    		
       	case "extract-extensions":
       		ExtractExtensions extensions = new ExtractExtensions(argsExtractExtensions);
       		extensions.execute();
			break;
			
    	default:
    		Out.println("... unrecognized command " + commandLine.getParsedCommand());
    		Out.println("... use --help for command line options");
    		System.exit(1);
    	}
    	
		
	}


}
