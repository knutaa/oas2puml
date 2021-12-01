  package no.paneon.api.diagram.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.Logger;

import no.paneon.api.graph.APIGraph;
import no.paneon.api.graph.AllOf;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;

public class EdgeAnalyzer {
	
	static final Logger LOG = LogManager.getLogger(EdgeAnalyzer.class);
	
	Node node;
	LayoutGraph layoutGraph;
	APIGraph apiGraph;

	Map<Place,Set<Node>> edgesPlaced;
	Map<Place,Set<Node>> edgeOptions; 

	Map<Integer, List<List<Node>> > circles;
	
	public EdgeAnalyzer(LayoutGraph graph, Node node, Map<Integer, List<List<Node>> > circles) {
		this.layoutGraph = graph;
		this.apiGraph = this.layoutGraph.getAPIGraph();
		
		this.node = node;
		this.edgesPlaced = new EnumMap<> (Place.class);
		this.edgeOptions = new EnumMap<> (Place.class);
		this.circles = circles;
		
		initialize();
	}
	
	enum Status {
		REJECT,
		ACCEPT,
		INDETERMINATE
	}
	
	final static Status REJECT = Status.REJECT;
	final static Status ACCEPT = Status.ACCEPT;
	final static Status INDETERMINATE = Status.INDETERMINATE;
	
	interface Condition {
		Status isCandidate(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph);
	}	
	
	static Map<Place,List<Condition>> edgeConditions = new EnumMap<> (Place.class);
	
	interface PlaceCounter {
		double get(Set<Node> options, Set<Node> placed);
	}

	static Map<Place,PlaceCounter> placeCounter = new EnumMap<> (Place.class);

	static {

		edgeConditions.put(Place.LEFT, Arrays.asList(
				
				EdgeAnalyzer::ifMultipleToInheritance,

				EdgeAnalyzer::notIfPivotSpecialCase,

				EdgeAnalyzer::notIfFromBelowAndTwoOthers,

				EdgeAnalyzer::notIfFromRightAndSingleTo,
								
				EdgeAnalyzer::notIfBidirectional,

				EdgeAnalyzer::notIfOneOf,

				EdgeAnalyzer::notIfDiscriminator,

				EdgeAnalyzer::notIfAllOf,

				EdgeAnalyzer::notIfFromPivotAndFewOutbound,

				EdgeAnalyzer::notIfComplexAndLeafExists,

				EdgeAnalyzer::notIfSelfReference,

				EdgeAnalyzer::notIfMultipleEdges,

				EdgeAnalyzer::notIfBelowAndFewOutbounds,
				
				EdgeAnalyzer::notIfSuperclassAndAlternatives,

				EdgeAnalyzer::ifSimple,

				EdgeAnalyzer::ifPlacedAboveAndLeafNode,
			
				EdgeAnalyzer::notIfTooManyOutboundNeighbours,

				EdgeAnalyzer::notIfNodePlacedBelowAndFewNeighbours,
				
				EdgeAnalyzer::isLeafNodeAndMultipleNeighbours,

				(toNode, node, apiGraph, layoutGraph) -> {	
					 
					boolean res = apiGraph.getInboundNeighbours(node).size()>=3 &&
							apiGraph.isLeafNode(toNode) && apiGraph.isSingleFromNode(toNode) &&
							(!toNode.startsWith(node) );
					
					return acceptWhenTrue(res);
					
				},

				(toNode, node, apiGraph, layoutGraph) -> {
					boolean res =  
							apiGraph.getOutboundNeighbours(node).size()>=4 &&
							apiGraph.isLinearPath(toNode,2); 
				
					return acceptWhenTrue(res);
		
				},

				(toNode, node, apiGraph, layoutGraph) -> {
					boolean res = 
							apiGraph.getNeighbours(node).size()>=4 &&
							apiGraph.isSingleFromNode(toNode) &&
							apiGraph.isLeafNode(toNode); 
							
					return acceptWhenTrue(res);

				},

				(toNode, node, apiGraph, layoutGraph) -> {
					boolean res = 
							layoutGraph.isPlacedAt(node,Place.ABOVE) && 
							layoutGraph.isPlacedAt(node,Place.BELOW) && 
							apiGraph.isSingleFromNode(toNode) &&
							apiGraph.isLeafNode(toNode);
					
					return acceptWhenTrue(res);

				},

				(toNode, node, apiGraph, layoutGraph) -> {
					boolean res = // !layoutGraph.isPlacedAt(node,Place.LEFT) && 
							layoutGraph.isPlacedAt(node,Place.ABOVE) && 
							apiGraph.getNeighbours(node).size()>=3 &&
							apiGraph.isSingleFromNode(toNode) &&
							apiGraph.isLeafNode(toNode); 
							
					return acceptWhenTrue(res);

				}

				));

		edgeConditions.put(Place.RIGHT, Arrays.asList( 
				
				EdgeAnalyzer::ifMultipleToInheritance,

				EdgeAnalyzer::notIfPivotSpecialCase,

				EdgeAnalyzer::notIfToSingleAndLargeSubgraph,

				EdgeAnalyzer::notIfFromBelowAndTwoOthers,

				EdgeAnalyzer::notIfFromRightAndBelow,
				
				EdgeAnalyzer::notIfFromRightAndSingleTo,
				
				EdgeAnalyzer::notIfBidirectional,

				EdgeAnalyzer::notIfDiscriminator,

				EdgeAnalyzer::notIfOneOf,

				EdgeAnalyzer::notIfFromPivotAndFewOutbound,

				EdgeAnalyzer::notIfMultipleEdges,

				(toNode, node, apiGraph, layoutGraph) ->  { 
										
					return acceptWhenTrue(true);

				},
				
				// EdgeAnalyzer::notIfBelowAndFewOutbounds,

				EdgeAnalyzer::notIfInheritance,
				
				EdgeAnalyzer::notIfTooManyOutboundNeighbours,

				EdgeAnalyzer::notIfNodePlacedBelowAndFewNeighbours,

				EdgeAnalyzer::notIfComposite,

				EdgeAnalyzer::ifSimple,

				EdgeAnalyzer::ifPlacedAbove,

				EdgeAnalyzer::isToLeafNodeAndFromComplex,

				EdgeAnalyzer::isLeafNodeAndMultipleNeighbours,

		
				(toNode, node, apiGraph, layoutGraph) ->  { 
										
					boolean res = (apiGraph.getOutboundNeighbours(node).size()>1) && (apiGraph.getOutboundNeighbours(toNode).size()>8);
					
					return acceptWhenTrue(res);


				},
				
				(toNode, node, apiGraph, layoutGraph) ->  { 
					
					Set<Node> outboundFromNode=apiGraph.getOutboundNeighbours(node);
					if(outboundFromNode.size()<2) return INDETERMINATE;

					Set<Node> outboundFromToNode=apiGraph.getOutboundNeighbours(toNode);

					Set<Node> common=Utils.intersection(outboundFromToNode,outboundFromNode);
					if(common.size()!=1) return INDETERMINATE; 

					boolean res = apiGraph.isSingleFromNode(toNode) && common.size()==1 && 
								  apiGraph.getEdges(node,toNode).size()==1;

					LOG.trace("edgeConditions: R02 toNode={} res={}", toNode, res);

					return acceptWhenTrue(res);
				},

				(toNode, node, apiGraph, layoutGraph) ->  { 

					boolean res = apiGraph.isSingleFromNode(toNode) && apiGraph.isLeafNode(toNode) ; // && 
							// apiGraph.getEdges(node,toNode).size()==1;

					LOG.trace("edgeConditions: R03 toNode={} res={}", toNode, res);

					return acceptWhenTrue(res);
				},

				(toNode, node, apiGraph, layoutGraph) ->  { 

					boolean res= apiGraph.isSingleFromNode(toNode) && 
								apiGraph.isLeafNode(toNode) && 
								apiGraph.getEdges(node,toNode).size()==1 &&
								(layoutGraph.isPlacedAt(node,Place.ABOVE) && !layoutGraph.isPlacedAt(node,Place.BELOW));

					LOG.trace("edgeConditions: R04 toNode={} res={}", toNode, res);

					return acceptWhenTrue(res);

				},

				(toNode, node, apiGraph, layoutGraph) -> {
					
					boolean res = 
							apiGraph.getOutboundNeighbours(node).size()>=4 &&
							apiGraph.getEdges(node,toNode).size()==1 &&
							apiGraph.isLinearPath(toNode,2);

							LOG.trace("edgeConditions: R05 toNode={} res={}", toNode, res);
							return acceptWhenTrue(res);

				},

				(toNode, node, apiGraph, layoutGraph) -> {
					
					boolean res = 
							apiGraph.isSingleFromNode(toNode) &&
							apiGraph.isLeafNode(toNode) &&
							apiGraph.getEdges(node,toNode).size()==1 &&
									layoutGraph.currentlyPlacedAtLevel(node,1) > layoutGraph.currentlyPlacedAtLevel(node,0) + 3;

							LOG.trace("edgeConditions: R06 toNode={} res={}", toNode, res);
							
					return acceptWhenTrue(res);

				},

				(toNode, node, apiGraph, layoutGraph) -> {
					
					boolean res =  apiGraph.isSingleFromNode(toNode) && 
							apiGraph.getOutboundNeighbours(toNode).size()==1 &&
							apiGraph.isLinearPath(toNode,2) &&
							apiGraph.getEdges(node,toNode).size()==1 &&
							apiGraph.getNeighbours(node).size()==2;	
					
					return acceptWhenTrue(res);

				}


				));

		edgeConditions.put(Place.ABOVE, Arrays.asList(
				
				// EdgeAnalyzer::notIfBelowAndFewOutbounds,
				
				EdgeAnalyzer::ifMultipleToInheritance,
				
				EdgeAnalyzer::notIfPivotSpecialCase,

				EdgeAnalyzer::notFromCircleNodeAndBelowInCircle,

				EdgeAnalyzer::notIfFromRightAndSingleTo,
				
				EdgeAnalyzer::notIfFromBelowAndFewOutbound,

				EdgeAnalyzer::notIfFromBelowAndToIsLeaf,

				EdgeAnalyzer::notIfFromBelowAndTwoOthers,
				
				EdgeAnalyzer::ifFromIsSingleNeighbourOfTo,
				
				EdgeAnalyzer::notIfFromLeftRightAndTwoOutbound,

				EdgeAnalyzer::notIfLargeSubGraph,

				EdgeAnalyzer::notFromCircleNode,

				EdgeAnalyzer::notIfBelowPivot,

				EdgeAnalyzer::notIfLongPath,
				
				EdgeAnalyzer::notIfOneOf,
				
				EdgeAnalyzer::notIfContainedOneOf,

				EdgeAnalyzer::notIfDiscriminator,

				EdgeAnalyzer::notIfContainedDiscriminator,

				EdgeAnalyzer::ifPlacedAboveAndLeafNode,

//				EdgeAnalyzer::notIfDirectInheritance,

				EdgeAnalyzer::notIfPartOfCircle,

				EdgeAnalyzer::notIfSelfReference,

				EdgeAnalyzer::notIfFromPivotAndFewOutbound,
				
				EdgeAnalyzer::notIfBelowAndInheritance,

				EdgeAnalyzer::ifSuperior,

				EdgeAnalyzer::notIfSubordinate,
				
				EdgeAnalyzer::notIfInheritance,

				EdgeAnalyzer::notIfComposite,

				EdgeAnalyzer::isSingleFromAndLeafAboveCondition,

				EdgeAnalyzer::isLinearPathAboveCondition,
						
				EdgeAnalyzer::anyNode

				));

		edgeConditions.put(Place.BELOW, Arrays.asList(
				
				EdgeAnalyzer::anyNode
				
				));

		
		placeCounter.put(Place.LEFT,  (options,place) -> 1 );
			
		placeCounter.put(Place.ABOVE, (options,place) -> {
			
					        Set<Node> realOptions = Utils.difference(options,place);
					        double res = realOptions.size()/2.0;
					        if(realOptions.size() <= options.size()/2) res=0;
					        
					        return res;
					        
					    });
		
		placeCounter.put(Place.RIGHT,  (options,place) -> 1 );
		
		placeCounter.put(Place.BELOW,  (options,place) -> options.size() );

		
	}
			
	@LogMethod(level=LogLevel.DEBUG)
	private static Status acceptWhenTrue(boolean value) {
		if(value)
			return Status.ACCEPT;
		else
			return Status.INDETERMINATE;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status rejectIfFalse(boolean value) {
		if(!value)
			return Status.REJECT;
		else
			return Status.INDETERMINATE;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status rejectIfTrue(boolean value) {
		if(value)
			return Status.REJECT;
		else
			return Status.INDETERMINATE;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	void initialize() {
	    for(Place place : Place.coreValues()) {
			if(!edgeOptions.containsKey(place)) edgeOptions.put(place, new HashSet<>());
			if(!edgesPlaced.containsKey(place)) edgesPlaced.put(place, new HashSet<>());
	    }
	}
	
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notFromCircleNodeAndBelowInCircle(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = !from.getCircleNodes().isEmpty() && !from.equals(apiGraph.getResourceNode()) && layoutGraph.isPlaced(from);
		
		Set<Node> nodes = apiGraph.getGraphNodes();
		nodes.retainAll(from.getCircleNodes());
		nodes.remove(from);
		
		OptionalInt topY = nodes.stream().filter(layoutGraph::isPlaced).map(layoutGraph::getPosition).map(Position::getY).mapToInt(v->v).min();

		if(res && topY.isPresent()) {
				
			res = topY.getAsInt()<layoutGraph.getPosition(from).getY();
			
			if(res) LOG.debug("notFromCircleNodeAndBelowInCircle: from={} to={} topY={} fromY={} res={}",  from, to, topY, layoutGraph.getPosition(from).getY(), res);
		
		} else {
			
			res=false;
		
		}
		
		return rejectIfTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfPivotSpecialCase(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = from.equals(apiGraph.getResourceNode());
		
		res = res && apiGraph.getOutboundEdges(from).size()<=2;
		
		if(res) LOG.debug("notIfPivotSpecialCase: from={} to={} res={}",  from, to, res);
		
		return rejectIfTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfFromBelowAndFewOutbound(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = layoutGraph.isPlacedBelow(from) && !from.equals(apiGraph.getResourceNode());
		
		if(res) LOG.debug("notIfFromBelowAndFewOutbound: from={} to={} #1 res={}",  from, to, res);

		res = res && apiGraph.getOutboundEdges(to).isEmpty();
		
		if(res) LOG.debug("notIfFromBelowAndFewOutbound: from={} to={} res={}",  from, to, res);
		
		return rejectIfTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfFromBelowAndToIsLeaf(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = layoutGraph.isPlacedBelow(from) && !from.equals(apiGraph.getResourceNode());
		
		LOG.debug("notIfFromBelowAndToIsLeaf: from={} to={} #1 res={}",  from, to, res);

		res = res && apiGraph.getInboundEdges(from).size() > apiGraph.getOutboundEdges(from).size();
		
		if(res) LOG.debug("notIfFromBelowAndToIsLeaf: from={} to={} res={}",  from, to, res);
		
		return rejectIfTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfToSingleAndLargeSubgraph(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = apiGraph.getInboundNeighbours(to).size()==1;
		
		if(res) LOG.debug("notIfToSingleAndLargeSubgraph: from={} to={} #1 res={}",  from, to, res);

		res = res && apiGraph.getSubGraph(to).size()>=5;
		
		if(res) LOG.debug("notIfToSingleAndLargeSubgraph: from={} to={} res={}",  from, to, res);
		
		return rejectIfTrue( res );

	}	
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfFromBelowAndTwoOthers(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = layoutGraph.isPlacedAt(from,Place.ABOVE) && !from.equals(apiGraph.getResourceNode());
		
		if(res) LOG.debug("notIfFromBelowAndTwoOthers: from={} to={} #1 res={}",  from, to, res);

		res = res && apiGraph.getOutboundNeighbours(from).size()==2;
		
		if(res) LOG.debug("notIfFromBelowAndTwoOthers: from={} to={} res={}",  from, to, res);
		
		return rejectIfTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfFromLeftRightAndTwoOutbound(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = layoutGraph.isPlacedAt(from,Place.LEFT) && layoutGraph.isPlacedAt(from,Place.RIGHT);
		
		if(res) LOG.debug("notIfFromLeftRightAndTwoOutbound: from={} to={} #1 res={}",  from, to, res);

		res = res && apiGraph.getOutboundNeighbours(to).size()<=2;
		
		if(res) LOG.debug("notIfFromLeftRightAndTwoOutbound: from={} to={} res={}",  from, to, res);
		
		return rejectIfTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status ifFromIsSingleNeighbourOfTo(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = apiGraph.getNeighbours(to).size()==1;
		
		LOG.debug("ifFromIsSingleNeighbourOfTo: from={} to={} res={}",  from, to, res);
		
		return acceptWhenTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfFromRightAndBelow(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = layoutGraph.isPlacedAt(from,Place.LEFT) && layoutGraph.isPlacedAt(from,Place.BELOW);
		
		LOG.debug("notIfFromRightAndBelow: from={} to={} res={}",  from, to, res);
		
		return rejectIfTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfFromRightAndSingleTo(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = layoutGraph.isPlacedAt(from,Place.LEFT)&& apiGraph.getOutboundNeighbours(from).size()==1; 
		
		res = res || layoutGraph.isPlacedAt(from,Place.ABOVE)&& apiGraph.getOutboundNeighbours(from).size()==1;

		LOG.debug("notIfFromRightAndSingleTo: from={} to={} res={}",  from, to, res);
		
		return rejectIfTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfFromLeftAndSingleTo(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
				
		boolean res = layoutGraph.isPlacedAt(from,Place.RIGHT)&& apiGraph.getOutboundNeighbours(from).size()==1;
		
		res = res || layoutGraph.isPlacedAt(from,Place.ABOVE)&& apiGraph.getOutboundNeighbours(from).size()==1;
		
		LOG.debug("notIfFromLeftAndSingleTo: from={} to={} res={}",  from, to, res);
		
		return rejectIfTrue( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfLargeSubGraph(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		
		int subGraphSize = apiGraph.getSubGraph(to).size();
		
		boolean res = subGraphSize >= 4;
		
		LOG.debug("notIfLargeSubGraph: from={} to={} subGraphSize={} res={}",  from, to, subGraphSize, res);
		
		return rejectIfTrue( res );

	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfBelowPivot(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		
		boolean singleInboundAndOutbound = apiGraph.getNeighbours(from).size()==2;
		boolean isBelowPivot      = apiGraph.getAllNeighbours(from).contains(apiGraph.getResourceNode());
		
		boolean res = singleInboundAndOutbound && isBelowPivot;
		
		LOG.debug("notIfBelowPivot: from={} to={} hasManyNodes={} isBelowPivot={} res={}",  from, to, singleInboundAndOutbound, isBelowPivot, res);
		
		return rejectIfTrue( res );


	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfLongPath(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean linearPath = true;
		int length=0;
		
		Optional<Node> nextNode = Optional.of(to);
		
		Set<Node> seen = new HashSet<>();
		while(linearPath && nextNode.isPresent()) {
			length++;
			Node next = nextNode.get();
			nextNode = Optional.empty();

			seen.add(next);
			
			Set<Edge> outbound = apiGraph.getOutboundEdges(next);
			Set<Edge> inbound = apiGraph.getInboundEdges(next);
			linearPath = (inbound.size()==1 && outbound.size()<=1);
			if(linearPath) {
				Optional<Edge> edge = Utils.getFirstElement(outbound);
				if(edge.isPresent()) {
					next = apiGraph.getGraph().getEdgeTarget(edge.get());
					if(!seen.contains(next)) {
						nextNode = Optional.of( next );
					}
				}
			}
			LOG.debug("notIfLongPath: from={} to={} nextNode={}", from, to, nextNode);
		}
		
		linearPath = linearPath & (length>=3);
		
		LOG.debug("notIfLongPath: from={} to={} linearPath={}", from, to, linearPath);

		return rejectIfTrue( linearPath );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notFromCircleNode(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean isFromCircleNode = from.isPartOfCircle();
		
		Predicate<Node> isAbove = n -> layoutGraph.isPositionedAbove(n,from);
		
		isFromCircleNode = isFromCircleNode && from.getCircleNodes().stream().anyMatch(isAbove);
		
		if(isFromCircleNode) LOG.debug("notFromCircleNode: from={} to={} circle={} res={}", from, to, from.getCircleNodes(), isFromCircleNode);
		
		return rejectIfTrue( isFromCircleNode );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfBidirectional(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean bidirectional = !apiGraph.getEdges(from, to).isEmpty() && !apiGraph.getEdges(to, from).isEmpty(); 
		
		if(bidirectional) LOG.debug("notIfBidirectional: from={} to={} notIfBidirectional={}", from, to, bidirectional);

		return rejectIfTrue( bidirectional );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfDiscriminator(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean isDiscriminator = apiGraph.getEdges(from, to).stream().anyMatch(Edge::isDiscriminator); // TBD
		
		isDiscriminator = isDiscriminator && apiGraph.getOutboundEdges(from).stream().count() < 4;  // filter(Edge::isDiscriminator).count() < 4;
		
		if(isDiscriminator) LOG.debug("notIfDiscriminator: from={} to={} isDiscriminator={}", from, to, isDiscriminator);

		return rejectIfTrue( isDiscriminator );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfAllOf(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean isAllOf = apiGraph.getEdges(from, to).stream().anyMatch(Edge::isAllOf);
		
		LOG.debug("notIfAllOf: from={} to={} isOneOf={}", from, to, isAllOf);

		return rejectIfTrue( isAllOf );

	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfOneOf(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean isOneOf = apiGraph.getEdges(from, to).stream().anyMatch(Edge::isOneOf);
		
		LOG.debug("notIfOneOf: from={} to={} isOneOf={}", from, to, isOneOf);

		return rejectIfTrue( isOneOf );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfContainedOneOf(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean isOneOf = apiGraph.getNeighbours(to).stream().map(child -> apiGraph.getEdges(to,child)).flatMap(Set::stream).anyMatch(Edge::isOneOf); 
		
		LOG.debug("notIfContainedOneOf: from={} to={} isOneOf={}", from, to, isOneOf);

		return rejectIfTrue( isOneOf );

	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfContainedDiscriminator(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean isDiscriminator = apiGraph.getNeighbours(to).stream().map(child -> apiGraph.getEdges(to,child)).flatMap(Set::stream).anyMatch(Edge::isDiscriminator); 
		
		if(isDiscriminator) LOG.debug("notIfContainedDiscriminator: from={} to={} isDiscriminator={}", from, to, isDiscriminator);

		return rejectIfTrue( isDiscriminator );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfPartOfCircle(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean inCircle = apiGraph.getCircles().stream().flatMap(List::stream).anyMatch(to::equals);
		
		LOG.debug("notIfPartOfCircle: from={} to={} inCircle={}", from, to, inCircle);

		return rejectIfFalse( !inCircle );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfFromPivotAndFewOutbound(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean hasManyNodes = apiGraph.getNeighbours(from).size()>=3;
		boolean isPivot      = apiGraph.getResourceNode().equals(from);
		
		boolean res = !isPivot || hasManyNodes;
		
		LOG.debug("notIfFromPivotAndFewOutbound: from={} to={} hasManyNodes={} isPivot={} res={}",  from, to, hasManyNodes, isPivot, res);
		
		return rejectIfFalse( res );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status anyNode(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		
		LOG.debug("anyNode: from={} to={}",  from, to);

		return acceptWhenTrue( true );
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfComplexAndLeafExists(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean hasLeafNodes = apiGraph.getOutboundNeighbours(from).stream().anyMatch(n -> apiGraph.getNeighbours(n).size()==1);
		boolean hasMultipleNeighbours = apiGraph.getAllNeighbours(to).size()>1;
		
		LOG.debug("notIfComplexAndLeafExists: to={} from={} hasLeafNodes={}",  to, from, hasLeafNodes);
		
		return rejectIfFalse( !hasMultipleNeighbours && hasLeafNodes );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfSelfReference(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean hasEdgeToSelf = apiGraph.getGraph().containsEdge(to, to);
		
		LOG.debug("notIfSelfReference: to={} hasEdgeToSelf={}",  to, hasEdgeToSelf);
		
		return rejectIfFalse( !hasEdgeToSelf );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfFromNodeInCircle(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean fromNodeInSomeCircle = apiGraph.getCircles().stream().flatMap(List::stream).anyMatch(from::equals);
		return rejectIfFalse( !fromNodeInSomeCircle );

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfMultipleEdges(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		int edges = apiGraph.getEdges(to, from).size() + apiGraph.getEdges(from, to).size();
		return rejectIfFalse( edges==1 );
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfBelowAndFewOutbounds(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		boolean placedBelowAndFewNeighbours = layoutGraph.isPlacedAt(from, Place.ABOVE) && apiGraph.getOutboundNeighbours(from).size() <=2;
		return rejectIfFalse( !placedBelowAndFewNeighbours );
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfTooManyOutboundNeighbours(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean many = apiGraph.getOutboundNeighbours(to).size()>=3;
		return rejectIfFalse( !many );
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfComposite(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		return rejectIfFalse( !apiGraph.isCompositeNode(to));
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfNodePlacedBelowAndFewNeighbours(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		return acceptWhenTrue( layoutGraph.isPlacedAt(from,Place.BELOW) && 
								  apiGraph.getNeighbours(from).size()>3 && 
								  apiGraph.getOutboundNeighbours(from).size()>2 );


	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfBelowAndInheritance(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean isInheritance = apiGraph.getEdges(from, to).stream().anyMatch(edge -> edge instanceof AllOf);
				
		boolean isBelow = layoutGraph.isPlacedAt(from,Place.ABOVE);
		
		boolean isInheritanceAndBelow = isInheritance && isBelow;
		
		Status res = rejectIfFalse(!isInheritanceAndBelow);
		
		LOG.debug("notIfBelowAndInheritance:: to={} from={} res={} isInheritance={} isBelow={}", to, from, res, isInheritance, isBelow);

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status ifMultipleToInheritance(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		Set<Edge> isInheritance = apiGraph.getGraph().incomingEdgesOf(to).stream().filter(Edge::isInheritance).collect(toSet());
				
		boolean res = isInheritance.size()>2;
		
		if(res) LOG.debug("ifMultipleToInheritance:: to={} from={} res={}", to, from, res);

		return acceptWhenTrue(res);

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfInheritance(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		Set<Edge> isInheritance = apiGraph.getGraph().outgoingEdgesOf(to).stream().filter(Edge::isInheritance).collect(toSet());
				
		Status res = rejectIfFalse(isInheritance.size()>0);
		
		LOG.debug("notIfInheritance:: to={} from={} res={}", to, from, res);

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfSuperclassAndAlternatives(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean isInheritance = apiGraph.getGraph().getAllEdges(from, to).stream().anyMatch(Edge::isInheritance);
		
		Predicate<Node> filterSimpleNodes = n -> apiGraph.isLeafNode(n) && !n.isEnumNode() && !n.equals(to);
		
		Set<Node> simpleNodes = apiGraph.getNeighbours(from).stream().filter(filterSimpleNodes).collect(toSet());
		
		Status res = INDETERMINATE;
		if(isInheritance) {
			res = simpleNodes.isEmpty() ? ACCEPT : REJECT ;
		}
		
		LOG.debug("notIfSuperclassAndAlternatives:: to={} from={} res={} simpleNodes={}", to, from, res, simpleNodes);

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfDirectInheritance(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean isInheritance = apiGraph.getGraph().getEdge(from, to).isInheritance() && !from.equals(apiGraph.getResourceNode());
				
		Status res = rejectIfFalse(!isInheritance);
		
		LOG.debug("notIfDirectInheritance:: to={} from={} res={}", to, from, res);

		return res;

	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status notIfSubordinate(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean isSubordinate = apiGraph.getGraph().outgoingEdgesOf(to).stream()
										.filter(Edge::isInheritance)
										.anyMatch(edge -> apiGraph.getGraph().getEdgeTarget(edge).equals(from));
				
		Status res = rejectIfFalse(!isSubordinate);
		
		LOG.debug("notIfSubordinate:: to={} from={} res={}", to, from, res);

		return res;

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status ifSuperior(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean isSuperior = apiGraph.getGraph().incomingEdgesOf(to).stream()
										.anyMatch(edge -> edge instanceof AllOf);
			
		LOG.debug("notIfSuperClass:: to={} from={} isSuperior={}", to, from, isSuperior);
		
		return acceptWhenTrue(isSuperior);

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status isSingleEdgeBetween(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean res = apiGraph.getEdges(from, to).size()==1;
		
		return acceptWhenTrue(res);

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status isLeafNodeAndFromIsBelow(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {	
        LOG.debug("isLeafNodeAndFromIsBelow: to={}, from={}", to, from);

		boolean res = apiGraph.isLeafNode(to) && layoutGraph.isPlaced(from, Place.BELOW);
		
		return acceptWhenTrue(res);

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status isLeafNodeAndMultipleNeighbours(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean res = apiGraph.isLeafNode(to) && apiGraph.getNeighbours(to).size()>2;
			   
		return acceptWhenTrue(res);

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status isToLeafNodeAndFromComplex(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean res = apiGraph.isLeafNode(to) && apiGraph.getNeighbours(from).size()>=3;	   
		return acceptWhenTrue(res);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status ifSimple(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		return acceptWhenTrue( apiGraph.isLeafNode(to) && apiGraph.getNeighbours(from).size()>4 );   
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status ifPlacedAbove(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		return acceptWhenTrue( layoutGraph.isPlacedAt(from, Place.BELOW) );   
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Status ifPlacedAboveAndLeafNode(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {	
		return acceptWhenTrue( layoutGraph.isPlacedAt(from, Place.BELOW) && apiGraph.isLeafNode(to) );   // TBD BELOW
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status isSingleFromAndLeafAboveCondition(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {		
		boolean res = !apiGraph.isPivotNodeAndSimpleStructure(from) &&
				apiGraph.isLeafNode(to) && 
				apiGraph.isSingleFromNode(to);
		
		return acceptWhenTrue(res);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Status isLinearPathAboveCondition(Node to, Node from, APIGraph apiGraph, LayoutGraph layoutGraph) {
		
		boolean res = !apiGraph.isPivotNodeAndSimpleStructure(from) &&  
			   !apiGraph.isItemSubResource(from, to) &&
				apiGraph.isSingleFromNode(to) && 
				apiGraph.getOutboundNeighbours(to).size()<=1 &&
				apiGraph.isLinearPath(to,2) &&
				apiGraph.getSubGraph(to).size()<2;	
		
        LOG.debug("isLinearPathAboveCondition: to={}, from={} res={}", to, from, res);

		return acceptWhenTrue(res);

	}

	@LogMethod(level=LogLevel.DEBUG)
	public void computeLayout() {
		
	    Set<Node> neighbours = apiGraph.getOutboundNeighbours(node);

        LOG.trace("computeLayout: node={}, neighbours={}", node, neighbours);

	    for(Node toNode: neighbours) {

	        // populate edgesPlaced property
	        Map<Place, Set<Node>> placed = layoutGraph.getPlaced(node);
	        	        
	        for(Place v : Place.coreValues() )  {           
	            if(placed.containsKey(v)) {
	            	for( Node n : placed.get(v) ) {
	                    if(!edgesPlaced.containsKey(v)) {
	                    	edgesPlaced.put(v, new HashSet<>());
	                    }
	                    if(!edgesPlaced.get(v).contains(n)) edgesPlaced.get(v).add(n);
	                }
	            }
	        }
	        
	        for(Place place : Place.coreValues()) {
	        	List<Condition> conditions = edgeConditions.get(place);
	        	if(!edgeOptions.get(place).contains(toNode)) {
	        			        		
		        	for(Condition condition: conditions) {
		        		Status cond = condition.isCandidate(toNode, node, apiGraph, layoutGraph); 
		        		LOG.debug("computeLayout: node={} qualified direction={} toNode={} cond={}", node, place, toNode, cond);
		        		
		        		if(cond!=INDETERMINATE) {
		        			if(cond==ACCEPT) {
		        				edgeOptions.get(place).add(toNode);
		        			} else {
		        				break;
		        			}
		        		}
		        	}
		        	
	        	}
	        	
	        }
	        
	    }
	    
	    LOG.debug("computeLayout: node={} edgeOptions={}",  node, edgeOptions);
	    			    
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isUnbalanced() {
		return layoutGraph.isUnbalancedLevel(this.node);		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean isChallengeToPlaceBelow() {
		boolean res=true;
        Place[] directions = {Place.RIGHT, Place.LEFT};
        Node nodeBelow = layoutGraph.getNearestBelow(this.node);
        
        if(nodeBelow==null) return false;
        
        boolean placedCircle = layoutGraph.hasPlacedCircle(this.node);
        
        if(placedCircle) return true;
        
        long atBelowLevel = layoutGraph.currentlyPlacedAtLevel(node,-1); // KJ WAS +1
        if(atBelowLevel < 5) return false;
        
        Set<Node> inboundToNodeBelow = apiGraph.getInboundNeighbours(nodeBelow);
        for( Place direction : directions ) {
        	Set<Node> placedInDirection = Utils.intersection(layoutGraph.getPlacedAt(this.node,Place.getMapping().get(direction)),inboundToNodeBelow);
        	placedInDirection.remove(node);

        	boolean placeToDirection = placedInDirection.isEmpty();
            if(!placeToDirection) {
            	res = false;
            } else {
                // check placement on the left side, find the ones that have been placed to right of others
                List<Node> pivot = layoutGraph.getAllRightLeftOf(nodeBelow, direction);

                Optional<Node> directConnection = pivot.stream().filter(n -> layoutGraph.hasDirectConnection(nodeBelow,n)).findFirst();
                res = res && directConnection.isPresent();
            } 
        }
        
        return res;
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getEdgesForPosition(Place direction) {
		Set<Node> res = new HashSet<>();
		
		computeLayout();
						
		Predicate<Node> isPlaced = layoutGraph::isPlaced;
		
		Set<Node> options = edgeOptions.get(direction).stream()
									.distinct()
									.filter(isPlaced.negate())
									.collect(toSet());
		
		Set<Node> placed = edgesPlaced.get(direction);

		double length = placeCounter.get(direction).get(options,placed);
		length = Math.ceil(length);

		boolean unbalanced = isUnbalanced();

		LOG.debug("getEdgesForPosition: node={} direction={} options={} length={} unbalanced={}", node, direction, options, length, unbalanced );

		if( (direction==Place.RIGHT || direction==Place.LEFT) && !layoutGraph.isPlaced(this.node, Place.ABOVE)) {
			
			if(unbalanced || placed.isEmpty()) {	

				LOG.debug("getEdgesForPosition: node={} direction={} placed={} options={}", node, direction, placed, options);

				int iLength = (int) length;
				iLength = Math.min(iLength, options.size());
												
				return getElementsFromSet(options,iLength);
				
			} else if(!node.equals(layoutGraph.resourceNode)){
				return res;
			}
		} 

		if(direction==Place.ABOVE) {
			
			Set<Node> inheritsFrom = options.stream().filter(n -> isSuperClass(n,node)).collect(toSet());
			if(!inheritsFrom.isEmpty()) {
				return inheritsFrom;
			}
			
			if(options.size()==1) {
				return options;
			}
			
			boolean challengeBelow = isChallengeToPlaceBelow();
			
			LOG.debug("getEdgesForPosition: node={} challengeBelow={} unbalanced={}", node, challengeBelow, unbalanced);

			// adjust for how many are already placed below or are candidates for below

			Set<Node> allOutbound = apiGraph.getOutboundNeighbours(node);
			if( !challengeBelow && allOutbound.size()<=3) return res;

			List<Node> placedLeft = layoutGraph.getPlacedAt(node,Place.RIGHT);
			List<Node> placedRight = layoutGraph.getPlacedAt(node, Place.LEFT);
			Set<Node> placedLeftRight = Utils.union(placedLeft,placedRight);

			List<Node> placedAbove = layoutGraph.getPlacedAt(node, Place.ABOVE);
			List<Node> placedBelow = layoutGraph.getPlacedAt(node, Place.BELOW);
			Set<Node> placedAboveBelow = Utils.union(placedAbove,placedBelow);

			Set<Node> alreadyPlaced = Utils.union(placedLeftRight,placedAboveBelow);

			Set<Node> remainingOptionsAbove = Utils.difference(edgeOptions.get(Place.ABOVE),alreadyPlaced);
			Set<Node> remainingOptionsBelow = Utils.difference(edgeOptions.get(Place.BELOW),alreadyPlaced);
			Set<Node> remainingOptionsAboveBelow = Utils.union(remainingOptionsAbove, remainingOptionsBelow);

			Set<Node> allOptionsAboveAndBelow = Utils.union(edgeOptions.get(Place.ABOVE), edgeOptions.get(Place.BELOW));

			allOptionsAboveAndBelow = Utils.difference(allOptionsAboveAndBelow,placedLeftRight);

			options = Utils.difference(edgeOptions.get(Place.ABOVE),alreadyPlaced).stream().distinct().collect(toSet());

			if(!challengeBelow && !unbalanced) {
	
				int max = remainingOptionsAboveBelow.size();

				int diff = Math.abs(placedAbove.size() - placedBelow.size());
				if(placedAbove.size() < placedBelow.size()) {
					// diff + half of rest above
					length = diff + (max - diff) / 2.0;
				} else {
					// half of rest above
					length = (max - diff) / 2.0;
				}
				LOG.debug("getEdgesForPosition: node={} max={}", node, max);
				LOG.debug("getEdgesForPosition: node={} diff={}", node, diff);
				LOG.debug("getEdgesForPosition: node={} placedAbove={}", node, placedAbove);
				LOG.debug("getEdgesForPosition: node={} placedBelow={}", node, placedBelow);
				
			} else if(challengeBelow) {
				// challenge to place below
				float maxAbove = allOptionsAboveAndBelow.size(); 
				
				// length = Math.min(Math.max(maxAbove - placedAbove.size(), 0), remainingOptionsAbove.size());
	
				length = maxAbove;
				
				LOG.debug("getEdgesForPosition:: node={} challengeBelow={} length={}", node, challengeBelow, length);
				LOG.debug("getEdgesForPosition:: allOptionsAboveAndBelow={}", allOptionsAboveAndBelow);
				
				return allOptionsAboveAndBelow;

			} else {
				// challenge to place below
				int unbalance = layoutGraph.currentlyPlacedAtLevel(node,1)-layoutGraph.currentlyPlacedAtLevel(node,-1);
				
				int max = remainingOptionsAboveBelow.size();

				int diff = Math.max(
								Math.abs(placedAbove.size() - unbalance),
								Math.abs(placedAbove.size() - placedBelow.size()));

				length = (max - diff) / 2.0;
				
				if(placedAbove.size() < placedBelow.size()) {
					length = diff + length;
				} 

				LOG.debug("getEdgesForPosition: unbalanced node={} max={}", node, max);
				LOG.debug("getEdgesForPosition: unbalanced node={} diff={}", node, diff);
				LOG.debug("getEdgesForPosition: unbalanced node={} placedAbove={}", node, placedAbove);
				LOG.debug("getEdgesForPosition: unbalanced node={} placedBelow={}", node, placedBelow);
				LOG.debug("getEdgesForPosition: unbalanced node={} length={}", node, length);
				
			}
			
			
		}
		
		int iLength = (int) length;
		iLength = Math.min(iLength, options.size());
		
		if(iLength<0) iLength=0;
		
		LOG.debug("getEdgesForPosition: options={} length={}", options, iLength);
		
		res = options.stream().distinct()
				.sorted(Comparator.comparing(nodeA -> getNodeComplexity(node,nodeA,direction)))
				.collect(toSet());
		
		LOG.debug("getEdgesForPosition: SORTED options={} length={}", res, iLength);

		res = getElementsFromSet(res,iLength);
		
		LOG.debug("getEdgesForPosition: before circlePath correction: node={} res={}", node, res);

		// correct for circlePath elements missing in res
		List<Node> circleNodes = new LinkedList<>();
		
		res.forEach(n -> {
			if(apiGraph.isCirclePath(n)) circleNodes.add(n);
		});
		
		LOG.debug("getEdgesForPosition: circleNodes={}", circleNodes);
		circleNodes.removeAll(res);
		res.addAll(circleNodes);
		
		LOG.debug("getEdgesForPosition: node={} direction={} res={}", node, direction, res);

		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean isSuperClass(Node superClass, Node subClass) {
		return this.apiGraph.getEdges(subClass, superClass).stream().anyMatch(edge -> edge instanceof AllOf);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getElementsFromSet(Set<Node> set, int size) {
		Set<Node> res = new HashSet<>();
		Iterator<Node> iter = set.iterator();
		while(iter.hasNext() && size>0) {
			res.add(iter.next());
			size--;
		}
		return res;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public int getNodeComplexity(Node node, Node nodeA, Place direction) {
		int res=0;
		
	    int inbound=apiGraph.getEdges(node,nodeA).size();
	    
	    int outbound=apiGraph.getOutboundNeighbours(nodeA).size();
	    
	    int subGraph=apiGraph.getSubGraph(node, nodeA).size();

	    boolean circlePath=apiGraph.isCirclePath(nodeA);
	    
	    Predicate<Set<Edge>> empty = Set<Edge>::isEmpty;
	    Predicate<Set<Edge>> notEmpty = empty.negate();
	    
	    int max = apiGraph.getGraphNodes().stream()
	    			.map(n -> apiGraph.getEdges(node, n))
					.filter(notEmpty)
					.map(edge -> edge.stream().map(e -> e.relation.length()).mapToInt(x->x).sum())
					.max(Comparator.naturalOrder())
					.orElse(200);
	    	    
	    int relationLength = apiGraph.getEdges(node, nodeA).stream().map(e -> e.relation.length()).mapToInt(x -> x).sum();
	    
	    if(direction==Place.LEFT) {
			res = 200 * subGraph + 10000 * (circlePath?1:0) + 1000 * outbound + 100 * inbound + relationLength - max;
	    } else {
	    	res = -200 * subGraph -10000 * (circlePath?1:0) + 1000 * outbound + 100 * inbound + relationLength;
	    }
	    
		return res;
	}
	
}
