package no.paneon.api.graph;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class AllOf extends Edge {
	
    static final Logger LOG = LogManager.getLogger(AllOf.class);
	
	public AllOf(Node node, Node related ) {
		super(node, "allOf", related, "", true);
	}

	@Override
	public String toString() {
		return "allOf: " + node + " --> " + related;
	}
	
}
