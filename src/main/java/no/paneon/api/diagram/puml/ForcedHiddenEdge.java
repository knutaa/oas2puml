package no.paneon.api.diagram.puml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.paneon.api.diagram.layout.Place;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;


public class ForcedHiddenEdge extends EnumEdge {

    static final Logger LOG = LogManager.getLogger(ForcedHiddenEdge.class);

	public ForcedHiddenEdge(Node from, Place place, Node to, String rule) {
		super(from, place, to, rule);
        LOG.debug("ForcedHiddenEdge: from=" + from + " place=" + place + " to=" + to);

	}

	public ForcedHiddenEdge(Node from, Place place, Node to) {
		super(from, place, to);
        LOG.debug("ForcedHiddenEdge: from=" + from + " place=" + place + " to=" + to);

	}

	public ForcedHiddenEdge(Place place, Edge edge) {
		super(place, edge);
        LOG.debug("ForcedHiddenEdge: place=" + place + " edge=" + edge);
	}


}
