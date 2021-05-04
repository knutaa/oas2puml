package no.paneon.api.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.Logger;
import org.aspectj.weaver.patterns.ThisOrTargetAnnotationPointcut;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.GraphIterator;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.diagram.app.Utils;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;

public class CoreAPIGraph {

    static final Logger LOG = LogManager.getLogger(CoreAPIGraph.class);

    private Map<String, Node> graphNodes;
    private Map<String, EnumNode> enumNodes;
    private Map<String, List<String>> enumMapping;
		
    private static final String INHERITANCE = "coreInheritanceTypes"; 
    private static final String INHERITANCE_PATTERN = "coreInheritanceRegexp"; 

    static final String INCLUDE_INHERITED = "includeInherited"; 
    
    static final String INCLUDE_DISCRIMINATOR_MAPPING = "includeDiscriminatorMapping";
    
    static final String REF = "$ref";
    
	Graph<Node,Edge> completeGraph;
	
	public CoreAPIGraph() {
		this.graphNodes = new HashMap<>();
		this.enumNodes = new HashMap<>();
		this.enumMapping = new HashMap<>();
		
		this.completeGraph = generateGraph();	
				
		LOG.debug("CoreAPIGraph:: completeGraph={}", completeGraph);
		
	}
	
	private Graph<Node, Edge> convertToUnidirectional(Graph<Node, Edge> subGraph) {
		Graph<Node,Edge> g = GraphTypeBuilder
				.<Node,Edge> undirected().allowingMultipleEdges(false).allowingSelfLoops(false)
				.edgeClass(Edge.class).buildGraph();

		subGraph.vertexSet().forEach(g::addVertex);
		
		subGraph.edgeSet().forEach(edge -> {
			Node source = subGraph.getEdgeSource(edge);
			Node target = subGraph.getEdgeTarget(edge);
			
			if(g.getEdge(source, target)!=null) {
				g.addEdge(source, target);
			}
			
		});
		
		return g;
	}

	public CoreAPIGraph(CoreAPIGraph core) {
		this.graphNodes = core.graphNodes;
		this.enumNodes = core.enumNodes;
		this.enumMapping = core.enumMapping;
		
		this.completeGraph = core.completeGraph;
		
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	private Graph<Node,Edge> generateGraph() {

		Graph<Node,Edge> g = GraphTypeBuilder
								.<Node,Edge> directed().allowingMultipleEdges(true).allowingSelfLoops(true)
								.edgeClass(Edge.class).buildGraph();

		addNodesAndEnums(g);						
		addProperties(g);

		LOG.debug("generateGraph: g=" + g);
		
		return g;
			
	}
	 
	@LogMethod(level=LogLevel.DEBUG)
	private void addNodesAndEnums(Graph<Node, Edge> g) {
		APIModel.getAllDefinitions().forEach(node -> getOrAddNode(g,node));
		
		LOG.debug("addNodesAndEnums: nodes={}", g.vertexSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Node getOrAddNode(Graph<Node, Edge> g, String definition) {		
		Node node;
		String coreDefinition = APIModel.removePrefix(definition);
		
		Optional<Node> candidate = getNodeByName(g,definition);
				
		if(candidate.isEmpty()) {
			node = APIModel.isEnumType(definition) ? new EnumNode(coreDefinition) : new Node(coreDefinition);
			g.addVertex(node);
			graphNodes.put(coreDefinition, node);
			
			if(node instanceof EnumNode) {
				addEnumNode((EnumNode) node);
			}
			
			LOG.debug("addNode:: adding node={}", definition);
		} else {
			node = candidate.get();
		}
		
		return node;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addProperties(Graph<Node, Edge> g) {
		for(String definition : APIModel.getAllDefinitions() ) {
						
			String coreDefinition = APIModel.removePrefix(definition);

			Node from = getOrAddNode(g,coreDefinition);
			// Node from = graphNodes.get(coreDefinition);

			LOG.debug("addProperties: coreDefinition={} from={}", coreDefinition, from);

			JSONObject properties = APIModel.getPropertyObjectForResource(definition);
			addProperties(g, from, definition, properties);			
			
			LOG.debug("addProperties: g={}", g);

			JSONArray allOfs = APIModel.getAllOfForResource(definition);
			
			allOfs.forEach(allOf -> {
				if(allOf instanceof JSONObject) {
					JSONObject allOfObject = (JSONObject) allOf;
					
					LOG.debug("addProperties:: allOfObject={}", allOfObject);

					if(allOfObject.has(REF)) {
						processAllOfReference(g, allOfObject, from);					
					} else {
						JSONObject obj = APIModel.getPropertyObjectBySchemaObject(allOfObject);
						LOG.debug("addProperties:: allOf: resource={} obj={}", definition, obj);

						if(obj!=null) {
							String type = APIModel.getTypeName(obj);
	
							// if(type==null || type.isEmpty()) type=definition;
							
							LOG.debug("addProperties:: allOf: resource={} type={} obj={}", definition, type, obj);
							
							addProperties(g, from, type, obj);			
		
						}
					}				
				}
			});
			
			boolean includeDiscriminaatorMapping = Config.getBoolean(INCLUDE_DISCRIMINATOR_MAPPING);
			JSONObject mapping = APIModel.getMappingForResource(definition);
			if(includeDiscriminaatorMapping && mapping !=null) {
				Set<String> oneofs = mapping.keySet();
				oneofs.remove(definition);
				
				LOG.debug("addProperties:: mapping: resource={} oneofs={}", definition, oneofs);
	
				for(String oneof : oneofs) {					
					Node to = getOrAddNode(g, oneof);
		
					Edge edge = new OneOf(from, to);
			
					g.addEdge(from, to, edge);		
		
					LOG.debug("addProperties:: adding edge={}", edge);	
				}
			}
			
		}		
	}
	
	private void processAllOfReference(Graph<Node, Edge> g, JSONObject allOfObject, Node from) {
		String type = APIModel.getTypeByReference(allOfObject.optString(REF));
		
		boolean flattenInheritance = isBasicInheritanceType(type) || isPatternInheritance(type);
		
		flattenInheritance = flattenInheritance && !APIModel.isEnumType(type);
		
		if(flattenInheritance) {
			
			from.addInheritance(type);
			
			boolean includeInherited = Config.getBoolean(INCLUDE_INHERITED); 
			if(includeInherited) {
				JSONObject obj = APIModel.getDefinitionBySchemaObject(allOfObject);							
				
				Set<Property> propertiesBefore = new HashSet<>(from.getProperties());
				
				from.addAllOfObject(obj, Property.VISIBLE_INHERITED);
				
				addEnumsToGraph(g, from, propertiesBefore);
				
			}
			
		} else {
			Node to = graphNodes.get(type);
			
			Edge edge = new AllOf(from, to);
	
			g.addEdge(from, to, edge);		

			LOG.debug("addProperties:: adding edge={}", edge);	
		}
		
	}

	private void addEnumsToGraph(Graph<Node, Edge> g, Node node, Set<Property> propertiesBefore) {
		Set<Property> propertiesAdded = new HashSet<>(node.getProperties());
		propertiesAdded.removeAll(propertiesBefore);
		Set<Property> enumsAdded = propertiesAdded.stream().filter(Property::isEnum).collect(toSet()); 

		if(!enumsAdded.isEmpty()) {
			LOG.debug("adding enums for node={} enums={}",  node, enumsAdded); 
			enumsAdded.forEach(property -> {
				Node to = graphNodes.get(property.getType());
				Edge edge = new EdgeEnum(node, property.name, to, property.cardinality, property.required);
				g.addEdge(node, to, edge);		
			});
		}		
	}

	private static boolean isBasicInheritanceType(String type) {
		final List<String> inheritance = Config.get(INHERITANCE);
		return inheritance.contains(type);
	}

	private static boolean isPatternInheritance(String type) {
		final List<String> patterns = Config.get(INHERITANCE_PATTERN);
		return patterns.stream().anyMatch(pattern -> type.matches(pattern));
	}
	
	public static boolean isPatternInheritance(Node type) {
		final List<String> patterns = Config.get(INHERITANCE_PATTERN);
		return patterns.stream().anyMatch(pattern -> type.getName().matches(pattern));
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addProperties(Graph<Node, Edge> graph, Node from, String typeName, JSONObject properties) {
		
		LOG.debug("addProperties: typeName={} properties={}", typeName, properties.keySet());

		for(String propertyName : properties.keySet()) {
			JSONObject property = properties.getJSONObject(propertyName);
			String type = APIModel.getTypeName(property);

			String coreType = APIModel.removePrefix(type);

			LOG.debug("addProperties: from={} propertyName={} type={} coreType={} property={}", from, propertyName, type, coreType, property);
			LOG.debug("addProperties: from={} propertyName={} type={} isSimpleType={}", from, propertyName, type, APIModel.isSimpleType(type));

			if(!APIModel.isSimpleType(type) || APIModel.isEnumType(type)) {
				
				Node to = graphNodes.get(coreType);

				LOG.debug("addProperties: type={} to={}", coreType, to);

				boolean isRequired = APIModel.isRequired(typeName, propertyName);
				String cardinality = APIModel.getCardinality(property, isRequired);

				Edge edge = APIModel.isEnumType(type) ? 
								new EdgeEnum(from, propertyName, to, cardinality, isRequired) :
								new Edge(from, propertyName, to, cardinality, isRequired);

				LOG.debug("addProperties: edge={}", edge);

				graph.addEdge(from, to, edge);				

			} 
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Edge> getOutboundEdges(Graph<Node,Edge> graph, Node node) {
		Set<Edge> res = new HashSet<>();
		if(graph.vertexSet().contains(node)) {
			res.addAll(  graph.outgoingEdgesOf(node) );
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getOutboundNeighbours(Graph<Node,Edge> graph, Node node) {
		Set<Node> res = new HashSet<>();
		if(graph.vertexSet().contains(node)) {
			res.addAll(  graph.outgoingEdgesOf(node).stream().map(graph::getEdgeTarget).collect(toSet()) );
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getInboundNeighbours(Graph<Node,Edge> graph, Node node) {
		Set<Node> res = new HashSet<>();
		if(graph.vertexSet().contains(node)) {
			res.addAll( graph.incomingEdgesOf(node).stream().map(graph::getEdgeSource).collect(toSet()) );
			// res.addAll( graph.vertexSet().stream().filter(n -> graph.containsEdge(node, n)).collect(toSet()) ); // if not explicit incoming edge
		}
		
		LOG.debug("getInboundNeighbours: node={} res={}", node, res);
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getNeighbours(Graph<Node,Edge> graph, Node node) {
		Set<Node> res = getOutboundNeighbours(graph,node);		
		res.addAll( getInboundNeighbours(graph,node) );
				
		LOG.debug("getNeighbours: node={} res={} graph={}", node, res, graph.vertexSet());

		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<EnumNode> getEnumsForNode(Graph<Node,Edge> graph, Node node) {
		return getOutboundNeighbours(graph, node).stream()
				.filter(n -> n instanceof EnumNode)
				.map(n -> (EnumNode) n)
				.collect(toSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getNodeNames(Graph<Node,Edge> graph) {
		return graph.vertexSet().stream().map(Node::getName).collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getNodesOfSubGraph(Graph<Node,Edge> graph, Node node) {
		Set<Node> res = new HashSet<>();

		GraphIterator<Node, Edge> it = new BreadthFirstIterator<>(graph, node);

		while(it.hasNext() ) {
			Node cand = it.next();
			// if(!res.contains(cand)) res.add(cand);
			res.add(cand);
		}

		Set<Node> superClassOf = graph.incomingEdgesOf(node).stream().filter(edge -> edge instanceof AllOf).map(graph::getEdgeSource).collect(toSet());
		
		superClassOf.removeAll(res);
		
		superClassOf.forEach(superior -> res.addAll( getNodesOfSubGraph(graph, superior)));
		
		LOG.debug("getNodesOfSubGraph:: node={} res={}", node, res);
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Graph<Node,Edge> getSubGraphWithInheritance(Graph<Node,Edge> graph, Node node, Node resource) {
		Set<Node> nodes = getNodesOfSubGraph(graph, node);
		
		LOG.debug("getSubGraph:: node={} resource={} nodes={}", node, resource, nodes);

		Set<Node> simpleTypes = graph.vertexSet().stream()
				.filter(Node::isSimpleType)
				.collect(toSet());

		nodes.removeAll(simpleTypes);

		Set<Node> excludeNodes = new HashSet<>();
		
		if(!node.equals(resource)) {
			Set<Node> inheritedBy = nodes.stream()
					.filter(n -> graph.getAllEdges(n, node).stream().anyMatch(Edge::isInheritance))
					.filter(n -> CoreAPIGraph.isLeafNodeOrOnlyEnums(graph, n))
					.collect(toSet());

			LOG.debug("getSubGraph:: node={} resource={} nodes={}", node, resource, nodes);
			LOG.debug("getSubGraph:: node={} resource={} inheritedBy={}", node, resource, inheritedBy);
			
			if(!Config.getBoolean(INCLUDE_INHERITED)) {
				inheritedBy.clear();
			} else {
				excludeNodes.addAll(inheritedBy);
			}
			
			LOG.debug("getSubGraph:: node={} resource={} inheritedBy={} nodes={}", node, resource, inheritedBy, nodes);

		}
	
		if(node.equals(resource)) {
			Set<Node> oneOfs = nodes.stream()
					.filter(n -> graph.getAllEdges(n, node).stream().anyMatch(Edge::isOneOf))
					.collect(toSet());

			LOG.debug("getSubGraph:: node={} oneOfs={}", node, oneOfs);

			excludeNodes.addAll(oneOfs);

		}

		LOG.debug("getSubGraph:: node={} excludeNodes={}", node, excludeNodes);

		nodes.removeAll(excludeNodes);

		Graph<Node,Edge> subGraph = new AsSubgraph<>(graph, nodes);
		
		LOG.debug("getSubGraph:: node={} subGraph edges={}", node, subGraph.edgeSet().stream().map(Object::toString).collect(Collectors.joining("\n")));

		removeOutboundFromDiscriminatorMappingNodes(subGraph,nodes);

		LOG.debug("getSubGraph:: node={} subGraph edges={}", node, subGraph.edgeSet());

		Predicate<Node> noNeighbours = n -> CoreAPIGraph.getNeighbours(subGraph, n).isEmpty();		
		Predicate<Node> noInboundNeighbours = n -> CoreAPIGraph.getInboundNeighbours(subGraph, n).isEmpty();
		
		Set<Node> orphans = selectGraphNodes(subGraph,noNeighbours,node);		
		orphans.forEach(subGraph::removeVertex);
		
		LOG.debug("getSubGraph:: node={} orphans={}", node, orphans);
		LOG.debug("getSubGraph:: node={} subGraph={}", node, subGraph);

		boolean remove=true;
		while(remove) {
			remove = false;
			
			Set<Node> reachable = CoreAPIGraph.getSubGraphNodes(subGraph, node);
			Set<Node> allNodes = subGraph.vertexSet();
			Set<Node> unReachable = new HashSet<>(allNodes);
			unReachable.removeAll(reachable);
			
			unReachable.removeAll(excludeNodes);
			
			unReachable.remove(node);
			
			LOG.debug("getSubGraph:: node={} reachable={} ", node, reachable);
			LOG.debug("getSubGraph:: node={} unReachabl={} ", node, unReachable);
			
			if(!reachable.isEmpty() && !unReachable.isEmpty()) {
				remove=true;
				unReachable.forEach(subGraph::removeVertex); 				
			}
			
//			Set<Node> noInbound = selectGraphNodes(subGraph,noInboundNeighbours,node);
//						
//			if(node.getName().contentEquals("GcQuote")) {
//				Optional<Node> pp = CoreAPIGraph.getNodeByName(subGraph, "ProductPrice");
//				
//				if(pp.isPresent()) {
//					LOG.debug("getSubGraph:: node={} inbound ProductPrice={} ", node, CoreAPIGraph.getInboundNeighbours(subGraph, pp.get()));
//				}
//			}
//			
//			LOG.debug("getSubGraph:: node={} noInbound={} ", node, noInbound);
//			LOG.debug("getSubGraph:: node={} subGraph={}", node, subGraph);
//
//			noInbound.removeAll(excludeNodes);
//			// noInbound.removeAll(nodes); // Check!
//
//			if(!noInbound.isEmpty()) {
//				remove=true;
//				noInbound.forEach(subGraph::removeVertex); 
//				
//				LOG.debug("getSubGraph:: node={} removed={} ", node, noInbound);
//
//			}
		}
		
		LOG.debug("getSubGraph:: node={} final subGraph={}", node, subGraph);

		return subGraph;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static void removeOutboundFromDiscriminatorMappingNodes(Graph<Node, Edge> graph, Set<Node> nodes) {
				
		LOG.debug("removeOutboundFromDiscriminatorMappingNodes: nodes={}", nodes);

		Set<Edge> edgesToRemove = new HashSet<>();

		for(Node node : nodes) {
			Set<String> mapping = node.getDiscriminatorMapping();
			if(!mapping.isEmpty()) {
				LOG.debug("removeOutboundFromDiscriminatorMappingNodes: node={} mapping={}", node.getName(), mapping);
				Set<Node> mappingNodes = mapping.stream()
											.map(n -> CoreAPIGraph.getNodeByName(graph,n))
											.filter(Optional::isPresent)
											.map(Optional::get)
											.collect(toSet());
			
				// mappingNodes.removeAll(currentGraphNodes);
				
				for(Node mappingNode : mappingNodes) {
					
					LOG.debug("removeOutboundFromDiscriminatorMappingNodes: node={} mappingNode={}", node, mappingNode);

					Set<Edge> edges = graph.outgoingEdgesOf(mappingNode);
					
					LOG.debug("removeOutboundFromDiscriminatorMappingNodes: mappingNode={} #1 edges={}", mappingNode, edges);

					edges = edges.stream().filter(edge -> !graph.getEdgeTarget(edge).equals(node)).collect(toSet());
					
					boolean atLeastOneLargeSubGraph = edges.stream().anyMatch(edge -> !isSmallSubGraph(graph,mappingNode,edge));
					
					if(atLeastOneLargeSubGraph) {
						LOG.debug("removeOutboundFromDiscriminatorMappingNodes: mappingNode={} edges={}", mappingNode, edges);
						edgesToRemove.addAll( edges );
					}
					
				}
				
			}
		}
		
		LOG.debug("removeOutboundFromDiscriminatorMappingNodes: nodes={}", nodes);
		LOG.debug("removeOutboundFromDiscriminatorMappingNodes: edgesToRemove={}", edgesToRemove);
		LOG.debug("");

		graph.removeAllEdges(edgesToRemove);

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isSmallSubGraph(Graph<Node, Edge> graph, Node node, Edge edge) {
		Node target = graph.getEdgeTarget(edge);
		
		Set<Node> subGraph = CoreAPIGraph.getNodesOfSubGraph(graph, target);
		
		subGraph.remove(node);
		
		LOG.debug("isSimpleSubGraph: node={} edge={} subGraph={}", node, edge, subGraph);
		
		return subGraph.size()<=3;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> selectGraphNodes(Graph<Node, Edge> g, Predicate<Node> condition, Node exclude) {
		Predicate<Node> notEqual = n -> !n.equals(exclude);
		return g.vertexSet().stream().filter(condition).filter(notEqual).collect(toSet());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> selectGraphNodes(Graph<Node, Edge> g, Predicate<Node> condition) {
		return g.vertexSet().stream().filter(condition).collect(toSet());
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
	public Node addNode(String node) {
		return graphNodes.get(node);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<String> getNodes() {
		return getNodeNames(completeGraph);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addEnumNode(EnumNode enumNode) {
		this.enumNodes.put(enumNode.getName(), enumNode);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Node getNode(String node) {
		return this.graphNodes.get(node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Optional<Node> getNodeByName(Graph<Node,Edge> graph, String name) {
		return graph.vertexSet().stream().filter(gn -> gn.getName().contentEquals(name)).findFirst();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Map<String, EnumNode> getEnumNodes() {
		return this.enumNodes;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public EnumNode getEnumNode(String name) {
		return this.enumNodes.get(name);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> getNodesByNames(Collection<String> nodes) {
		nodes.retainAll( getNodes() );
		return graphNodes.values().stream().filter(node -> nodes.contains( node.getName())).collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getSubGraphNodes(Graph<Node,Edge> graph, Node node) {
		return getSubGraphByParent(graph, node,node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getSubGraphByParent(Graph<Node,Edge> graph, Node parent, Node node) {
		Set<Node> seen = new HashSet<>();
		seen.add(parent);
		Set<Node>  res = getSubGraphHelper(graph, node, seen);
		
		res.remove(parent);
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Set<Node> getSubGraphHelper(Graph<Node,Edge> graph, Node node, Set<Node> seen) {
		Set<Node> neighbours = getOutboundNeighbours(graph, node);
		
		Set<Node> res = new HashSet<>();
		
		res.addAll(neighbours);

		seen.add(node);
		
		for(Node n : neighbours) {
			if(!seen.contains(n)) {
				Set<Node> sub = getSubGraphHelper(graph, n,seen);
				res.addAll(sub);
			}
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getReverseSubGraph(Graph<Node,Edge> graph, Node node) {
		Set<Node> seen = new HashSet<>();
		seen.add(node);
		return getReverseSubGraphHelper(graph, node, seen);	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Set<Node> getReverseSubGraphHelper(Graph<Node,Edge> graph, Node node, Set<Node> seen) {
		Set<Node> neighbours = getInboundNeighbours(graph, node);
		
		Set<Node> res = new HashSet<>();
		
		res.addAll(neighbours);
		seen.add(node);
		
		for(Node n : neighbours) {
			if(!seen.contains(n)) {
				Set<Node> sub = getReverseSubGraphHelper(graph, n,seen);
				res.addAll(sub);
			}
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Graph<Node, Edge> getCompleteGraph() {
		return this.completeGraph;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Graph<Node, Edge> copyGraph(Graph<Node, Edge> graph) {
		Graph<Node, Edge> revGraph = new EdgeReversedGraph<>(graph);		
		return new EdgeReversedGraph<>(revGraph);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isLeafNode(Graph<Node, Edge> graph, Node node) {
		return graph.outgoingEdgesOf(node).isEmpty();
	}

	public static boolean isLeafNodeOrOnlyEnums(Graph<Node, Edge> graph, Node n) {
		return isLeafNode(graph,n) ||
			   getOutboundNeighbours(graph,n).stream().allMatch(Node::isEnumNode);
		
	}	

}

