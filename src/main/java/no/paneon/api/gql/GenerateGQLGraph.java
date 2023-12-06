package no.paneon.api.gql;

import java.io.File;

import no.paneon.api.diagram.app.Args;
import no.paneon.api.diagram.app.args.GQLGraph;
import no.paneon.api.generator.GenerateCommon;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class GenerateGQLGraph extends GenerateCommon {

	static final Logger LOG = LogManager.getLogger(GenerateGQLGraph.class);

	GQLGraph args;
	
	public GenerateGQLGraph(GQLGraph args) {
		super(args);
		this.args = args;
		
	}
	
	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public void execute() {
		
		super.execute();
			
	    processArgs(args);
        
		String file = getAPISource(args);
		String target = args.targetDirectory;
		
		if(file==null) {
			Out.println("expected one file name as argument (try --help for usage details)");
			System.exit(1);
		}
		
	    GQLGenerator generator = new GQLGenerator(args, file, target);
	    	  
	    String gql = generator.generateGQLGraph();
	            	    
	    saveFile(gql, args.targetDirectory, args.outputFileName);
		
	}


	private void saveFile(String gql, String target, String filename) {
	    LOG.debug("saveFile: gql={}",  gql);
	    
	    String fileName = target + File.separator + filename;
	   	
	    Utils.save(gql, fileName);
	    
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void processArgs(GQLGraph args) {
				
	   	if(args.debug!=null) {
    		setLogLevel( Utils.getLevelmap().get(args.debug));
    	} else {
    		setLogLevel( Level.OFF );
    	}
        
        args.configs.forEach(Config::setConfig);
        
	}

	@LogMethod(level=LogLevel.DEBUG)
	private String getAPISource(GQLGraph args) {
    	return args.files.isEmpty() ? args.openAPIFile : args.files.get(0) ; 
	}

	
}
