package no.paneon.api.graph;

import java.util.List;

import org.apache.logging.log4j.Logger;

import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;

import org.apache.logging.log4j.LogManager;

public class Edge {
	
    static final Logger LOG = LogManager.getLogger(Edge.class);

	public String relation;
	public Node related;
	public String cardinality;
	public Node node;
	public boolean required=false;
		
	public Edge(Node node, String relation, Node related, String cardinality, boolean required) {
		this.node=node;
		this.relation=relation;
		this.related=related;
		this.cardinality=cardinality;
		this.required=required;
	}
		
	private boolean isPlaced(List<Object> processed) {
		boolean res = processed.contains(this);
		
		LOG.debug("isPlaced: edge=" + this + " res=" + res );

		return res;
	}
	
	private void placed(List<Object> processed) {
		processed.add(this);
	}
	
	public String toString() {
		return "edge: " + node + " --> " + cardinality + " " + related;
	}
	
	public boolean isRequired() {
		return required;
	}
	
	public boolean isEnumEdge() {
		return (this instanceof EdgeEnum);
	}
	
	public String getRelationship() {
		return this.relation;
	}
	
	public Node getRelated() {
		return this.related;
	}
	
	public boolean isInheritance() {
		boolean allOf = this instanceof AllOf;
		return allOf && !flattenedInheritance();
	}

	public boolean isOneOf() {
		boolean oneOf = this instanceof OneOf;
		return oneOf && !flattenedInheritance();
	}
	
	private boolean flattenedInheritance() {
		List<String> flatten = Config.getFlattenInheritance();
		boolean res = flatten.contains(this.related.getName());
		
//		LOG.debug("flattenedInheritance: name={} flatten={}", this.related.getName(), flatten);
		
		if(res) return res;
		
		List<String> flattenPattern = Config.getFlattenInheritanceRegexp();
		
//		LOG.debug("flattenedInheritance: name={} pattern={}", this.related.getName(), flattenPattern);
		res = flattenPattern.stream().anyMatch(pattern -> this.related.getName().matches(pattern));

//		LOG.debug("flattenedInheritance: name={} pattern={} res={}", this.related.getName(), flattenPattern, res);

		return res;
	}
	
}
