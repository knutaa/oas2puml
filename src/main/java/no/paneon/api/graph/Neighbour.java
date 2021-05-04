package no.paneon.api.graph;

import java.util.HashSet;
import java.util.Set;

public class Neighbour {
	
	String to;
	Set<Edge> edges;
	
	Neighbour(String to) {
		this.to=to;
		this.edges = new HashSet<>();
	}
	
}
