package no.paneon.api.diagram.layout;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.diagram.puml.ClassEntity;
import no.paneon.api.diagram.puml.ClassProperty;
import no.paneon.api.diagram.puml.Comment;
import no.paneon.api.diagram.puml.Diagram;
import no.paneon.api.diagram.puml.EnumEntity;
import no.paneon.api.diagram.puml.ClassProperty.Visibility;
import no.paneon.api.graph.APIGraph;
import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.EnumNode;
import no.paneon.api.graph.Node;
import no.paneon.api.graph.OtherProperty;
import no.paneon.api.graph.Property;
import no.paneon.api.graph.complexity.GraphAlgorithms;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class Layout {

	static final Logger LOG = LogManager.getLogger(Layout.class);

	APIGraph apiGraph;
	JSONObject layoutConfig;
	
	LayoutGraph layoutGraph;
	Node resourceNode;

	public Layout(APIGraph apiGraph, JSONObject layoutConfig) {
		
		this.apiGraph = apiGraph;
		this.layoutConfig = layoutConfig;
		this.resourceNode = apiGraph.getResourceNode();
		
		this.layoutGraph = new LayoutGraph(this.apiGraph);

	}


	@LogMethod(level=LogLevel.DEBUG)
	public ClassEntity generateUMLClasses(Diagram diagram, Node node, String resource) {
		String stereoType = Utils.getStereoType(apiGraph, node.getName(), resource);
						
		Collection<String> incomplete = new HashSet<>();
		
		List<ClassProperty> properties = getPropertiesForClass(node, apiGraph.getOutboundNeighbours(node), incomplete);
		
		ClassEntity cls = new ClassEntity(node);
		
		cls.addProperties(properties);
		cls.setStereoType(stereoType);

		if(!node.getInline().isEmpty()) {
			cls.setInline(node.getInline());
		}
		
		for(EnumNode enumNode : apiGraph.getEnumsForNode(node) ) {
			generateForEnum(cls, enumNode);
		}
					
		LOG.debug("generateUMLClasses: node={} incomplete={}",  node, incomplete);
		
		if(incomplete.isEmpty()) {
			diagram.removeIncomplete(node.getName());
		} else {
			diagram.addIncomplete(node.getName());
		}
		
		diagram.addClass(cls);

		return cls;
	}

	private List<ClassProperty> getPropertiesForClass(Node node, Collection<Node> referencedNodes, Collection<String> incomplete) {
		
		List<ClassProperty> properties = new LinkedList<>();
		
		Collection<String> referenced = APIGraph.getNodeNames( referencedNodes.stream().filter(n -> !(n instanceof EnumNode)).collect(toSet()) );
		
		node.getProperties().stream()
			.filter(p -> !referenced.contains(p.getType()))
			.forEach(p -> {
					
				ClassProperty.Visibility visibility = getClassPropertyVisibility(p);
				
				properties.add( new ClassProperty(p,visibility));

				if(!APIModel.isSimpleType(p.getType()) || APIModel.isEnumType(p.getType())) incomplete.add(node.getName());
				
			});	  
		
		node.getOtherProperties().forEach(prop -> {
		
			ClassProperty.Visibility visibility = ClassProperty.VISIBLE;

			properties.add( new ClassProperty(prop, visibility));
		
		});	 
		
		return properties;

	}

	private Visibility getClassPropertyVisibility(Property prop) {
		switch(prop.getVisibility()) {		
		case BASE:
			return ClassProperty.VISIBLE;
			
		case VISIBLE_INHERITED:
			return ClassProperty.INHERITED;
			
		case HIDDEN_INHERITED:
			return ClassProperty.HIDDEN;
			
		default:
			return ClassProperty.HIDDEN;
		}
	}


	@LogMethod(level=LogLevel.DEBUG) 
	public void processEdgesForCoreGraph(Diagram diagram) {
	    List<Node> coreGraph = layoutGraph.extractCoreGraph();

        diagram.addComment(new Comment("layout of the core: " + coreGraph));

	    List<Node> processed = processEdgesForInheritance(diagram);

    	List<List<Node>> circles = apiGraph.getCircles();
    	
        List<Node> nodesToProcess = getNodesToProcess(Utils.copyList(coreGraph), resourceNode).stream()
        								.filter(n -> !processed.contains(n))
        								.collect(toList());
                
        
    	LOG.debug("generateDiagram:: processing node={}", nodesToProcess);
             
        Optional<Node> nextNode = getNextNode(nodesToProcess);
        while(nextNode.isPresent()) {
        	                    	            		                	
        	Node node = nextNode.get();
        	        	
        	LOG.debug("generateDiagram:: processing node={}", node);
        	        	
        	Map<Integer, List<List<Node>> > circlesForNode = GraphAlgorithms.getCirclesForNode(circles, node);
        	        	
        	LOG.debug("generateDiagram:: node={} circlesForNode={}", node, circlesForNode);

    		generateUMLEdges(diagram, node, coreGraph, circlesForNode);

        	LOG.debug("generateDiagram:: processing edges for node={}", node);

    		circles = GraphAlgorithms.removeCirclesForNode(circles, node);	

    		nodesToProcess.remove(node);
    		nextNode = getNextNode(nodesToProcess);
            
        	LOG.debug("generateDiagram:: processing nextNode={}", nextNode);

        }	
        
        diagram.addComment(new Comment("finished layout of the core"));

        
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean generateUMLEdges(Diagram diagram, Node node, List<Node> includeNodes) {
		return generateUMLEdges(diagram, node, includeNodes, new HashMap<>() );
	}

	@LogMethod(level=LogLevel.DEBUG)
	boolean generateUMLEdges(Diagram diagram, Node node, List<Node> includeNodes, Map<Integer, List<List<Node>> > circles) {

		ClassEntity cls = diagram.getClassEntityForResource(node.getName());

		if(cls==null) return false;

		List<Node> neighbours = apiGraph.getOutboundNeighbours(node).stream().sorted().collect(toList());
		
		int edgeCount = cls.getEdgeCount();

		cls.addComment(new Comment("'processing edges for " + node));

		EdgeAnalyzer edgeAnalyzer = new EdgeAnalyzer(this.layoutGraph, node, circles);
		edgeAnalyzer.computeLayout();

		LOG.debug("### ");
		LOG.debug("### generateUMLEdges for {} includeNodes={}", node, includeNodes);
		LOG.debug("### ");

		LOG.debug("generateUMLEdges: node={} isComposite={}",  node, this.apiGraph.isCompositeNode(node));
		LOG.debug("generateUMLEdges: node={} vertex={}",  node, this.apiGraph.getGraph().vertexSet());

		for(Node vertex : apiGraph.getGraph().vertexSet()) {
			LOG.debug("generateUMLEdges: vertex={} isCompositeNode={}", vertex, apiGraph.isCompositeNode(vertex));
		}
		
		//
		// first process based on configuration details (manual override)
		//
		processManualOverride(cls, node);
		
		// special case of recursive?
		//
		
		LOG.debug("generateUMLEdges: node={} #000",  node);

		boolean isRecursive = processRecursive(cls, node);
		
		//
		// special case of 'Item' sub-resource - this we try to place to the right
		//
		
		LOG.debug("generateUMLEdges: node={} #00",  node);

		boolean hasItem = processItemSpecialCase(cls, node);
		
		// 
		// start layout with identified circles
		//
		
		LOG.debug("generateUMLEdges: node={} #0",  node);

		if(isDiscriminatorNode(node)) {
			layoutCircleDiscriminatorNodes(node,cls,circles);
		} else {
			layoutCircleNodes(node,cls,circles);
		}

		//
		// layout in multiple steps / phases:
		//
		// 1) process neighbors with inbound edges from multiple already placed nodes
		// 2) process neighbors outbound edges to already placed nodes
		// 3) placed edges with already placed nodes
		// 4) left
		// 5) right
		// 6) above 
		// 7) check for unbalance above / below
		// 8) below (looking left and right)
		// 9) below any remaining
		//		

		LOG.debug("generateUMLEdges: node={} #1",  node);

		layoutBetweenAlreadyPlacedNodes(node,cls,neighbours,includeNodes);

		layoutOutboundToPlacedNodes(node,cls,neighbours,includeNodes);

		layoutOutboundEdgesWithPlacedNodes(node,cls,includeNodes);

		layoutBetweenCommonNode(node,cls,neighbours,includeNodes);

		layoutEnums(node, diagram, cls, isRecursive);
		
		layoutLeft(node,cls,includeNodes, edgeAnalyzer);    

		layoutRight(node,cls,includeNodes, edgeAnalyzer, hasItem || isRecursive);

		layoutAbove(node,cls,includeNodes, edgeAnalyzer);

		layoutUnbalancedAboveBelow(node,cls,includeNodes, edgeAnalyzer);

		// layoutBelowLeftRight(node,cls,includeNodes, edgeAnalyzer);

		layoutBelowRemaining(node,cls,includeNodes, edgeAnalyzer);

		cls.addComment(new Comment("'completed processing of edges for " + node));

		return cls.getEdgeCount()>edgeCount;

	}

	
	private boolean isDiscriminatorNode(Node node) {
		boolean res=false;
	    Set<String> neighbours = this.apiGraph.getNeighbours(node).stream().map(Node::getName).collect(toSet());

	    Set<String> mapping = node.getAllDiscriminatorMapping();
	    
	    if(mapping.size() >= neighbours.size()) res=true;
	    
	    
	    LOG.debug("isDiscriminatorNode: node={} neighbours={} mapping={} res={}", node, neighbours, mapping, res);
	    
	    return res;
	
	}


	// TBD - Inheritance
	@LogMethod(level=LogLevel.DEBUG) 
	private void processEdgesForInheritance_old(Diagram diagram) {
		
		Predicate<Node> notInheritance = n -> !CoreAPIGraph.isPatternInheritance(n);

	    List<Node> coreGraph = layoutGraph.extractCoreGraph().stream().filter(notInheritance).collect(toList());

	    List<Edge> edges = coreGraph.stream()
	    						.map(n -> CoreAPIGraph.getOutboundEdges(this.apiGraph.getGraph(),n))
	    						.flatMap(Set::stream)
	    						.filter(e -> e.isInheritance())
	    						.collect(toList());
	    		
    	List<Node> superClasses = edges.stream().map(Edge::getRelated).sorted().distinct().collect(toList());
    	
		Predicate<Node> notSuperclass = n -> !superClasses.contains(n);

		Predicate<Node> notOneOf         = n -> !layoutGraph.apiGraph.getGraph().edgeSet().stream().filter(Edge::isOneOf).anyMatch(edge -> edge.getRelated().equals(n));
		Predicate<Node> notDiscriminator = n -> !layoutGraph.apiGraph.getGraph().edgeSet().stream().filter(Edge::isDiscriminator).anyMatch(edge -> edge.getRelated().equals(n));

        List<Node> nodesToProcess = getNodesToProcess(Utils.copyList(coreGraph), this.resourceNode)
        								.stream()
        								.filter(notSuperclass)
        								.filter(notOneOf)
        								.filter(notDiscriminator)
        								.distinct()
        								.collect(toList());
                  
    	LOG.debug("processEdgesForInheritance:: nodesToProcess={}", nodesToProcess );

    	if(nodesToProcess.isEmpty()) return;
    	
    	// TBD - or > 1 ?
    	
        diagram.addComment(new Comment("layout of the inheritance: " + coreGraph));
     
        Set<Node> allSuperclassSubGraphNodes = nodesToProcess.stream()
        										.map(n -> CoreAPIGraph.getOutboundEdges(this.apiGraph.getGraph(),n))
        										.flatMap(Set::stream)
												.filter(Edge::isInheritance)
												.map(Edge::getRelated)
								            	.map(n -> CoreAPIGraph.getSubGraphNodes(this.apiGraph.getGraph(),n))
								            	.flatMap(Set::stream)
								            	.collect(toSet());

    	LOG.debug("processEdgesForInheritance:: allSuperclassSubGraphNodes={}", allSuperclassSubGraphNodes );

        nodesToProcess.forEach(node -> {
        	
            List<Node> nodeSuperclasses = CoreAPIGraph.getOutboundEdges(this.apiGraph.getGraph(),node).stream()
											.filter(e -> e.isInheritance())
											.map(Edge::getRelated)
											.sorted()
											.distinct()
											.collect(toList());
            
	    	LOG.debug("processEdgesForInheritance:: node={} superclasses={}", node, nodeSuperclasses );
	    	
        });
        
        
    	LOG.debug("processEdgesForInheritance:: nodesToProcess={} ", nodesToProcess );
    	LOG.debug("processEdgesForInheritance:: edges={}", edges);
    	LOG.debug("processEdgesForInheritance:: superClasses={}", superClasses);
    	
        Iterator<Node> iter = nodesToProcess.iterator();
        while(iter.hasNext()) {
        	                    	            		                	
        	Node node = iter.next();
        	
        	Set<Node> subGraphOfNode = CoreAPIGraph.getSubGraphNodes(this.apiGraph.getGraph(),node);
        	
        	boolean nodeConnectedToSuperclasses = subGraphOfNode.stream().anyMatch(n ->allSuperclassSubGraphNodes.contains(n));
        	
	    	LOG.debug("processEdgesForInheritance:: node={} connected={} usbGraphOfNode={}", node, nodeConnectedToSuperclasses, subGraphOfNode );
	    	LOG.debug("processEdgesForInheritance:: node={} allSuperclassSubGraphNodes={}", node, allSuperclassSubGraphNodes );

        	if(nodeConnectedToSuperclasses && !layoutGraph.isPlaced(node)) {
            	LOG.debug("processEdgesForInheritance:: processing node={} nodeConnectedToSuperclasses={}", node, nodeConnectedToSuperclasses);
            	processEdgesForInheritanceHelper(diagram,node);
        	}
            
        }	
        
        diagram.addComment(new Comment("finished layout of the inheritance"));
        
	}
	
	
	// TBD - Inheritance
	@LogMethod(level=LogLevel.DEBUG) 
	public List<Node> processEdgesForInheritance(Diagram diagram) {
		
        List<Node> nodesToProcess = layoutGraph.apiGraph.getGraph().incomingEdgesOf(resourceNode).stream()
        								.filter(Edge::isAllOf)
        								.map( layoutGraph.apiGraph.getGraph()::getEdgeSource )
        								.collect(toList());
               
        nodesToProcess = nodesToProcess.stream()
        					.filter( n -> layoutGraph.apiGraph.getGraph().incomingEdgesOf(n).isEmpty() )
        					.collect( toList() );
        
    	LOG.debug("processEdgesForInheritance:: nodesToProcess={}", nodesToProcess );

    	List<Place> directions = new LinkedList<>();
    	
    	directions.add( Place.LEFT);
    	directions.add( Place.RIGHT);
    	
    	for(int i=0; i<(nodesToProcess.size()-2)/2; i++) directions.add( Place.ABOVE);
    	for(int i=0; i<nodesToProcess.size()/2; i++) directions.add( Place.BELOW);
    	
		ClassEntity cls = diagram.getClassEntityForResource(this.resourceNode.getName());

		String rule = "inheritance rule";
		
		Iterator<Node> iterNode       = nodesToProcess.iterator();
		Iterator<Place> iterDirection = directions.iterator();
		
		while(iterNode.hasNext()) {
			Node node = iterNode.next();
			Place direction = iterDirection.next();
						
			ClassEntity nodeEntity = diagram.getClassEntityForResource(node.getName());
			if(nodeEntity==null) {
				generateUMLClasses(diagram, node, diagram.getResource());
			}			

			layoutGraph.placeReverseEdges(cls, resourceNode, node, direction, rule + " in direction " + direction);
			
	    	LOG.debug("processEdgesForInheritance:: placeing={}", node );

		}

		nodesToProcess.remove(resourceNode);
		
        return nodesToProcess;
	}
	
	
	private void processEdgesForInheritanceHelper(Diagram diagram, Node node) {
		Optional<Node> noparent = Optional.empty();
		Set<Node> processed = new HashSet<>();
		processEdgesForInheritanceHelper(diagram, noparent, node, processed);
	}

	private void processEdgesForInheritanceHelper(Diagram diagram, Optional<Node> parent, Node node, Set<Node> processed) {
	    Set<Node> subClasses = CoreAPIGraph.getOutboundEdges(this.apiGraph.getGraph(),node).stream()
	    						.filter(e -> e.isInheritance())
	    						.map(e -> this.apiGraph.getGraph().getEdgeTarget(e))
	    						.filter(n -> !processed.contains(n))
	    						// .filter(n -> !CoreAPIGraph.isLeafNodeOrOnlyEnums(this.apiGraph.getGraph(),n))
	    						.sorted()
	    						.distinct()
	    						.collect(toSet());

	    if(processed.contains(node)) return;

    	LOG.debug("processEdgesForInheritanceHelper:: node={} subClasses={}", node, subClasses );

	    if(subClasses.isEmpty()) {
	    	return;
	    }
	    
	    	
    	processed.add(node);
		ClassEntity cls = diagram.getClassEntityForResource(node.getName());

    	if(parent.isPresent()) {
			String rule = "#Inheritance rule - place BELOW parent";
			Place defaultDirection = Place.BELOW;
			Optional<Node> following = Optional.empty();
			
			LOG.debug("processEdgesForInheritanceHelper: place BELOW parent: node={} candidate={}", node, parent);

			layoutGraph.placeEdgesBetween(this, cls, parent.get(), node, following, defaultDirection, rule);
			
			// return;
    	}
    											
    	LOG.debug("processEdgesForInheritanceHelper:: node={} subClasses={}", node, subClasses);

    	LOG.debug("processEdgesForInheritanceHelper:: node={} isPlaced={}", node, !this.layoutGraph.isPlaced(node, Place.ABOVE));
    	LOG.debug("processEdgesForInheritanceHelper:: node={} isPlacedAt={}", node, !this.layoutGraph.isPlacedAt(node, Place.ABOVE));

    	for(Node candidate : subClasses) {
    		
    		boolean isCandidateLeaf = CoreAPIGraph.isLeafNode(this.apiGraph.getGraph(),candidate);
    		boolean hasEnumsOnly = CoreAPIGraph.getOutboundNeighbours(this.apiGraph.getGraph(),candidate).stream().allMatch(Node::isEnumNode);

    		boolean singleInbound = CoreAPIGraph.getInboundNeighbours(this.apiGraph.getGraph(),candidate).size()<=1;
    		boolean placedBelow = this.layoutGraph.isPlacedAt(node, Place.BELOW) && this.layoutGraph.isPlaced(node);
    		boolean simpleNode = isCandidateLeaf || hasEnumsOnly;
    		
	    	LOG.debug("processEdgesForInheritanceHelper:: node={} candidate={} singleInbound={} placedBelow={} simpleNode={}", node, candidate, singleInbound, placedBelow, simpleNode);

	    	if(node.getName().endsWith("RefOrValue")) { // TBD - should be generalized
	    		
	    		String rule = "#Inheritance rule - RefOrValue";
				Place defaultDirection = Place.BELOW;
				Optional<Node> following = Optional.empty();
				
				LOG.debug("processEdgesForInheritanceHelper: place BELOW: node={} candidate={}", node, candidate);
	
				layoutGraph.placeEdgesBetween(this, cls, node, candidate, following, defaultDirection, rule);
				
	    	} else if(simpleNode && singleInbound && !placedBelow) {
    			// ignore if leaf
    			
				LOG.debug("processEdgesForInheritanceHelper: isCandidateLeaf: node={} candidate={}", node, candidate);

				String rule = "#Inheritance rule - above";
				Place defaultDirection = Place.ABOVE;
				Optional<Node> following = Optional.empty();
				
				LOG.debug("processEdgesForInheritanceHelper: place ABOVE: node={} candidate={}", node, candidate);
	
				//layoutGraph.placeEdges(cls,  node,  candidate, func, rule);						
				layoutGraph.placeEdgesBetween(this, cls, node, candidate, following, defaultDirection, rule);
				
    		} else if(simpleNode && (placedBelow || !this.layoutGraph.isPlaced(node))) {
    			// ignore if leaf
    			
				LOG.debug("processEdgesForInheritanceHelper: isCandidateLeaf: node={} candidate={}", node, candidate);

				String rule = "#Inheritance rule - below";
				Place defaultDirection = Place.BELOW;
				Optional<Node> following = Optional.empty();
				
				LOG.debug("processEdgesForInheritanceHelper: place BELOW: node={} candidate={}", node, candidate);
	
				//layoutGraph.placeEdges(cls,  node,  candidate, func, rule);						
				layoutGraph.placeEdgesBetween(this, cls, node, candidate, following, defaultDirection, rule);
				
    		} else {
				String rule = "#Inheritance rule - right";
				Place defaultDirection = Place.RIGHT;
				Optional<Node> following = Optional.empty();

				LOG.debug("processEdgesForInheritanceHelper: place RIGHT: node={} candidate={}", node, candidate);
	
				// layoutGraph.placeEdges(cls,  node,  candidate, func, rule);	
				layoutGraph.placeEdgesBetween(this, cls, node, candidate, following, defaultDirection, rule);

    		}
    	}
    	
		List<Node> neighbours = apiGraph.getOutboundNeighbours(node).stream().sorted().collect(toList());
		
		neighbours.forEach(neighbour -> {
			Optional<Node> parentNode = Optional.of(node);
			
			boolean isLinearPath = this.apiGraph.isLinearPath(neighbour,Integer.MAX_VALUE);
			if(!isLinearPath) 
				processEdgesForInheritanceHelper(diagram,parentNode,neighbour, processed);
			
		});
    		    
		
	}

	@LogMethod(level=LogLevel.DEBUG) 
	private List<Node> shiftCircle(List<Node> circle) {
		Predicate<Node> isPlaced = layoutGraph::isPlaced;		
		boolean someAlreadyPlaced = circle.stream().anyMatch(isPlaced);

		if(someAlreadyPlaced) {
			Node front = circle.get(0);
			while(!layoutGraph.isPlaced(front)) {
				circle.remove(0);
				circle.add(front);
				front = circle.get(0);
			}
		}
		return circle;
	}


	@LogMethod(level=LogLevel.DEBUG) 
	private List<Node> rotateCircle(List<Node> circle, Node pivot) {
		if(!circle.isEmpty() && circle.contains(pivot)) {
			
	    	LOG.debug("rotateCircle:: #0 pivot={} circle={}", pivot, circle);

			while(!circle.get(0).equals(pivot)) {
				Node first = circle.get(0);
				circle.remove(0);
				if(!circle.get(circle.size()-1).equals(first)) circle.add(first);
			}
			circle.add(pivot);
			if(layoutGraph.isPlaced(circle.get(1))) {
				Collections.reverse(circle);
			}
        	LOG.debug("rotateCircle:: #1 pivot={} circle={}", pivot, circle);

		}
		return circle;
	}
	
	@LogMethod(level=LogLevel.DEBUG) 
	private List<Node> getNodesToProcess(List<Node> list, Node pivot) {
		list.remove(pivot);
		list.add(0,pivot);
		return list;
	}


	@LogMethod(level=LogLevel.DEBUG) 
	void processEdgesForRemainingNodes(Diagram diagram) {

		List<Node> nodes = apiGraph.getGraphNodes().stream()
				.filter(node -> !node.equals(resourceNode))
				.sorted(Comparator.comparing(this::subGraphSize))
				.collect(toList());
		
		
		LOG.debug("processEdgesForRemainingNodes: #1 nodes={}", nodes);
		
		Predicate<Node> isNotPlaced = n -> !this.layoutGraph.isPlaced(n);
		
		List<Node> placedNodes = nodes.stream().filter(this.layoutGraph::isPlaced).collect(toList());
		List<Node> nonPlacedNodes = nodes.stream().filter(isNotPlaced).collect(toList());

		nodes = placedNodes;
		nodes.addAll(nonPlacedNodes);
		
		nodes.add(0, resourceNode );
		
		LOG.debug("processEdgesForRemainingNodes: #2 nodes={}", nodes);

		for(Node node: nodes ) {
			if(LOG.isDebugEnabled()) LOG.debug("generateDiagram:: adding edges with node={}", node);
			generateUMLEdges(diagram, node, apiGraph.getGraphNodeList());
		}		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private int subGraphSize(Node node) {
		return CoreAPIGraph.getSubGraphNodes(this.apiGraph.getGraph(), node).size();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Optional<Node> getNextNode(List<Node> nodesToProcess) {
		List<Node> sorted = nodesToProcess.stream()
					.sorted(Comparator.comparing(n -> - this.layoutGraph.getInboundEdgesFromPlaced(n) ))
					.collect(toList());
		
		LOG.debug("getNextNode: sorted={}", sorted);

		Optional<Node> found = sorted.stream()
							.filter(this.layoutGraph::isPlaced)
							.findFirst();	
		
		if(!found.isPresent()) {
			found = sorted.stream().findFirst();
		}
		
		return found;
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean presentEnumForNode(String resource, String node) {
		return !Utils.isBaseType(resource, node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	EnumEntity generateForEnum(ClassEntity cls, EnumNode enumNode) {
		EnumEntity enode = null;
		String type = enumNode.getType();
		
		if(!cls.isEnumProcessed(type)) {
			enode = new EnumEntity(enumNode);
			cls.addEnum(enode);
		}
		
		return enode;
	}


	private void layoutCircleNodes(Node node, ClassEntity cls, Map<Integer, List<List<Node>> > circleMap) {
		
		
		if(!circleMap.isEmpty()) {
			LOG.debug("layoutCircleNodes:: node={} circleMap=\n ... {}", node, circleMap.values().stream().map(Object::toString).collect(Collectors.joining("\n ... ")));
		}
				
		List<List<Node>> sortedCircles = circleMap.values().stream()
												.flatMap(List::stream)
												.sorted((xs1, xs2) -> xs2.size() - xs1.size())
												.collect(toList());

						
 		if(!sortedCircles.isEmpty()) {
			LOG.debug("layoutCircleNodes:: node={}, sorted==\n ... {}", node, sortedCircles.stream().map(Object::toString).collect(Collectors.joining("\n ... ")));
		}
		
		Map<Node, Long> commonNodeCount = sortedCircles.stream()
												.map(HashSet::new)
												.flatMap(Set::stream)
												.filter(n -> !n.equals(node))
												.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
 
		LOG.debug("layoutCircleNodes:: node={}, commonNodeCount={}", node, commonNodeCount);

				
		Optional<Long> maxCommon = commonNodeCount.values().stream().max(Long::compare);
		
		Set<Node> commonNodes = commonNodeCount.entrySet().stream()
									.filter(entry -> entry.getValue()>1)
									.map(Map.Entry::getKey)
									.collect(toSet());
		
		LOG.debug("layoutCircleNodes: node={} commonNodes={}", node, commonNodes);
		
		for(List<Node> circle : sortedCircles) {
			if(commonNodes.contains(circle.get(1)) || !apiGraph.isLeafNode(circle.get(1))) {
				LOG.debug("layoutCircleNodes:: node={}, circle={}", node, circle);
				Collections.reverse(circle);
				LOG.debug("layoutCircleNodes:: node={}, reversed circle={}", node, circle);

			}
		}
		
		if(!sortedCircles.isEmpty()) {
			LOG.debug("layoutCircleNodes:: node={}, sorted==\n ... {}", node, sortedCircles.stream().map(Object::toString).collect(Collectors.joining("\n ... ")));
		}

		sortedCircles.forEach(circle -> {

			// circle.retainAll( this.layoutGraph.layoutGraph.vertexSet() );
			circle.retainAll( apiGraph.getGraph().vertexSet() );

			LOG.debug("layoutCircleNodes: #1 node={} circle={}", node, circle);

			if(circle.contains(node)) {				
				layoutCircleNodes(cls, node, circle, maxCommon);
			}

		});

	}
	
	
	private void layoutCircleDiscriminatorNodes(Node node, ClassEntity cls, Map<Integer, List<List<Node>> > circleMap) {
		
		
		if(!circleMap.isEmpty()) {
			LOG.debug("layoutCircleDiscriminatorNodes:: node={} circleMap=\n ... {}", node, circleMap.values().stream().map(Object::toString).collect(Collectors.joining("\n ... ")));
		}
			
		Set<String> mapping = node.getAllDiscriminatorMapping();
		
		Predicate<String> isMapped = s -> mapping.contains(s);

		Predicate<List<Node>> isAllMapped = nodeList -> nodeList.stream().map(Node::getName).allMatch(isMapped);
		
		List<List<Node>> sortedCircles = circleMap.values().stream()
												.flatMap(List::stream)
												.filter(isAllMapped)
												.sorted((xs1, xs2) -> xs2.size() - xs1.size())
												.collect(toList());

						
 		if(!sortedCircles.isEmpty()) {
			LOG.debug("layoutCircleDiscriminatorNodes:: node={}, sorted==\n ... {}", node, sortedCircles.stream().map(Object::toString).collect(Collectors.joining("\n ... ")));
		}
		
		Map<Node, Long> commonNodeCount = sortedCircles.stream()
												.map(HashSet::new)
												.flatMap(Set::stream)
												.filter(n -> !n.equals(node))
												.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
 
		LOG.debug("layoutCircleDiscriminatorNodes:: node={}, commonNodeCount={}", node, commonNodeCount);

				
		Optional<Long> maxCommon = commonNodeCount.values().stream().max(Long::compare);
		
		Set<Node> commonNodes = commonNodeCount.entrySet().stream()
									.filter(entry -> entry.getValue()>1)
									.map(Map.Entry::getKey)
									.collect(toSet());
		
		LOG.debug("layoutCircleDiscriminatorNodes: node={} commonNodes={}", node, commonNodes);
		
		for(List<Node> circle : sortedCircles) {
			if(commonNodes.contains(circle.get(1)) || !apiGraph.isLeafNode(circle.get(1))) {
				LOG.debug("layoutCircleDiscriminatorNodes:: node={}, circle={}", node, circle);
				Collections.reverse(circle);
				LOG.debug("layoutCircleDiscriminatorNodes:: node={}, reversed circle={}", node, circle);

			}
		}
		
		if(!sortedCircles.isEmpty()) {
			LOG.debug("layoutCircleDiscriminatorNodes:: node={}, sorted==\n ... {}", node, sortedCircles.stream().map(Object::toString).collect(Collectors.joining("\n ... ")));
		}

		sortedCircles.forEach(circle -> {

			// circle.retainAll( this.layoutGraph.layoutGraph.vertexSet() );
			circle.retainAll( apiGraph.getGraph().vertexSet() );

			LOG.debug("layoutCircleDiscriminatorNodes: #1 node={} circle={}", node, circle);

			if(circle.contains(node)) {				
				layoutCircleNodes(cls, node, circle, maxCommon);
			}

		});

	}
	
	
	private void layoutCircleNodes(ClassEntity cls, Node node, List<Node> circle, Optional<Long> maxCommon) {
		
		if(circle.isEmpty()) return;
			
		Predicate<Node> isNotPlaced = n -> !layoutGraph.isPlaced(n) || n.equals(node) || true; // TBD last true
		
		List<Node> effectiveCircle = circle.stream().filter(isNotPlaced).collect(toList());

		if(!effectiveCircle.get(0).equals(node) && effectiveCircle.contains(node)) {
			if(effectiveCircle.get(0).equals(effectiveCircle.get(effectiveCircle.size()-1))) {
				effectiveCircle.remove(effectiveCircle.size()-1);
			}
			
			Node first = effectiveCircle.get(0);
			while(!first.equals(node)) {
				effectiveCircle.remove(0);
				effectiveCircle.add(first);
				first = effectiveCircle.get(0);
			}
			
			if(first.equals(node)) effectiveCircle.add(first);
			
		} 
		
		LOG.debug("layoutCircleNodes:: node={} circle={}", node, circle);
		LOG.debug("layoutCircleNodes:: node={} effectiveCircle={}", node, effectiveCircle);

		if(effectiveCircle.isEmpty()) return;

		String rule = "circle rule :: effectiveCircle = " + effectiveCircle.stream().map(Object::toString).collect(Collectors.joining(" "));

		boolean someOfCirclePlaced = effectiveCircle.stream().anyMatch(layoutGraph::isPlaced);

		Node effectiveNode = effectiveCircle.get(0);
		if(someOfCirclePlaced) {
			if(circleContainsItemSpecialCase(effectiveCircle)) {
				Optional<Node> optItemNode = getItemSpecialCase(effectiveCircle);
				if(optItemNode.isPresent()) {
					rotateCircle(effectiveCircle,optItemNode.get());
				}
				effectiveNode = effectiveCircle.get(0);
				effectiveCircle.remove(0);
	
				LOG.debug("layoutCircleNodes:: effectiveNode={} circleContainsItemSpecialCase optItemNode={} effectiveCircle={}", effectiveNode, optItemNode, effectiveCircle);
	
			} else if(layoutGraph.isPlaced(effectiveNode)) {
				shiftCircle(effectiveCircle);
				effectiveNode = effectiveCircle.get(0);
				effectiveCircle.remove(0);
				
				LOG.debug("layoutCircleNodes:: effectiveNode={} shiftCircle effectiveCircle={}", effectiveNode, effectiveCircle);
	
			}
		} 
		else {
		
			LOG.debug("layoutCircleNodes: #2 effectiveNode={} effectiveCircle={}", effectiveNode, effectiveCircle);
				
			Set<Node> alreadyPlaced = this.apiGraph.getGraph().incomingEdgesOf(effectiveNode).stream()
											.map(this.apiGraph.getGraph()::getEdgeSource)
											.filter(this.layoutGraph::isPlaced)
											.collect(toSet());
			
			LOG.debug("layoutCircleNodes: ### none of circle placed effectiveCircle={} alreadyPlaced={}", effectiveCircle, alreadyPlaced );

			for(Node placedNode : alreadyPlaced) {
				
				layoutGraph.placeEdges(cls, placedNode, effectiveNode, 
						layoutGraph.getDirection(this, placedNode, effectiveNode, Optional.empty(), Place.RIGHT), 
						"placing first element in floating circle");
			}
			
			effectiveCircle.remove(0);

		}
		
		
		List<Place> defaultDirections;
		
		boolean multipleCommon = maxCommon.isPresent() && maxCommon.get()>=4;
		
		LOG.debug("layoutCircleNodes: node={} effectiveNode={} circle={}", node, effectiveNode, effectiveCircle);
		
		if(isInheritanceEdge(node,effectiveCircle)) {
			defaultDirections = Arrays.asList(Place.RIGHT, Place.BELOW, Place.LEFT);
			// defaultDirections = Arrays.asList(Place.LEFT, Place.BELOW, Place.RIGHT);
			rule = rule + " - special case - inheritance";
			
			LOG.debug("layoutCircleNodes: node={} :: isInheritanceEdge", effectiveNode);

			LOG.debug("layoutCircleNodes: node={} circle={}", node, effectiveCircle);

		
		} else if(singleCommon(effectiveCircle)) {
			defaultDirections = Arrays.asList(Place.BELOW, Place.BELOW, Place.ABOVE, Place.ABOVE);
			rule = rule + " - special case - singleCommon";
			
		} else if(multipleCommon) {
			defaultDirections = Arrays.asList(Place.BELOW, Place.BELOW, Place.RIGHT);
			rule = rule + " - special case - multipleCommon";

		} else if(threePartSimpleCicle(node, effectiveCircle) || multipleEdgesWithNeighbour(node,effectiveCircle)) {
			defaultDirections = Arrays.asList(Place.BELOW, Place.RIGHT);
			rule = rule + " - special case - threePart=" + threePartSimpleCicle(node, effectiveCircle) + " multiple=" + multipleEdgesWithNeighbour(node,effectiveCircle);
			
		} else if(!layoutGraph.isPlacedAt(effectiveNode, Place.RIGHT) && effectiveCircle.size()>5) {
			defaultDirections = createCircleDirections(effectiveCircle, Arrays.asList(Place.RIGHT, Place.BELOW, Place.LEFT) );
			rule = rule + " - free to RIGHT";

		} else if(!layoutGraph.isPlacedAt(effectiveNode, Place.LEFT) ) {
			defaultDirections = Arrays.asList(Place.LEFT, Place.BELOW, Place.RIGHT);
			rule = rule + " - free to LEFT";
			
		} else {
			defaultDirections = Arrays.asList(Place.BELOW, Place.RIGHT);
			rule = rule + " - not LEFT or RIGHT - effectiveNode: " + effectiveNode;

		}
								
		placeCircleSegment(cls, effectiveNode, effectiveCircle, defaultDirections, rule);

		
	}

	private boolean isInheritanceEdge(Node node, List<Node> circle) {
		boolean res=false;
		if(!circle.isEmpty()) {
			Node firstNode = circle.get(0);
			res = this.apiGraph.getEdges(node, firstNode).stream().anyMatch(Edge::isInheritance);
			
			LOG.debug("isInheritanceEdge: node={} firstNode={} res={}", node, firstNode, res );

		}
		return res;
	}


	private boolean singleCommon(List<Node> effectiveCircle) {
		boolean res=false;
		LOG.debug("singleCommon: circle={}", effectiveCircle);

		if(effectiveCircle.size()==4) {
			Node pivot=effectiveCircle.get(1);
			Set<Edge> edges = this.apiGraph.getInboundEdges(pivot);
			edges.addAll( this.apiGraph.getOutboundEdges(pivot) );
			res = edges.stream().map(edge -> edge.getRelationship()).distinct().collect(toSet()).size()==1;
		}
		return res;
	}


	private Optional<Node> getItemSpecialCase(List<Node> circle) {
		return circle.stream().filter(node -> apiGraph.isItemSubResource(apiGraph.getResourceNode(),node)).findFirst();
	}


	private boolean circleContainsItemSpecialCase(List<Node> circle) {
		return circle.stream().anyMatch(node -> apiGraph.isItemSubResource(apiGraph.getResourceNode(),node));
	}


	private List<Place> createCircleDirections(List<Node> circle, List<Place> template) {
		List<Place> res = new LinkedList<>(template);
		
		Place defaultDirection = template.listIterator(template.size()).previous();
		
		while(res.size()<circle.size()) res.add(defaultDirection);
		
		if(circle.size()>5) {
			for(int i=0; i<2; i++) {
				res.set(res.size()-1-i, Place.getReverse(template.get(i)) );
			}
		}
		
		LOG.debug("createCircleDirections: defaultDirection={} template={} res={}", defaultDirection, template, res);
		
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG) 
	private boolean threePartSimpleCicle(Node start, List<Node> circle) {
		return threePartCircle(start,circle) && maximumLinkedCircles(start,circle)<3;
	}

	@LogMethod(level=LogLevel.DEBUG) 
	private boolean threePartCircle(Node start, List<Node> circle) {
		return ( (circle.contains(start) && circle.size()==3) || (circle.size()==2) );
	}

	@LogMethod(level=LogLevel.DEBUG) 
	private int maximumLinkedCircles(Node start, List<Node> circle) {
		Map<Node,Long> countOfNodes = apiGraph.getCircles().stream()
							.flatMap(List::stream)
							.filter(node -> !node.equals(start))
							.filter(circle::contains)
							.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	
		LOG.debug("maximumLinkedCircles:: start={} countOfNodes={}",  start, countOfNodes);
		
		return countOfNodes.values().stream().mapToInt(Math::toIntExact).max().orElse(0);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG) 
	private boolean multipleEdgesWithNeighbour(Node node, List<Node> circle) {
		return edgesWithNeighbour(node,circle.get(0))>1;
	}

	@LogMethod(level=LogLevel.DEBUG) 
	private int edgesWithNeighbour(Node nodeA, Node nodeB) {
		return apiGraph.getGraph().getAllEdges(nodeA, nodeB).size() + 
			   apiGraph.getGraph().getAllEdges(nodeB, nodeA).size();
	}
	
	@LogMethod(level=LogLevel.DEBUG) 
	private int edgesWithNeighbour(List<Node> circle) {
		int res = 0;
		if(circle.size()>=2) {
			res = apiGraph.getGraph().getAllEdges(circle.get(0), circle.get(1)).size() + 
				  apiGraph.getGraph().getAllEdges(circle.get(1), circle.get(0)).size();
		}
		
		LOG.debug("edgesWithNeighbour: res={} circle={}", res, circle);
		
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	private void placeCircleSegment(ClassEntity cls, Node node, List<Node> circle, List<Place> defaultDirections, String rule) {

		LOG.debug("placeCircleSegment: node={} circle={} defaultDirections={} rule={}",  node, circle, defaultDirections, rule);

		Place defaultDirection = Place.BELOW;
		Iterator<Place> directionIterator = defaultDirections.iterator();
		
		LookaheadIterator<Node> circleIterator = skipPlaced(node, circle, directionIterator);

		if(!circleIterator.hasNext()) return;

		Node fromNode = node;
		Optional<Node> optFromNode = circleIterator.current();
		if(optFromNode.isPresent()) {
			
			LOG.debug("placeCircleSegment: node={} fromNode={} optFromNode={}",  node, fromNode, optFromNode);

			fromNode = optFromNode.get();
		}
		
		Place previousDirection = Place.EMPTY;
		Place lastDirection     = Place.EMPTY;
		
		LOG.debug("placeCircleSegment: node={} circle={} defaultDirections={}",  node, circle, defaultDirections);

		boolean firstHidden=true;
		boolean previousOverride=false;
	
		while(circleIterator.hasNext()) {
			
			Node toNode = circleIterator.next();
			
			toNode.addCircleElements(circle);
			
			LOG.debug("#0 placeCircleSegment: toNode={} ",  toNode);

			Optional<Node> followingNode = followingNode(circleIterator);
			
			String detailedRule = rule + " - place " + defaultDirections;

			if(directionIterator.hasNext()) {
				defaultDirection=directionIterator.next();
				LOG.debug("#1 placeCircleSegment: defaultDirection next={} ",  defaultDirection);
				lastDirection=defaultDirection;
				
			} else {
				// defaultDirection=defaultDirections.get(defaultDirections.size()-1);
				defaultDirection=lastDirection;
				LOG.debug("placeCircleSegment: defaultDirection missing next={} ",  defaultDirection);

			}
			
			Place activeDirection=defaultDirection;
			Place forceDirection=defaultDirection;

			if(previousOverride && previousDirection==Place.BELOW && (defaultDirection==Place.RIGHT || defaultDirection==Place.LEFT)) {
				forceDirection=defaultDirection;
				activeDirection=Place.ABOVE;
			}			
			
			Place placedDirection;
			previousOverride=false;
			if(isRecursive(fromNode) && activeDirection==Place.RIGHT) {
				// forceDirection=activeDirection;
				activeDirection=Place.BELOW;
				detailedRule = detailedRule + " - BELOW as recursive from";
				placedDirection = layoutGraph.placeEdgesBetween(this, cls, fromNode, toNode, followingNode, activeDirection, detailedRule);
				previousOverride=true;
				
				LOG.debug("placeCircleSegment: #1 from={} to={} activeDirection={} previous={} placedDirection={}",  fromNode, toNode, activeDirection, previousDirection, placedDirection);

			
			} else {		
				detailedRule = detailedRule + " - default to " + defaultDirection;

				LOG.debug("placeCircleSegment: from={} to={} activeDirection={} previousDirection={}",  fromNode, toNode, activeDirection, previousDirection);

				if(defaultDirection==Place.EMPTY) {
					defaultDirection = Place.BELOW;
					activeDirection = defaultDirection;
				}
				
				placedDirection = layoutGraph.placeEdgesBetween(this, cls, fromNode, toNode, followingNode, activeDirection, detailedRule);
				
				LOG.debug("placeCircleSegment: from={} to={} activeDirection={} previous={} placedDirection={}",  fromNode, toNode, activeDirection, previousDirection, placedDirection);

				
			}
						
			if(false && forceDirection!=Place.EMPTY) {
				
				LOG.debug("placeCircleSegment: FORCE to={} fromNode={}", toNode, fromNode);

				detailedRule = rule + " force direction after override";
				layoutGraph.forceLeftRight(cls, forceDirection, fromNode, toNode);

			} else if((placedDirection==Place.RIGHT || placedDirection==Place.LEFT) && placedDirection!=previousDirection) {
								
				Node from = fromNode;
				List<Node> toRightLeft = layoutGraph.getPlacedNodes().stream()
											.filter(n -> layoutGraph.isAtSameLevel(n,from))
											.filter(n -> !n.equals(from))
											.sorted(Comparator.comparingInt(this::xPositionOfNode))											
											.collect(toList());
				
				toRightLeft.removeAll(circle);
				
				if(!toRightLeft.isEmpty() && firstHidden) {
					firstHidden=false;
					if(placedDirection==Place.RIGHT) {
						// Collections.sort(toRightLeft, Collections.reverseOrder());    
						Collections.reverse(toRightLeft);
					}
					LOG.debug("placeCircleSegment: toRightLeft={}", toRightLeft);
					LOG.debug("placeCircleSegment: toRightLeft={}", toRightLeft.stream().map(this::xPositionOfNode).collect(toList()));

					Node pivot = toRightLeft.get(0);
					if(layoutGraph.isAtSameLevel(pivot, fromNode)) {
						layoutGraph.forceLeftRight(cls, placedDirection, pivot, fromNode);

						// cls.addEdge(new HiddenEdge(pivot,placedDirection,fromNode,rule));
		
						LOG.debug("placeCircleSegment: fromNode={} toNode={} leftRight={} pivot={}", fromNode, toNode, toRightLeft, pivot);
					}
					
				}
		
			}

			previousDirection = placedDirection;
			previousOverride = previousOverride || (placedDirection!=defaultDirection);
			
			fromNode = toNode;
		}
	}

	private int xPositionOfNode(Node node) {
		return layoutGraph.getPosition(node).getX();
	}

	private int yPositionOfNode(Node node) {
		return layoutGraph.getPosition(node).getY();
	}
			
	private Optional<Node> followingNode(LookaheadIterator<Node> iterator) {
		Optional<Node> res = Optional.empty();
		if(iterator.hasNext()) {
			res = Optional.of(iterator.peek());
			
			LOG.debug("followingNode: res={}", res);
		}
		return res;
	}


	private LookaheadIterator<Node> skipPlaced(Node pivot, List<Node> circle, Iterator<Place> directionIterator) {
		LookaheadIterator<Node> iterator = new LookaheadIterator<>( circle.iterator() );
		
		boolean done=false;
		while(!done && iterator.hasNext()) {
			Node next = iterator.peek();
			
			if(next.equals(pivot) && iterator.hasNext()) {
				next=iterator.next();
			}
			
			done = !layoutGraph.isPlaced(next) || !iterator.hasNext();
			
			if(!done) {
				iterator.next();
				if(directionIterator.hasNext()) directionIterator.next();
			}
		}
				
		return iterator;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean layoutEnums(Node node, Diagram diagram, ClassEntity cls, boolean recursive) {
		return layoutGraph.layoutEnums(diagram, cls, node, recursive);		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean processItemSpecialCase(ClassEntity cls, Node node) {
		String rule = "Item special case";

		Predicate<Node> isNotPlaced = n -> !layoutGraph.isPlaced(n);
		Predicate<Node> isItem = n -> apiGraph.isItemSubResource(node,n);

		Optional<Node> subResource = apiGraph.getOutboundNeighbours(node).stream()
				.filter(isNotPlaced)
				.filter(isItem)
				.findFirst();

		LOG.debug("processItemSpecialCase: node={} neighbours={}", node, apiGraph.getOutboundNeighbours(node));
		LOG.debug("processItemSpecialCase: node={} found={}", node, subResource.isPresent());

		if(subResource.isPresent()) {
			
			Node itemResource = subResource.get();
						
			LOG.debug("processItemSpecialCase: node={} found={}", node, itemResource);
			
			Set<Node> placeNodes = Collections.singleton(itemResource);
			
			int edgeCount = apiGraph.getEdges(node, itemResource).size();
						
			if(edgeCount==1 && !layoutGraph.isPlaced(node, Place.RIGHT)) {
				layoutGraph.placeEdgesToNeighbours(cls, node, placeNodes, rule, Place.RIGHT);
			} else {
				layoutGraph.placeEdgesToNeighbours(cls, node, placeNodes, rule, Place.BELOW);
			}

		}
		
		return subResource.isPresent();
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean processRecursive(ClassEntity cls, Node node) {
		boolean recursive=false;
		String rule = "Recursive (self-reference)";

		Predicate<Edge> isNotDiscriminator = e -> !e.isDiscriminator(); 
		
		Set<Node> neighbours = apiGraph.getOutboundEdges(node).stream()
									.filter(isNotDiscriminator)
									.map(this.apiGraph.getGraph()::getEdgeTarget)
									.collect(toSet());
		
		if(neighbours.contains(node)) {
			Set<Node> placeNodes = Collections.singleton(node);
			recursive = layoutGraph.placeEdgesToNeighbours(cls, node, placeNodes, rule, Place.RIGHT);      	
		}

		return recursive;
	}

	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean isRecursive(Node node) {	
		return apiGraph.getOutboundNeighbours(node).contains(node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void processManualOverride(ClassEntity cls, Node node) {
		String nodeName=node.getName();
		if(layoutConfig.has(nodeName)) {
			Place[] directions = Place.values();

			for( Place direction: directions) {
				if(layoutConfig.getJSONObject(nodeName).has(direction.label)) {
					JSONArray config = layoutConfig.getJSONObject(nodeName).getJSONArray(direction.label);
					List<String> placeNodes = Utils.JSONArrayToList(config); 
					String rule = "Configuration override: " + direction;
					LOG.trace("manual layout of node={} placeNodes={}", node, placeNodes);
																	
					Set<Node> nodes = new HashSet<>(apiGraph.getNodesByNames(placeNodes));
					layoutGraph.placeEdgesToNeighbours(cls, node, nodes, rule, direction);
				}
			}
		}
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Place getManualOverride(Node from, Node to) {
		Place res = getManualOverrideHelper(from, to);
		
		if(res==Place.EMPTY) {
			res = getManualOverrideHelper(to,from);
			if(res!=Place.EMPTY) res = Place.getReverse(res);
		}

		return res;
		
	}

	private Place getManualOverrideHelper(Node from, Node to) {
		Place res = Place.EMPTY;
		String nodeName=from.getName();
		if(layoutConfig.has(nodeName)) {
			Place[] directions = Place.values();

			for( Place direction: directions) {
				if(layoutConfig.getJSONObject(nodeName).has(direction.label)) {
					JSONArray config = layoutConfig.getJSONObject(nodeName).getJSONArray(direction.label);
					List<String> placeNodes = Utils.JSONArrayToList(config); 																	
					if(placeNodes.contains(to.getName())) return direction;
				}
			}
		}
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	private void layoutBetweenCommonNode(Node node, ClassEntity cls, List<Node> neighbours, List<Node> includeNodes) {

		List<Node> neighboursWithCircle = neighbours.stream()
				.filter(n -> apiGraph.getNeighbours(n).size()==2)
				// .filter(n -> graph.isCirclePath(n))
				.filter(apiGraph::isCirclePath)
				.collect(toList());

		LOG.debug("layoutBetweenCommonNode: node={} neighbours with circles: {}", node, neighboursWithCircle);

		for(Node neighbour : neighboursWithCircle) {
			List<Node> myCandidates = new LinkedList<>(neighboursWithCircle);

			LOG.debug("layoutBetweenCommonNode: node={} myCandidates={}", node, myCandidates);

			boolean hasPath = myCandidates.stream().anyMatch(cand -> apiGraph.isConnectedPath(neighbour, cand));
			
			LOG.debug("layoutBetweenCommonNode: node={} neighbour={} hasPath={}", node, neighbour, hasPath);

			if(hasPath) {
				// place n below node
				Place func = Place.BELOW;
				String rule = "Common node - direction: " + func;

				LOG.debug("layoutBetweenCommonNode: node={} neighbour={}", node, neighbour);
				
				layoutGraph.placeEdges(cls, node, neighbour, func, rule);

			}
			
		}
			
		LOG.debug("layoutBetweenCommonNode: node={} done", node);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void layoutInboundFromPlacedNodes(Node node, ClassEntity cls, List<Node> neighbours, List<Node> includeNodes) {
		
		List<Node> inboundFromPlaced = apiGraph.getInboundNeighbours(node).stream()
				.filter(layoutGraph::isPlaced)
				.collect(toList());
			
		LOG.debug("layoutInboundFromPlacedNodes:: inboundFromPlaced={}", inboundFromPlaced);

		String rule = "Place edges inbound from already placed nodes";

		for(Node toNode : inboundFromPlaced) {
			if(layoutGraph.isPositionedToLeft(toNode, node)) 
				layoutGraph.placeEdges(cls, toNode, node, Place.RIGHT, rule);
			else if(layoutGraph.isPositionedToRight(toNode, node)) 
				layoutGraph.placeEdges(cls, toNode, node, Place.LEFT, rule);	
			else if(layoutGraph.isPositionedAbove(toNode, node)) 
				layoutGraph.placeEdges(cls, toNode, node, Place.BELOW, rule);
			else if(layoutGraph.isPositionedBelow(toNode, node)) 
				layoutGraph.placeEdges(cls, toNode, node, Place.ABOVE, rule);
		}
		
		LOG.debug("layoutInboundFromPlacedNodes:: done=");

	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutBelowRemaining(Node node, ClassEntity cls, List<Node> includeNodes, EdgeAnalyzer edgeAnalyzer) {

		String rule = "General below rule - either none already or unable to place left / right of currently placed";

		LOG.debug("layoutBelowRemaining:: processing rule: {}", rule);

		Set<Node> candidatesBelow = apiGraph.getOutboundNeighbours(node);

		LOG.debug("layoutBelowRemaining:: remaining below - candidates for below: {}", candidatesBelow);
		LOG.debug("layoutBelowRemaining:: includeNodes: {}", includeNodes);

		Set<Node> candidatesList = candidatesBelow.stream()
				.sorted(Comparator.comparing(n -> apiGraph.getOutboundNeighbours(n).size()))
				.filter(includeNodes::contains)
				.collect(toSet());

		LOG.debug("layoutBelowRemaining:: remaining below - sorted candidates for below: node={} candidates={}", node, candidatesList);

		layoutGraph.placeEdgesToNeighboursBelow(cls, node, candidatesList, rule, edgeAnalyzer);		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private int outboundSpan(Node n) {
		return apiGraph.getOutboundNeighbours(n).size();
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutBelowLeftRight(Node node, ClassEntity cls, List<Node> includeNodes, EdgeAnalyzer edgeAnalyzer) {

		Node nodeBelow = layoutGraph.getNearestBelow(node);
		
		LOG.debug("layoutBelowLeftRight:: node={} nodeBelow={}", node, nodeBelow);

		if(nodeBelow==null) {
			Place direction = Place.BELOW;
			Set<Node> candidates = edgeAnalyzer.getEdgesForPosition(direction);
						
			candidates = candidates.stream()
					.filter(includeNodes::contains)
					.sorted(Comparator.comparing(this::outboundSpan))
					.collect(toSet());

			LOG.debug("layoutBelowLeftRight:: sorted candidates for below: node={} candidates={}", node, candidates);
			LOG.debug("layoutBelowLeftRight:: placing below to {} all remaining below:: {}", direction, candidates);
			
			String rule = "General below rule";

			layoutGraph.placeEdgesToNeighboursBelow(cls, node, candidates, rule, edgeAnalyzer);
			
		    return;
		}

		Set<Node> connectedToNodeBelow = apiGraph.getNeighbours(nodeBelow);

		List<Node> nodesBelow = connectedToNodeBelow.stream()
				.map(apiGraph::getNeighbours)
				.flatMap(Set::stream)
				.filter(n -> layoutGraph.isPlaced(n) && layoutGraph.isPlacedAt(n,Place.BELOW))
				//.filter(node::equals)
				.distinct()
				.collect(toList());

		nodesBelow.add(nodeBelow);
			
		Optional<Node> someNodePlacedRightOrLeft = nodesBelow.stream()
				.filter(n -> layoutGraph.isPlacedAt(n,Place.LEFT) || layoutGraph.isPlacedAt(n,Place.RIGHT) )
				.findFirst();

		boolean someEdgeFromRight = nodesBelow.stream()
				.map(apiGraph::getInboundNeighbours)
				.flatMap(Set::stream)
				.map(layoutGraph::getPosition)
				.anyMatch(n -> n.getX() > layoutGraph.getPosition(node).getX());

		boolean someEdgeFromLeft = nodesBelow.stream()
				.map(apiGraph::getNeighbours)
				.flatMap(Set::stream)
				.map(layoutGraph::getPosition)
				.anyMatch(n -> n.getX() <= layoutGraph.getPosition(node).getX());

		LOG.debug("layoutBelowLeftRight:: node={} place below: nodesBelow={}", node, nodesBelow);
		LOG.debug("layoutBelowLeftRight:: node={} place below: someNodePlacedRightOrLeft={}", node, someNodePlacedRightOrLeft);
		LOG.debug("layoutBelowLeftRight:: node={} place below: someEdgeFromLeft={} someEdgeFromRight={}", node, someEdgeFromLeft, someEdgeFromRight);


		List<Place> directions = new LinkedList<>();
		if(someEdgeFromRight && !someEdgeFromLeft) {
			directions.add(Place.LEFT);
		} else {
			if(!someEdgeFromRight && someEdgeFromLeft) {
				directions.add(Place.RIGHT);
			} else {
				directions.add(Place.RIGHT);
				directions.add(Place.LEFT);
			}
		}

		LOG.debug("layoutBelowLeftRight:: node={} directions={}", node, directions);

		for( Place direction : directions ) {
			
			Predicate<Node> notAlreadyPlaced = n -> !layoutGraph.isPlaced(n);
			
			Set<Node> candidates = edgeAnalyzer.getEdgesForPosition(Place.BELOW).stream().filter(notAlreadyPlaced).collect(toSet());
			if(!candidates.isEmpty()) {
				String rule = "General below rule - direction to " + direction;
			
				LOG.debug("layoutBelowLeftRight:: node={} candidates={}", node, candidates);

				layoutGraph.placeEdgesToNeighboursBelow(cls, node, candidates, rule, nodeBelow, direction, edgeAnalyzer);
			}
			
		}
		
//		if(someNodePlacedRightOrLeft.isPresent()) {
//			for( Place direction : directions ) {
//				// Set<Node> placedInDirection = Utils.intersection(layoutGraph.getPlacedAt(node,Place.getMapping().get(direction)),inboundToNodeBelow);
//				Set<Node> placedInDirection = Utils.intersection(layoutGraph.getPlacedAt(node,direction),connectedToNodeBelow);
//
//				placedInDirection.remove(node);
//
//				String rule = "General below rule - direction to " + direction;
//
//				LOG.debug("layoutBelowLeftRight:: processing rule: node={} rule={} placedInDirection={} connectedToNodeBelow={}", 
//						node, rule, placedInDirection, connectedToNodeBelow);
//
//				boolean placeToDirection = placedInDirection.isEmpty();
//				if(placeToDirection) {
//					// check placement on the left side, find the ones that have been placed to right of others
//					List<Node> pivot = layoutGraph.getAllRightLeftOf(nodeBelow, direction);
//
//					Optional<Node> directConnection = pivot.stream().filter(n -> layoutGraph.hasDirectConnection(nodeBelow,n)).findFirst();
//					if(!directConnection.isPresent()) {
//						pivot.add(nodeBelow);
//						Node pivotNode = layoutGraph.getEdgeBoundary(pivot,direction);
//
//						Set<Node> candidates = edgeAnalyzer.getEdgesForPosition(Place.BELOW);
//						if(!candidates.isEmpty()) {
//							LOG.debug("layoutBelowLeftRight:: pivotNode={} candidates for below: {}", pivotNode, candidates);
//
//							candidates = candidates.stream()
//									.filter(includeNodes::contains)
//									.sorted(Comparator.comparing(n -> apiGraph.getOutboundNeighbours(n).size()))
//									.collect(toSet());
//
//							LOG.debug("layoutBelowLeftRight:: pivotNode={} sorted candidates for below: {}", pivotNode, candidates);
//							LOG.debug("layoutBelowLeftRight:: placing below to {} all remaining below:: {}", direction, candidates);
//
//							layoutGraph.placeEdgesToNeighboursBelow(cls, node, candidates, rule, pivotNode, direction, edgeAnalyzer);
//
//						}
//					}
//				} else {
//					
//					Set<Node> candidates = edgeAnalyzer.getEdgesForPosition(Place.BELOW);
//					candidates = candidates.stream()
//							.filter(includeNodes::contains)
//							.sorted(Comparator.comparing(n -> apiGraph.getOutboundNeighbours(n).size()))
//							.collect(toSet());
//
//					layoutGraph.placeEdgesToNeighboursBelow(cls, node, candidates, rule, nodeBelow, direction, edgeAnalyzer);
//				}
//			}
//		}	
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutBelowLeftRight_old(Node node, ClassEntity cls, List<Node> includeNodes, EdgeAnalyzer edgeAnalyzer) {

		Node nodeBelow = layoutGraph.getNearestBelow(node);
		
		LOG.debug("layoutBelowLeftRight:: node={} nodeBelow={}", node, nodeBelow);

		if(nodeBelow==null) {
			Place direction = Place.BELOW;
			Set<Node> candidates = edgeAnalyzer.getEdgesForPosition(direction);
			
			candidates = candidates.stream()
					.sorted(Comparator.comparing(n -> apiGraph.getOutboundNeighbours(n).size()))
					.filter(includeNodes::contains)
					.collect(toSet());

			LOG.debug("layoutBelowLeftRight:: sorted candidates for below: node={} candidates={}", node, candidates);
			LOG.debug("layoutBelowLeftRight:: placing below to {} all remaining below:: {}", direction, candidates);
			
			String rule = "General below rule";

		    for( Node toNode: candidates) {
		    	layoutGraph.placeEdges(cls, node, toNode, direction, rule); 
		    }

		    return;
		}

		Set<Node> inboundToNodeBelow = apiGraph.getInboundNeighbours(nodeBelow);

		List<Node> nodesBelow = inboundToNodeBelow.stream()
				.map(n -> apiGraph.getOutboundNeighbours(n))
				.flatMap(Set::stream)
				.filter(n -> layoutGraph.isPlaced(n) && layoutGraph.isPlacedAt(n,Place.BELOW))
				.filter(n -> !n.equals(node))
				.distinct()
				.collect(toList());

		Optional<Node> someNodePlacedRightOrLeft = nodesBelow.stream()
				.filter(n -> layoutGraph.isPlacedAt(n,Place.LEFT) || layoutGraph.isPlacedAt(n,Place.RIGHT) )
				.findFirst();

		boolean someEdgeFromRight = nodesBelow.stream()
				.map(n -> apiGraph.getInboundNeighbours(n))
				.flatMap(Set::stream)
				.map(n -> layoutGraph.getPosition(n))
				.anyMatch(n -> n.getX() >= layoutGraph.getPosition(node).getX());

		boolean someEdgeFromLeft = nodesBelow.stream()
				.map(n -> apiGraph.getInboundNeighbours(n))
				.flatMap(Set::stream)
				.map(n -> layoutGraph.getPosition(n))
				.anyMatch(n -> n.getX() < layoutGraph.getPosition(node).getX());

		LOG.debug("layoutBelowLeftRight:: node={} place below: nodesBelow={}", node, nodesBelow);
		LOG.debug("layoutBelowLeftRight:: node={} place below: someNodePlacedRightOrLeft={}", node, someNodePlacedRightOrLeft);


		List<Place> directions = new LinkedList<>();
		if(someEdgeFromRight && !someEdgeFromLeft) {
			directions.add(Place.LEFT);
		} else {
			if(!someEdgeFromRight && someEdgeFromLeft) {
				directions.add(Place.RIGHT);
			} else {
				directions.add(Place.RIGHT);
				directions.add(Place.LEFT);
			}
		}

		if(someNodePlacedRightOrLeft.isPresent()) {
			for( Place direction : directions ) {
				Set<Node> placedInDirection = Utils.intersection(layoutGraph.getPlacedAt(node,Place.getMapping().get(direction)),inboundToNodeBelow);
				placedInDirection.remove(node);

				String rule = "General below rule - direction to " + direction;

				LOG.debug("layoutBelowLeftRight:: processing rule: {} placedInDirection: {}", rule, placedInDirection);

				boolean placeToDirection = placedInDirection.isEmpty();
				if(placeToDirection) {
					// check placement on the left side, find the ones that have been placed to right of others
					List<Node> pivot = layoutGraph.getAllRightLeftOf(nodeBelow, direction);

					Optional<Node> directConnection = pivot.stream().filter(n -> layoutGraph.hasDirectConnection(nodeBelow,n)).findFirst();
					if(!directConnection.isPresent()) {
						pivot.add(nodeBelow);
						Node pivotNode = layoutGraph.getEdgeBoundary(pivot,direction);

						Set<Node> candidates = edgeAnalyzer.getEdgesForPosition(Place.BELOW);
						if(!candidates.isEmpty()) {
							LOG.debug("layoutBelowLeftRight:: pivotNode={} candidates for below: {}", pivotNode, candidates);

							candidates = candidates.stream()
									.sorted(Comparator.comparing(n -> apiGraph.getOutboundNeighbours(n).size()))
									.filter(includeNodes::contains)
									.collect(toSet());

							LOG.debug("layoutBelowLeftRight:: pivotNode={} sorted candidates for below: {}", pivotNode, candidates);
							LOG.debug("layoutBelowLeftRight:: placing below to {} all remaining below:: {}", direction, candidates);

							layoutGraph.placeEdgesToNeighboursBelow(cls, node, candidates, rule, pivotNode, direction, edgeAnalyzer);

						}
					}
				}
			}
		}		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutUnbalancedAboveBelow(Node node, ClassEntity cls, List<Node> includeNodes, EdgeAnalyzer edgeAnalyzer) {

		// if(apiGraph.isNodeInCircles(node)) return;
		
		List<Node> neighbours = edgeAnalyzer.getEdgesForPosition(Place.ABOVE).stream().collect(toList());
		
		Predicate<Node> isPlaced = layoutGraph::isPlaced;
				
		boolean remainingAboveCandidates = neighbours.stream().anyMatch(isPlaced.negate());
					
		LOG.debug("layoutUnbalancedAboveBelow: node={} check for unbalance above / below - remainingAboveCandidates={}", node, remainingAboveCandidates);
		
		int unbalance = layoutGraph.currentlyPlacedAtLevel(node,+1)-layoutGraph.currentlyPlacedAtLevel(node,-1);
		
		LOG.debug("layoutUnbalancedAboveBelow:: node={} unbalance={} above={} below={}", node, unbalance, 
				  layoutGraph.currentlyPlacedAtLevel(node,+1), layoutGraph.currentlyPlacedAtLevel(node,-1));

		if(remainingAboveCandidates) return;
		if(apiGraph.getNeighbours(node).size()<4) return;

		if(false && unbalance>=0) { // TBD
						
			neighbours = edgeAnalyzer.getEdgesForPosition(Place.BELOW).stream().collect(toList());
			if(!neighbours.isEmpty()) {
				int subset = Math.min(neighbours.size(),Math.abs(unbalance));
				Set<Node> candidates = neighbours.subList(0, subset).stream()
						.filter(includeNodes::contains)
						.collect(toSet());
				
				LOG.debug("layoutUnbalancedAboveBelow:: node={} unbalance={} candidates={}", node, unbalance, candidates);

				String rule = "Unbalance below / above";
				Place func = Place.ABOVE;

				LOG.debug("layoutUnbalancedAboveBelow: unbalanced above/below placeAbove: node={} candidates={}", node, candidates);
				layoutGraph.placeEdgesToNeighbours(cls, node, candidates, rule, func);
			}
		}		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutAbove(Node node, ClassEntity cls, List<Node> includeNodes, EdgeAnalyzer edgeAnalyzer) {
		
		Node nodeAbove = layoutGraph.getNearestAbove(node);
						
		Set<Node> candidates = edgeAnalyzer.getEdgesForPosition(Place.ABOVE);
		
		LOG.debug("layoutAbove: check for above: node={} nodeAbove={}", node, nodeAbove);
		LOG.debug("layoutAbove: check for above: node={} candidates edgeAnalyser={}", node, candidates);

		if(candidates.isEmpty()) return;
				
		if(nodeAbove==null) {
			
			candidates = candidates.stream()
					.filter(includeNodes::contains)
					.sorted(Comparator.comparing(n -> 100 - apiGraph.getOutboundNeighbours(n).size()))
					.collect(toSet());
		
			LOG.debug("layoutAbove: node={} check for above - candidates={}", node, candidates);
			
			String rule = "General above rule";
			Place func = Place.ABOVE;
			
		    candidates.forEach(toNode -> layoutGraph.placeEdges(cls, node, toNode, func, rule) );
			
		} else {
		
			Set<Node> inboundToNodeAbove = apiGraph.getInboundNeighbours(nodeAbove);
	
			LOG.debug("layoutAbove: check for above: node={} inboundToNodeAbove={}", node, inboundToNodeAbove);
			LOG.debug("layoutAbove: check for above: node={} edgeAnalyser={}", node, edgeAnalyzer.getEdgesForPosition(Place.ABOVE));

			inboundToNodeAbove.remove(node);
			
			boolean directConnectionAbove = apiGraph.isConnectionBetweenNodes(inboundToNodeAbove);		
			boolean blockingEdges = layoutGraph.isBlockingEdges(node, nodeAbove);
				
			if(directConnectionAbove || blockingEdges) {
				LOG.debug("layoutAbove: nothing to do with directConnectionAbove={} blockingEdges={}", directConnectionAbove, blockingEdges);
				return ;
			}
			
			Place[] directions = {Place.RIGHT, Place.LEFT};
			for( Place direction : directions ) {
				
				edgeAnalyzer.computeLayout(); 
				
				Set<Node> placedInDirectionFromNode = layoutGraph.getPlacedInReverseDirection(node,direction);
						
				Set<Node> nodes = Utils.intersection(placedInDirectionFromNode,inboundToNodeAbove);
				nodes.remove(node);
	
				boolean placeToDirection = nodes.isEmpty();
				if(placeToDirection) {
					// check placement on the each side, find the ones that have been placed in direction of others
					List<Node> pivot = layoutGraph.getAllInHorisontalDirection(nodeAbove, direction);
	
					LOG.debug("layoutAbove: check for above node={} nodeAbove={} direction={} pivot={}", node, nodeAbove, direction, pivot);
	
					Optional<Node> directConnection = pivot.stream().filter(n -> layoutGraph.hasDirectConnection(nodeAbove,n)).findFirst();
					
					if(!directConnection.isPresent() || apiGraph.isLeafNode(directConnection.get())) {
						LOG.debug("layoutAbove: check for above - no direction connection found with nodeAbove={}", nodeAbove);
						Node pivotNode = layoutGraph.getEdgeBoundary(pivot,direction);
						LOG.debug("layoutAbove: check for above - using pivotNode={}", pivotNode);
	
						candidates = candidates.stream()
								.filter(includeNodes::contains)
								.sorted(Comparator.comparing(this::getSubGraphSize).reversed())
								.collect(toSet());
					
						LOG.debug("layoutAbove: check for above: node={} - candidates={}", node, candidates);
	
						if(!candidates.isEmpty()) {
							String rule = "General above rule - direction: " + direction;
							Place func = Place.ABOVE;
	
							LOG.debug("layoutAbove: placeAbove: node={} candidates={} pivotNode={}", node, candidates, pivotNode);
							layoutGraph.placeEdgesToNeighbours(cls, node, candidates, rule, func, pivotNode, direction, edgeAnalyzer);
							
//							if(directConnection.isPresent()) {
//								String forceRule = "Place below node above";
//								candidates.forEach(candidate -> layoutGraph.placeForced(cls, nodeAbove, candidate, Place.FORCEBELOW, forceRule) );
//							}
	
						}
					}
				}
			}	
		}
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutRight(Node node, ClassEntity cls, List<Node> includeNodes, EdgeAnalyzer edgeAnalyzer, boolean recursive) {

		if(recursive || layoutGraph.isPlacedAt(node,Place.RIGHT)) {
			LOG.debug("layoutRight: node={} check for right - recursive={} isPlacedRight={}", node, recursive, layoutGraph.isPlacedAt(node,Place.RIGHT));
			return;
		}
		
		LOG.debug("layoutRight: node={} check for right - candidates={}", node, edgeAnalyzer.getEdgesForPosition(Place.RIGHT));

//		Set<Node> candidates = edgeAnalyzer.getEdgesForPosition(Place.RIGHT).stream()
//				.filter(includeNodes::contains)
//				.collect(toSet());

		List<Node> candidates = edgeAnalyzer.getEdgesForPosition(Place.RIGHT).stream()
				.filter(includeNodes::contains)
				.sorted(Comparator.comparing(n -> apiGraph.getNeighbours(n).size()))
				.collect(toList());
		
		LOG.debug("layoutRight: node={} check for right - candidates={}", node, candidates);

		if(!candidates.isEmpty()) {			
			Set<Node> candidate = Collections.singleton(candidates.get(0));

			String rule = "General right rule";
			Place func = Place.RIGHT;

			LOG.debug("layoutRight: placeRIGHT: node={} candidates={}", node, candidates);

			LOG.debug("layoutRight: placeRIGHT: node={} isPlaced RIGHT = {}", node, layoutGraph.getPlacedAt(node,Place.RIGHT));
			LOG.debug("layoutRight: placeRIGHT: node={} isPlaced LEFT = {}", node, layoutGraph.getPlacedAt(node,Place.LEFT));

			layoutGraph.placeEdgesToNeighbours(cls, node, candidate, rule, func);

		}
	
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutLeft(Node node, ClassEntity cls, List<Node> includeNodes, EdgeAnalyzer edgeAnalyzer) {

		LOG.debug("layoutLeft: check for left: node={} includeNodes={}", node, includeNodes);

		if(layoutGraph.isPlacedAt(node, Place.LEFT)) {
			
			LOG.debug("layoutLeft: check for left: node={} placeLeft={}", node, layoutGraph.getPlacedAt(node, Place.LEFT));

			return;
		}
		
		List<Node> candidates = edgeAnalyzer.getEdgesForPosition(Place.LEFT).stream()
								.filter(includeNodes::contains)
								.sorted(Comparator.comparing(n -> apiGraph.getNeighbours(n).size()))
								.collect(toList());
						
		int balanceIndex = layoutGraph.getBalancedIndex(node,0,-1);

		if(node.getName().contentEquals("QuoteItem"))  {
			LOG.debug("layoutLeft: check for left - node={} candidates={} balanceIndex={}", node, candidates, balanceIndex);
		}
		
		LOG.debug("layoutLeft: check for left - node={} candidates={} balanceIndex={}", node, candidates, balanceIndex);
		LOG.debug("layoutLeft: check for left - node={} candidates={}", node, edgeAnalyzer.getEdgesForPosition(Place.LEFT));

		if(candidates.isEmpty()) return;

		if(balanceIndex<3) { 
			Set<Node> neighbours = Collections.singleton(candidates.iterator().next());  
			String rule = "General left rule";
			Place func = Place.LEFT;

			LOG.debug("layoutLeft: place LEFT: node={} candidates={}", node, candidates);
			layoutGraph.placeEdgesToNeighbours(cls, node, neighbours, rule, func);
		}		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutOutboundEdgesWithPlacedNodes(Node node, ClassEntity cls, List<Node> includeNodes) {

		LOG.debug("layoutOutboundEdgesWithPlacedNodes: check for edges with already placed node={}", node);

		Set<Node> placed = layoutGraph.getPlacedNodes();

		List<Node>candidates = apiGraph.getOutboundNeighbours(node).stream()
				.filter(n -> !n.equals(node))
				.filter(placed::contains)
				.collect(toList());

		LOG.debug("layoutOutboundEdgesWithPlacedNodes: check for edges with already placed node={} candidates={}", node, candidates);

		if(!candidates.isEmpty()) {
			layoutGraph.layoutWithExistingNodes(cls,node,candidates,includeNodes);
		} else {
			LOG.debug("layoutOutboundEdgesWithPlacedNodes node={} : no layout towards placed neighbours", node);
		}

	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutOutboundToPlacedNodes(Node node, ClassEntity cls, List<Node> neighbours, List<Node> includeNodes) {

		List<Node> candidates = neighbours.stream()
				.filter(n -> includeNodes.contains(n) && !layoutGraph.isPlaced(n))
				.map( n -> layoutGraph.hasPlacedSomeOutboundNeighbours(n,node) )
				.filter(Optional::isPresent) 
				.map(Optional::get)
				.distinct().collect(toList());

		LOG.debug("layoutOutboundToPlacedNodes: node={} process neighbours with outbound to placed neighbours: {}", node, candidates);

		final List<Node> cand = candidates;
				
		if(!candidates.isEmpty()) { 
			
			candidates = candidates.stream()
					.sorted(Comparator.comparing(p -> apiGraph.getAdditionalNeighbours(p,cand)))
					.collect(toList());		

			Node prevNodeB=null;

			for( Node n : candidates) {
				Node toNode = n;
				Node nodeA = node;

				List<Node> optB = apiGraph.getOutboundNeighbours(n).stream()
						.filter(p -> !p.equals(nodeA))
						.filter(layoutGraph::isPlaced)
						.collect(toList());

				LOG.debug("layoutOutboundToPlacedNodes: node={} candidate: {} optB: {}", node, n, optB);
				LOG.debug("layoutOutboundToPlacedNodes: node={} nodeA: {}", node, nodeA);
				LOG.debug("layoutOutboundToPlacedNodes: node={} prevNodeB: {}", node, prevNodeB);

				Node nodeB=null;
				if(prevNodeB!=null && optB.contains(prevNodeB))
					nodeB=prevNodeB;
				else if(!optB.isEmpty()) 
					nodeB=optB.iterator().next();

				if(nodeB!=null) { 
					layoutGraph.placeBetween(cls, nodeA, nodeB, toNode);
					layoutGraph.placeBetween(cls, nodeB, nodeA, toNode);
				}
				prevNodeB=nodeB;
			}
		}

	}

	@LogMethod(level=LogLevel.DEBUG)
	private void layoutBetweenAlreadyPlacedNodes(Node node, ClassEntity cls, List<Node> neighbours, List<Node> includeNodes) {

		List<Node> candidates = neighbours.stream()
				.filter(includeNodes::contains)
				.map(n -> layoutGraph.hasAlreadyPlacedNeighbours(n,node) )
				.filter(Optional::isPresent) 
				.map(Optional::get)
				.distinct().collect(toList());

		LOG.debug("layoutBetweenAlreadyPlacedNodes: node={} process neighbours with placed neighbours: {}", node, candidates);

		candidates = candidates.stream()
				.filter( n -> apiGraph.getInboundNeighbours(n).size()==2 && 
				(apiGraph.getOutboundNeighbours(n).isEmpty() ||
				(apiGraph.getOutboundNeighbours(n).size()==1 && 
				apiGraph.isLeafNode(apiGraph.getOutboundNeighbours(n).iterator().next()))))
				.collect(toList());

		LOG.debug("layoutBetweenAlreadyPlacedNodes: node={} process neighbours with placed neighbours: candidates={}", node, candidates);
		LOG.debug("layoutBetweenAlreadyPlacedNodes: node={} candidates={}", node, candidates); 

		if(candidates.size()>1) { // NOTE - maybe only one non-simpe candidates ?
			candidates = candidates.stream()
					.sorted(Comparator.comparing(n -> apiGraph.getOutboundNeighbours(n).size()))
					.collect(toList());

			LOG.debug("layoutBetweenAlreadyPlacedNodes: node={} SORTED candidates={}", node, candidates); 

			List<Node> placedCandidates = new LinkedList<>();
			for( Node n : candidates) {
				Node toNode = n;
				Node nodeA = node;
				Optional<Node> optB = apiGraph.getInboundNeighbours(n).stream().filter(p -> !p.equals(nodeA)).findFirst();

				boolean placed=false;
				boolean d;
				if(optB.isPresent()) {
					Node nodeB = optB.get();
					d = layoutGraph.placeBetween(cls, nodeA, nodeB, toNode);
					placed = d; 
					d = layoutGraph.placeBetween(cls, nodeB, nodeA, toNode);
					placed = placed|| d; 
				}
				if(placed) placedCandidates.add(toNode);
			}

			LOG.debug("layoutBetweenAlreadyPlacedNodes: node={} placed candidates={}", node, placedCandidates); 

//			if(placedCandidates.size()>1) {
//				Iterator<Node> iter = placedCandidates.iterator();
//				Node from = iter.next();
//				String rule = "layoutBetweenAlreadyPlacedNodes: force left right direction";
//
//				LOG.debug("layoutBetweenAlreadyPlacedNodes: node={} placed from={}", node, from); 
//				while(iter.hasNext()) {
//					Node to = iter.next();
//					LOG.debug("layoutBetweenAlreadyPlacedNodes: node={} placed to={}", node, to); 
//
//					cls.addEdge(new HiddenEdge(from, Place.RIGHT, to, rule));
//					from=to;
//				}
//			}

		} else if(isSingleAndSimple(candidates)) {
			Node toNode = candidates.iterator().next();
			Node nodeA = node;
			Optional<Node> optB = apiGraph.getInboundNeighbours(toNode).stream().filter(p -> !p.equals(nodeA)).findFirst();

			if(optB.isPresent()) {
				Node nodeB = optB.get();
				if(layoutGraph.isPlaced(nodeA) && layoutGraph.isPlaced(nodeB)) {
					layoutGraph.placeBetween(cls, nodeA, nodeB, toNode);
					layoutGraph.placeBetween(cls, nodeB, nodeA, toNode);
				} else if(layoutGraph.isPlaced(nodeA) && layoutGraph.isPlaced(toNode)) {
					layoutGraph.placeBetween(cls, nodeA, toNode, nodeB);
					layoutGraph.placeBetween(cls, toNode, nodeA, nodeB);
				} else if(layoutGraph.isPlaced(nodeB) && layoutGraph.isPlaced(toNode)) {
					layoutGraph.placeBetween(cls, nodeB, toNode, nodeA);
					layoutGraph.placeBetween(cls, toNode, nodeB, nodeA);
				}
			}
		}		
		LOG.debug("layoutBetweenAlreadyPlacedNodes: node={} done", node);
	}

	private boolean isSingleAndSimple(List<Node> candidates) {
		return candidates.size()==1 && !apiGraph.isSimple(candidates);
	}


	@LogMethod(level=LogLevel.DEBUG)
	public void addOrphanEnums(Diagram diagram, Node resource) {   
		
  	    Collection<String> allRefs = APIModel.getAllReferenced();
  	    
  	    Collection<String> allOrphanEnums = APIModel.getAllDefinitions().stream()
								  	    		.filter(APIModel::isEnumType)
								  	    		.filter(candidate -> !layoutGraph.isPlaced(candidate))
								  	    		.filter(candidate -> !allRefs.contains(candidate))
								  	    		.collect(toList());
  	    
	    for(String orphanEnum : allOrphanEnums ) {
	    	Node node = this.apiGraph.getNode(orphanEnum);
	    	addOrphanEnum(diagram, resource, node);
	    }
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addOrphanEnum(Diagram diagram, Node resource, Node orphanEnum) {    
  	
		// TBD
		
//    	EnumNode enode = new EnumNode(orphanEnum.getName());
//    	enode.addValues(APIModel.getEnumValues(orphanEnum.getName()));
//    	apiGraph.addEnum(orphanEnum, enode);
//
//    	ClassEntity entity = diagram.getClassEntityForResource(resource.getName());
//    	generateForEnum(entity, enode);
		
	}


	public List<String> getNodePlacement() {
		List<String> res = new LinkedList<>();
		
		List<Integer> ypos = layoutGraph.position.entrySet().stream()
			.map(Map.Entry::getValue)
			.map(Position::getY)
			.sorted()
			.distinct()
			.collect(toList());
		
		for(Integer y : ypos) {
			Map<Node, Position> layer = layoutGraph.position.entrySet().stream()
				.filter(entry -> entry.getValue().getY()==y)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
		                (e1, e2) -> e1, LinkedHashMap::new));
			
			String line = layer.entrySet().stream().map(entry -> entry.getKey() + " (" + entry.getValue().getX() + ")" ).collect(Collectors.joining(" "));

			res.add( "y=" + y + " : " + line );
			
		}
		
		return res;
				
	}

	private int getSubGraphSize(Node node) {
		int res = CoreAPIGraph.getSubGraphWithInheritance(this.apiGraph.getGraph(), node, node).vertexSet().size();
		
		LOG.debug("getSubGraphSize: node={} size={}",  node, res);
		
		return res;
	}


}
