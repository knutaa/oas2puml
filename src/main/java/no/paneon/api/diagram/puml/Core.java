package no.paneon.api.diagram.puml;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;

import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;

public class Core {

    static final Logger LOG = LogManager.getLogger(Core.class);

	protected static final String INDENT = "    ";
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
