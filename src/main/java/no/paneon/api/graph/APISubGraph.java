package no.paneon.api.graph;

import org.jgrapht.Graph;

public class APISubGraph extends APIGraph {

	public APISubGraph(CoreAPIGraph core, Graph<Node,Edge> graph, String node) {
		super(core, graph, node);
	}
	
}
