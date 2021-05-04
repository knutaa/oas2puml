package no.paneon.api.graph.complexity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.GraphIterator;
import org.apache.logging.log4j.LogManager;

public class GraphAlgorithms {

    static final Logger LOG = LogManager.getLogger(GraphAlgorithms.class);

	Set<Node> settledNodes;
	Set<Node> unSettledNodes;

	Map<Node,Node> predecessors;

	Graph<Node,Edge> graph;
	Node resource;
	
	GraphAlgorithms(Graph<Node,Edge> graph, Node resource) {

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
		Set<Node> neighbours = getOutboundNeighbours(pivot);
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
		Set<Node> neighbours = getOutboundNeighbours(pivot);
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

		Set<Node> subGraph = getNodesOfSubGraph(this.graph, pivot);

		Map<Node,Integer> distance = new HashMap<>();

		for(Node node : subGraph) distance.put(node, Integer.MIN_VALUE);
		distance.put(pivot,0);

		Set<Node> neighbours = getOutboundNeighbours(pivot);

		neighbours.retainAll(visited);

		for(Node node : neighbours) {
			distance.put(node, distance.get(node) + 1);
		}

		neighbours = getOutboundNeighbours(pivot);
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

//	@LogMethod(level=LogLevel.DEBUG)
//	public Set<Node> getSubGraph(Node node) {
//		return getSubGraph(node,node);
//	}
//
//	@LogMethod(level=LogLevel.DEBUG)
//	public Set<Node> getSubGraph(Node parent, Node node) {
//		Set<Node> seen = new HashSet<>();
//		seen.add(parent);
//		Set<Node>  res = getSubGraphHelper(node, seen);
//		
//		res.remove(parent);
//		
//		return res;
//	}
//	
//	@LogMethod(level=LogLevel.DEBUG)
//	private Set<Node> getSubGraphHelper(Node node, Set<Node> seen) {
//		Set<Node> neighbours = this.getOutboundNeighbours(node);
//		
//		Set<Node> res = new HashSet<>();
//		
//		res.addAll(neighbours);
//
//		seen.add(node);
//		
//		for(Node n : neighbours) {
//			if(!seen.contains(n)) {
//				Set<Node> sub = getSubGraphHelper(n,seen);
//				res.addAll(sub);
//			}
//		}
//		
//		return res;
//	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getReverseSubGraph(Node node) {
		Set<Node> seen = new HashSet<>();
		seen.add(node);
		return getReverseSubGraphHelper(node, seen);	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getReverseSubGraphHelper(Node node, Set<Node> seen) {
		Set<Node> neighbours = getInboundNeighbours(node);
		
		Set<Node> res = new HashSet<>();
		
		res.addAll(neighbours);
		seen.add(node);
		
		for(Node n : neighbours) {
			if(!seen.contains(n)) {
				Set<Node> sub = getReverseSubGraphHelper(n,seen);
				res.addAll(sub);
			}
		}
		
		return res;
	}

	private Set<Node> getOutboundNeighbours(Node node) {
		return graph.outgoingEdgesOf(node).stream().map(graph::getEdgeTarget).collect(toSet());
	}

	private Set<Node> getInboundNeighbours(Node node) {
		return graph.incomingEdgesOf(node).stream().map(graph::getEdgeSource).collect(toSet());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Graph<Node,Edge> getSubGraph(Graph<Node,Edge> graph, Node node) {
		Set<Node> nodes = getNodesOfSubGraph(graph, node);		
		
		return new AsSubgraph<>(graph, nodes);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Map<Node,Graph<Node,Edge>> getSubGraphsOfNeighbours(Graph<Node,Edge> graph, Node node) {	
		Map<Node,Graph<Node,Edge>> res = new HashMap<>();
		
		Set<Node> neighbours = getOutboundNeighbours(graph,node);
			
		for(Node neighbour : neighbours) {
			Set<Node> sub = getNodesOfSubGraph(graph, neighbour);
			Graph<Node,Edge> subGraph = new AsSubgraph<>(graph, sub);
			res.put(node,  subGraph);
		}
		
		return res;
	}

	
	@LogMethod(level=LogLevel.DEBUG)
	public static <N,E> Set<N> getOutboundNeighbours(Graph<N,E> graph, N node) {
		return graph.outgoingEdgesOf(node).stream().map(graph::getEdgeTarget).collect(toSet());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static <N,E> Set<N> getInboundNeighbours(Graph<N,E> graph, N node) {
		return graph.incomingEdgesOf(node).stream().map(graph::getEdgeSource).collect(toSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static <N,E> Set<N> getNeighbours(Graph<N,E> graph, N node) {
		Set<N> res = getOutboundNeighbours(graph,node);		
		res.addAll( getInboundNeighbours(graph,node) );
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getNodeNames(Graph<Node,Edge> graph) {
		return graph.vertexSet().stream().map(Node::getName).collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static <N,E> Set<N> getNodesOfSubGraph(Graph<N,E> graph, N node) {
		Set<N> res = new HashSet<>();

		if(!graph.vertexSet().contains(node)) 
			return res;

		res.add(node);
		
		GraphIterator<N, E> it = new BreadthFirstIterator<>(graph, node);

		while(it.hasNext() ) { res.add(it.next()); }
		
		return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
    public static List<Node> cycles(Graph<Node,Edge> graph) 
    { 
		List<Node> path = new LinkedList<>();		
		Map<Node,Boolean> visited = new HashMap<>();
		graph.vertexSet().forEach(node -> visited.put(node, false));
		
		for(Node node : graph.vertexSet()) {
			if(Boolean.FALSE.equals(visited.get(node))) {
				path.add(node);
				List<Node> cycle = cyclicUtil(graph, node, visited, path, null);
				if(!cycle.isEmpty()) return cycle;
				path.remove(node);
			}
		}
		return path; 
    } 

	@LogMethod(level=LogLevel.DEBUG)
    private static List<Node>  cyclicUtil(Graph<Node,Edge> graph, Node node, Map<Node,Boolean> visited, List<Node> path, Node parent) 
    { 
        visited.put(node, true); 
  
        Set<Node> neighbours = getNeighbours(graph, node).stream()
        						.filter(n -> !n.equals(node))
        						.collect(toSet());
        
        
        if(neighbours.size() > 1) {
	        for(Node neighbour : neighbours) {
	        	if(Boolean.FALSE.equals(visited.get(neighbour))) {
					path.add(neighbour);
					List<Node> cycle = cyclicUtil(graph, neighbour, visited, path, node);
					if(!cycle.isEmpty()) return cycle;
					path.remove(neighbour);
	            } else if (path.get(0).equals(neighbour) && !neighbour.equals(parent)) {
					path.add(neighbour);
	                LOG.debug("cycleUtil: found cycle node=" + node + " path=" + path + " neighbour=" + neighbour + " parent=" + parent);
	                return path; 
	            }
	        } 
        }
        return new LinkedList<>(); 
    } 
  
	@LogMethod(level=LogLevel.DEBUG)
    public static List<List<Node>>  cyclicAllCycles(Graph<Node,Edge> graph, Node startNode) 
    { 
		List<List<Node>> cycles = new ArrayList<>();
		List<Node> path = new ArrayList<>();		
		Set<Node> visited = new HashSet<>();		

		boolean startNodeInGraph = graph.vertexSet().contains(startNode);
		
		if(!startNodeInGraph) {
			LOG.debug("... cycle identifier: start node {} missing in graph {}",  startNode, graph.vertexSet());
			return cycles;
		}
		
		BreadthFirstIterator<Node,Edge> iterator = new BreadthFirstIterator<>(graph, startNode);
		
		while(iterator.hasNext()) {
			Node node = iterator.next();
			if(visited.contains(node)) continue;
			
			path.add(node);
			cyclicUtilAllCycles(graph, node, path, node, cycles, visited);
			path.remove(node);

		}
		
		cycles.sort(GraphAlgorithms::compareBySize); 
		
		return cycles; 
    } 
	
	
	public static int compareBySize(Collection<?> coll1, Collection<?> coll2) { 
		return coll1.size() - coll2.size();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
    private static List<List<Node>>  cyclicUtilAllCycles(Graph<Node,Edge> graph, Node node, List<Node> path, Node parent, List<List<Node>> foundCycles, Set<Node> visited) 
    {   
        Set<Node> neighbours = getNeighbours(graph, node).stream()
        						.filter(n -> !n.equals(node))
        						.collect(toSet());
        
        
        LOG.debug("cyclicUtilAllCycles: node={} neighbours={} #path={}", node, neighbours, path.size());
        if(!foundCycles.isEmpty()) {
        	foundCycles.forEach(cycle -> LOG.debug("cyclicUtilAllCycles: node={} cycle={}", node, cycle));
        }
        
        for(Node neighbour : neighbours) {
        	if(neighbour.isEnumNode() || neighbour.equals(parent) || (true && visited.contains(neighbour))) continue;

        	visited.add(neighbour);

        	if (path.contains(neighbour)) {
				List<Node> circle = getCircle(path, neighbour);

				if(!isCycleAlreadyFound(foundCycles,circle)) {
					foundCycles.add(circle);
				}
                // visited.add(neighbour);
                
            } else {        	
	        	path.add(neighbour);
                // visited.add(neighbour);
	        	cyclicUtilAllCycles(graph, neighbour, path, node, foundCycles, visited);
	        	path.remove(neighbour);
            }
        } 
        return foundCycles; 
    } 
  
	private static boolean isCycleAlreadyFound(List<List<Node>> foundCycles, List<Node> circle) {
		return foundCycles.stream().anyMatch( cycle -> cycle.containsAll(circle) && circle.containsAll(cycle));
	}

	@LogMethod(level=LogLevel.DEBUG)
    private static List<Node> getCircle(List<Node> path, Node start) {
		List<Node> res = new LinkedList<>();

		if(path.contains(start)) {
			// iterate from end of path until the 'start' node is found
			ListIterator<Node> iterator = path.listIterator(path.size());
			boolean done=false;
			while(!done && iterator.hasPrevious()) {
				Node item = iterator.previous();
				res.add(item);
				done = item.equals(start);
			}
			Collections.reverse(res);
			res.add(start);			
		}		
		return res;
	}

	public static Map<Integer, List<List<Node>> > getCirclesForNode(List<List<Node>> circles, Node node) {
		return circles.stream().filter(circle -> circle.contains(node)).collect(Collectors.groupingBy(List::size));
	}

	public static List<List<Node>> removeCirclesForNode(List<List<Node>> circles, Node node) {
		return circles.stream().filter(circle -> !circle.contains(node)).collect(toList());
	}
	
}


