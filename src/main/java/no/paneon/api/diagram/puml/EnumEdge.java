package no.paneon.api.diagram.puml;

import no.paneon.api.diagram.layout.Place;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;

public class EnumEdge extends HiddenEdge {
	
	public EnumEdge(Node from, Place place, Node to, boolean first) {
		super(from, place, to, first);
	}

	public EnumEdge(Node from, Place place, Node to, String rule) {
		super(from, place, to, false, rule);
	}

	public EnumEdge(Node from, Place place, Node to) {
		super(from, place, to, false);
	}

	public EnumEdge(Place place, Edge edge) {
		super(place, edge);
	}

}
