package no.paneon.api.diagram.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.traverse.BreadthFirstIterator;

import no.paneon.api.diagram.puml.AllOfEdge;
import no.paneon.api.diagram.puml.ClassEntity;
import no.paneon.api.diagram.puml.Comment;
import no.paneon.api.diagram.puml.Diagram;
import no.paneon.api.diagram.puml.EdgeEntity;
import no.paneon.api.diagram.puml.EnumEdge;
import no.paneon.api.diagram.puml.EnumEntity;
import no.paneon.api.diagram.puml.ForcedHiddenEdge;
import no.paneon.api.diagram.puml.HiddenEdge;
import no.paneon.api.graph.APIGraph;
import no.paneon.api.graph.AllOf;
import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.EnumNode;
import no.paneon.api.graph.Node;
import no.paneon.api.graph.complexity.GraphAlgorithms;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;
import org.jgrapht.alg.cycle.CycleDetector;


public class LayoutGraph extends Positions {

    static final Logger LOG = LogManager.getLogger(LayoutGraph.class);

	private static final List<Place> funcAboveAbove = Arrays.asList(Place.ABOVE, Place.ABOVE);
	private static final List<Place> funcAboveBelow = Arrays.asList(Place.ABOVE, Place.BELOW);
	private static final List<Place> funcBelowAbove = Arrays.asList(Place.BELOW, Place.ABOVE);
	private static final List<Place> funcBelowBelow = Arrays.asList(Place.BELOW, Place.BELOW);
	private static final List<Place> funcLeftRight  = Arrays.asList(Place.LEFT, Place.RIGHT);
	private static final List<Place> funcRightLeft  = Arrays.asList(Place.RIGHT, Place.LEFT);
    
	private static final List<Place> funcAboveBelowLong = Arrays.asList(Place.ABOVE_LONG, Place.BELOW_LONG);
	private static final List<Place> funcBelowAboveLong = Arrays.asList(Place.BELOW_LONG, Place.ABOVE_LONG);

	String resource;
	 
    List<String> processedSwagger;

	Graph<Node,LayoutEdge> layoutGraph;
	APIGraph apiGraph;

	Node resourceNode;
	
	private Set<Edge> placedEdges;
		
	public LayoutGraph(APIGraph graph) {
		
		this.apiGraph=graph;
		
		this.resource = apiGraph.getResource();
		this.resourceNode = apiGraph.getResourceNode();
		
		this.layoutGraph = GraphTypeBuilder
								.<Node,LayoutEdge> directed().allowingMultipleEdges(true).allowingSelfLoops(true)
								.edgeClass(LayoutEdge.class).buildGraph();
		
		graph.getGraphNodes().forEach(this.layoutGraph::addVertex);
		
		graph.getGraphNodes().forEach(Node::resetPlacement);

		this.placedEdges = new HashSet<>();
				
		this.setPosition(resourceNode);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Node getResourceNode() {
		return this.resourceNode;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getResource() {
		return this.resource;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> extractCoreGraph() {
		return extractCoreGraph(this.getResourceNode());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> extractCoreGraph(Node resource) {
	    List<Node> core = new LinkedList<>();
	    	    	
//	    CycleDetector<Node,Edge> cycleDetector = new CycleDetector<>(apiGraph.getGraph());
//	    
//	    Set<Node> includedInCycles = cycleDetector.findCycles();
//	    
//	    core.addAll( includedInCycles );
//	        	    
//	    LOG.debug("extractCoreGraph: core={}", core);
	    
		Graph<Node,Edge> graph = apiGraph.getGraph(); 
		Set<Node> nonLeafNodes = graph.vertexSet().stream()
									.filter(node -> !graph.outgoingEdgesOf(node).isEmpty())
									.collect(toSet());
		
		core.addAll( nonLeafNodes );
		 	    
	    // check if there is an 'Item' resource not included in the core set
	    String item = resource + "Item";
	   
	    Optional<Node> itemNode = apiGraph.getNodeByName(item);
	    if(itemNode.isPresent()) core.add(itemNode.get());
	    
	    core.remove(resource);
	    core.add(0, resource);
	    
	    LOG.debug("extractCoreGraph: final core={}", core);

	    return core;
	}

	@LogMethod(level=LogLevel.DEBUG)
	boolean forceLeftRight(ClassEntity cls, Place horizonalDirection, Node pivot, Node toNode) {
		boolean res=false;
		// Place place = Place.getMapping().get(horizonalDirection);
		Place place = horizonalDirection;
		// placeAt(toNode,place,pivot);
		placeAt(pivot,place,toNode);	
		String rule="forceLeftRight";
		res = cls.addEdge(new HiddenEdge(pivot,place,toNode,rule));
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean placeEdgesToNeighbours(ClassEntity cls, Node node, Set<Node> placeNodes, String rule, Place direction) {
		return placeEdgesToNeighbours(cls, node, placeNodes, rule, direction, null, Place.LEFT);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean placeEdgesToNeighbours(ClassEntity cls, Node node, Set<Node> placeNodes, String rule, 
			Place placeDirection, Node pivot, Place horizonalDirection) {
		
		return placeEdgesToNeighbours(cls, node, placeNodes, rule, placeDirection, pivot, horizonalDirection, null);
	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean placeEdgesToNeighbours(ClassEntity cls, Node node, Set<Node> placeNodes, String rule, Place placeDirection,
			Node pivot, Place horizonalDirection, EdgeAnalyzer edgeAnalyzer) {
		 	
		boolean res=false;
//	    boolean first=true;

	    LOG.debug("placeEdgesToNeighbours: node={} placeNodes={} rule={} pivot={} placeDirection={} horizonalDirection={}", 
	    		node, placeNodes, rule, pivot, placeDirection, horizonalDirection
	    		);
	    
        Node prev=null;
	    for( Node toNode: placeNodes) {
	        boolean d = placeEdges(cls, node, toNode, placeDirection, rule);
	        res = res || d;
	        if(d && pivot!=null) {
//	            if(first) {
//	                cls.addEdge(new HiddenEdge(toNode, Place.ABOVE, node, rule + " - first"));
//	                first=false;
//	            }
	            forceLeftRight(cls, horizonalDirection, pivot, toNode);
//	            placeAt(toNode, Place.BELOW, pivot);
//	            pivot=toNode;
	            pivot=null;
	        }
	        if(prev!=null) {
	        	// this.position(toNode, prev, horizonalDirection==Place.LEFT ? Place.RIGHT : Place.LEFT);
	        	// this.position(toNode, prev, horizonalDirection);

	        }
            prev=toNode;
	    }
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean placeEdges(ClassEntity cls, Node from, Node to, Place direction, String rule) {
		boolean res=false;
		
    	if(direction.isForced()) {
        	res = placeForced(cls, from, to, direction, rule);
    	} else {
    		
        	if(apiGraph.getNeighbours(from).contains(to)) {
   
        		List<Edge> edges = apiGraph.getEdges(from, to).stream()
        			.filter(edge -> !isEdgePlaced(edge))
        			.filter(edge -> !edge.isEnumEdge())
        			.collect(toList());
        		
        		for(Edge edge : edges) {
        			boolean d = placeEdgeHelper(cls, direction, from, edge, rule);
        			res = res || d;
        		}
        		
	    	}
        	
	    }

		return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean isEdgePlaced(Edge edge) {
		return placedEdges.contains(edge);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean placeEdgeHelper(ClassEntity cls, Place direction, Node from, Edge edge, String rule) {
		
		if(isEdgePlaced(edge)) return false;
				
		Node to = this.apiGraph.getEdgeTarget(edge);
		
		// TO BE UPDATED - KJ
	    placeAt(from, direction, to);
	    // position(from, to, direction);
	    
	    LOG.debug("placeEdgeHelper: from={} to={} direction={}", from, to, direction);
	    
	    if(edge instanceof AllOf) {
	    	cls.addEdge(new AllOfEdge(direction,edge, rule));
	    } else {
	    	cls.addEdge(new EdgeEntity(direction,edge, rule));
	    }
	    addPlacedEdge(edge);
	    
	    return true;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	void addPlacedEdge(Edge edge) {
		placedEdges.add(edge);
	}


	@LogMethod(level=LogLevel.DEBUG)
	boolean placeForced(ClassEntity cls, Node from, Node to, Place direction, String rule) {
		
		Place coreDirection = Place.getCoreDirectionMapping().get(direction);
		
	    placeAt(to, coreDirection, from);
	    placeAt(from, Place.getMapping().get(coreDirection), to);
	    // position(from, to, coreDirection);
	    
	    return cls.addEdge(new HiddenEdge(from, coreDirection, to, rule));
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	void placeAt(Node node, Place direction, Node pivot) {			
		placeAtHelper(node, direction, pivot);
		placeAtHelperReverse(node, direction, pivot);			
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	void placeAtHelper(Node node, Place direction, Node pivot) {	
		LayoutEdge edge = new LayoutEdge(direction, node, pivot);
		layoutGraph.addEdge(node,pivot,edge);
		
	    if(!node.equals(pivot)) position(node, pivot, direction);

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	void placeAtHelperReverse(Node node, Place direction, Node pivot) {	
		LayoutEdge edge = new LayoutEdge(Place.getMapping().get(direction), pivot, node);
		layoutGraph.addEdge(pivot,node,edge);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPlaced(Node node) {				
		return !layoutGraph.outgoingEdgesOf(node).isEmpty() || !layoutGraph.incomingEdgesOf(node).isEmpty(); 			        
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPlaced(String name) {	
		Optional<Node> node = CoreAPIGraph.getNodeByName(this.apiGraph.getGraph(), name);
		return node.isPresent() && isPlaced(node.get());			        
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPlacedAt(Node node, Place direction) {
		return !getPlacedAt(node, direction).isEmpty();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> getPlacedAt(Node node, Place direction) {
		
		List<Node> res = new LinkedList<>();
				
		if(!layoutGraph.containsVertex(node)) {
			return res;
		}
				
		res.addAll( layoutGraph.outgoingEdgesOf(node).stream()
						.filter(edge -> edge.isSameDirection(direction))
						.map(layoutGraph::getEdgeTarget)
						.collect(toList()) );
		
		res.addAll( layoutGraph.incomingEdgesOf(node).stream()
						.filter(edge -> edge.isSameDirection(Place.getReverse(direction)))
						.map(layoutGraph::getEdgeSource)
						.filter(n -> !res.contains(n))
						.collect(toList()) );
						
		res.remove(node);
		
		LOG.debug("getPlacedAt: node={} direction={} res={}",  node, direction, res);
		
		return res;
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	Node getEnumPlacedAt(Node node, Place direction) {
		Node res = null;
		
		List<Node> placed = getPlacedAt(node, direction);	
		Set<EnumNode> enums = apiGraph.getEnumsForNode(node);	

		res = placed.stream()
				.filter(enums::contains)
				.sorted(Comparator.comparing(n -> this.getPosition(n).getX()))
				.reduce((first, second) -> second)
				.orElse(null);				
								
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getPlacedNodes() {
		return layoutGraph.vertexSet().stream().filter(this::isPlaced).distinct().collect(toSet());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Map<Place,Set<Node>> getPlaced(Node node) {
		Map<Place,Set<Node>> res = new EnumMap<>(Place.class);
		
		Set<Place> directions = layoutGraph.outgoingEdgesOf(node).stream()
									.map(LayoutEdge::getDirection)
									.collect(toSet());
									
		directions.stream().forEach(direction -> res.put(direction,  new HashSet<>()));
									
		for(LayoutEdge edge : layoutGraph.outgoingEdgesOf(node)) {
			res.get(edge.getDirection()).add(layoutGraph.getEdgeTarget(edge));
		}

		return res;
	}
	

	@LogMethod(level=LogLevel.DEBUG)
	public boolean layoutEnums(Diagram diagram, ClassEntity cls, Node node, boolean recursive) {
		boolean placed=false;
		
		Predicate<Node> isNotPlaced = n -> !isPlaced(n);
		
		Set<EnumNode> enums = apiGraph.getEnumsForNode(node).stream()
											.filter(isNotPlaced)
											.collect(toSet());
		
    	if(enums.isEmpty()) return false;

	    boolean floatingEnums = Config.getFloatingEnums();

	    if(floatingEnums) {
		    for(EnumNode enode : enums) {	
		    	this.setPlacementFloating(enode);
		    }
		    return true;
	    }
	        	
	    Node current=node;
	    Place direction=Place.RIGHT;
	                    
        if(!isOnlySelfReference(node) && isPlacedAt(node,Place.LEFT) ) {
            
            List<Node> atRight = getAllRightLeftOf(node, Place.RIGHT).stream()
								.sorted(this::compareXPosition)
								.collect(toList());

            List<Node> atLeft = getAllRightLeftOf(node, Place.LEFT).stream()
            					.sorted(this::compareXPosition)
            					.collect(toList());

            if(atRight.size() >= atLeft.size()) {
            	current = atLeft.get(0);
            	direction = Place.LEFT;
            } else {
            	current = atRight.get(0);
            }
        } else if(!isOnlySelfReference(node) ) {
        	direction=Place.LEFT;
        }

	    boolean firstPlaced = true;
	    for(EnumNode enode : enums) {	     
	    	placeEnum(cls, current, enode, direction, firstPlaced);
	    	diagram.addEnum(new EnumEntity(enode)); 
	        current=enode;
	        firstPlaced = false;
	    }
	    
	    return placed;
	}
		
	private boolean isOnlySelfReference(Node node) {
		List<Node> right = getPlacedAt(node, Place.RIGHT);
		right.remove(node);
		return right.isEmpty();
	}

	private int compareXPosition(Node n1, Node n2) {
		return this.getPosition(n1).getX() - this.getPosition(n2).getX();
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean layoutEnums_old(Diagram diagram, ClassEntity cls, Node node, boolean recursive) {
		boolean placed=false;
		
		Set<EnumNode> enumCandidates = apiGraph.getEnumsForNode(node);
		
    	if(enumCandidates==null) return false;

	    boolean floatingEnums = Config.getFloatingEnums();

	    if(floatingEnums) {
		    for(EnumNode enode : enumCandidates) {	
		    	this.setPlacementFloating(enode);
		    }
		    return true;
	    }
	        	
	    Set<EnumNode> enums = enumCandidates.stream()
	    							.filter(e -> !isPlaced(e)) 
	    							.collect(toSet());
	    	    	    
	    Node current=node;
	    Place prevDirection=null;
	    boolean firstPlaced = true;
	    for(EnumNode enode : enums) {	     
	        //
	        // place to right if only one node already to right AND also something to left
	        //
	        int diffX=Integer.MAX_VALUE;
	        if(isPlacedAt(node,Place.RIGHT) && isPlacedAt(node,Place.LEFT) && prevDirection==null) {

	            List<Node> atRight=getRightmostOf(node).stream()
	            		.sorted(Comparator.comparing(n -> -this.getPosition(n).getX()))
	            		.filter(n -> !isFloatingNode(n, node))
	            		.collect(toList());
	            
	            if(!atRight.isEmpty()) {
	            	Node rightMost=atRight.get(0);
	                diffX=getPosition(rightMost).getX() - getPosition(current).getX();
	                if(diffX<=1) current=rightMost;
	            }
	        }

	        if(recursive || !isPlacedAt(current,Place.RIGHT) || (diffX<=1) || prevDirection==Place.RIGHT) {
	        	prevDirection = placeEnum(cls, current, enode, Place.RIGHT, firstPlaced);
	        } else if(!isPlacedAt(current,Place.LEFT) || prevDirection==Place.LEFT) {
	        	prevDirection = placeEnum(cls, current, enode, Place.LEFT, firstPlaced);
	        } else {
	        	prevDirection = placeEnum(cls, current, enode, Place.ABOVE, firstPlaced);		        
		    }
	        
	        diagram.addEnum(new EnumEntity(enode)); 
        	placed=true;
        	firstPlaced=false;
	        current=enode;
	    }
	    
	    return placed;
	}
	
	private Place placeEnum(ClassEntity cls, Node current, EnumNode enode, Place direction, boolean firstPlaced) {
    	cls.addEdge(new EnumEdge(current, direction, enode, firstPlaced));
        placeAt(current,direction,enode);
        // position(current,enode,direction);
        return direction;		
	}


	private Set<Node> floatingPlacement = new HashSet<>(); 

	@LogMethod(level=LogLevel.DEBUG)
	private void setPlacementFloating(Node node) {
		floatingPlacement.add(node);
	}

			
	@LogMethod(level=LogLevel.DEBUG)
	public Optional<Node> hasAlreadyPlacedNeighbours(Node node, Node exclude) {
		Optional<Node> res= Optional.empty();
		
		Predicate<Node> excludeEquals = exclude::equals;

		boolean allInboundPlaced = apiGraph.getInboundNeighbours(node).stream()
									.filter(excludeEquals.negate())
									.allMatch(this::isPlaced);
								
		if(allInboundPlaced) 
			res = Optional.of(node);
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Optional<Node> hasPlacedSomeOutboundNeighbours(Node node, Node exclude) {
		Optional<Node> res= Optional.empty();
		
		Predicate<Node> excludeEquals = exclude::equals;

		boolean allInboundPlaced = apiGraph.getOutboundNeighbours(node).stream()
									.filter(excludeEquals.negate())
									.anyMatch(this::isPlaced);
								
		if(allInboundPlaced) 
			res = Optional.of(node);
		
		return res;
	}
	
    
	@LogMethod(level=LogLevel.DEBUG)
	public boolean layoutWithExistingNodes(ClassEntity cls, Node node, List<Node> neighbours, List<Node> includeNodes) {
		boolean res=false;
						
		for(Node toNode : neighbours) {
			boolean placed = placeWithPlaced(cls, node, toNode);
			res = res || placed;
		}
		
		return res;
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isFloatingNode(String node, String related) {
		boolean res=false;
		Set<String> neighbours=apiGraph.getNeighboursByName(node);
		neighbours.remove(related);
		res=neighbours.size()==1;

		return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isFloatingNode(Node node, Node related) {
		boolean res=false;
		Set<Node> neighbours=apiGraph.getNeighbours(node);
		neighbours.remove(related);
		res=neighbours.size()==1;
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean placeWithPlaced(ClassEntity cls, Node nodeA, Node nodeB) {
		boolean res=false;
		
    	boolean floatingNodeA = isFloatingNode(nodeA, nodeB);
    	
	    if(!isPositionedToLeft(nodeA, nodeB) && !floatingNodeA)  {
	    	Node tmp = nodeA;
	    	nodeA = nodeB;
	    	nodeB = tmp;
	    }
		 
		String rule = "";
		List<Place> func = null;
		
		LOG.debug("placeWithPlaced: nodeA={} nodeB={} floatingNodeA={}",  nodeA, nodeB, floatingNodeA);
		
		if(floatingNodeA) {
			
			if(nodeA.inheritsFrom(apiGraph.getGraph(),nodeB)) {
				rule = "inheritance nodeA -> nodeB";
				func = funcAboveBelow;
				
			} else if(nodeB.inheritsFrom(apiGraph.getGraph(),nodeA)) {
				rule = "inheritance nodeB -> nodeA";
				func = funcBelowAbove;
				
			} else {
				
				rule = "general floating nodeA";

				if(!isPlacedAt(nodeB,Place.RIGHT)) {
					func = funcRightLeft;
				} else if(!isPlacedAt(nodeB,Place.LEFT)) {
					func = funcLeftRight;
				} else {
					if(isPlacedEnumAt(nodeB,Place.RIGHT)) {
	    				func = funcRightLeft;
					} else if(isPlacedEnumAt(nodeB,Place.LEFT)) {
	    				func = funcLeftRight;
					} else {
						func = funcBelowAbove;
					}
				}
			}
			
		} else if(isAtSameColumn(nodeA, nodeB)) { 
	    	
			if(isPositionedAbove(nodeA, nodeB)) { 
	    		rule="P01-1";
	    		func = funcBelowAboveLong;
	    		// func = funcAboveBelowLong;
	    		
	    	} else if(isPositionedBelow(nodeA, nodeB)) {
	    		rule="P01-2";
	    		func = funcAboveBelowLong;
	    		// func = funcBelowAboveLong;

	    	} else {
	    		LOG.debug("ERROR: nodeA and nodeB in same position - should NOT happen");
	    		LOG.debug("ERROR:    nodeA={}", nodeA);
	    		LOG.debug("ERROR:    nodeB={}", nodeB);
	    	}
	    	
	    } else {
	    	if(isPositionedAbove(nodeA, nodeB)) { 
	    		rule="P02-1";
	    		func = funcBelowAbove; 

	    	} else if(isPositionedBelow(nodeA, nodeB)) {
	    		rule="P02-2";
	    		func = funcAboveBelow; 
	    		
	    	} else {
	    		
	    		rule="placeWithPlaced P02-3";

	     			
	    		Position posA=getPosition(nodeA);
	    		
	    		int inboundFromSameLevel = apiGraph.getInboundNeighbours(nodeB).stream()
	    									.map(this::getPosition)
	    									.filter(p -> p.getY() == posA.getY())
	    									.mapToInt(e -> 1)
	    									.sum();
	    		
	            LOG.debug("placeWithPlaced #2-3 inboundFromSameLevel={}", inboundFromSameLevel );

	    		switch(inboundFromSameLevel) {
	    		case 2:
	    			if(!isPlacedAt(nodeA,Place.LEFT) && !isPlacedAt(nodeB,Place.RIGHT)) {
	    				func = funcRightLeft;
	    			} else if(!isPlacedAt(nodeB,Place.LEFT) && !isPlacedAt(nodeA,Place.RIGHT)) {
	    				func = funcLeftRight;
	    			} else {
				        func = funcBelowAbove;
	    			}
	    			break;
	    			
	    		case 1:
		    		Optional<Node> optCommon = apiGraph.getCommonPathEnd(nodeA,nodeB);
		    		
			        func = inboundFromSameLevel>1 ? funcBelowAbove: funcRightLeft;
			        
			        if(isPlacedAt(nodeA,Place.LEFT) && isPlacedAt(nodeA,Place.RIGHT)) func = funcAboveBelow;
			        if(isPlacedAt(nodeA,Place.LEFT) && !isPlacedAt(nodeA,Place.RIGHT)) func = funcLeftRight; 
			        if(!isPlacedAt(nodeA,Place.LEFT) && isPlacedAt(nodeA,Place.RIGHT)) func = funcRightLeft; 
			        if(!isPlacedAt(nodeA,Place.LEFT) && !isPlacedAt(nodeA,Place.RIGHT)) func = funcRightLeft; 

		        	long countAtLevel = currentlyPlacedAtLevel(nodeA);
		        	
		            func = countAtLevel<=4 ? func : funcBelowAbove;
		            LOG.debug("placeWithPlaced #2-3 countAtLevel=" + countAtLevel + " optCommon=" + optCommon);
		            break;
		         
		        default:
			        func = funcBelowAbove;
	    			break;
	    		}
	    			    		
	    	}

	    }
	    
//	    if(func!=null) {
//    		if(isPlacedEnumAt(nodeB,func.get(0))) {
//    			rule = "isPlacedEnum for node=" + nodeB;
//    			Node enumNode=getEnumPlacedAt(nodeB,func.get(0));    			
//            	cls.addEdge(new ForcedHiddenEdge(enumNode, func.get(0), nodeA, rule));
//    		}
//    		res = placeEdgePackage(cls,nodeA,nodeB,func,rule);
//	    	cls.addComment(new Comment("' finished with " + rule));
//    		LOG.debug("rule: {} finished", rule);
//	    }
		
	    if(func!=null) {
//    		if(isPlacedEnumAt(nodeA,func.get(0))) {
//    			rule = "isPlacedEnum for node=" + nodeA;
//    			Node enumNode=getEnumPlacedAt(nodeA,func.get(0));    			
//            	cls.addEdge(new ForcedHiddenEdge(enumNode, func.get(0), nodeA, rule));
//    		}
    		res = placeEdgePackage(cls,nodeA,nodeB,func,rule);
	    	cls.addComment(new Comment("' finished with " + rule));
    		LOG.debug("rule: {} finished", rule);
	    }
	   
		return res;
		
	}
	
	

	@LogMethod(level=LogLevel.DEBUG)
	private boolean isPlacedEnumAt(Node node, Place direction) {
		boolean res = false;
		
		List<Node> placed = getPlacedAt(node, direction);	
		Set<EnumNode> enums = apiGraph.getEnumsForNode(node);

		res = placed.stream().anyMatch(enums::contains);
		
        return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean placeBetween(ClassEntity cls, Node nodeA, Node nodeB, Node toNode) {
		boolean res=false;
		
	    if(!isPositionedToLeft(nodeA, nodeB))  {
	    	Node tmp = nodeA;
	    	nodeA = nodeB;
	    	nodeB = tmp;
	    }

	    boolean multipleBetween = apiGraph.isMultipleBetween(nodeA, nodeB);
	    boolean placedBetween = isPlacedBetween(nodeA, nodeB);
	    boolean directConnection = hasDirectConnection(nodeA, nodeB);
	    
	    Set<Node> outbound = apiGraph.getOutboundNeighbours(toNode);
	    outbound.remove(nodeA);
	    outbound.remove(nodeB);
	    boolean hasOutbound = outbound.isEmpty();
	    
	    if(isPlaced(toNode)) {
	    	return placeBetweenToNodeAlreadyPlaced(cls, nodeA, nodeB, toNode);
	    }

	    if(multipleBetween) {
	    	Set<Node> between = apiGraph.getBetween(nodeA, nodeB);
	    	between.remove(toNode);
	    	if(between.size()==1) {
	    		Node otherBetween = between.iterator().next();
	    		if( isAtSameLevel(nodeA,otherBetween) || isAtSameLevel(nodeB,otherBetween)) multipleBetween=false;
	    	}
	    }
	    
	    String rule = "placeBetween - " + nodeA + " < " + nodeB + " placing " + toNode + 
	    			  " multipleBetween=" + multipleBetween + " placedBetween=" + placedBetween + " directConnection=" + directConnection;

    	LOG.debug("nodeA = {} posA={}", nodeA, this.getPosition(nodeA));
    	LOG.debug("nodeB = {} posB={}", nodeB, this.getPosition(nodeB));

        List<Place> func;
        
	    if(!placedBetween && !directConnection ) {
	        // nothing between
	        if(isAtSameLevel(nodeA,nodeB)) {
	            // just right-left as nothing between and the two nodes are at the same level
	        	// place between if multiple nodes between, otherwise vertically
	        	String ruleDetails = rule + " !between && sameLevel";

	        	long countAtLevel = currentlyPlacedAtLevel(nodeA);
	        	boolean horizontal = countAtLevel<=4 && (multipleBetween || hasOutbound);
	        	
	            func = horizontal ? funcRightLeft : funcBelowAbove;
	            LOG.debug("placebetween #1-1 countAtLevel={} multipleBetween={} hasOutbound={} horizontal={}", countAtLevel, multipleBetween, hasOutbound, horizontal);
	            res = placeEdgePackage(cls, nodeA,toNode,func,ruleDetails);

	            func = horizontal ? funcLeftRight : funcBelowAbove;
	            LOG.debug("placebetween #1-2");
	            boolean placed = placeEdgePackage(cls, nodeB,toNode,func,ruleDetails);
	            res = res || placed;
	            
	        } else {
	        	String ruleDetails = rule + " !between && !sameLevel";

	            func = !this.isPlacedAt(nodeB, Place.BELOW) ? funcRightLeft : 
	            			(isPositionedAbove(nodeA,nodeB) ? funcBelowAbove : funcAboveBelow);
	        	LOG.debug("placebetween #2-1");
	        	res = placeEdgePackage(cls, nodeA,toNode,func,ruleDetails);

	            func = this.isPlacedAt(nodeB, Place.BELOW) ? funcLeftRight : 
        				  (isPositionedAbove(nodeA,nodeB) ? funcBelowAbove : funcAboveBelow);
	            LOG.debug("placebetween #2-2");
	            boolean placed = placeEdgePackage(cls, toNode,nodeB,func,ruleDetails);
	            res = res || placed;
	        }
	    } else {
	        // something between
	        if(isAtSameLevel(nodeA,nodeB)) {
	            // something already between and the two nodes are at the same level
	        	// place below unless there is also another path between
	        	
	        	
	        	boolean anotherPath = isPath(nodeA, nodeB, Arrays.asList(toNode));
	        	
	        	LOG.debug("anotherPath = {}", anotherPath);

	        	String ruleDetails = rule + " between && sameLevel";

	        	if(!anotherPath) {
	        		func = funcAboveBelow;
	        	} else {
	        		func = funcBelowAbove;
	        	}
	        	
	        	LOG.debug("not placebetween #3-1: rule={}", rule);
	        	res = placeEdgePackage(cls, nodeA,toNode,func,ruleDetails);
	        	
	            LOG.debug("not placebetween #3-2: rule={}", rule);
	            boolean placed = placeEdgePackage(cls, nodeB,toNode,func,ruleDetails);
	            res = res || placed;

	        } else {
	            // the nodeA and nodeB are not at the same level
	        	String ruleDetails = rule + " between && !sameLevel";
	        		        	
//	        	if(multipleBetween || isPlacedAt(nodeA,Place.RIGHT) || isPlacedAt(nodeA,Place.LEFT) ) {
//		            func = isPositionedAbove(nodeA, nodeB) ? funcBelowAbove : funcAboveBelow; // IS THIS CORRECT ?
//	        	} else {
//	        		func = funcRightLeft;
//	        	}
	        	if(multipleBetween) {
		            func = isPositionedAbove(nodeA, nodeB) ? funcBelowAbove : funcAboveBelow; 
	        	} else if(isPositionedAbove(nodeA, nodeB)) {
		            func = funcBelowAbove; 
	        	} else {
	        		if(isPlacedAt(nodeA,Place.LEFT) && !isPlacedAt(nodeA,Place.RIGHT))
	        			func = funcRightLeft;
	        		else if(!isPlacedAt(nodeA,Place.LEFT) && isPlacedAt(nodeA,Place.RIGHT))
	        			func = funcLeftRight;
	        		else
			            func = funcBelowAbove; 

	        	}

	            LOG.debug("not placebetween #4-1");	   
	                     		        
	            res = placeEdgePackage(cls, nodeA,toNode,func,ruleDetails);

	            LOG.debug("not placebetween #4-1: res={}", res);	   

	            if(res && !multipleBetween) {
	                Node finalA = nodeA;
		            Set<Node> candidates = apiGraph.getInboundNeighbours(finalA).stream()
		            							.map(apiGraph::getOutboundNeighbours)
		            							.flatMap(Set::stream)
		            							.filter(n -> !n.equals(toNode) && !n.equals(finalA))
		            							.filter(n -> placedAtLevel(finalA).contains(n))
		            							.collect(toSet());
		            
		   		    LOG.debug("not placebetween #4-1: candidates={}", candidates);

		   		    
	            }
	            
	            // func = isPositionedAbove(nodeA, nodeB) ? funcAboveBelow : funcBelowAbove;
	            
	        	if(multipleBetween) {
		            func = isPositionedAbove(nodeA, nodeB) ? funcBelowAbove : funcAboveBelow; 
	        	} else if(isPositionedAbove(nodeA, nodeB)) {
		            // func = funcRightLeft; 
	        		if(isPlacedAt(nodeB,Place.LEFT) && !isPlacedAt(nodeB,Place.RIGHT))
	        			func = funcRightLeft;
	        		else if(!isPlacedAt(nodeB,Place.LEFT))
	        			func = funcLeftRight;
	        		else
			            func = funcBelowAbove; 

	        	} else {
	        		func = funcAboveBelow;
	        	}
	        	
	        	ruleDetails = ruleDetails + "#4-2";
           
				LOG.debug("not placebetween #4-2");		
				boolean placed = placeEdgePackage(cls, nodeB,toNode,func,ruleDetails);
	            res = res || placed;

	        }
	    }
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean placeBetweenToNodeAlreadyPlaced(ClassEntity cls, Node nodeA, Node nodeB, Node toNode) {
		boolean res=false;

		boolean placed;
	    placed = placeEdgesBetweenNodesAlreadyPlaced(cls, toNode,nodeA, nodeB);
	    res = placed;
	    
	    placed = placeEdgesBetweenNodesAlreadyPlaced(cls, toNode,nodeB, nodeA);
	    res = res || placed;
	    
		return res;
	}
	

	@LogMethod(level=LogLevel.DEBUG)
	boolean placeEdgesBetweenNodesAlreadyPlaced(ClassEntity cls, Node nodeA, Node nodeB, Node nodeC) {
	    boolean res=false;
		List<Place> func;

	    boolean multipleBetween = apiGraph.isMultipleBetween(nodeB, nodeC);

	    if(!isPositionedToLeft(nodeA, nodeB))  {
	    	Node tmp = nodeA;
	    	nodeA = nodeB;
	    	nodeB = tmp;
	    }
	    
	    LOG.debug("placeEdgesBetweenNodesAlreadyPlaced: nodeA={} nodeB={} nodeC={}", nodeA, nodeB, nodeC);
	
	    String rule = "placeEdgesBetweenNodesAlreadyPlaced - " + nodeA + " < " + nodeB ;
	
	    if(!multipleBetween && !isPlacedBetween(nodeA,nodeB) && isAtSameLevel(nodeA,nodeB)) {
	        // at same level and nothing in between => left right
	        // if already some node to left, we place to right
//	        List<Node> alreadyPlacedToLeftOfNodeA = getPlacedAt(nodeA,Place.RIGHT);
//	        List<Node> alreadyPlacedToRightOfNodeA = getPlacedAt(nodeA,Place.LEFT);
	    	// TEST 
	        List<Node> alreadyPlacedToLeftOfNodeA = getPlacedAt(nodeA,Place.LEFT);
	        List<Node> alreadyPlacedToRightOfNodeA = getPlacedAt(nodeA,Place.RIGHT);

	        Set<Node> othersAtLeft = Utils.difference(alreadyPlacedToLeftOfNodeA, Arrays.asList(nodeA,nodeB)); 
	        Set<Node> othersAtRight = Utils.difference(alreadyPlacedToRightOfNodeA, Arrays.asList(nodeA,nodeB)); 
	
	        if(othersAtLeft.isEmpty()) {
	            func = funcRightLeft; 
	            LOG.debug("placeEdgesBetweenNodesAlreadyPlaced #1-1");
	        } else if(othersAtRight.isEmpty()) {
	        	func = funcLeftRight; 
	            LOG.debug("placeEdgesBetweenNodesAlreadyPlaced #1-2");
	        } else {
	        	func = funcBelowAbove;
	            LOG.debug("placeEdgesBetweenNodesAlreadyPlaced #1-3");
	        }
	        res = placeEdgePackage(cls,nodeA,nodeB,func,rule);
	        
	    } else if(isAtSameLevel(nodeA,nodeB)) {
	        //
	        // possibly same level, but not placing left-right
	        // 
	        boolean nodeCisAbove = isPositionedAbove(nodeC,nodeA) || isPositionedAbove(nodeC,nodeB);
	        func = nodeCisAbove ? funcAboveBelow : funcBelowAbove;
	        LOG.debug("placeEdgesBetweenNodesAlreadyPlaced #3-1");
	        res = placeEdgePackage(cls, nodeA, nodeB, func, rule);
	        
	    } else {
	        //
	        // not at same level - above below 
	        // 
	        func = isPositionedAbove(nodeA,nodeB) ? funcBelowAbove : funcAboveBelow;
	        		
	        LOG.debug("placeEdgesBetweenNodesAlreadyPlaced #2-1");
	        res = placeEdgePackage(cls, nodeA,nodeB,func,rule);

	    }
	
	    return res;
	}

	
	@LogMethod(level=LogLevel.DEBUG)
	boolean placeEdgePackage(ClassEntity cls, Node nodeA, Node nodeB, List<Place> func, String rule) {
	   boolean res = false;
	   
	   LOG.debug("placeEdgePackage: nodeA={} nodeB={} func={} rule={}", nodeA, nodeB, func, rule);
	   
	   res = placeEdges(cls, nodeA, nodeB, func.get(0), rule);
	   
	   boolean placed = placeEdges(cls, nodeB, nodeA, func.get(1), rule);
	   res = res || placed;
	   
	   return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isPlacedBetween(Node nodeA, Node nodeB) {
	    LOG.trace("isPlacedBetween: nodeA={} nodeB={}", nodeA, nodeB);
	    Set<Node> rightA = new HashSet<>(getPlacedAt(nodeA,Place.LEFT));
	    rightA.removeAll(Arrays.asList(nodeA,nodeB));
	    
	    Set<Node> leftB = new HashSet<>(getPlacedAt(nodeB,Place.RIGHT));
	    leftB.removeAll(Arrays.asList(nodeA,nodeB));

	    boolean isBetween = !rightA.isEmpty() || !leftB.isEmpty();

	    LOG.trace("isPlacedBetween: {}", isBetween);
	    return isBetween;
	}
		
	
	@LogMethod(level=LogLevel.DEBUG)
	public Node getNearestAbove(Node node) {
	    Node res=null;
	    Position closestPosition=null;

	    List<Node> candidates = getPlacedAt(node,Place.ABOVE);
	    if(candidates.contains(node)) candidates.remove(node);
	    
	    for(Node candidate : candidates) {
	        Position pos=getPosition(candidate);
	        if(closestPosition==null || pos.getY()>closestPosition.getY()) {
	            closestPosition=pos;
	            res=candidate;
	        }
	    }
	    return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Node getEdgeBoundary(List<Node> nodes, Place direction) {
	    Node res=null;
	    Position currentPosition=null;
	    
	    for( Node node : nodes) {
	        Position pos=getPosition(node);
	        if(currentPosition==null) {
	            currentPosition=pos;
	            res = node;
	        } else {
	        	switch(direction) {
	        	case LEFT:
		        	if(pos.getX()<currentPosition.getX()) {
			            currentPosition=pos;
			            res = node;
			        }
	        		break;
	        		
	        	case RIGHT:
		        	if(pos.getX()>currentPosition.getX()) {
			            currentPosition=pos;
			            res = node;
			        }
	        		break;
	        		
	        	// following two should never happen	
	        	case ABOVE:
	        		break; 
	        	case BELOW:
	        		break;
	        	default:
	        	}
	        	
	        }
	    }
	    return res;
	}	
	
	@LogMethod(level=LogLevel.DEBUG)
	public Node getNearestBelow(Node node) {
	    Node res = null;
	    Position closestPosition = null;

	    List<Node> candidates = getPlacedAt(node,Place.BELOW);
	    if(candidates.contains(node)) candidates.remove(node);
	    
	    LOG.debug("getNearestBelow: node={} :: candidates={}", node, candidates);

	    for( Node p : candidates) {
	        LOG.debug("getNearestBelow: node={} p={}", node, p);
	        Position pos=getPosition(p);
	        if(closestPosition==null || pos.getY()<closestPosition.getY()) {
	            closestPosition=pos;
	            res=p;
	        }
	    }
	    
	    LOG.debug("getNearestBelow: node={} :: res={}", node, res);

	    return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean placeEdgesToNeighboursBelow(ClassEntity cls, Node node, Set<Node> neighbours, String rule, EdgeAnalyzer edgeAnalyzer) {
		return 	placeEdgesToNeighboursBelow(cls, node, neighbours, rule, null, Place.LEFT, edgeAnalyzer);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean placeEdgesToNeighboursBelow(ClassEntity cls, Node node, Set<Node> neighbours, String rule) {
		return 	placeEdgesToNeighboursBelow(cls, node, neighbours, rule, null, Place.LEFT);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean placeEdgesToNeighboursBelow(ClassEntity cls, Node node, Set<Node> neighbours, String rule, Node pivot, Place horizonalDirection) {
		return placeEdgesToNeighboursBelow(cls, node, neighbours, rule, pivot, horizonalDirection, null);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean placeEdgesToNeighboursBelow(ClassEntity cls, Node node, Set<Node> neighbours, String rule, Node pivot, 
					Place horizonalDirection, EdgeAnalyzer edgeAnalyzer) {

		boolean res=false;
	    boolean first=true;
	    Node placed=pivot;
	    Place direction = Place.BELOW;
	    
	    if(pivot!=null && !isPlaced(pivot)) {
	        LOG.debug("placeEdgesToNeighboursBelow: pivot={} not placed - ERROR", pivot);
	        pivot=null;
	    }
	
        LOG.debug("placeEdgesToNeighboursBelow: node={} pivot={} neighbours={} edgeAnalyzer={}", node, pivot, neighbours, edgeAnalyzer);

	    for(Node toNode : neighbours) {
	        LOG.debug("placeEdgesToNeighboursBelow: node={} toNode={} pivot={} horizonalDirection={}", node, toNode, pivot, horizonalDirection);
	  
			Place effectiveDirection = getDirection(node, toNode, Optional.empty(), direction, edgeAnalyzer);
			
	        boolean d = placeEdges( cls, node, toNode, effectiveDirection, rule);
        	res = res || d;
	        
	        LOG.debug("placeEdgesToNeighboursBelow: node={} toNode={} d={} direction={} effectiveDirection={}", node, toNode, d, direction, effectiveDirection);

	        if(d) {
//        		if(first && placed==null) {
//        			// positionBelow(node,toNode);
//        		} else 
        			
	        	if(!first && placed!=null) {
	        		if(horizonalDirection==Place.LEFT) {
	                	positionToLeft(placed, toNode);
	                } else {
	                	positionToRight(placed,toNode);
	                	
	                	LOG.debug("positionToRight: placed={} toNode={}", placed, toNode);
	                }
	        	}
	             	          
	            if(pivot!=null) {
//	                if(first) {
//	                	cls.addEdge(new HiddenEdge(node, Place.BELOW, toNode, rule));
//	                } 
	                
	            	if(!first) {
		                Place placeDirection = Place.reverse(horizonalDirection);
		                if(isPlacedAt(node, horizonalDirection) && !isPlacedEnumAt(node, horizonalDirection)) {
		                	rule = rule + " place in direction " + placeDirection + " of node=" + node;
		                	res = cls.addEdge(new ForcedHiddenEdge(toNode, placeDirection, placed, rule));
		                } else {
		                	rule = rule + " - not placed " + horizonalDirection + " pivot: " + pivot;
		                	res = cls.addEdge(new HiddenEdge(toNode, placeDirection, placed, rule));
		                }
		                
	                	placeAt(placed,placeDirection,toNode);
	            	}
	                
	            } 
	            
                placed = toNode;
                first=false;	  
	        }
	    }
	    return res;
	}
		

	@LogMethod
	public boolean isBlockingEdges(Node node, Node nodeAbove) {

		if(nodeAbove==null) return false;
		
		Set<Node> outboundFromNodeAbove = apiGraph.getOutboundNeighbours(nodeAbove);
		Set<Node> edgesWithNode = apiGraph.getInboundNeighbours(node);
		
		edgesWithNode.addAll(apiGraph.getOutboundNeighbours(node));
		
		Set<Node> common = Utils.intersection(outboundFromNodeAbove, edgesWithNode);
		common.remove(node);
		common.remove(nodeAbove);
		
		boolean circlePath = common.stream().allMatch(apiGraph::isCirclePath);
		boolean blockingEdges = !common.isEmpty() && !circlePath;
			
		return false; // ; blockingEdges;
	}

	@LogMethod
	public Set<Node> getPlacedInReverseDirection(Node node, Place direction) {
		Set<Node> res = new HashSet<>();
		
		res.addAll( getPlacedAt(node, Place.getMapping().get(direction)));  

		return res;
	}

	@LogMethod
	public List<Node> getAllInHorisontalDirection(Node nodeAbove, Place direction) {
		return getAllRightLeftOf(nodeAbove, direction);
	}

	@LogMethod
	public String toString() {
		return this.layoutGraph.toString();
	}

	public APIGraph getAPIGraph() {
		return this.apiGraph;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public int getInboundEdgesFromPlaced(Node node) {
		return apiGraph.getInboundNeighbours(node).stream()
				.filter(this::isPlaced)
				.collect(Collectors.reducing(0, e -> 1, Integer::sum));
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPath(Node from, Node to, List<Node> exclude) {
		return isPath(from, to, exclude, new ArrayList<>(), true);
	}
	

	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isPath(Node from, Node to, List<Node> exclude, List<Node> seen, boolean first) {
	
	    LOG.debug("isPath: from={} to={} exclude={} seen={} first={}", from, to, exclude, seen, first);

		if(exclude.contains(to) || to.equals(from) || seen.contains(from)) {
			return false;
		}
		
	    seen.add(from);

	    Optional<Node> res = apiGraph.getOutboundNeighbours(from).stream()
			    					.filter(this::isPlaced)
			    					.filter(n -> !exclude.contains(n))
			    					.filter(n -> (n.equals(to) &&!first) || isPath(n,to,exclude,seen,false))
			    					.findFirst();
	    
	    if(!res.isPresent()) {
		    res = apiGraph.getInboundNeighbours(from).stream()
					.filter(this::isPlaced)
					.filter(n -> !exclude.contains(n))
					.filter(n -> (n.equals(to) && !first) || isPath(n,to,exclude,seen,false))
					.findFirst();
	    }
	    
	    return res.isPresent();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean hasDirectConnection(Node from, Node to) {
	    return !layoutGraph.getAllEdges(from, to).isEmpty();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> getRightmostOf(Node node) {
	    return getRightmostOf(node,null);
	}

	@LogMethod(level=LogLevel.DEBUG)
	List<Node> getRightmostOf(Node node, Node start) {
	    List<Node> res = new ArrayList<>();
	    
	    res.add(node);
	    
	    if(start==null || !node.equals(start)) {
	        List<Position> rightmostPosition=new ArrayList<>();
	        LOG.debug("getRightmostof: node={}", node);
	        getPlacedAt(node,Place.LEFT)
	        .stream()
	        .filter(n-> !n.equals(start))
	        .map(n-> {
	        	LOG.debug("n={} node={}", n, node); 
	        	return this.getRightmostOf(n,node);
	        })
	        .forEach(m -> {
	            LOG.debug("getRightmostOf: m={}", m); 
	            m.forEach(n -> {
	                LOG.debug("getRightmostOf: n={}", n);
	                Position pos = getPosition(n);
	                if(rightmostPosition.isEmpty()) {
	                    rightmostPosition.add(pos);
	                    res.add(n);
	                } else if(pos.getX()>rightmostPosition.get(0).getX()) {
	                    rightmostPosition.add(0,pos);
	                    res.add(n);
	                }
	            });
	        });
	    }
        LOG.debug("getRightmostof: res={}", res);
	    return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> getAllRightLeftOf(Node node, Place direction) {
		Set<Node> seen = new HashSet<>();
		return getAllRightLeftOfHelper(node, direction, seen);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	List<Node> getAllRightLeftOfHelper(Node node, Place direction, Set<Node> seen) {
		Place activeDirection = direction; // Place.getMapping().get(direction);
	    List<Node> res=new ArrayList<>();
	    for( Node n : getPlacedAt(node, activeDirection)) {
	        if(!seen.contains(n)) {
	            seen.add(n);
	            LOG.debug("getAllRightLeftOf: n ={}", n);
	            res.add(n);
	            List<Node> tmp = getAllRightLeftOfHelper(n, activeDirection, seen);
	            res.addAll(tmp);
	        }
	    }
	    	    	    
	    return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean isEnumNeighbour(Node from, Node to) {
		return layoutGraph.containsEdge(from, to);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Place placeEdgesBetween(ClassEntity cls, Node from, Node to, Place defaultDirection, String rule) {
		Place direction = getDirection(from, to, Optional.empty(), defaultDirection);
		
		LOG.debug("placeEdgesBetween: from={} to={} direction={}",  from, to, direction);

		if(direction!=defaultDirection) rule = rule + " - override default direction - now " + direction;
		
		placeEdges(cls, from, to, direction, rule);
		
		LOG.debug("placeEdgesBetween: from={} to={} direction={}",  to, from, Place.getReverse(direction));
		
		placeEdges(cls, to, from, Place.getReverse(direction), rule);
		
		return direction;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Place placeEdgesBetween(ClassEntity cls, Node from, Node to, Optional<Node> following, Place defaultDirection, String rule) {
		Place direction = getDirection(from, to, following, defaultDirection);
		
		LOG.debug("placeEdgesBetween: from={} to={} direction={} defaultDirection={}",  from, to, direction, defaultDirection);

		if(direction!=defaultDirection) rule = rule + " - override default direction - now " + direction;
		
		placeEdges(cls, from, to, direction, rule);
		
		LOG.debug("placeEdgesBetween: from={} to={} direction={}",  to, from, Place.getReverse(direction));
		
		placeEdges(cls, to, from, Place.getReverse(direction), rule);
		
		return direction;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Place getDirection(Node nodeA, Node nodeB, Optional<Node> following, Place defaultDirection) {
		return getDirection(nodeA, nodeB, following, defaultDirection, null);
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	public Place getDirection(Node nodeA, Node nodeB, Optional<Node> following, Place defaultDirection, EdgeAnalyzer edgeAnalyzer) {
		
		Place res = defaultDirection;

		boolean edgeLengthWithinBounds = isEdgeLengthWithinBounds(nodeA, nodeB);

		LOG.debug("getDirection: nodeA={} nodeB={} following={} defaultDirection={} edgeLengthWithinBound={}", 
				nodeA, nodeB, following, defaultDirection, edgeLengthWithinBounds);

		if(isPlaced(nodeA) && isPlaced(nodeB)) {			
			res=getDirectionWithPlacedNodes(nodeA, nodeB, following, defaultDirection, edgeAnalyzer);
			
		} else if(following.isPresent() && isPlaced(following.get()) && (isPlaced(nodeA) || isPlaced(nodeB))) {
			res=getDirectionWithFollowingPlaced(nodeA, nodeB, following, defaultDirection, edgeAnalyzer);
		
		} else if(!edgeLengthWithinBounds) {
			res = Place.BELOW;
		}
							
		LOG.debug("getDirection: nodeA={} nodeB={} following={} defaultDirection={} edgeLengthWithinBound={} res={}", 
				nodeA, nodeB, following, defaultDirection, edgeLengthWithinBounds, res);

		if(!isPlaced(nodeA) || !isPlaced(nodeB)) {
			res = getDirectionNotBothPlaced(nodeA, nodeB, following, defaultDirection, edgeAnalyzer);
		}
		
		
		LOG.debug("getDirection: nodeA={} nodeB={} following={} defaultDirection={} edgeLengthWithinBound={} res={}", 
				nodeA, nodeB, following, defaultDirection, edgeLengthWithinBounds, res);

		if(edgeAnalyzer!=null) {
			Set<Node> candidatesInDirection = edgeAnalyzer.getEdgesForPosition(res);
			
			LOG.debug("getDirection: nodeA={} nodeB={} following={} defaultDirection={} edgeLengthWithinBound={} res={}", 
					nodeA, nodeB, following, defaultDirection, edgeLengthWithinBounds, res);

			if(!candidatesInDirection.contains(nodeB)) {
				res = defaultDirection; 
			}
		}
		
		LOG.debug("getDirection: nodeA={} nodeB={} following={} current res={}", nodeA, nodeB, following, res);
		if(following.isPresent()) LOG.debug("following isPlaced={}", isPlaced(following.get()));

		LOG.debug("nodeB neighbours: {}", this.getNeighbours(nodeB));
		
		if(res==Place.RIGHT && following.isPresent() && !isPlaced(following.get())) {
			LOG.debug("#2 getDirection: nodeA={} nodeB={} following={} current res={}", nodeA, nodeB, following, res);
			boolean directPath = this.apiGraph.getNeighbours(nodeB).size()==2;
			if(directPath) {
				res=Place.BELOW;
				LOG.debug("getDirection: nodeA={} nodeB={} directPath={} res={}", nodeA, nodeB, directPath, res);
			} else {
				Set<Node> neighboursOfB = getNeighbours(nodeB);
				neighboursOfB.remove(nodeA);
				neighboursOfB.remove(following.get());
				boolean isInheritanceOnly = neighboursOfB.stream().map(n -> this.apiGraph.getEdges(nodeB, n)).flatMap(Set::stream).allMatch(Edge::isInheritance);
				if(!neighboursOfB.isEmpty() && isInheritanceOnly) {
					res=Place.BELOW;
					LOG.debug("getDirection: nodeA={} nodeB={} directPath={} neighboursOfB={} res={}", nodeA, nodeB, directPath, neighboursOfB, res);
				}
			}
		}
		
		if(res!=defaultDirection) {
			LOG.debug("getDirection: nodeA={} nodeB={} defaultDirection={} res={}", nodeA, nodeB, defaultDirection, res);
		}
		
		return res;
	}

	private Place getDirectionNotBothPlaced(Node nodeA, Node nodeB, Optional<Node> following, Place defaultDirection, EdgeAnalyzer edgeAnalyzer) {
		Place res = defaultDirection;
		
		Position pos = getPositionFromNode(nodeA, res);
		
		LOG.debug("getDirectionNotBothPlaced: nodeA={} res={} pos={}", nodeA, res, pos); 

		Set<Node> placedInPosition = this.position.entrySet().stream().filter(entry -> entry.getValue().equals(pos)).map(Map.Entry::getKey).collect(toSet());
		
		LOG.debug("getDirectionNotBothPlaced: pos={} placedInPosition={}", pos, placedInPosition); 

		if(placedInPosition.size()>1) {
			List<Place> allDirections = new LinkedList<>( Arrays.asList(Place.LEFT, Place.RIGHT, Place.ABOVE, Place.BELOW) );
			allDirections.remove(res);
			for(Place candidate : allDirections) {
				Position candidatePos = getPositionFromNode(nodeA, candidate);
				placedInPosition = this.position.entrySet().stream().filter(entry -> entry.getValue().equals(candidatePos)).map(entry -> entry.getKey()).collect(toSet());
				LOG.debug("getDirectionNotBothPlaced: candidate={} pos={} placedInPosition={}", candidate, candidatePos, placedInPosition); 
				if(placedInPosition.isEmpty() && !this.isPlacedAt(nodeA, candidate) ) {
					res = candidate;
					break;
				}
			}
		}

		if(res!=defaultDirection) {
			LOG.debug("getDirectionNotBothPlaced: nodeA={} nodeB={} defaultDirection={} res={}", nodeA, nodeB, defaultDirection, res);
		}

		return res;
	}

	private Place getDirectionWithFollowingPlaced(Node nodeA, Node nodeB, Optional<Node> following, Place defaultDirection, EdgeAnalyzer edgeAnalyzer) {
	
		Place res = defaultDirection;
		
		if(!following.isPresent()) return res;
		
		Node next = following.get();
		
		Node from = isPlaced(nodeA) ? nodeA : nodeB;
		Node to   = isPlaced(nodeA) ? nodeB : nodeA;

		if(!isPlaced(nodeA)) res = Place.getReverse(res);

		LOG.debug("getDirectionWithFollowingPlaced:: #0 from={} next={} res={}", from, next, res);

		List<Place> directions = Arrays.asList(Place.LEFT, Place.RIGHT, Place.ABOVE, Place.BELOW);
		
		String dirs = directions.stream().map(dir -> dir.toString() + "=" + isDirectionOf(from,next,dir)).collect(Collectors.joining(" "));
		
		LOG.debug("getDirectionWithFollowingPlaced:: directions::{}", dirs);
		
		if(isDirectionOf(from,next,Place.ABOVE)) {
			if(res!=Place.ABOVE && res!=Place.LEFT && res!=Place.RIGHT) res=Place.ABOVE;
		} else if(isDirectionOf(from,next,Place.BELOW)) {
			if(res!=Place.BELOW && res!=Place.LEFT && res!=Place.RIGHT) res=Place.BELOW;
		} 
		
		boolean selfReference = apiGraph.getGraph().containsEdge(from,from);
		if(selfReference && res==Place.RIGHT) {
			res=Place.ABOVE;
		}

		LOG.debug("getDirectionWithFollowingPlaced:: #1 from={} to={} next={} res={}", from, to, next, res);


		if(res!=defaultDirection) {
			LOG.debug("getDirectionWithFollowingPlaced: nodeA={} nodeB={} defaultDirection={} res={}", nodeA, nodeB, defaultDirection, res);
		}

		return res;
		
	}

	private Place getDirectionWithPlacedNodes(Node nodeA, Node nodeB, Optional<Node> following, Place defaultDirection, EdgeAnalyzer edgeAnalyzer) {
		Place res=defaultDirection;
		
		Position posA = getPosition(nodeA);
		Position posB = getPosition(nodeB);

		LOG.debug("getDirectionWithPlacedNodes: #00 nodeA={} posA={} nodeB={} posB={} following={}", nodeA, posA, nodeB, posB, following);

		if(posA.getY()<posB.getY()) {
			res = Place.BELOW;
		} else if(posA.getY()>posB.getY()) { 
			res = Place.ABOVE;
		} else {
			boolean edgeLengthWithinBounds = isEdgeLengthWithinBounds(nodeA, nodeB); 

			if(!edgeLengthWithinBounds) {
				res = Place.BELOW;
			} else if(posA.getX()<posB.getX() & !this.isPlaced(nodeB, Place.LEFT)) {
				res = Place.RIGHT;
			} else if(posA.getX()>posB.getX() && !this.isPlaced(nodeB, Place.RIGHT)) {
				res = Place.LEFT;
			} else {
					
				List<List<Place>> placed = this.getPlacePaths(getNeighbours(nodeA),  getNeighbours(nodeB));

				List<Place> optPlaced = this.getAggregatedDirection(placed);
				
				LOG.debug("getDirectionWithPlacedNodes:: placed={} optPlaced={}",  placed, optPlaced);
				
				if(optPlaced.contains(Place.LEFT)) 
					res=Place.LEFT;
				else if(optPlaced.contains(Place.RIGHT)) 
					res=Place.RIGHT;

				
			}
		}
		

		if(res!=defaultDirection) {
			LOG.debug("getDirectionWithPlacedNodes: nodeA={} nodeB={} defaultDirection={} res={}", nodeA, nodeB, defaultDirection, res);
		}

		return res;

	}

	private Position getPositionFromNode(Node node, Place direction) {
		
		Position pos = new Position(getPosition(node));
		
		switch(direction) {
		case LEFT:
			pos.setX(pos.getX()-1);
			break;
			
		case RIGHT:
			pos.setX(pos.getX()+1);
			break;

		case ABOVE:
			pos.setY(pos.getY()-1);
			break;

		case BELOW:
			pos.setY(pos.getY()+1);
			break;

		default:
		}
		return pos;
	}

	private Set<Node> getNeighbours(Node node) {
		return GraphAlgorithms.getNeighbours(layoutGraph, node);
	}

	private List<Place> getAggregatedDirection(List<List<Place>> placed) {
		
		List<Place> res = getAggregatedDirectionOfSteps( placed.stream()
								.map(this::getAggregatedDirectionOfSteps)
							    .flatMap(List::stream)
								.collect(toList()) );
			
		return res;
	}

	private List<Place> getAggregatedDirectionOfSteps(List<Place> steps) {
		List<Place> res = new LinkedList<>();
		
		int x=0;
		int y=0;
		
		for(Place step : steps) {
			switch(step) {
			case ABOVE:
			case FORCEABOVE:
				y++;
				break;
				
			case BELOW:
			case FORCEBELOW:
				y--;
				break;
				
			case LEFT:
			case FORCELEFT:
				x--;
				break;
				
			case RIGHT:
			case FORCERIGHT:
				x++;
				break;
				
			default:
				
			}
		}
		
		if(x<0) res.add(Place.LEFT);
		if(x>0) res.add(Place.RIGHT);
		
		if(y<0) res.add(Place.BELOW);
		if(y>0) res.add(Place.ABOVE);
		
		return res;
		
	}
	
	private boolean isDirectionOf(Node node, Node pivot, Place direction) {

		boolean res = isDirectionOf_Helper(node, pivot, direction, new HashSet<>());
		
		LOG.debug("isDirectionOf: node={} pivot={} direction={} res={}", node, pivot, direction, res);
		
		return res;
		
	}
	
	private boolean isDirectionOf_Helper(Node node, Node pivot, Place direction, Set<Node> seen) {
		
		if(seen.contains(node)) return false;
		
		// boolean res = layoutGraph.getAllEdges(pivot, node).stream().map(LayoutEdge::getDirection).anyMatch(direction::equals);
		boolean res = layoutGraph.getAllEdges(node, pivot).stream().map(LayoutEdge::getDirection).anyMatch(direction::equals);

		seen.add(node);
		
		if(!res) {
			res = layoutGraph.outgoingEdgesOf(node).stream()
					.map(layoutGraph::getEdgeTarget)
					.anyMatch(n -> isDirectionOf_Helper(n,pivot,direction,seen));			
		}
		
		if(!res) {
			res = layoutGraph.incomingEdgesOf(node).stream()
					.map(layoutGraph::getEdgeSource)
					.anyMatch(n -> isDirectionOf_Helper(n,pivot,Place.getReverse(direction),seen));			
		}
			
		return res;
		
	}

	private boolean isEdgeLengthWithinBounds(Node from, Node to) {
		boolean res = false;
	  	if(apiGraph.getNeighbours(from).contains(to)) {
	  	   
    		res = apiGraph.getEdges(from, to).stream()
			    			.filter(edge -> !isEdgePlaced(edge))
			    			.filter(edge -> !edge.isEnumEdge())
			    			.map(Edge::getRelationship)
			    			.map(String::length)
			    			.noneMatch(l -> l>=35);
    		
    	}
	  	
	  	LOG.debug("isEdgeLengthWithinBounds: from={} to={} res={}", from, to, res);
	  	
	  	return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public int getBalancedIndex(Node node, int offset1, int offset2) {
		return currentlyPlacedAtLevel(node,offset1)-currentlyPlacedAtLevel(node,offset2);
	}

	public void addHiddenIfGroup(ClassEntity cls, Node fromNode, Node toNode, Place placedDirection, String detailedRule) {
		List<Node> leftOf = this.currentPlacedLeftOf(toNode);
		// LOG.debug("addHiddenIfGroup:: toNode={} leftOf={}",  toNode, leftOf);
		List<Node> connected = leftOf.stream()
									.filter(n -> nonEmptyIntersection(apiGraph.getNeighbours(n),leftOf))
									.collect(Collectors.toList());
		
		LOG.debug("addHiddenIfGroup:: toNode={} leftOf={} connected={}",  toNode, leftOf, connected);

		List<List<Node>> segments = getPartitions(connected);
	
		List<Node> rightmostOf = getRightmostOfSegments(segments);
	
		LOG.debug("addHiddenIfGroup:: toNode={} leftOf={} connected={} segments={} rightmostOf={}",  toNode, leftOf, connected, segments, rightmostOf );

		if(!rightmostOf.isEmpty()) {
			
			String rule = "addHiddenIfGroup";
			cls.addEdge(new HiddenEdge(rightmostOf.get(0),Place.RIGHT,toNode,rule));
		}

	}

	private boolean nonEmptyIntersection(Collection<Node> collectionA, Collection<Node> collectionB) {
		return collectionA.stream().anyMatch(collectionB::contains);
	}
	
	private List<List<Node>> getPartitions(Collection<Node> connected) {
		List<Node> remaining = new LinkedList<>(connected);
		List<List<Node>> res = new LinkedList<>();

		List<Node> partition = new LinkedList<>();

		Iterator<Node> iterator = remaining.iterator();
		while(iterator.hasNext()) {
			Node node = iterator.next();
			
			if(!partition.isEmpty() && !partition.contains(node)) {
				res.add(partition);
				partition = new LinkedList<>();
			}
			if(!partition.contains(node)) partition.add(node);
			Set<Node> neighbours = apiGraph.getNeighbours(node);
			neighbours.retainAll(connected);
			neighbours.removeAll(partition);
			partition.addAll(neighbours);
		}
		
		if(!partition.isEmpty() ) {
			res.add(partition);
		}

		return res;
	}

	private List<Node> getRightmostOfSegments(List<List<Node>> segments) {
		return segments.stream().map(this::getRightmostInSegment).collect(toList());
	}

	private Node getRightmostInSegment(List<Node> segment) {
		List<Node> sorted = segment.stream()
				.sorted(Comparator.comparing(n -> this.getPosition(n).getX()))
				.collect(toList());
		
		return sorted.get(sorted.size()-1);

	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
    private List<List<Place>> getPlacePaths(Set<Node> sources, Set<Node> targets) 
    { 
		List<List<Place>> paths = new LinkedList<>();		

		targets.removeAll(sources);

		LOG.debug("getPlacePaths:: sources={} targets={}",  sources, targets);
				
		for(Node source : sources) {
			List<Place> path = getPlacePaths(source, targets);
			if(!path.isEmpty()) paths.add(path);
		}
		
		return paths; 
    } 
	
	
	@LogMethod(level=LogLevel.DEBUG)
    private List<Place> getPlacePaths(Node from, Set<Node> targets) 
    { 
		List<Place> path = new LinkedList<>();	
		
		Map<Node,Boolean> visited = new HashMap<>();
		layoutGraph.vertexSet().forEach(node -> visited.put(node, false));
		
		LOG.debug("getPlacePaths: from={} targets={} ", from, targets);

		Set<Node> neighbours = getNeighbours(from).stream().filter(n -> !n.equals(from)).collect(toSet());
		
        Set<Node> common = Utils.intersection(neighbours, targets);
        
        if(!common.isEmpty()) {
        	      	
    		path = common.stream()
    							.map(node -> layoutGraph.getAllEdges(from, node))
    							.flatMap(Set::stream)
    							.map(LayoutEdge::getDirection).collect(toList());
       		 		
    		LOG.debug("getPlacePaths: from={} targets={} res={}", from, targets, path);

    		return path;
        	
        } else {   
			for(Node node : neighbours) {
				if(Boolean.FALSE.equals(visited.get(node))) {			
					path = getPlacePathsUtil(node, targets, visited, new LinkedList<>() ); 
					if(!path.isEmpty()) return path;
				}
			}
        }
		return path; 
    } 

	@LogMethod(level=LogLevel.DEBUG)
    private List<Place>  getPlacePathsUtil(Node node, Set<Node> targets, Map<Node,Boolean> visited, List<Place> path) 
    {   
        if( Boolean.TRUE.equals(visited.get(node)) || targets.contains(node)) return path;
      
        visited.put(node, true); 

		Set<Node> neighbours = getNeighbours(node).stream().filter(n -> !n.equals(node)).collect(toSet());
      
        if(neighbours.size() > 1) {
	        for(Node neighbour : neighbours) {
	        	if(Boolean.FALSE.equals(visited.get(neighbour))) {
	        		
	        		List<Place> subPath = new LinkedList<>();
	        		subPath.addAll(path);
	
	        		Set<Place> steps = layoutGraph.getAllEdges(node,  neighbour).stream().map(LayoutEdge::getDirection).collect(toSet());
	        			        		    
	        		subPath.addAll(steps);

	                LOG.debug("getPlacePathsUtil: #1 node={} targets={} neighbour={} subPath={}", node, targets, neighbour, subPath);

	        		// and the other direction
	                Set<Place> reverseSteps = layoutGraph.getAllEdges(neighbour, node).stream().map(LayoutEdge::getDirection).collect(toSet());

//	                retractStep = new HashSet<>();
//	                retractStep.addAll(reverseSteps);
//	                retractStep.removeAll(subPath);
//	                
//	        		reverseSteps.removeAll(retractStep);
//	        		// subPath.removeAll(retractStep);
	        		
	        		steps = reverseSteps.stream().map(Place::getReverse).collect(toSet());
	        		    
	        		subPath.addAll(steps);
	        		
	        		
//	        		reverseSteps.retainAll(subPath);
//	        		subPath.removeAll(reverseSteps);
//	        		
//	        		steps = reverseSteps.stream().map(Place::getReverse).collect(toSet());
//	        		    
//	        		subPath.addAll(steps);

	                LOG.debug("getPlacePathsUtil: #2 node={} targets={} neighbour={} subPath={}", node, targets, neighbour, subPath);

					List<Place> candidate = getPlacePathsUtil(neighbour, targets, visited, subPath);
					if(!candidate.isEmpty()) return candidate;
					
					// path = subPath;
					
	            } else if(targets.contains(neighbour)) {
	                LOG.debug("getPlacePathsUtil: found path node={} targets={} path={}", node, targets, path);
	                return path; 
	            }
	        } 
        }
        return new LinkedList<>(); 
    }

	public boolean hasPlacedCircle(Node node) {
		List<List<Node>> circles = this.apiGraph.getCircles();
		List<Node> placedLeft = this.getPlacedAt(node, Place.LEFT);
		List<Node> placedRight = this.getPlacedAt(node, Place.RIGHT);
		
		boolean res=false;
		for(List<Node> circle : circles) {
			Set<Node> commonLeft = Utils.intersection(circle,  placedLeft);
			Set<Node> commonRight = Utils.intersection(circle,  placedRight);

			res = !commonLeft.isEmpty() && !commonRight.isEmpty();
			
			if(res) break;
		}
		
		return res;
		
	}

  	
	
}

