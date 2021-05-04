package no.paneon.api.graph;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class EdgeEnum extends Edge {
	
    static final Logger LOG = LogManager.getLogger(EdgeEnum.class);
	
	public EdgeEnum(Node node, String relation, Node related, String cardinality, boolean required) {
		super(node, relation, related, cardinality, required);
	}

	
}
