package no.paneon.api.diagram.puml;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import no.paneon.api.graph.EnumNode;
import no.paneon.api.utils.Config;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toList;

import no.paneon.api.utils.Out;

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
		this.values.addAll(valueList.stream().filter(v -> !this.values.contains(v)).collect(toList()));
		
		LOG.debug("EnumEntity:addValue type={} values={}", type, values);

	}

	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		StringBuilder res = new StringBuilder();
		
		if(Config.getIncludeDebug()) {
			res.append( getCommentInfo() );
		}

		res.append( "class " + this.type + " <<Enumeration>> {");
		res.append( NEWLINE );
	    
		
		int count = values.size();
		if(Config.getBoolean("truncateLargeEnums")) {	
			count = Math.min(count, Config.getInteger("truncateEnumsCount",30));
			if(count+1==values.size()) count = values.size();
		}
		boolean truncated = count != values.size();
		
	    for(String v : values.subList(0, count)) {	
	    	res.append( INDENT + v);
	    	res.append( NEWLINE );
	    }
	    
	    if(truncated) {
	    	String truncatedMessage = Config.getString("truncatedMessage");
	    	if(truncatedMessage.isEmpty()) truncatedMessage = "{field}<color:red>.. truncated .. %1$s of %2$s";
	    		
	    	truncatedMessage = String.format(truncatedMessage, count, values.size());
	    	
	    	res.append( INDENT + truncatedMessage);
	    	res.append( NEWLINE );
	    	
	    	Out.printOnce("... truncated presentation of list of enums for type {} - showing first {} enums", type, count);
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
	
