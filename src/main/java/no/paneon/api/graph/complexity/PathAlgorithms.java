package no.paneon.api.graph.complexity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.apache.logging.log4j.LogManager;

public class PathAlgorithms {

    static final Logger LOG = LogManager.getLogger(PathAlgorithms.class);

	Set<Node> settledNodes;
	Set<Node> unSettledNodes;

	Map<Node,Node> predecessors;

	Graph<Node,Edge> graph;
	
	Node resource;
	
	PathAlgorithms(Graph<Node,Edge> graph, Node resource) {

		this.graph = graph;
		this.resource = resource;
		
		this.settledNodes = new HashSet<>();
		this.unSettledNodes = new HashSet<>();

		this.predecessors = new HashMap<>();

	}

	@LogMethod(level=LogLevel.DEBUG)
	Map<Node,Integer> computeShortestPath() {

		Map<Node,Integer> distance = new HashMap<>();

		for(Node node : graph.vertexSet() ) {
			distance.put(node,  Integer.MAX_VALUE);
		}
		
		unSettledNodes.add(resource); 
		distance.put(resource,  0);

		while (!unSettledNodes.isEmpty() ) {
			Node pivot = getNodeWithShortestDistance(distance, unSettledNodes);

			unSettledNodes.remove(pivot); 
			settledNodes.add(pivot); 

			evaluatedNeighbors(distance, pivot);
		}

		return distance;

	}

	@LogMethod(level=LogLevel.DEBUG)
	private void evaluatedNeighbors(Map<Node,Integer> distance, Node pivot) {
		Set<Node> neighbours = CoreAPIGraph.getOutboundNeighbours(graph,pivot);
		neighbours.removeAll(settledNodes);

		for(Node candidate : neighbours) {
			int candidateDistance = distance.get(pivot) + getDistance(pivot, candidate);
			if(!distance.containsKey(candidate) || (candidateDistance < distance.get(candidate)) ) {
				distance.put(candidate, candidateDistance);
				predecessors.put(candidate, pivot);
				unSettledNodes.add(candidate);
			}  

		}
	}

	@LogMethod(level=LogLevel.DEBUG)
	private Integer getDistance(Node pivot, Node neighbour) {
		Set<Node> neighbours = CoreAPIGraph.getOutboundNeighbours(graph,pivot);
		if(neighbours.contains(neighbour))
			return 1;
		else
			return Integer.MAX_VALUE;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private Node getNodeWithShortestDistance(Map<Node,Integer> distance, Set<Node> nodes) {
		Node res = null;
		for (Node node : nodes) {
			if (res == null) {
				res = node;
			} else if (distance.get(node) < distance.get(res)) {
				res = node;
			}
		}
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	Map<Node,Integer> computeLongestPath() {
		return computeLongestPath(resource, new HashSet<>());
	}

	@LogMethod(level=LogLevel.DEBUG)
	Map<Node,Integer> computeLongestPath(Node pivot, Set<Node> visited) {

		Set<Node> subGraph = CoreAPIGraph.getSubGraphNodes(graph, pivot);

		Map<Node,Integer> distance = new HashMap<>();

		for(Node node : subGraph) distance.put(node, Integer.MIN_VALUE);
		distance.put(pivot,0);

		Set<Node> neighbours = CoreAPIGraph.getOutboundNeighbours(graph, pivot);

		neighbours.retainAll(visited);

		for(Node node : neighbours) {
			distance.put(node, distance.get(node) + 1);
		}

		neighbours = CoreAPIGraph.getOutboundNeighbours(graph, pivot);
		neighbours.removeAll(visited);

		for(Node node : neighbours) {

			visited.add(node);

			Map<Node,Integer> longestPathsFromNeighbour = computeLongestPath(node, visited); 
			for(Entry<Node, Integer> entry : longestPathsFromNeighbour.entrySet()) {

				Node seen = entry.getKey();
				Integer candidateLongest = entry.getValue();

				if(!distance.containsKey(seen) || candidateLongest > distance.get(seen)) {
					distance.put(seen, candidateLongest + 1);
				}
			}

		}

		return distance;

	}


}


