package no.paneon.api.diagram.layout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.convert.converter.ConditionalGenericConverter;

import no.paneon.api.diagram.app.Args;
import no.paneon.api.diagram.puml.Comment;
import no.paneon.api.diagram.puml.Diagram;
import no.paneon.api.diagram.puml.Extensions;
import no.paneon.api.graph.APIGraph;
import no.paneon.api.graph.APISubGraph;
import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.EdgeEnum;
import no.paneon.api.graph.EnumNode;
import no.paneon.api.graph.Node;
import no.paneon.api.graph.OneOf;
import no.paneon.api.graph.Property;
import no.paneon.api.graph.complexity.Complexity;
import no.paneon.api.graph.complexity.ComplexityAdjustedAPIGraph;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.security.cert.Extension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;

import java.util.Objects;

public class DiagramGenerator 
{
		
    static final Logger LOG = LogManager.getLogger(DiagramGenerator.class);

    Args.Diagram args;
    
	JSONObject layoutConfig;
	Layout layout; 
	
	String file;
	String target;

	List<String> resources;
	
	CoreAPIGraph coreGraph;

	static String REMOVE_INHERITED = "removeInherited";
		
	public DiagramGenerator(Args.Diagram args, String file, String target) {
		this.args = args;	
		this.layoutConfig = Config.getLayout();
		
		this.file = file;    
		this.target = target;
		
		this.resources = new LinkedList<>();		
				
		if(args.resource==null || args.resource.isEmpty()) {
			this.resources.addAll(getResources(args));
		} else {
			this.resources.add(args.resource);
		}
		
		this.coreGraph = new CoreAPIGraph(this.resources);
		
		if(args.resource!=null) {
			String[] parts = args.resource.split(",");
			ArrayList<String> list = new ArrayList<String>(Arrays.asList(parts));
			if(args.includeDefaultResources)
				this.resources.addAll(list);
			else
				this.resources = list;
		}
	
		Set<String> allDefinitions = APIModel.getAllDefinitions().stream().collect(toSet());
		List<String> invalidArguments = this.resources.stream().filter(r -> !allDefinitions.contains(r)).collect(toList());
		
		for(String s : invalidArguments) {
			Out.printAlways("... resource '" + s + "' not found in API - diagram not generated");
		}
		
		this.resources.retainAll(allDefinitions);
		
		LOG.debug("this.resources=" + this.resources);
		
		if(!args.subResourceConfig.isEmpty()) {
			JSONObject subResourceConfig = Utils.readJSONOrYaml(args.subResourceConfig);
			if(subResourceConfig!=null && !subResourceConfig.keySet().isEmpty()) {
				if(subResourceConfig.has("subResourceConfig")) {
					JSONObject envelop = new JSONObject();
					envelop.put("subResourceConfig",  subResourceConfig);
					subResourceConfig=envelop;
				}
				Config.addConfiguration(subResourceConfig);
			} else {
				if(subResourceConfig==null) {
					Out.debug("... empty configuration for sub-resource graphs - using graph complexity analysis");
				} else {
					Out.debug("... configuration file for sub-resource graphs ({}) not found", args.subResourceConfig);
				}
			}
		}
		
	    createDirectory(target);
	    if(!Config.getBoolean("keepExistingPuml")) removeExistingFiles(target, ".puml");

		LOG.debug("DiagramGenerator() resources={}", this.resources);
		
	}
		
	
	@LogMethod(level=LogLevel.DEBUG)
	public Map<String,String> generateDiagramGraph() {
		
		Map<String,String> diagramConfig = new LinkedHashMap<>();
		
		LOG.debug("generateDiagramGraph: coreGraph={}", coreGraph.getCompleteGraph().edgeSet());

//	    coreGraph.getCompleteGraph().vertexSet().stream()
//	       	.filter(n -> n.getName().contentEquals("PermissionSpecificationRefOrValue"))
//	       	.forEach(n -> Out.debug("init: node={} discriminators={}", n.getName(), n.getAllDiscriminatorMapping()));
//
//	    coreGraph.getCompleteGraph().vertexSet().stream()
//       	.filter(n -> n.getName().contentEquals("PermissionSpecificationRefOrValue"))
//       	.map(n -> coreGraph.getCompleteGraph().outgoingEdgesOf(n))
//       	.forEach(e -> Out.debug("init: edge={}", e));

	    		
		ComplexityAdjustedAPIGraph graphs = new ComplexityAdjustedAPIGraph(coreGraph, args.keepTechnicalEdges);
		
		LOG.debug("generateDiagramGraph: coreGraph nodes={}", coreGraph.getNodes() );
		LOG.debug("generateDiagramGraph: coreGraph edges={}", coreGraph.getCompleteGraph().edgeSet() );

		Set<String> seenResources = new HashSet<>();	

		JSONObject subResourceConfig = Config.getConfig("subResourceConfig");
		
		for(String resource : this.resources) {
			
			LOG.debug("### generateDiagramGraph: resource={}", resource);

			if(subResourceConfig!=null && subResourceConfig.has(resource)) {
				graphs.generateSubGraphsFromConfig(this.resources, resource, Config.getList(subResourceConfig, resource));
			} else {
				graphs.generateSubGraphsForResource(this.resources, resource);
			}
			
			List<String> subGraphs = graphs.getSubGraphLabels(resource);
								
			LOG.debug("## generateDiagramGraph: ## resource={} subGraphs={}", resource, subGraphs);
			
			for(String pivot : subGraphs ) {
				
				if(Config.getBoolean("onlyFirstSeenSubResource") && seenResources.contains(pivot)) continue;
				
				LOG.debug("generateDiagramGraph: resource={} subGraph={}", resource, pivot);

				Node n = CoreAPIGraph.getNodeByName(coreGraph.getCompleteGraph(), pivot).get();
				
				LOG.debug("#00 generateDiagramGraph: resource={} pivot={} edges={}", resource, pivot, coreGraph.getCompleteGraph().outgoingEdgesOf(n));

				Optional<Graph<Node,Edge>> pivotGraph = graphs.getSubGraph(resource, pivot);
			
				LOG.debug("generateDiagramGraph: pivot={} pivotGraph={}", pivot, pivotGraph);

				if(!pivotGraph.isPresent()) continue;
								
				Graph<Node,Edge> currentGraph = pivotGraph.get();
				
				LOG.debug("generateDiagramGraph: pivot={} currentGraph={}", pivot, currentGraph.vertexSet());
				LOG.debug("generateDiagramGraph: pivot={} currentGraph=\n{}", pivot, currentGraph.edgeSet().stream().map(Object::toString).collect(Collectors.joining("\n")));
			
				boolean onlyDiscriminatorEdges = onlyDiscriminatorEdgesToPivot(currentGraph, resource, pivot);
				
				LOG.debug("generateDiagramGraph: pivot={} onlyDiscriminatorEdges={}", pivot, onlyDiscriminatorEdges);

				if(onlyDiscriminatorEdges) continue;

				String label; 
				APIGraph apiGraph;
				
				if(pivot.contentEquals(resource)) {
					apiGraph = new APIGraph(this.resources, resource, coreGraph, currentGraph, pivot, args.keepTechnicalEdges);
					label = resource;
				} else {
					apiGraph = new APISubGraph(this.resources, resource, coreGraph, currentGraph, resource, pivot, args.keepTechnicalEdges);
					label = resource + "_" + pivot;
				} 
				
				LOG.debug("generateDiagramGraph:: graph pivot={} edges={}", pivot, apiGraph.getGraph().edgeSet().stream().filter(Edge::isDiscriminator).collect(Collectors.toSet()));

				addExplicitSubResource(pivot, apiGraph);
				
				LOG.debug("generateDiagramGraph:: graph edges={}", apiGraph.getGraph().edgeSet().stream().map(Object::toString).collect(Collectors.joining("\n")));
				
				Diagram diagram = generateDiagramForGraph(pivot, apiGraph, subGraphs);

				label = label.replace(resource, APIModel.getMappedResource(resource) );
				
				// Out.printAlways("... generated diagram for " + pivot + " label=" + label);
				if(pivot.contentEquals(resource)) {
					Out.printAlways("... generated diagram(s) for " + pivot);
				} else {
					Out.printAlways("... generated sub-diagram(s) of " + pivot + " for " + resource);
				}
				
				diagramConfig.putAll( writeDiagram(diagram, label, target) );
					
				seenResources.add(pivot);
			}
			
		}
				
		return diagramConfig;

	}
	        	

	private void addExplicitSubResource(String resource, APIGraph apiGraph) {
		JSONObject includeSubResources = Config.getConfig("includeSubResources");
		if(includeSubResources==null || includeSubResources.isEmpty()) return;

		LOG.debug("addExplicitSubResource: resource={} includeSubResources={}", resource, includeSubResources.toString(2));

		Graph<Node, Edge> completeGraph = apiGraph.getCompleteGraph();
		Graph<Node, Edge> g = apiGraph.getGraph();
		
		includeSubResources.keySet().stream()
			.filter(n -> n.contentEquals(resource))
			.map(includeSubResources::optJSONArray)
			.filter(Objects::nonNull)
			.map(JSONArray::toList)
			.flatMap(List::stream)
			.map(Object::toString)
			.map(apiGraph::getNode)
			.filter(Objects::nonNull)
			.forEach(node -> {
			
				LOG.debug("generateDiagramGraph: graph={} node={}", resource, node);

				if(!g.containsVertex(node)) g.addVertex(node);
				
				completeGraph.edgeSet().stream()
					.filter(e -> completeGraph.getEdgeTarget(e).equals(node))
					.filter(e -> completeGraph.getEdgeSource(e)!=node)
					.filter(e -> !g.edgeSet().contains(e))
					.filter(e -> g.vertexSet().contains(completeGraph.getEdgeSource(e)))
					.forEach(edge -> {
						g.addEdge(g.getEdgeSource(edge), node, edge);
						LOG.debug("generateDiagramGraph: graph={} add edge edge={}", resource, edge);
					});
			});
					
	}


	private boolean onlyDiscriminatorEdgesToPivot(Graph<Node, Edge> graph, String resource, String pivot) {
		boolean res = false;
		
		if(!pivot.contentEquals(resource)) {
			Optional<Node> optPivotNode = CoreAPIGraph.getNodeByName(graph,  pivot);
			if(optPivotNode.isPresent()) {
				Node pivotNode = optPivotNode.get();
	
				if(!graph.outgoingEdgesOf(pivotNode).isEmpty()) {
					res = graph.outgoingEdgesOf(pivotNode).stream().allMatch(Edge::isInheritance);
				}
				res = res && graph.incomingEdgesOf(pivotNode).stream().allMatch(Edge::isDiscriminator);
				
				LOG.debug("onlyDiscriminatorEdgesToPivot: pivot={} edges={}", pivot, graph.incomingEdgesOf(pivotNode));
				
			}
		}
		
		LOG.debug("onlyDiscriminatorEdgesToPivot: pivot={} res={}", pivot, res);

		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	private Map<String,String> writeDiagramsForSubResources(Diagram diagram, String resource, Set<String> generated) {
		Map<String,String> config = new HashMap<>();

		for(Diagram subDiagram : diagram.getSubDiagrams()) {
			
			LOG.debug("writeDiagramsForSubResources: subResource={}", subDiagram.getLabel());
			
			String subResource = subDiagram.getLabel();
			String subDiagramLabel = resource + "_" + subResource;
			
			config.putAll( writeDiagram(subDiagram, subDiagramLabel, target) );
			generated.add(subResource);
								
			writeDiagramsForSubResources(subDiagram, resource, generated);
		}	
		
		return config;
	}


//	@LogMethod(level=LogLevel.DEBUG)
//	private Diagram generateDiagramForResource(String resource, Set<String> producedDiagrams, List<String> subGraphs) {
//		
//        List<Object> generated = new ArrayList<>();
//
//	    Diagram diagram = new Diagram(args, file, resource);
//
//	    producedDiagrams.add(resource);
//	    
//	    APIGraph graph = new APIGraph(resource);
//
//	    LOG.debug("#1 generateDiagramForResource: resource=" + resource);
//	    
//	    graph.filterSimpleTypes();
//	    
//		if(Config.processComplexity()) {						
//			graph = complexityAdjustedGraph(graph,diagram);
//		} 
//		
//	    graph.filterSimpleTypes();
//
//	    LOG.debug("filtering adjusted apiGraph: " + graph.getAllNodes().size() + " " + graph);
//	       	    
//	    layout = new Layout(graph, layoutConfig);
//	            	    
//	    LOG.debug("#4 adjusted apiGraph nodes: " + graph.getNodes());
//
//	    for(Node node: graph.getGraphNodes() ) {
//	    	if(!(node instanceof EnumNode)) {
//	    		layout.generateUMLClasses(diagram, node, resource, subGraphs);
//	    	}
//	    }
//	            
//	    LOG.debug("incomplete: " + diagram.getIncomplete());
//	    
//	    layout.processEdgesForCoreGraph(diagram, subGraphs);
//        
//	    layout.processEdgesForRemainingNodes(diagram);
//	    	               
//	    addOrphanEnums(graph,diagram);
//	      	    	    
//  	    addDiagramForBaseTypes(diagram, producedDiagrams, subGraphs);
//  	    
//  	    layout.getNodePlacement();
//  	    		
//  	    return diagram;
//  	    
//	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Diagram generateDiagramForGraph(String resource, APIGraph apiGraph, List<String> subGraphs) {
		        
	    Diagram diagram = new Diagram(args, file, resource);
	       	    
	    if(true || Config.getBoolean(REMOVE_INHERITED)) {
	    //	removeInherited(apiGraph);
	    }
	    
	    layout = new Layout(apiGraph, layoutConfig);
	            	    	    
	    List<Node> nodesInGraph = getSequenceOfNodesInGraph(apiGraph,resource);
	    	   	    
	    LOG.debug("generateDiagramForGraph:: resource={} nodes={}", resource, nodesInGraph );
	    LOG.debug("generateDiagramForGraph:: resource={} edges={}", resource, apiGraph.getGraph().edgeSet().stream().map(Object::toString).collect(Collectors.joining("\n") ));

	    Set<Node> reachable = CoreAPIGraph.getReachable(apiGraph.getGraph(), resource);
	    
	    LOG.debug("generateDiagramForGraph:: resource={} reachable={}", resource, reachable);
	    	    
	    for(Node node: nodesInGraph ) {
	    	if(isResourceNode(node,resource) || !(node instanceof EnumNode) && reachable.contains(node)) {
	    		
	    	    LOG.debug("generateDiagramForGraph:: resource={} node={}", resource, node);

	    		layout.generateUMLClasses(diagram, node, resource, subGraphs);
	    	}
	    }
	    
	    // Set<Node> discriminatorNodes = apiGraph.getGraphNodeList().stream().filter(Node::isDiscriminatorNode).collect(toSet());
	    Set<Node> discriminatorNodes = apiGraph.getCompleteGraph().vertexSet().stream().filter(Node::isDiscriminatorNode).collect(toSet());

    	LOG.debug("generateDiagramForGraph:: discriminatorNodes={}", discriminatorNodes);

	    for(Node node: apiGraph.getGraphNodeList().stream().filter(Node::isDiscriminatorNode).collect(toSet()) ) {	    		
	    	LOG.debug("generateDiagramForGraph:: resource={} isDiscriminatorNode={}", resource, node);

	    }
	            
	    LOG.debug("generateDiagramForGraph: resource={} processed classes",  resource);

	    layout.processEdgesForCoreGraph(diagram, subGraphs);
        
	    LOG.debug("generateDiagramForGraph: resource={} processed core graph",  resource);

	    layout.processEdgesForRemainingNodes(diagram);
	    	            
	    // addOrphanEnums(apiGraph,diagram);
	      	    	
  	    List<String> placement = layout.getNodePlacement();
  	    placement.forEach(line -> diagram.addComment(new Comment(line)));
  	    
	    LOG.debug("generateDiagramForGraph: resource={} DONE", resource);

  	    return diagram;
  	    
	}

	private void removeInherited(APIGraph apiGraph) {
				
		Collection<String> allNodes = apiGraph.getAllNodes();
		
		for(String nodeName : allNodes) {
			Node node = apiGraph.getNode(nodeName);
			
			Collection<Property> allOfsProperties = apiGraph.getOutboundEdges(node).stream()
														.filter(Edge::isAllOf)
														.map(apiGraph::getEdgeTarget)
														.map(Node::getProperties)
														.flatMap(List::stream)
														.collect(toSet());
			
			LOG.debug("node={} allOfs={} ", node, allOfsProperties);								
									
			allOfsProperties = allOfsProperties.stream().filter(p->!p.getName().contentEquals("@type")).collect(toSet());
			
			LOG.debug("node={} allOfs={} ", node, allOfsProperties);	
			
			node.removeProperties(allOfsProperties);
			
			LOG.debug("node={} properties={} ", node, node.getProperties());	

		}
			
	}


	private boolean isResourceNode(Node node, String resource) {
		return node.getName().contentEquals(resource);
	}


	private List<Node> getSequenceOfNodesInGraph(APIGraph graph, String resource) {
		Node resourceNode = graph.getNode(resource);
	    
		Set<Node> allNodes = graph.getGraphNodes();
		allNodes.remove(resourceNode);
		
	    List<Node> nodesInGraph = new LinkedList<>();
	    
	    nodesInGraph.add(resourceNode);
	    nodesInGraph.addAll( allNodes );
	    
	    return nodesInGraph;
	}


//	@LogMethod(level=LogLevel.DEBUG)
//	private Diagram generateDiagramForResource(String resource, String stereoType, Set<String> producedDiagrams, List<String> subGraphs) {
//		
//		Diagram diagram = generateDiagramForResource(resource, producedDiagrams, subGraphs);
//		
//		producedDiagrams.add(resource);
//				
//		diagram.getClassEntityForResource(resource).setStereoType(stereoType);
//		
//		return diagram;
//		
//	}
	
//	private void addDiagramForBaseTypes(Diagram diagram, Set<String> producedDiagrams, List<String> subGraphs) {
//		
//		Collection<String> missingDiagrams = diagram.getBaseTypes();
//				
//		for(String baseType : missingDiagrams) { 
//	    	if(!producedDiagrams.contains(baseType)) {
//	  	    	String stereoType = Config.getDefaultStereoType();
//	  	    	Diagram subDiagram = generateDiagramForResource(baseType, stereoType, producedDiagrams, subGraphs);
//	  	    	diagram.addSubDiagram(subDiagram);
//	    	}
//  	    }		
//	}


//	@LogMethod(level=LogLevel.DEBUG)
//	private void addOrphanEnums(APIGraph graph, Diagram diagram) {
//  	    List<String> orphans = Config.getOrphanEnums();
//  	    LOG.debug("addOrphanEnums: orphans={}", orphans);
//  	    if(Config.getIncludeOrphanEnums() || orphans.contains(graph.getResource())) {
//  	    	List<String> orphanEnums = Config.getOrphanEnums(graph.getResource());
//  	    	
//  	  	    LOG.debug("addOrphanEnums: resource={} orphanEnums={}", graph.getResource(), orphans);
//
//  	    	if(orphanEnums.isEmpty()) {
//  	    		layout.addOrphanEnums(diagram, graph.getNode(graph.getResource()));   	
//  	    	} else {
//  	    		Node resource = graph.getNode(graph.getResource());
//  	    		for(String orphan : orphanEnums) {
//  	    			Node orphanNode = graph.getNode(orphan);
//  	    	  	    LOG.debug("addOrphanEnums: resource={} orphanEnumNode={}", graph.getResource(), orphanNode);
//
//  	    	  	    Edge edge = new EdgeEnum(resource,"", orphanNode,"",false);
//  	    	  	    graph.getCompleteGraph().addEdge(resource, orphanNode, edge);
//  	    	  	    
//  	    			layout.addOrphanEnum(diagram, resource, orphanNode);
//  	    		}
//  	    	}    		
//  	    }
//	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private APIGraph complexityAdjustedGraph(APIGraph rawGraph, Diagram diagram) {
		
	    Complexity analyser = processComplexity(rawGraph.getResource());	

	    Set<Node> baseTypes = analyser.getBaseTypes();
	    Set<Node> simpleTypes = analyser.getSimpleTypes();

	    LOG.debug("complexityAdjustedGraph: baseTypes=" + baseTypes);
	    LOG.debug("complexityAdjustedGraph: simpleTypes=" + simpleTypes);

    	Set<Node> invalidBaseTypes = baseTypes.stream()
										.filter(analyser::noComplexityContribution)
										.filter(baseNode -> rawGraph.getOutboundNeighbours(baseNode).size()<3)
										.collect(toSet());

    	for(Node node : baseTypes) {
    		LOG.debug("complexityAdjustedGraph: node=" + node.getName() + " complexity=" + analyser.getContribution(node.getName()));
    	}
    	
    	LOG.debug("complexityAdjustedGraph: invalidBaseTypes=" + invalidBaseTypes);

    	final APIGraph g = rawGraph;
    	
    	Set<Node> invalidSimpleTypes = invalidBaseTypes.stream()
											.map(g::getSubGraph)
											.flatMap(Set::stream)
											.collect(toSet());
    	

    	APIGraph graph = new APIGraph(rawGraph.getResource());

    	baseTypes.removeAll(invalidBaseTypes);
    	graph.setBaseTypes(baseTypes);
    	
      	simpleTypes.removeAll(invalidSimpleTypes);
    	graph.setSimpleTypes(simpleTypes);
 
    	diagram.setComplexityDetails(analyser);
    	
		diagram.setBaseTypes( APIGraph.getNodeNames(baseTypes) );
		diagram.setSimpleTypes( APIGraph.getNodeNames(simpleTypes) );
		
		
		
		return graph;
	}


//	@LogMethod(level=LogLevel.DEBUG)
//	private Diagram processDiagramForResource(String resource, Set<String> producedDiagrams) {
//				
//  	    Diagram diagram = generateDiagramForResource(resource, producedDiagrams);
//  	      	    	      	    
//  	    producedDiagrams.add(resource);
//  	    		
//		for(String incomplete : diagram.getIncomplete() ) {
//  	    	String stereoType = Config.getDefaultStereoType();
//  	    	
//  	    	if(!producedDiagrams.contains(incomplete)) {
//	  	    	Diagram subDiagram = generateDiagramForResource(incomplete, stereoType, producedDiagrams);
//	  	    	diagram.addSubDiagram(subDiagram);	
//	  	    	producedDiagrams.add(incomplete);
//  	    	}
//  	    	  	    	  	    	
//		}
//  	    		
//  	    return diagram;
//  	    
//	}
//	

	@LogMethod(level=LogLevel.DEBUG)
	private Complexity processComplexity(String resource) {
	    APIGraph graph = new APIGraph(resource);
	    
	    Complexity analyser = new Complexity(this.resources, graph.getGraph(),graph.getResourceNode());
	    	    
	    analyser.computeGraphComplexity();
		    						
    	LOG.debug("processComplexity:: resource={} analyser={}", resource, analyser);

		return analyser;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private long countOfNotPlacedOutbound(APIGraph graph, String node, List<String> generated) {
		return graph.getOutboundNeighboursByName(node).stream()
					.filter(o -> !generated.contains(o))
					.count();
	}

	@LogMethod(level=LogLevel.DEBUG)
	private Map<String,String> writeDiagram(Diagram diagram, String resource, String target) {
		Map<String,String> config = new HashMap<>();
		
	    String puml = diagram.toString();
	    
	    String fileName = "Resource_" + resource + ".puml";
	    if(!target.isEmpty() && !target.endsWith(File.separator)) target = target + File.separator;
	    String destination = target + fileName;
	    
	    try(BufferedWriter writer = new BufferedWriter(new FileWriter(destination)) ) {    
	    	
	    	LOG.debug("writeDiagram:: resource={} destination={}", resource, destination);
    	    writer.write(puml);
    	        	  
    	    String fileType = Config.getString("diagramFileType");
    	    if(fileType==null || fileType.isEmpty()) fileType = ".png";
    	    fileName = fileName.replace(".puml", fileType);
    	    
    	    config.put(resource, fileName);
    	    
	    } catch(Exception ex) {
	    	Out.println("exception: " + ex.getLocalizedMessage());
	    }

	    return config;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean createDirectory(String target) {
		File f = new File(target);
		return f.mkdirs();		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void removeExistingFiles(String target, String fileType) {
		File dir = new File(target);
		
		for(File file: dir.listFiles()) 
		    if (!file.isDirectory() && file.getName().endsWith(fileType)) 
		        file.delete();
	}
	

	@LogMethod(level=LogLevel.DEBUG)
	public void displayComplexity() {
		for(String resource : resources) {
			displayComplexityForResource(resource);
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void displayComplexityForResource(String resource) {
		
	    APIGraph preGraph = new APIGraph(resource);
	
	    Complexity analyser = new Complexity(this.resources, preGraph.getGraph(),preGraph.getResourceNode());
	    
	    displayComplexity(analyser, resource, "Resource diagram complexity", "... Total graph complexity");
	    		
	    APIGraph graph = new APIGraph(resource);
	    
	    graph.applyComplexity(analyser, resource);
	    		
	    analyser = new Complexity(this.resources, graph.getGraph(),graph.getResourceNode());
	    
	    displayComplexity(analyser, resource, "Complexity after applying configuration of base types", "... Total graph complexity");

		Collection<Node> baseTypes = analyser.getBaseTypes();
		if(!baseTypes.isEmpty()) {
			Out.println("... Suggested to handle the following nodes as base types:");
			for(Node node : baseTypes) {
				Out.println("... ... " + node);
			}
		}
		
		Collection<Node> simpleTypes = analyser.getSimpleTypes();
		if(!simpleTypes.isEmpty()) {
			Out.println("... Suggested to handle the following nodes as simple types:");
			for(Node node : simpleTypes) {
				Out.println("... ... " + node);
			}
		}
	
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void displayComplexity(Complexity analyser, String resource, String header1, String header2) {
		
	    Map<Node,Integer> complexity = analyser.computeGraphComplexity();
	    
	    int graphComplexity = complexity.entrySet().stream().map(Map.Entry::getValue).mapToInt(Integer::intValue).sum();
	    
	    if(graphComplexity==0) {
			Out.println("... Resource: " + resource + " - diagram is not complex (complexity measure=" + graphComplexity + ")");
			return;
	    }
	    			
	    Out.println(header1 + ": " + resource);
	    Out.println(header2 + ": " + graphComplexity);
		
	    Out.println("... Node complexity contributions: ");
		complexity.entrySet().stream().forEach(entry -> {
			Out.println("... ... " + entry.getKey() + " : " + entry.getValue());
		});
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private List<String> getResources(Args.Common args) {
       	List<String> resourcesFromAPI = APIModel.getResources();
 	       
    	LOG.debug("getResources:: {}", resourcesFromAPI);

    	List<String> resourcesFromRules = Utils.extractResourcesFromRules(args.rulesFile);
    	
    	resourcesFromRules.removeAll(resourcesFromAPI);
    	resourcesFromAPI.addAll( resourcesFromRules );
  
    	resourcesFromAPI = resourcesFromAPI.stream().distinct()
    						.map(APIModel::removePrefix)
    						.filter(x -> (args.resource==null)||(x.equals(args.resource)))
    						.collect(toList());
  	
    	LOG.debug("getResources:: {}", resourcesFromAPI);
    	
    	return resourcesFromAPI;
    	
	}

	public boolean hasExplicitResources() {
		return !this.resources.isEmpty();
	}


	public void applyVendorExtensions() {
		JSONObject extensions = Config.getConfig(Extensions.EXTENSIONS);
		
		LOG.debug("vendorExtensions: extensions={}", extensions==null ? "null" : extensions.toString(2));

		if(extensions==null) return;
		
		LOG.debug("vendorExtensions: {}", extensions.toString(2));
		
		JSONArray resourceExtension = extensions.optJSONArray(Extensions.RESOURCE_EXTENSION);

		
		if(resourceExtension!=null) {			
			List<String> extendedResources = StreamSupport.stream(resourceExtension.spliterator(), false)
		            .map(val -> (JSONObject) val)
		            .map(val -> val.optString(Extensions.EXTENSION_NAME))
		            .collect(Collectors.toList());
			
			LOG.debug("resourceExtension: {}", extendedResources);

			List<String> excludeAsExtensions = Config.get(Extensions.EXCLUDE_AS_EXTENSION);
			
			extendedResources.stream()
				.filter(r -> !excludeAsExtensions.contains(r))
				.map(s -> CoreAPIGraph.getNodeByName(this.coreGraph.getCompleteGraph(), s))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.forEach(node -> {
					LOG.debug("add extension: {}", node);
					node.setVendorExtension();
					this.coreGraph.getCompleteGraph().outgoingEdgesOf(node).stream().forEach(Edge::setVendorExtension);
				});
			
			
		}
			
		JSONArray resourceAttributeExtension = extensions.optJSONArray(Extensions.RESOURCE_ATTRIBUTE_EXTENSION);
		
		LOG.debug("resourceAttributeExtension: {}", resourceAttributeExtension.toString());

		if(resourceAttributeExtension!=null) {			
			StreamSupport.stream(resourceAttributeExtension.spliterator(), false)
		            .map(val -> (JSONObject) val)
		            .forEach(val -> {
		            	String resource = val.optString(Extensions.EXTENSION_NAME);
		            	Optional<Node> optNode = CoreAPIGraph.getNodeByName(this.coreGraph.getCompleteGraph(), resource);
		            	if(optNode.isPresent()) {
		            		Node node = optNode.get();
		            		
		            		if(Config.getBoolean(Extensions.RESOURCE_ATTRIBUTE_AS_EXTENSION)) {
		            			LOG.debug("resourceAttributeExtensionAsExtensions: true");
		            			node.setVendorExtension();
		            		}
		            		
							JSONArray attributeExtension = val.optJSONArray(Extensions.ATTRIBUTE_EXTENSION);
							if(attributeExtension!=null) {
//								List<String> extendedAttributes = StreamSupport.stream(attributeExtension.spliterator(), false)
//							            .map(attr -> (JSONObject) attr)
//							            .map(attr -> attr.optString(Extensions.EXTENSION_NAME))
//							            .collect(Collectors.toList());
//								
//								LOG.debug("extendedAttributes: {}", extendedAttributes);
//	
//								node.setVendorAttributeExtension(extendedAttributes);
								
								StreamSupport.stream(attributeExtension.spliterator(), false)
							            .map(attr -> (JSONObject) attr)
							            .forEach(attr -> {
							            	String propName = attr.optString(Extensions.EXTENSION_NAME);
							            	boolean required = attr.optBoolean(Extensions.EXTENSION_REQUIRED);
							            	boolean type = attr.optBoolean(Extensions.EXTENSION_TYPE);

							            	node.setVendorAttributeExtension(propName,required,type);
							            });
								
//							            .map(attr -> attr.optString(Extensions.EXTENSION_NAME))
//							            .collect(Collectors.toList());
//								
//								LOG.debug("extendedAttributes: {}", extendedAttributes);
//	
//								node.setVendorAttributeExtension(extendedAttributes);
	
								List<String> extendedAttributes = StreamSupport.stream(attributeExtension.spliterator(), false)
									            .map(attr -> (JSONObject) attr)
									            .map(attr -> attr.optString(Extensions.EXTENSION_NAME))
									            .collect(Collectors.toList());
								
								APIGraph.getOutboundEdges(this.coreGraph.getCompleteGraph(), node).stream()
								.filter(e -> extendedAttributes.contains(e.relation))
								.forEach(Edge::setVendorExtension);
								
								Map<String,JSONObject> extendedAttributesDetails = StreamSupport.stream(attributeExtension.spliterator(), false)
							            .map(attr -> (JSONObject) attr)
							            .collect(Collectors.toMap(o -> o.getString(Extensions.EXTENSION_NAME),Function.identity()));
						
		            			LOG.debug("extendedAttributesDetails: {}", extendedAttributesDetails);

								APIGraph.getOutboundEdges(this.coreGraph.getCompleteGraph(), node).stream()
								.filter(edge -> extendedAttributesDetails.containsKey(edge.relation))
								.forEach(edge -> {
									boolean cardinalityExtension = extendedAttributesDetails.get(edge.relation).optBoolean(Extensions.EXTENSION_CARDINALITY);
						
			            			LOG.debug("cardinalityExtension: edge={} {}", edge, cardinalityExtension);

									if(cardinalityExtension) {
										edge.setCardinalityExtension();
									}
									
									try {
										boolean requiredExtension = extendedAttributesDetails.get(edge.relation).getBoolean(Extensions.EXTENSION_REQUIRED);
									
										LOG.debug("requiredExtension: edge={} {}", edge, requiredExtension);

										if(requiredExtension) {
											edge.setRequiredExtension(requiredExtension);
										}
									} catch(Exception e) {
										// nothing to do
									}
									
								});
								
							}
							
		            	}    	
		            	
		            });
			
		}
				
		JSONArray resourceDiscriminatorExtension = extensions.optJSONArray(Extensions.RESOURCE_DISCRIMINATOR_EXTENSION);
		
		if(resourceDiscriminatorExtension!=null) {			
			StreamSupport.stream(resourceDiscriminatorExtension.spliterator(), false)
	            .map(val -> (JSONObject) val)
	            .forEach(val -> {
	            	String resource = val.optString(Extensions.EXTENSION_NAME);
	            	Optional<Node> optNode = CoreAPIGraph.getNodeByName(this.coreGraph.getCompleteGraph(), resource);
	            	if(optNode.isPresent()) {
	            		Node node = optNode.get();
	            		
						JSONArray discriminatorExtension = val.optJSONArray(Extensions.DISCRIMINATOR_EXTENSION);
						if(discriminatorExtension!=null) {
							
							LOG.debug("discriminatorExtension: {}", discriminatorExtension);
							List<String> discriminators = discriminatorExtension.toList().stream().map(Object::toString).collect(toList());
									
							node.setVendorDiscriminatorExtension(discriminators);
							
						}
	            	}    	
	            });
			
		}
		
		
		JSONArray resourceInheritanceExtension = extensions.optJSONArray(Extensions.RESOURCE_INHERITANCE_EXTENSION);
		
		if(resourceInheritanceExtension!=null) {			
			StreamSupport.stream(resourceInheritanceExtension.spliterator(), false)
	            .map(val -> (JSONObject) val)
	            .forEach(val -> {
	            	String resource = val.optString(Extensions.EXTENSION_NAME);
	            	Optional<Node> optNode = CoreAPIGraph.getNodeByName(this.coreGraph.getCompleteGraph(), resource);
	            	if(optNode.isPresent()) {
	            		Node node = optNode.get();
	            		
						JSONArray inheritanceExtension = val.optJSONArray(Extensions.INHERITANCE_EXTENSION);
						if(inheritanceExtension!=null) {
							
							LOG.debug("inheritanceExtension: node={} {}", resource, inheritanceExtension);
							List<String> inheritance = inheritanceExtension.toList().stream().map(Object::toString).collect(toList());
									
							node.setVendorInheritanceExtension(inheritance);
							
						}
	            	}    	
	            });
			
		}
		
	}


}
	
