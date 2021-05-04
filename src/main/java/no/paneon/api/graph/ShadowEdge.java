package no.paneon.api.graph;

public class ShadowEdge extends Edge {

	public ShadowEdge(Node node, String relation, Node related, String cardinality) {
		super(node, relation, related, cardinality, false);
	}

	public ShadowEdge(Node from, Node to) {
		super(from, "", to, "", false);
	}
	
}
