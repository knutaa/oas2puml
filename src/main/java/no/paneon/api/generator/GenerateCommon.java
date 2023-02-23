package no.paneon.api.generator;

import no.paneon.api.diagram.app.Args;
import no.paneon.api.diagram.app.Args.Common;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Timestamp;
import no.paneon.api.utils.Utils;

import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger;
import no.paneon.api.logging.AspectLogger.LogLevel;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class GenerateCommon {
	
	protected Args.Common common;
	
    static final Logger LOG = LogManager.getLogger(GenerateCommon.class);

	public GenerateCommon(Args.Common common) {
		this.common = common;
		
//		List<String> dirs = getDirectories(common.workingDirectory);
//				
//		try {
//			APIModel.loadAPI(common.openAPIFile, Utils.getFile(common.openAPIFile, dirs));
//		
//			Timestamp.timeStamp("api specification loaded");
//			
//		} catch(Exception ex) {
//			Out.println("... unable to read API specification from " + common.openAPIFile);
//			System.exit(0);
//		}
//		
		loadAPI(common);
		
		Out.silentMode = common.silentMode;
		
		setLogLevel( Utils.getLevelmap().get(common.debug));

        Config.setConfigSources(common.configs);
        
    	if(common.debug!=null && Utils.getLevelmap().containsKey(common.debug)) {
			setLogLevel( Utils.getLevelmap().get(common.debug) );
    	}
        
    	if(common.openAPIFile!=null) {
    		APIModel.setSwaggerSource(common.openAPIFile);
    	}
 
    	if(common.rulesFile!=null) {
    		Config.setRulesSource(common.rulesFile);
    	}
    	
    	if(common.conformanceSourceOnly) {
    		Config.setBoolean("conformanceSourceOnly",true);
    	}
    	
    	if(common.timestamp) {
    		Timestamp.setActive();  		
    	}

    	   
	}
	
	
	public static void loadAPI(Common args) {
		List<String> dirs = getDirectories(args.workingDirectory);
		
		try {
			APIModel.loadAPI(args.openAPIFile, Utils.getSource(args.openAPIFile, dirs));
		
			Timestamp.timeStamp("api specification loaded");
			
		} catch(Exception ex) {
			Out.println("... unable to read API specification from " + args.openAPIFile);
			System.exit(0);
		}
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getDirectories(String baseDir) {
		List<String> res = new LinkedList<>();
		if(baseDir!=null) {
			res.add(baseDir);
			if(!baseDir.isEmpty()) {
				res.add(baseDir + "/swaggers" );
			}
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	protected void setLogLevel(org.apache.logging.log4j.Level level) {		
		
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration config = context.getConfiguration();
		LoggerConfig rootConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
		rootConfig.setLevel(level);

		AspectLogger.setGlobalDebugLevel(level);
			
	}

	@LogMethod(level=LogLevel.DEBUG)
	protected void execute() {
							
		Timestamp.timeStamp("common execute finished");


	}
	
}
