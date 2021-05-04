package no.paneon.api.graph.complexity;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.jgrapht.Graph;

import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Utils;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

public class Complexity {

    static final Logger LOG = LogManager.getLogger(Complexity.class);

	Graph<Node,Edge> graph;
	Node resource;
	
	Map<Node, Integer> nodeComplexity;
	
	public Complexity(Graph<Node,Edge> graph, Node resource) {
		this.graph = CoreAPIGraph.getSubGraphWithInheritance(graph, resource, resource);
		this.resource = resource;
		this.nodeComplexity = new HashMap<>();

	}
	
	static final String REF_OR_VALUE = "RefOrValue";

	static final int PATH_LENGTH_THRESHOLD = 0;
	static final int MIN_COMPLEXITY = 75;
	static final int MIN_COMPLEXITY_HIGH = 150;

	static final int MAX_DIAGRAM_COMPLEXITY = 1500;
	static final int LOW_DIAGRAM_COMPLEXITY = 750;

	@LogMethod(level=LogLevel.DEBUG)
	public Map<Node, Integer> computeGraphComplexity() {

		PathAlgorithms pathAlgs = new PathAlgorithms(this.graph, this.resource);
		
		Map<Node,Integer> shortestPath = pathAlgs.computeShortestPath();		
		Map<Node,Integer> longestPath = pathAlgs.computeLongestPath();
					
		LOG.debug("graph=" + graph);
		
		if(!shortestPath.isEmpty() && !longestPath.isEmpty()) {
			for(Node node : graph.vertexSet().stream().sorted().collect(toList())) {
	
				int complexityContribution = computeComplexityContribution(graph, node, shortestPath.get(node), longestPath.get(node));
				
				if(complexityContribution>0) nodeComplexity.put(node, complexityContribution);
							
			}
		}
				
		return nodeComplexity;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private int computeComplexityContribution(Graph<Node,Edge> graph, Node node, int shortest, int longest) {
		
		int complexityContribution = 0;
		
		LOG.debug("computeComplexityContribution: node=" + node );

		if(shortest>PATH_LENGTH_THRESHOLD) {
			Set<Node> subGraph = CoreAPIGraph.getSubGraphNodes(graph, node);
			Set<Node> inbound = CoreAPIGraph.getInboundNeighbours(graph, node);
			Set<Node> outbound = CoreAPIGraph.getOutboundNeighbours(graph, node);

			Set<Node> outboundNonLeafs = outbound.stream().filter(n -> !graph.outgoingEdgesOf(n).isEmpty()).collect(toSet());
			
			LOG.debug("computeComplexityContribution: node=" + node + " outboundNonLeafs=" + outboundNonLeafs);
			
			if(outboundNonLeafs.size()>1) {
				
				Set<Node> allInboundOutbound = Utils.union( inbound, outbound );					
				Set<Node> differenceInboundOutbound = Utils.difference( inbound, outbound );
				
				int pathComplexity = longest * shortest;
				int subGraphContribution = subGraph.size()+1;
				int allEdgesContribution = allInboundOutbound.size();
				int differenceContribution = differenceInboundOutbound.size();
				
				// complexityContribution = pathComplexity * subGraphContribution * allEdgesContribution * differenceContribution;
				
				complexityContribution = 1 * subGraphContribution * allEdgesContribution * differenceContribution;
			}

		}
		
		return complexityContribution;

	}	

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getSimpleTypes() {

		Set<Node> res = getCandidateSimpleTypes();
		
		res = res.stream()
				.filter(node -> !nodeComplexity.containsKey(node) || nodeComplexity.get(node).intValue()<=MIN_COMPLEXITY)
				.collect(toSet());
						
		return res;

	}
	

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getCandidateSimpleTypes() {

		Set<Node> res = new HashSet<>();
		
		Deque<Node> candidates = nodeComplexity.entrySet().stream()
									.sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
									.filter(item -> item.getValue() > MIN_COMPLEXITY)
									.sorted(Map.Entry.comparingByValue())
									.map(Map.Entry::getKey)
									.filter(item -> !item.equals(resource))
									.collect(Collectors.toCollection(ArrayDeque::new));

		int totalComplexity = computeTotalComplexity();
				
		boolean done = (totalComplexity <= MAX_DIAGRAM_COMPLEXITY);
		
		while(!done && !candidates.isEmpty()) {

			Set<Node> processed = new HashSet<>();

			Node candidate = candidates.removeLast();
			
			Set<Node> subGraph = CoreAPIGraph.getSubGraphNodes(graph, candidate);

			for(Node node : subGraph) {
				
				Set<Node> inboundsubGraph = CoreAPIGraph.getReverseSubGraph(graph, node);
				
				inboundsubGraph.removeAll(subGraph);
				inboundsubGraph.remove(candidate);
				inboundsubGraph.remove(this.resource);

				int contribution = nodeComplexity.containsKey(node) ? nodeComplexity.get(node) : 0;

				if(!inboundsubGraph.isEmpty() && !processed.contains(node)) {
										
					totalComplexity = totalComplexity - contribution;
					
					res.add(node);
					
					done = (totalComplexity <= MAX_DIAGRAM_COMPLEXITY);

				} 

				processed.add(node);

			}
					
		}
						
		return res;

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getBaseTypes() {
		Set<Node> res = getCandidateSimpleTypes();
				
		res = res.stream()
				.filter(node -> nodeComplexity.containsKey(node) && nodeComplexity.get(node).intValue()>MIN_COMPLEXITY)
				.collect(toSet());

		if(res.size()==1) res.clear();
		
		if(Config.getSimplifyRefOrValue()) {
			Set<Node> refOrValue = getRefOrValueResources();
			refOrValue.removeAll(res);
			res.addAll( refOrValue); 
		}
		
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getRefOrValueResources() {
		Set<Node> res = new HashSet<>();
						
		for(Node node : graph.vertexSet()) {
			String refOrValueName = node.getName() + REF_OR_VALUE;
			Optional<Node> refOrValue = CoreAPIGraph.getNodeByName(this.graph, refOrValueName);
			if(refOrValue.isPresent()) res.add( refOrValue.get() );
		}
		
		return res;
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public int computeTotalComplexity() {
		return nodeComplexity.entrySet().stream().mapToInt(Map.Entry::getValue).sum();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Map<Node, Integer> getNodeComplexity() {
		return nodeComplexity;
	}

	static final String NEWLINE = "\n";

	@LogMethod(level=LogLevel.DEBUG)
	public void getComments(StringBuilder res) {
		res.append(NEWLINE);
		res.append("' Overall complexity: " + computeTotalComplexity());
		res.append(NEWLINE);

		getNodeComplexity().entrySet().stream()
			.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
			.forEach(entry -> {
				res.append("' class " + entry.getKey() + " complexity: " + entry.getValue());
				res.append(NEWLINE);			
			});
		res.append(NEWLINE);	
	}

	@LogMethod(level=LogLevel.DEBUG)
	public int getContribution(String node) {
		Optional<Node> optNode = CoreAPIGraph.getNodeByName(this.graph, node);
		if(optNode.isPresent())
			return CoreAPIGraph.getSubGraphNodes(this.graph, optNode.get()).size()-1;
		else
			return 0;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean noComplexityContribution(Node node) {
		return nodeComplexity.containsKey(node) && 	nodeComplexity.get(node).intValue() == 0;
	}
	
}

