package no.paneon.api.diagram.puml;

import no.paneon.api.utils.Config;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.graph.DiscriminatorNode;
import no.paneon.api.graph.Node;
import no.paneon.api.logging.AspectLogger.LogLevel;


public class DiscriminatorEntity extends Entity {

	String type;
	
	private DiscriminatorEntity(String type) {
		super();
		this.type = type;

	}
	
	public DiscriminatorEntity(Node node) {
		super();
		this.type = node.getName();

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		StringBuilder res = new StringBuilder();
		
		if(Config.getIncludeDebug()) {
			res.append( getCommentInfo() );
		}

		res.append( "diamond " + this.type );
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
		if(o instanceof DiscriminatorEntity) {
			return type.contentEquals(((DiscriminatorEntity)o).type);
		}
		return false;
	}
	
}
	
