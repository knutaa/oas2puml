package no.paneon.api.diagram.puml;

import org.apache.logging.log4j.Logger;

import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;

public class Core {

    static final Logger LOG = LogManager.getLogger(Core.class);

	protected static final String INDENT       = "    ";
	protected static final String INDENT_SMALL = " ";
	protected static final String BLANK =  "                                        ";

	protected static final String NEWLINE = "\n";
	
	static int sequenceNumber = 0;
			
	int seq;
		
	Core() {
		this.seq = (++sequenceNumber);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public int getSeq() {
		return seq;
	}
			
	@LogMethod(level=LogLevel.TRACE)
	public static void reset() {
		sequenceNumber = 0;
	}

}
