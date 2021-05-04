package no.paneon.api.graph.complexity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;

import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.graph.Property;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

public class ComplexityAdjustedAPIGraph {

    static final Logger LOG = LogManager.getLogger(ComplexityAdjustedAPIGraph.class);
	
	Map<String, Map<String,Graph<Node,Edge>> > allGraphs;

	CoreAPIGraph graph;
	Set<String> resources;
	
	static final int GRAPH_PRUNE_MIN_SIZE = 10;
	static final String REF_OR_VALUE = "RefOrValue";
	
	public ComplexityAdjustedAPIGraph(CoreAPIGraph graph) {
		this.graph = graph;
		this.allGraphs = new HashMap<>();		
		this.resources = new HashSet<>();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void generateSubGraphs() {
		for(String resource : resources) {
			generateSubGraphsForResource(resource);
		}	
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void generateSubGraphsForResource(String resource) {
			
	    Node resourceNode = graph.getNode(resource);

	    LOG.debug("generateSubGraphsForResource: resource={} completeGraph={}" , resource, graph.getCompleteGraph());

	    Graph<Node,Edge> resourceGraph = CoreAPIGraph.getSubGraphWithInheritance(graph.getCompleteGraph(), resourceNode, resourceNode);
	    
	    LOG.debug("generateSubGraphsForResource: resource={} resourceGraph={}" , resource, resourceGraph);

	    GraphComplexity analyser = new GraphComplexity(resourceGraph, resourceNode);
	    
	    Map<Node, Integer> complexity = analyser.computeGraphComplexity();
	    
	    LOG.debug("generateSubGraphsForResource: complexity resourceNode={} keys={}" , resourceNode, complexity.keySet());
	    
	    Map<String,Graph<Node,Edge>> graphMap = new HashMap<>();
	    
	    if(complexity.isEmpty()) {    	
	    	graphMap.put(resource, resourceGraph);
	    } else {
	    	complexity.keySet().stream().forEach(node -> graphMap.put(node.getName(), CoreAPIGraph.getSubGraphWithInheritance(resourceGraph, node, resourceNode)) );
	    }
	    
	    Map<String,Graph<Node,Edge>> mappingGraphs = addMissingMappedResources(graph.getCompleteGraph(), resourceGraph.vertexSet());

	    mappingGraphs.keySet().forEach(key -> graphMap.put(key,mappingGraphs.get(key)));
	    
	    LOG.debug("generateSubGraphsForResource: resource={} mappingGraphs={}" , resource, mappingGraphs.keySet());
	    
	    LOG.debug("generateSubGraphsForResource: complexity resourceNode={} graph for resource={}" , resourceNode, graphMap.get(resource).vertexSet());

//	    Set<String> allNodes = resourceGraph.vertexSet().stream().map(Node::getName).collect(toSet());
//	    Set<String> refOrValueNodes = allNodes.stream().filter(s -> s.endsWith(REF_OR_VALUE)).collect(toSet());
//	    
//	    Set<String> complexRefOrValueNodes = allNodes.stream().filter(s -> refOrValueNodes.contains(s + REF_OR_VALUE)).collect(toSet());
	    	    
//	    LOG.debug("generateSubGraphsForResource: resource={} graphMap={} refOrValueNodes={}", resource, graphMap.keySet(), complexRefOrValueNodes);
//
//	    for(String candidate : complexRefOrValueNodes) {
//	    	String refOrValue = candidate + "RefOrValue";
//	    	if(!graphMap.containsKey(refOrValue)) {
//	    		Node refOrValueNode = CoreAPIGraph.getNodeByName(resourceGraph, refOrValue).get();
//	    		graphMap.put(refOrValue, CoreAPIGraph.getSubGraph(resourceGraph, refOrValueNode));
//	    	}
//	    }
	    
	    allGraphs.put(resource, graphMap);
	    	    
	    // LOG.debug("generateSubGraphsForResource: complexity resourceNode={} graph for resource={}" , resourceNode, graphMap.get(resource).vertexSet());

	    pruneSubGraphsFromContainingGraphs(resource, graphMap);
	    
	    LOG.debug("generateSubGraphsForResource: #1 resource={} final graphMap={}", resource, graphMap.keySet());

	    // LOG.debug("generateSubGraphsForResource: complexity resourceNode={} graph for resource={}" , resourceNode, graphMap.get(resource).vertexSet());

		removeSubGraphsCoveredByContainingGraph(graphMap);

	    // LOG.debug("generateSubGraphsForResource: complexity resourceNode={} graph for resource={}" , resourceNode, graphMap.get(resource).vertexSet());

	    allGraphs.put(resource, graphMap);

	    LOG.debug("generateSubGraphsForResource: #2 resource={} final graphMap={}", resource, graphMap.keySet());
	    

	}
	
	private Map<String,Graph<Node,Edge>> addMissingMappedResources(Graph<Node, Edge> graph, Set<Node> nodes) {
		
		Map<String, Graph<Node, Edge>> res = new HashMap<>();
		
		Set<String> mapping = nodes.stream()
								.map(Node::getDiscriminatorMapping)
								.flatMap(Set::stream)
								.collect(toSet());
			    		
		Set<String> refs = mapping.stream()
				.filter(s -> s.endsWith("Ref"))
				// .map(s -> s.replaceFirst("Ref$",""))
				.collect(toSet());
		
		final Set<String> refsCore = nodes.stream()
				.map(Node::getName)
				.filter(refs::contains)
				.collect(toSet());
		
		mapping = mapping.stream()
				.filter(s -> !refsCore.contains(s))
				.collect(toSet());
		
	    LOG.debug("addMissingMappedResources: nodes={}", nodes);
		LOG.debug("addMissingMappedResources: refsCore={}", refsCore);

	    LOG.debug("addMissingMappedResources: mapping={}", mapping);
	    LOG.debug("addMissingMappedResources: nodes={}",   nodes);
	    LOG.debug("addMissingMappedResources: graph={}",   graph.edgeSet());

	    for(String candidate : mapping) {
	    	Optional<Node> optNode = CoreAPIGraph.getNodeByName(graph, candidate);
	    	
	    	if(optNode.isPresent()) {
	    		Node node = optNode.get();
			    LOG.debug("addMissingMappedResources: node={} edges={}", node, graph.outgoingEdgesOf(node));
	
		    	Graph<Node, Edge> g = CoreAPIGraph.getSubGraphWithInheritance(graph, node, node);
		    	if(!g.edgeSet().isEmpty()) {
		    		res.put(candidate, g);
		    		LOG.debug("addMissingMappedResources: candidate={} graph={}", candidate, res.get(candidate));
		    	}
	    	}
	    }
		
	    return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void pruneSubGraphsFromContainingGraphs(String resource, Map<String, Graph<Node, Edge>> graphMap) {
	    List<String> remainingGraphs = getGraphsBySize(graphMap);
	    
		while(!remainingGraphs.isEmpty()) {		
			String node = remainingGraphs.remove(0);
							
			LOG.debug("pruneSubGraphsFromContainingGraphs: resource={} node={}",  resource, node);

			for(String containing : remainingGraphs) {
				removeContainedSubgraph(resource, containing, graphMap.get(node), graphMap);
			}
				
		}
			
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void removeContainedSubgraph(String resource, String root, Graph<Node, Edge> subGraphToRemove, Map<String,Graph<Node,Edge>> graphMap) {
		Graph<Node,Edge> originalGraph = graphMap.get(root);

		Optional<Node> optSubResource = getNodeByName(originalGraph,root);

		LOG.debug("removeContainedSubgraph: subResource={}",  optSubResource);

		if(!optSubResource.isPresent()) return;
		
		Node subResource = optSubResource.get();
		
		Optional<Node> optResource = getNodeByName(originalGraph,resource);
		if(!optResource.isPresent()) return;

		Node resourceNode = optResource.get();

		Graph<Node,Edge> graphToPrune = CoreAPIGraph.getSubGraphWithInheritance(originalGraph, subResource, resourceNode);

		if(graphToPrune.vertexSet().size()<GRAPH_PRUNE_MIN_SIZE) return;
						
		Set<Node> nodesToPrune = subGraphToRemove.vertexSet().stream().filter(graphToPrune::containsVertex).collect(toSet());
				    	
		LOG.debug("removeContainedSubgraph: #0 subResource={} nodesToPrune={}",  subResource, nodesToPrune);
				
		Set<Edge> edgesToPrune = nodesToPrune.stream()
									.map(graphToPrune::outgoingEdgesOf)
									.flatMap(Set::stream)
									.collect(toSet());

		LOG.debug("removeContainedSubgraph: #1 subResource={} nodesToPrune={}",  subResource, nodesToPrune);
		LOG.debug("removeContainedSubgraph: #1 subResource={} edgesToPrune={}",  subResource, edgesToPrune);

		graphToPrune.removeAllEdges(edgesToPrune);
					
		removeOrphans(graphToPrune, subResource);
				 
		Set<Node> remainingNodes = graphToPrune.vertexSet();
		
		LOG.debug("removeContainedSubgraph: #2 subResource={} remainingNodes={}",  subResource, remainingNodes);

		final Graph<Node,Edge> g = graphToPrune;
		Set<Edge> reinstateEdges = edgesToPrune.stream()
			.filter(edge -> remainingNodes.contains(edge.node))
			.filter(edge -> remainingNodes.contains(edge.related))
			.filter(edge -> isCompleteCandidate(g, edge.node))
			.filter(edge -> isCompleteCandidate(g, edge.related))
			.filter(Edge::isInheritance)
			.filter(Edge::isOneOf)
			.collect(toSet());
		
		LOG.debug("removeContainedSubgraph: subResource={} reinstateEdges={}",  subResource, reinstateEdges);
		
		for(Edge edge : reinstateEdges) {
			Node source = originalGraph.getEdgeSource(edge);
			Node target = originalGraph.getEdgeTarget(edge);
			if(!isRefOrValue(source)) {
				graphToPrune.addEdge(source, target, edge);
				LOG.debug("removeContainedSubgraph: addEdge source={} target={} edge={}", source, target, edge);
			}
		}
		
		graphToPrune = addSimpleInheritance(subResource, graphToPrune, originalGraph, root.contentEquals(resource));
		
		graphToPrune = revertToOriginalIfTooSmall(subResource, graphToPrune, originalGraph, root.contentEquals(resource));
							
		graphMap.put(root, graphToPrune);
		
		LOG.debug("removeContainedSubgraph: final subResource={} graphMap={}", subResource, graphMap.keySet());

		
	}

	private Graph<Node, Edge> addSimpleInheritance(Node resource, Graph<Node, Edge> activeGraph, Graph<Node, Edge> originalGraph, boolean contentEquals) {
		
		Predicate<Node> isInheritanceOnly = n -> originalGraph.outgoingEdgesOf(n).stream().allMatch(Edge::isInheritance);
		
		Predicate<Node> notCoreInheritance = n -> n.getInheritance().size()==0 && !n.isEnumNode();
		
		Set<Node> simpleInheritance = activeGraph.vertexSet().stream().filter(isInheritanceOnly).filter(notCoreInheritance).collect(toSet());
		
		LOG.debug("addSimpleInheritance: resource={} simpleInheritance={}", resource, simpleInheritance);
		
		for(Node n : simpleInheritance) {
			for(Edge edge : originalGraph.outgoingEdgesOf(n)) {
				Node source = originalGraph.getEdgeSource(edge);
				Node target = originalGraph.getEdgeTarget(edge);
				activeGraph.addVertex(target);
				activeGraph.addEdge(source, target, edge);
				LOG.debug("addSimpleInheritance: addEdge source={} target={} edge={}", source, target, edge);
			}
		}

		return activeGraph;
	}

	private boolean isRefOrValue(Node node) {
		return node.getName().endsWith("RefOrValue");
	}

	private Graph<Node, Edge> revertToOriginalIfTooSmall(Node subResource, Graph<Node, Edge> prunedGraph, Graph<Node, Edge> originalGraph, boolean isResourceGraph) {
	    int preComplexity = getComplexity(originalGraph, subResource);	
	    int postComplexity = getComplexity(prunedGraph, subResource);

	    double fraction = (preComplexity-postComplexity)/(1.0*preComplexity);
	    
	    boolean isAboveLimit = GraphComplexity.isAboveLowerLimit(postComplexity);
	    
		if( !isAboveLimit && 
			( fraction<0.05 || prunedGraph.vertexSet().size()<6 || (prunedGraph.vertexSet().size()<6 && isResourceGraph))) {			

			LOG.debug("revertToOriginalIfTooSmall: subResource={} isAboveLowerLimit={} fraction={} preComplexity={} postComplexity={}",  
					subResource, isAboveLimit, fraction, preComplexity, postComplexity);
			
			LOG.debug("revertToOriginalIfTooSmall: prunedGraph={}", prunedGraph);
			
			prunedGraph = originalGraph;
		}

		return prunedGraph;
	
	}

	private int getComplexity(Graph<Node, Edge> graph, Node root) {
	    GraphComplexity analyser = new GraphComplexity(graph, root);	    
	    analyser.computeGraphComplexity();
	    return analyser.getComplexity();	

	}

	@LogMethod(level=LogLevel.DEBUG)
	private Optional<Node> getNodeByName(Graph<Node, Edge> graph, String node) {
		return CoreAPIGraph.getNodeByName(graph, node);	
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void removeSubGraphsCoveredByContainingGraph(Map<String, Graph<Node, Edge>> graphMap) {
		Set<String> unusedSubGraphs = new HashSet<>();
	    List<String> remainingGraphs = getGraphsBySize(graphMap);
	    
		while(!remainingGraphs.isEmpty()) {		
			String node = remainingGraphs.remove(0);
																
			remainingGraphs.stream()
				.map(graphMap::get)
				.forEach(superior -> {
					boolean covered = graphMap.get(node).vertexSet().stream().allMatch(n -> superior.vertexSet().contains(n));
					
					if(covered) {
						unusedSubGraphs.add(node);
					}
				});
		}
				
		LOG.debug("removeSubGraphsCoveredByContainingGraph: subgraphs to remove={}", unusedSubGraphs);
		
		unusedSubGraphs.forEach(graphMap::remove);
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private List<String> getGraphsBySize(Map<String, Graph<Node, Edge>> graphMap) {
	   return graphMap.entrySet().stream()
				.sorted((e1, e2) -> compareGraphSize(e1.getValue(), e2.getValue()))		
				.map(Map.Entry::getKey)
				.collect(toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void removeOrphans(Graph<Node, Edge> graphToPrune, Node subResource) {
		boolean removed=true;
		while(removed) {
			Set<Node> orphans = graphToPrune.vertexSet().stream()
									.filter(node -> graphToPrune.containsVertex(node) && graphToPrune.incomingEdgesOf(node).isEmpty())
									.collect(toSet());
			
			orphans.remove(subResource);
			
			graphToPrune.removeAllVertices(orphans);
			
			removed=!orphans.isEmpty();
		}
	}

	private int compareGraphSize(Graph<Node, Edge> graph1, Graph<Node, Edge> graph2) {
		Integer sizeGraph1 = new Integer(graph1.vertexSet().size());
		Integer sizeGraph2 = new Integer(graph2.vertexSet().size());
		return sizeGraph1.compareTo(sizeGraph2);

	}


	@LogMethod(level=LogLevel.DEBUG)
	private boolean isSimpleType(Node node) {
		String type = node.getName();
		return Utils.isSimpleType(type) && !APIModel.isEnumType(type);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void complexityAdjustedGraph(Graph<Node,Edge> graph, Node pivot) {

		Set<Node> simpleTypes = graph.vertexSet().stream()
									.filter(this::isSimpleType)
									.collect(toSet());
		
       	graph.removeAllVertices(simpleTypes);
      	
	    GraphComplexity analyser = new GraphComplexity(graph, pivot);
	    	    
	    analyser.computeGraphComplexity();

	    Set<Node> baseTypes = analyser.getBaseTypes();
	    Set<Node> nonComplexTypes = analyser.getNonComplexTypes();

    	Set<Node> invalidBaseTypes = baseTypes.stream()
										.filter(analyser::noComplexityContribution)
										.filter(baseNode -> getOutboundNeighbours(baseNode).size()<3)
										.collect(toSet());
    	
    	Set<Node> invalidNonComplexTypes = invalidBaseTypes.stream()
											.map(this::getNodesOfSubGraph)
											.flatMap(Set::stream)
											.collect(toSet());
    	
    	baseTypes.removeAll(invalidBaseTypes);
    	nonComplexTypes.removeAll(invalidNonComplexTypes);
      	      	
      	Set<Node> nodesWithoutOutboundEdges = Utils.union( baseTypes, nonComplexTypes );
      	for(Node node : nodesWithoutOutboundEdges ) {
      		if(!node.equals(pivot) && baseTypes.contains(node)) {
      			Set<Edge> edgesToRemove = graph.outgoingEdgesOf(node);
      			
      			graph.removeAllEdges(edgesToRemove);
      		}
      	}
      	
      	Set<Node> reachableNodes = GraphAlgorithms.getNodesOfSubGraph(graph, pivot);
      	      	
      	Set<Node> nonReachableNodes = Utils.difference( graph.vertexSet(), reachableNodes);
    		
      	graph.removeAllVertices(nonReachableNodes);
      	
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean isPivot(Node node, Node pivot) {
		return node.equals(pivot);	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean isIndependent(Graph<Node,Edge> graph, Node node) {
		return graph.outgoingEdgesOf(node).isEmpty() && graph.incomingEdgesOf(node).isEmpty();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getOutboundNeighbours(Node node) {
		return CoreAPIGraph.getOutboundNeighbours(graph.getCompleteGraph(), node);	
	}

	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getNodesOfSubGraph(Node node) {
		return CoreAPIGraph.getNodesOfSubGraph(graph.getCompleteGraph(), node);	
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isComplete(Graph<Node,Edge> graph, Node node) {
		for(Property property : node.getProperties() ) {
			if(!property.isSimpleType() || property.isEnum()) {
				String type = property.getType();
				Optional<Node> typeNode = CoreAPIGraph.getNodeByName(graph, type);
				
				if(!typeNode.isPresent()) return false;
				if(graph.getAllEdges(node, typeNode.get()).isEmpty()) return false;
				
			}
		}
		return true;
 	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isCompleteCandidate(Graph<Node,Edge> graph, Node node) {
		for(Property property : node.getProperties() ) {
			if(!property.isSimpleType()) {
				String type = property.getType();
				Optional<Node> typeNode = CoreAPIGraph.getNodeByName(graph, type);
				
				if(!typeNode.isPresent()) return false;
				
			}
		}
		return true;
 	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Map<String,Graph<Node,Edge>> getGraphsForResource(String resource) {
		if(allGraphs.containsKey(resource)) {
			return allGraphs.get(resource);
		} else {
			return new HashMap<>(); 
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<String> getSubGraphLabels(String resource) {
		List<String> res = new LinkedList<>();
		if(allGraphs.containsKey(resource)) res.addAll( allGraphs.get(resource).keySet() );
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Optional<Graph<Node, Edge>> getSubGraph(String resource, String pivot) {
		Optional<Graph<Node,Edge>> res = Optional.empty();
		
//		if(allGraphs.containsKey(resource) && allGraphs.get(resource).containsKey(pivot)) {
			res = Optional.of( allGraphs.get(resource).get(pivot) );
//		}
			
		LOG.debug("getSubGraph:: resource={} pivot={} res={}",  resource, pivot, res);
		
		return res;
	}
	
}

