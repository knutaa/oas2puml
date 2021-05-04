package no.paneon.api.graph;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class OneOf extends Edge {
	
    static final Logger LOG = LogManager.getLogger(OneOf.class);
	
	public OneOf(Node node, Node related ) {
		super(node, "oneOf", related, "", true);
	}

	@Override
	public String toString() {
		return "oneOf: " + node + " --> " + related;
	}
	
}
