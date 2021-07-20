package no.paneon.api.diagram.puml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.paneon.api.diagram.layout.Place;
import no.paneon.api.graph.Discriminator;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.utils.Config;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;


public class AllOfReverseEdge extends AllOfEdge {
	
	public AllOfReverseEdge(Node from, Place place, Node to, boolean first) {
		super(from, place, to, first);
	}

	public AllOfReverseEdge(Node from, Place place, Node to) {
		super(from, place, to, false);
	}

	public AllOfReverseEdge(Place place, Edge edge) {
		super(place, edge);
	}
	
	public AllOfReverseEdge(Node from, Place place, Node to, boolean required, String id, String rule) {
		this(from,place,to);
		addComment(new Comment("'rule: " + rule));
	}

	public AllOfReverseEdge(Place direction, Edge edge, String rule) {
		this(direction, edge);
		addComment(new Comment("'rule: " + rule));
	}

	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		return toString(to, from);
	}
	
}
