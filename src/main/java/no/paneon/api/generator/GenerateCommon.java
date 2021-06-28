package no.paneon.api.generator;

import no.paneon.api.diagram.app.Args;
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
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

//import org.apache.logging.log4j.core.LoggerContext;
//import org.apache.logging.log4j.core.config.Configuration;
//import org.apache.logging.log4j.core.config.LoggerConfig;

public class GenerateCommon {
	
	protected Args.Common common;
	
    static final Logger LOG = LogManager.getLogger(GenerateCommon.class);

	public GenerateCommon(Args.Common common) {
		this.common = common;
		
		List<String> dirs = getDirectories(common.workingDirectory);
				
		APIModel.loadAPI(common.openAPIFile, Utils.getFile(common.openAPIFile, dirs));
		
		Timestamp.timeStamp("api specification loaded");

		Out.silentMode = common.silentMode;
		
		setLogLevel( Utils.getLevelmap().get(common.debug));

        Config.setConfigSources(common.configs);
        
    	if(common.debug!=null && Utils.getLevelmap().containsKey(common.debug)) {
			setLogLevel( Utils.getLevelmap().get(common.debug) );
    	}
        
    	if(common.openAPIFile!=null) {
    		APIModel.setSwaggerSource(common.openAPIFile);
    	}
 
    	if(common.conformanceSourceOnly) {
    		Config.setBoolean("conformanceSourceOnly",true);
    	}
    	   
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private List<String> getDirectories(String baseDir) {
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
//		LoggerContext context = (LoggerContext) LogManager.getContext(false);
//		Configuration config = context.get
//		LoggerConfig rootConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
//		rootConfig.setLevel(level);	
//		
		
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration config = context.getConfiguration();
		LoggerConfig rootConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
		rootConfig.setLevel(level);

//		LogManager.getRootLogger().atLevel(level);

//		Configurator.setAllLevels(LogManager.getRootLogger().getName(), level);

		AspectLogger.setGlobalDebugLevel(level);
		
		
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	protected void execute() {
							
		Timestamp.timeStamp("common execute finished");


	}
	
}
