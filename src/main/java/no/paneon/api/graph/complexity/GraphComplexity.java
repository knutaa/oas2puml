package no.paneon.api.graph.complexity;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.apache.logging.log4j.LogManager;

public class GraphComplexity {

    static final Logger LOG = LogManager.getLogger(GraphComplexity.class);

	Graph<Node,Edge> graph;
	Node resource;
	
	Map<Node, Integer> nodeComplexity;
	
	public GraphComplexity(Graph<Node,Edge> graph, Node resource) {
		this.graph = graph;	
		this.resource = resource;
		this.nodeComplexity = new HashMap<>();

	}
	
	static final String REF_OR_VALUE = "RefOrValue";

	static final int PATH_LENGTH_THRESHOLD = 0;
	static final int MIN_COMPLEXITY = 100;
	static final int MIN_COMPLEXITY_HIGH = 200;

	static final int MAX_DIAGRAM_COMPLEXITY = 2000;
	static final int LOW_DIAGRAM_COMPLEXITY = 1000;

	@LogMethod(level=LogLevel.DEBUG)
	public Map<Node, Integer> computeGraphComplexity() {

		LOG.debug("computeGraphComplexity: nodes={}", this.graph.vertexSet());

		int minimum = Config.getInteger("minimum_complexity");
		if(minimum==0) minimum=Integer.MAX_VALUE;
				
		removeSimpleTypeNodes();
		
		PathAlgorithms pathAlgs = new PathAlgorithms(this.graph, this.resource);
		
		Map<Node,Integer> shortestPath = pathAlgs.computeShortestPath();		
		Map<Node,Integer> longestPath = pathAlgs.computeLongestPath();
					
		Set<Node> nodes = graph.vertexSet();
		
		Set<String> mappings = nodes.stream().map(Node::getDiscriminatorMapping).flatMap(Set::stream).collect(toSet());
		
		LOG.debug("computeGraphComplexity:: nodes={}", nodes);
		LOG.debug("computeGraphComplexity:: mappings={}", mappings);

		for(Node node : graph.vertexSet().stream().sorted().collect(toList())) {

			if(!shortestPath.containsKey(node) || 
			   !longestPath.containsKey(node) ||
			   (!node.equals(resource) && isSimplePrefixGraph(graph,node))) continue;
			
			int complexityContribution = computeComplexityContribution(graph, node, shortestPath.get(node), longestPath.get(node));
			
			if(complexityContribution>minimum+MIN_COMPLEXITY || this.resource.equals(node)) nodeComplexity.put(node, complexityContribution);
					
			LOG.debug("computeGraphComplexity:: node={} complexityContribution={}", node, complexityContribution);

		}
				
		nodeComplexity = nodeComplexity.entrySet().stream()
							.sorted(Map.Entry.comparingByValue())
							.collect(Collectors.toMap(
								    Map.Entry::getKey, 
								    Map.Entry::getValue, 
								    (oldValue, newValue) -> oldValue, LinkedHashMap::new));
						
		return nodeComplexity;
	}
	
	private boolean isSimplePrefixGraph(Graph<Node, Edge> graph, Node node) {
		Set<Node> inbound = GraphAlgorithms.getInboundNeighbours(graph, node);
		Set<Node> outbound = GraphAlgorithms.getOutboundNeighbours(graph, node);

		Set<Node> outboundNonLeaf = outbound.stream()
										.filter(n -> !CoreAPIGraph.isLeafNode(graph,n))
										.collect(toSet());
		
		LOG.debug("computeComplexityContribution: node={} outboundNonLeaf={}", node, outboundNonLeaf);

		outbound.removeAll(inbound);
		
		Predicate<Node> isEnumNode = Node::isEnumNode;
		
		outbound = outbound.stream().filter(isEnumNode.negate()).collect(toSet());
				
		return (outbound.size()<3) || (outboundNonLeaf.size()==1);
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public int getComplexity() {
		return nodeComplexity.values().stream().mapToInt(Integer::intValue).sum();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isAboveLowerLimit() {
		return getComplexity() > LOW_DIAGRAM_COMPLEXITY;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isAboveUpperLimit() {
		return getComplexity() > MAX_DIAGRAM_COMPLEXITY;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isAboveLowerLimit(int complexity) {
		return complexity > LOW_DIAGRAM_COMPLEXITY;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isAboveUpperLimit(int complexity) {
		return complexity > MAX_DIAGRAM_COMPLEXITY;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void removeSimpleTypeNodes() {		
		Set<Node> simpleNodes = this.graph.vertexSet().stream()
									.filter(Node::isSimpleType)
									.collect(toSet());
		
		LOG.debug("removeSimpleTypeNodes:: simpleNodes={}", simpleNodes);
		
		simpleNodes.forEach(this.graph::removeVertex);
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private int computeComplexityContribution(Graph<Node,Edge> graph, Node node, int shortest, int longest) {
		
		int complexityContribution = 0;
		
		if(shortest>PATH_LENGTH_THRESHOLD || node.equals(this.resource)) {
			Set<Node> subGraph = CoreAPIGraph.getNodesOfSubGraph(graph, node);
			Set<Node> inbound = CoreAPIGraph.getInboundNeighbours(graph, node);
			Set<Node> outbound = CoreAPIGraph.getOutboundNeighbours(graph, node);

			Set<Node> allInboundOutbound = Utils.union( inbound, outbound );					
			Set<Node> differenceInboundOutbound = Utils.difference( outbound, inbound );
			
			int degree = graph.degreeOf(node);
			int pathComplexity = 1 + longest * shortest;
			int subGraphContribution = (subGraph.size()<4) ? 1 : subGraph.size()+1;
			int allEdgesContribution = allInboundOutbound.size();
			int differenceContribution = (degree<3) ? 1 : differenceInboundOutbound.size();
						
			complexityContribution = pathComplexity * subGraphContribution * allEdgesContribution * differenceContribution;
					
			
		}
		
		complexityContribution = node.equals(this.resource) && complexityContribution==0 ? MAX_DIAGRAM_COMPLEXITY : complexityContribution;

		return complexityContribution;

	}	

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getNonComplexTypes() {

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
		
		LOG.debug("getCandidateSimpleTypes: candidates={} totalComplexity={}", candidates, totalComplexity);
		
		boolean done = (totalComplexity <= MAX_DIAGRAM_COMPLEXITY);
		
		while(!done && !candidates.isEmpty()) {

			Set<Node> processed = new HashSet<>();

			Node candidate = candidates.removeLast();
			
			Set<Node> subGraph = CoreAPIGraph.getNodesOfSubGraph(graph, candidate);

			LOG.debug("getCandidateSimpleTypes: processing candidate={} subGraph={}", candidate, subGraph );

			for(Node node : subGraph) {
				
				Set<Node> inboundsubGraph = CoreAPIGraph.getReverseSubGraph(graph, node);
				
				inboundsubGraph.removeAll(subGraph);
				inboundsubGraph.remove(candidate);
				inboundsubGraph.remove(this.resource);

				LOG.debug("getCandidateSimpleTypes: node={} inboundsubGraph={}", node, inboundsubGraph );

				int contribution = nodeComplexity.containsKey(node) ? nodeComplexity.get(node) : 0;

				if(!inboundsubGraph.isEmpty() && !processed.contains(node)) {
										
					totalComplexity = totalComplexity - contribution;
					
					res.add(node);
					
					done = (totalComplexity <= MAX_DIAGRAM_COMPLEXITY);

					LOG.debug("getCandidateSimpleTypes: node={} contribution={} processed={}",  node, contribution, processed);

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
				.filter(this::isAboveMinimumComplexity)
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
	private boolean isAboveMinimumComplexity(Node node) {
		int minimum = Config.getInteger("minimum_complexity");
		if(minimum==0) minimum=Integer.MAX_VALUE;
		// if(minimum==0) minimum=Integer.MAX_VALUE;

		return nodeComplexity.containsKey(node) && nodeComplexity.get(node).intValue()>minimum;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getRefOrValueResources() {
		Set<Node> res = new HashSet<>();
				
		if(Config.getSimplifyRefOrValue()) {

			for(Node node : graph.vertexSet()) {
				String refOrValueName = node.getName() + REF_OR_VALUE;
				Optional<Node> refOrValue = CoreAPIGraph.getNodeByName(graph,refOrValueName);
				if(refOrValue.isPresent()) res.add( refOrValue.get() );
			}
		
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
		Optional<Node> optNode = CoreAPIGraph.getNodeByName(graph,node);
		if(optNode.isPresent())
			return CoreAPIGraph.getSubGraphNodes(graph, optNode.get()).size()-1;
		else
			return 0;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean noComplexityContribution(Node node) {
		return nodeComplexity.containsKey(node) && 	nodeComplexity.get(node).intValue() == 0;
	}
	
}

