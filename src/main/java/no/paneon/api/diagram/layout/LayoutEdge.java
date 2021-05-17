package no.paneon.api.diagram.layout;


import no.paneon.api.graph.Node;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class LayoutEdge {

	private Place direction;
	
	private Node source;
	private Node target;
	
	private String effectiveID;
	
	public LayoutEdge(Place direction, Node source, Node target) {
		this.direction = direction;
		this.source = source;
		this.target = target;
		this.effectiveID = "";
	}

	public LayoutEdge(Place direction, Node source, Node target, String id) {
		this(direction, source, target);
		this.effectiveID = id;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSameDirection(Place direction) {
		return this.direction==direction;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Place getDirection() {
		return this.direction;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		return "layoutEdge(direction=" + direction + " source=" + source + " target=" + target;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getId() {
		return this.effectiveID;
	}
	
}

