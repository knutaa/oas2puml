package no.paneon.api.diagram.layout;

import org.json.JSONObject;

import no.paneon.api.diagram.app.Args;
import no.paneon.api.diagram.puml.Comment;
import no.paneon.api.diagram.puml.Diagram;
import no.paneon.api.graph.APIGraph;
import no.paneon.api.graph.APISubGraph;
import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.EnumNode;
import no.paneon.api.graph.Node;
import no.paneon.api.graph.OneOf;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;

public class DiagramGenerator 
{
		
    static final Logger LOG = LogManager.getLogger(DiagramGenerator.class);

    Args.Diagram args;
    
	JSONObject layoutConfig;
	Layout layout; 
	
	String file;
	String target;

	List<String> resources;
		
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
	
		
		LOG.debug("DiagramGenerator() resources={}", this.resources);
		
	}
		

//	@LogMethod(level=LogLevel.DEBUG)
//	private Map<String,String> generateDiagram() {
//		Map<String,String> diagramConfig = new HashMap<>();
//		
//		Set<String> generated = new HashSet<>();
//		
//		for(String resource : this.resources) {
//			Diagram diagram = processDiagramForResource(resource, generated);
//			
//			Out.printAlways("... generated diagram for " + resource);
//			
//			diagramConfig = writeDiagram(diagram, resource, target);
//			
//			Set<String> incompleteTypes = diagram.getSubResourcesWithIncompleteDiagram();
//			generated.removeAll(incompleteTypes);
//			
//			diagramConfig.putAll( writeDiagramsForSubResources(diagram,resource,generated) );
//						
//		}
//		
//		return diagramConfig;
//
//	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Map<String,String> generateDiagramGraph() {
		
		Map<String,String> diagramConfig = new LinkedHashMap<>();

		CoreAPIGraph coreGraph = new CoreAPIGraph();
		
		LOG.debug("generateDiagramGraph: coreGraph={}", coreGraph.getCompleteGraph().edgeSet());

		ComplexityAdjustedAPIGraph graphs = new ComplexityAdjustedAPIGraph(coreGraph);
		
		LOG.debug("generateDiagramGraph: coreGraph nodes={}", coreGraph.getNodes() );
		LOG.debug("generateDiagramGraph: coreGraph resources={}", resources);

		for(String resource : this.resources) {
			
			LOG.debug("generateDiagramGraph: resource={}", resource);

			graphs.generateSubGraphsForResource(resource);
				
			List<String> subGraphs = graphs.getSubGraphLabels(resource);
								
			LOG.debug("generateDiagramGraph: resource={} subGraphs={}", resource, subGraphs);

			for(String pivot : subGraphs ) {
				
				LOG.debug("generateDiagramGraph: pivot={}", pivot);

				Optional<Graph<Node,Edge>> pivotGraph = graphs.getSubGraph(resource, pivot);
			
				if(!pivotGraph.isPresent()) continue;
								
				Graph<Node,Edge> currentGraph = pivotGraph.get();
				
				LOG.debug("generateDiagramGraph: pivot={} currentGraph={}", pivot, currentGraph);
				
				String label; 
				APIGraph apiGraph;
				
				if(pivot.contentEquals(resource)) {
					apiGraph = new APIGraph(coreGraph, currentGraph, pivot);
					label = resource;
				} else {
					apiGraph = new APISubGraph(coreGraph, currentGraph, pivot);
					label = resource + "_" + pivot;
				} 
				
				if(!args.keepTechnicalEdges) { 
					apiGraph.removeTechnicalAllOfs();
				}
				
				Diagram diagram = generateDiagramForGraph(pivot, apiGraph);
							
				Out.printAlways("... generated diagram for " + pivot);

				diagramConfig.putAll( writeDiagram(diagram, label, target) );
				
			}
			
		}
		
		return diagramConfig;

	}
	        	
	@LogMethod(level=LogLevel.DEBUG)
	private Map<String,String> writeDiagramsForSubResources(Diagram diagram, String resource, Set<String> generated) {
		Map<String,String> config = new HashMap<>();

		for(Diagram subDiagram : diagram.getSubDiagrams()) {
			
			LOG.debug("writeDiagramsForSubResources: subResource={}", subDiagram.getLabel());
			
			String subResource = subDiagram.getLabel();
			String subDiagramLabel = resource + "_" + subResource;
							
			//if(!generated.contains(subResource) && diagram.isUsedAsSimpleType(subResource)) {	
				
				config.putAll( writeDiagram(subDiagram, subDiagramLabel, target) );
				generated.add(subResource);
				
			//}	
				
			writeDiagramsForSubResources(subDiagram, resource, generated);
		}	
		
		return config;
	}


	@LogMethod(level=LogLevel.DEBUG)
	private Diagram generateDiagramForResource(String resource, Set<String> producedDiagrams) {
		
        List<Object> generated = new ArrayList<>();

	    Diagram diagram = new Diagram(args, file, resource);

	    producedDiagrams.add(resource);
	    
	    APIGraph graph = new APIGraph(resource);

	    LOG.debug("generateDiagramForResourceCore: resource=" + resource);
	    
	    graph.filterSimpleTypes();
	    
	    LOG.debug("#2 apiGraph: " + graph.getAllNodes().size() + " " + graph);

		if(Config.processComplexity()) {						
			graph = complexityAdjustedGraph(graph,diagram);
		} 
		
	    LOG.debug("#3 adjusted apiGraph: " + graph.getAllNodes().size() + " " + graph);

	    graph.filterSimpleTypes();

	    LOG.debug("filtering adjusted apiGraph: " + graph.getAllNodes().size() + " " + graph);
	       	    
	    layout = new Layout(graph, layoutConfig);
	            	    
	    LOG.debug("#4 adjusted apiGraph nodes: " + graph.getNodes());

	    for(Node node: graph.getGraphNodes() ) {
	    	if(!(node instanceof EnumNode)) {
	    		layout.generateUMLClasses(diagram, node, resource);
	    	}
	    }
	            
	    LOG.debug("incomplete: " + diagram.getIncomplete());
	    
	    layout.processEdgesForCoreGraph(diagram);
        
	    layout.processEdgesForRemainingNodes(diagram);
	    	               
	    addOrphanEnums(graph,diagram);
	      	    	    
  	    addDiagramForBaseTypes(diagram, producedDiagrams);
  	    
  	    layout.getNodePlacement();
  	    		
  	    return diagram;
  	    
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private Diagram generateDiagramForGraph(String resource, APIGraph apiGraph) {
		        
	    Diagram diagram = new Diagram(args, file, resource);
	       	    
	    layout = new Layout(apiGraph, layoutConfig);
	            	    	    
	    List<Node> nodesInGraph = getSequenceOfNodesInGraph(apiGraph,resource);
	    	   
	    for(Node node: nodesInGraph ) {
	    	if(!(node instanceof EnumNode)) {
	    		layout.generateUMLClasses(diagram, node, resource);
	    	}
	    }
	            	    	
	    layout.processEdgesForInheritance(diagram);

	    layout.processEdgesForCoreGraph(diagram);
        
	    layout.processEdgesForRemainingNodes(diagram);
	    	               
	    addOrphanEnums(apiGraph,diagram);
	      	    	
  	    List<String> placement = layout.getNodePlacement();
  	    placement.forEach(line -> diagram.addComment(new Comment(line)));
  	    
  	    return diagram;
  	    
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


	@LogMethod(level=LogLevel.DEBUG)
	private Diagram generateDiagramForResource(String resource, String stereoType, Set<String> producedDiagrams) {
		
		Diagram diagram = generateDiagramForResource(resource, producedDiagrams);
		
		producedDiagrams.add(resource);
				
		diagram.getClassEntityForResource(resource).setStereoType(stereoType);
		
		return diagram;
		
	}
	
	private void addDiagramForBaseTypes(Diagram diagram, Set<String> producedDiagrams) {
		
		Collection<String> missingDiagrams = diagram.getBaseTypes();
				
		for(String baseType : missingDiagrams) { 
	    	if(!producedDiagrams.contains(baseType)) {
	  	    	String stereoType = Config.getDefaultStereoType();
	  	    	Diagram subDiagram = generateDiagramForResource(baseType, stereoType, producedDiagrams);
	  	    	diagram.addSubDiagram(subDiagram);
	    	}
  	    }		
	}


	@LogMethod(level=LogLevel.DEBUG)
	private void addOrphanEnums(APIGraph graph, Diagram diagram) {
  	    List<String> orphans = Config.getOrphanEnums();
  	    if(Config.getIncludeOrphanEnums() || orphans.contains(graph.getResource())) {
  	    	List<String> orphanEnums = Config.getOrphanEnums(graph.getResource());
  	    	if(orphanEnums.isEmpty()) {
  	    		layout.addOrphanEnums(diagram, graph.getNode(graph.getResource()));   	
  	    	} else {
  	    		Node resource = graph.getNode(graph.getResource());
  	    		for(String orphan : orphanEnums) {
  	    			Node orphanNode = graph.getNode(orphan);
  	    			layout.addOrphanEnum(diagram, resource, orphanNode);
  	    		}
  	    	}    		
  	    }
	}
	
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


	@LogMethod(level=LogLevel.DEBUG)
	private Diagram processDiagramForResource(String resource, Set<String> producedDiagrams) {
				
  	    Diagram diagram = generateDiagramForResource(resource, producedDiagrams);
  	      	    	      	    
  	    producedDiagrams.add(resource);
  	    		
		for(String incomplete : diagram.getIncomplete() ) {
  	    	String stereoType = Config.getDefaultStereoType();
  	    	
  	    	if(!producedDiagrams.contains(incomplete)) {
	  	    	Diagram subDiagram = generateDiagramForResource(incomplete, stereoType, producedDiagrams);
	  	    	diagram.addSubDiagram(subDiagram);	
	  	    	producedDiagrams.add(incomplete);
  	    	}
  	    	  	    	  	    	
		}
  	    		
  	    return diagram;
  	    
	}
	

	@LogMethod(level=LogLevel.DEBUG)
	private Complexity processComplexity(String resource) {
	    APIGraph graph = new APIGraph(resource);
	    
	    Complexity analyser = new Complexity(graph.getGraph(),graph.getResourceNode());
	    	    
	    analyser.computeGraphComplexity();
		    								
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

	    createDirectory(target);
	    
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
	public void displayComplexity() {
		for(String resource : resources) {
			displayComplexityForResource(resource);
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void displayComplexityForResource(String resource) {
		
	    APIGraph preGraph = new APIGraph(resource);
	
	    Complexity analyser = new Complexity(preGraph.getGraph(),preGraph.getResourceNode());
	    
	    displayComplexity(analyser, resource, "Resource diagram complexity", "... Total graph complexity");
	    		
	    APIGraph graph = new APIGraph(resource);
	    
	    graph.applyComplexity(analyser, resource);
	    		
	    analyser = new Complexity(graph.getGraph(),graph.getResourceNode());
	    
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

}
	
