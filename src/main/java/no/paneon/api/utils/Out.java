package no.paneon.api.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.panoen.api.logging.AspectLogger;

public class Out {

	private Out() {
	}
	
	static final Logger LOG = LogManager.getLogger(Out.class);

	public static boolean silentMode = false;

	public static void println(String s) {
		if(!silentMode) LOG.log(AspectLogger.VERBOSE, s);
	}
	
	public static void println() {
		if(!silentMode)  LOG.log(AspectLogger.VERBOSE, "");
	}
	
	public static void printAlways(String s) {
		LOG.log(AspectLogger.VERBOSE, s);
	}

	public static void println(String ... args) {
		if(!silentMode && LOG.isInfoEnabled()) {
			StringBuilder builder = new StringBuilder();
			String delim = "";
			for(String s : args) {
				builder.append(delim + s);
				delim = " ";
			}
			LOG.log(AspectLogger.VERBOSE, builder.toString());
		}
	}
	
	public static void debug(String format, Object ...args) {
		LOG.log(AspectLogger.VERBOSE, format, args);
	}
}
