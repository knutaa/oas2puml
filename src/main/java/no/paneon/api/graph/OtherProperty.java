package no.paneon.api.graph;

import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

public class OtherProperty {

	String name;
	String value;
	boolean required;
	
	public OtherProperty(String name, String value, boolean required) {
		this.name = name;
		this.value = value;
		this.required = required;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String getName() { 
		return name;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getValue() {
		return value;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isRequired() {
		return required;
	}
	
}