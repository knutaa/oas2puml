package no.paneon.api.diagram.puml;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import no.paneon.api.graph.EnumNode;
import no.paneon.api.utils.Config;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import static java.util.stream.Collectors.toSet;

public class EnumEntity extends Entity {

	String type;
	List<String> values;
	boolean nullable;
	
	private EnumEntity(String type) {
		super();
		this.type = type;
		this.values = new LinkedList<>();
		this.nullable = false;
	}
	
	public EnumEntity(EnumNode enode) {
		super();
		this.type = enode.getType();
		this.values = new LinkedList<>();
		this.nullable = enode.getNullable();
		
		addValue(enode.getValues());
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void addValue(String value) {
		this.values.add(value);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addValue(Collection<String> valueList) {	
		this.values.addAll(valueList.stream().filter(v -> !this.values.contains(v)).collect(toSet()));
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		StringBuilder res = new StringBuilder();
		
		if(Config.getIncludeDebug()) {
			res.append( getCommentInfo() );
		}

		res.append( "class " + this.type + " <<Enumeration>> {");
		res.append( NEWLINE );
	    
	    for(String v : values) {	
	    	res.append( INDENT + v);
	    	res.append( NEWLINE );
	    }
	    
	    if(this.nullable) {
	    	res.append("--------");
			res.append( NEWLINE );
	    	res.append(INDENT + "nullable");
			res.append( NEWLINE );
	    }
	    
	    res.append("}");
	    res.append( NEWLINE );
	    	
	    return res.toString();
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	public int hashCode() {
		return this.type.hashCode();
	}

	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public boolean equals(Object o) {
		if(o instanceof EnumEntity) {
			return type.contentEquals(((EnumEntity)o).type);
		}
		return false;
	}
	
}
	
