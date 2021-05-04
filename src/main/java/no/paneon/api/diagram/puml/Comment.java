package no.paneon.api.diagram.puml;

import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

public class Comment extends Core {
	
	String text;
	
	public Comment(String text) {
		super();
		this.text = text;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		return getComment() + " (seq=" + seq + ")";
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String getComment() {
		if(text.startsWith("'")) 
			return text;
		else 
			return "'" + text;
	}
	
}
	
