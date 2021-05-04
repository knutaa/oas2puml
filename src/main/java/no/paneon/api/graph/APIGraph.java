package no.paneon.api.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.graph.complexity.Complexity;
import no.paneon.api.graph.complexity.GraphAlgorithms;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.apache.logging.log4j.LogManager;
import org.jgrapht.alg.cycle.CycleDetector;

public class APIGraph extends CoreAPIGraph {

    static final Logger LOG = LogManager.getLogger(APIGraph.class);
    
	String resource;
	 
    List<String> processedSwagger;

	Map<String, JSONArray> required;
	
	Collection<Node> simpleTypes;
	Collection<Node> baseTypes;
	
	Collection<String> simpleTypesNames;
	Collection<String> baseTypesNames;

	Graph<Node,Edge> graph;
	
	Node resourceNode;
	
	List<List<Node>> circles;
	
	public APIGraph(CoreAPIGraph core, Graph<Node,Edge> graph, String resource) {
		super(core);
		
		this.graph=graph;
		
		this.resource = resource;	
		this.resourceNode = getNode(resource);
		
		init();
	}
	
	public APIGraph(String resource) {
		super();
		
		this.resource = resource;
		this.resourceNode = getNode(resource);
		
		this.graph = getSubGraphWithInheritance(this.completeGraph, this.resourceNode, this.resourceNode);

		init();
		
	}
	
	private void init() {
		this.required = new HashMap<>();

		this.processedSwagger = new ArrayList<>();

		this.simpleTypes = new HashSet<>();
		this.baseTypes = new HashSet<>();

		this.simpleTypesNames = Config.getSimpleTypes();
		this.baseTypesNames = Config.getBaseTypesForResource(resource);
							    
	    this.graph.vertexSet().stream().forEach(Node::resetPlacement);
	    
		LOG.debug("init:: #1");

		this.circles = GraphAlgorithms.cyclicAllCycles(this.graph, this.resourceNode);
		
		LOG.debug("init:: #2");


	}
	
	static final String ITEMS = "items";
	static final String REF = "$ref";
	static final String TYPE = "type";
	static final String ARRAY = "array";

	static final String REF_OR_VALUE = "RefOrValue";
	
	
//	@LogMethod(level=LogLevel.DEBUG)
//	public List<String> getNodes() {
//		return getNodeNames(graph);
//	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getGraphNodes() {
		Set<Node> res = new HashSet<>();
		res.addAll( graph.vertexSet() );
		return res;
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> getGraphNodeList() {
		return graph.vertexSet().stream().collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean presentEnumForResource(String resource) {
		return !Utils.isBaseType(this.resource, resource);
	}


	@LogMethod(level=LogLevel.DEBUG)
	private boolean showRelationship(String resource, String part) {		
		return !isSimpleType(resource, part);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean isSimpleType(String resource, String part) {
		
		Optional<Node> partNode = this.getNodeByName(part);
		Optional<Node> resNode = this.getNodeByName(resource);

		boolean res = (partNode.isPresent() && this.simpleTypes.contains(partNode.get())) 
							|| Utils.isSimpleType(part) 
							|| Utils.isBaseType(this.resource,resource)
							|| (resNode.isPresent() && this.baseTypes.contains(resNode.get()));
		
		if(partNode.isPresent()) {
			res = res && !partNode.get().isEnumNode();
		}
		
		return res;
		
	}
	
		
	@LogMethod(level=LogLevel.DEBUG)
	public Optional<Node> getNodeByName(String name) {
		return getNodeByName(graph,name);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getNeighboursByName(Graph<Node,Edge> graph, String node) {
		Optional<Node> n = getNodeByName(graph,node);
		
		if(n.isPresent()) {
			Set<Node> res = getOutboundNeighbours(graph, n.get());
			res.addAll( getInboundNeighbours(graph, n.get()) );
			return res;
		} else {
			return new HashSet<>();
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getNeighboursByName(String node) {
		Set<String> res = getOutboundNeighboursByName(node);
		res.addAll( getInboundNeighboursByName(node) );
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getNeighboursByNode(Node node) {
		Set<String> res = getOutboundNeighboursByName(node.getName());
		res.addAll( getInboundNeighbours(node.getName()) );
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getNeighbours(Node node) {
		Set<Node> res = getOutboundNeighbours(node);
		res.addAll( getInboundNeighbours(node) );
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getOutboundNeighboursByName(String node) {
		Node n = getNode(node);
		return getOutboundNeighbours(n).stream().map(Node::getName).collect(toSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getInboundNeighboursByName(String node) {
		Node n = getNode(node);
		return getInboundNeighbours(n).stream().map(Node::getName).collect(toSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getOutboundNeighbours(Node node) {
		return getOutboundNeighbours(graph, node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getInboundNeighbours(Node node) {
		return getInboundNeighbours(graph, node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getAllNeighbours(Node node) {
		Set<Node> s = getInboundNeighbours(node);
		s.addAll(getOutboundNeighbours(node));
		return s;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getInboundNeighbours(String node) {
		Node n = getNode(node);
		return getInboundNeighbours(graph, n).stream().map(Node::getName).collect(toSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getAllNeighbours(String node) {
		Set<String> s = getInboundNeighbours(node);
		s.addAll(getOutboundNeighboursByName(node));
		return s;
	}
			
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSingleFromNode(Node node) {
	    Set<Node> fromNodes=getInboundNeighbours(node);
	    fromNodes.remove(node);
	    
	    return fromNodes.size()<=1;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isLeafNode(Node node) {
		return graph.outgoingEdgesOf(node).isEmpty();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Edge> getEdges(Node from, Node to) {	
		return graph.getAllEdges(from,to);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean isFloatingNode(String node, String related) {
		boolean res=false;
		Set<String> neighbours=getNeighboursByName(node);
		neighbours.remove(related);
		res=neighbours.size()==1;

		return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	Node getLastElementOfPath(Node from, Node to, Node exclude, List<Node> seen) {
	    Node res = null;
	    
		LOG.debug("getLastElementOfPath: from=" + from + " to=" + to + " exclude=" + exclude + " seen=" + seen);

	    if(to.equals(exclude) || to.equals(from) || seen.contains(from)) return res;
	    seen.add(from);
	    List<Node> outbound = getOutboundNeighbours(from).stream().filter(x-> !x.equals(exclude)).collect(toList());
	    if(outbound.contains(to))
	        return from;
	    else {
	        for(Node node : outbound) {
	            res=getLastElementOfPath(node,to,exclude,seen);
	            if(res!=null) break;
	        }
	    }	    
	    return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isConnection(String from, String to) {
		
		Set<String> outbound = getOutboundNeighboursByName(from);
		if(outbound.contains(to)) return true;
		
		boolean connection = outbound.stream()
								.anyMatch(n -> isConnection(n,to));
		
		if(connection) return connection;
		
		outbound = getOutboundNeighboursByName(to);
		if(outbound.contains(from)) return true;

		connection = outbound.stream()
						.anyMatch(n -> isConnection(n,from));
		
		return connection;
		
	}
			
	@LogMethod(level=LogLevel.DEBUG)
	boolean isEdgeBetween(Node from, Node to) {
		return getOutboundNeighbours(from).contains(to);
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	boolean isOnlyBetween(String target,String nodeA, String nodeB) {
		boolean res;
		
	    res = getInboundNeighbours(target).stream()
	    				.filter(x-> !x.equals(nodeA) && !x.equals(nodeB))
	    				.collect(toList()).isEmpty();
	    
	    res = res && getOutboundNeighboursByName(target).stream()
	    				.filter(x-> !x.equals(nodeA) && !x.equals(nodeB))
	    				.collect(toList()).isEmpty();
	    	    
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isBetween(String target,String nodeA, String nodeB) {
	    boolean res;
	    
	    Set<String> inbound = getInboundNeighboursByName(target);
	    Set<String> outbound = getOutboundNeighboursByName(target);
	 
	    res = (inbound.contains(nodeA) && outbound.contains(nodeB));
	    res = res || (inbound.contains(nodeB) && outbound.contains(nodeA));
	    	    
	    return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isBetween(String nodeA, String nodeB) {
	    boolean res=false;
	 
	    res = getInboundNeighbours(nodeA).contains(nodeB);
	    res = res || getOutboundNeighboursByName(nodeA).contains(nodeB);
	    
	    res = res || getInboundNeighboursByName(nodeB).contains(nodeA);
	    res = res || getOutboundNeighboursByName(nodeB).contains(nodeA);
 	    
	    return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isLinearPath(Node toNode, int maxLength) {
		boolean res=false;
		
	    Set<Node> inbound = getInboundNeighbours(toNode);
	    Set<Node> outbound = getOutboundNeighbours(toNode);
	    
	    res = inbound.size()==1 && outbound.isEmpty();
	    
	    if(!res && maxLength==1) {
	    	res = inbound.size()==1 && outbound.isEmpty();
	    } else if(!res && inbound.size()==1 && outbound.size()==1) {
	    	Node next=outbound.iterator().next(); 
		    LOG.trace("isLinearPath: node=" + toNode + " next=" + next);

	    	res = isLinearPath(next,maxLength-1);
	    }
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isLinearPathLongerThan(Node node, int minLength) {
		boolean res=false;
			
		int seenLength = 0;
	    boolean valid=true;
	        
	    Set<Node> outbound;
	    while(valid && node!=null) {
		    Set<Node> inbound = getInboundNeighbours(node);
		    outbound = getOutboundNeighbours(node);

	    	valid = inbound.size()==1 && outbound.size()<=1;
	    	
	    	if(valid) {
	    		seenLength++;
	    		Iterator<Node> iter = outbound.iterator();
	    		if(iter.hasNext()) {
	    			node = iter.next();
	    		} else {
	    			node = null;
	    		}

	    	}
	    }
	    
		return seenLength>=minLength;
		
	}
	
	public boolean isCirclePath(Node node) {
		return isCirclePath(node, 99); // was 3 99
	}
	
	
	private Map<Node,Boolean> isComputedCirclePath = new HashMap<>();
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isCirclePath(Node node, int maxLength) {
		
		LOG.debug("isCirclePath: node={} maxLength={} ", node, maxLength);

		if(isComputedCirclePath.containsKey(node)) {
			return isComputedCirclePath.get(node);
		
		} else {
			boolean res=false;
			
		    Set<Node> target = getInboundNeighbours(node);
		    
		    if(!target.isEmpty()) {
			    Set<Node> neighbours = getOutboundNeighbours(node);
			    
			    List<Node> path = new LinkedList<>();
			    path.add(node);
			    
			    res = neighbours.stream().anyMatch(n -> !isCirclePathHelper(n, maxLength-1, target, path).isEmpty());
		    }
		    
		    isComputedCirclePath.put(node,res);
		    
			return res;
		}
		
	}
	
	@LogMethod(level=LogLevel.TRACE)
	List<Node> isCirclePathHelper(Node node, int maxLength, Set<Node> target, List<Node> path) {
				
		LOG.debug("isCirclePathHelper: node={} maxLength={} target={}", node, maxLength, target);
		
		List<Node> res = new LinkedList<>();
		
		if(maxLength<=0)
			return res;
		
		if(path.contains(node))
			return res;
		
		if(target.contains(node)) {
			path.add(node);
			return path;
		}
			
		path.add(node);
		
		Set<Node> neighbours = getOutboundNeighbours(node);
		
		for(Node neighbour : neighbours) {
			List<Node> candPath = isCirclePathHelper(neighbour, maxLength-1, target, path);
			if(!candPath.isEmpty()) {
				return candPath;
			}
		}
			
		path.remove(node);

		return res;
		
	}
	

	@LogMethod(level=LogLevel.DEBUG)
	Optional<String> getCommonPathEnd(String nodeA, String nodeB) {
		Optional<String> res = Optional.empty();
		
		Set<String> nodeCs = getInboundNeighbours(nodeB);
		nodeCs.addAll(getOutboundNeighboursByName(nodeB));
		nodeCs.remove(nodeA);
		nodeCs.remove(nodeB);
		
		if(nodeCs.size()!=1) return res;
		
		Optional<String> optC = nodeCs.stream().findFirst();
	
		return optC;
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Optional<Node> getCommonPathEnd(Node nodeA, Node nodeB) {
		Optional<Node> res = Optional.empty();
		
		Set<Node> nodeCs = getInboundNeighbours(nodeB);
		nodeCs.addAll(getOutboundNeighbours(nodeB));
		nodeCs.remove(nodeA);
		nodeCs.remove(nodeB);
		
		if(nodeCs.size()!=1) return res;
		
		Optional<Node> optC = nodeCs.stream().findFirst();
	
		return optC;
		
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	Set<Node> getIntermediate(Node nodeA, Node nodeB) {
		Set<Node> res = new HashSet<>();
		
		Set<Node> nodeCs = getInboundNeighbours(nodeB);
		nodeCs.addAll(getOutboundNeighbours(nodeB));
		nodeCs.remove(nodeA);
		nodeCs.remove(nodeB);
		
		if(nodeCs.size()!=1) return res;
		
		Optional<Node> optC = nodeCs.stream().findFirst();
		
		if(!optC.isPresent()) return res;
		
		Node nodeC = optC.get();
		
		if(nodeC.equals(nodeA) || nodeC.equals(nodeB)) return res;
		
		Set<Node> fromA = getOutboundNeighbours(nodeA);
		Set<Node> fromC = getOutboundNeighbours(nodeC);
		
		Set<Node> intermediate = Utils.intersection(fromA, fromC);
		
	    LOG.trace("getIntermediate: nodeA=" + nodeA + " nodeC=" + nodeC + " intermediate=" + intermediate);
		
		return intermediate;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean hasMultipleIntermediate(Node nodeA, Node nodeB) {
		return getIntermediate(nodeA, nodeB).size()>1;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimple(List<Node> candidates) {
		boolean res=false;
		
		if(candidates.size()>1) return res;
		
		Optional<Node> cand = candidates.stream()
									.filter(c -> getInboundNeighbours(c).size()==2 && getOutboundNeighbours(c).size()==0)
									.filter(this::isCommonLeafNode)
									.findFirst();
				
	    LOG.debug("isSimple: cand=" + cand);

		return cand.isPresent();
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isCommonLeafNode(Node node) {
		boolean res=false;
		
		Set<Node> inbounds = getInboundNeighbours(node);
		
		res = inbounds.stream().allMatch(c -> getOutboundNeighbours(c).size()==1 && getOutboundNeighbours(c).contains(node));
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isCommonLeafNode(String node) {
		boolean res=false;
		
		Set<String> inbounds = getInboundNeighbours(node);
		
		res = inbounds.stream().allMatch(c -> getOutboundNeighboursByName(c).size()==1 && getOutboundNeighboursByName(c).contains(node));
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isMultipleBetween(Node nodeA, Node nodeB) {
		return getBetween(nodeA,nodeB).size()>1;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getBetween(Node nodeA, Node nodeB) {
		Set<Node> res = new HashSet<>();
		
		Set<Node> common = getOutboundNeighbours(nodeA);
		
		Set<Node> neighbours = getOutboundNeighbours(nodeB);
		neighbours.addAll(getInboundNeighbours(nodeB));

		common.retainAll(neighbours);

		res.addAll(common);
		
		common = getOutboundNeighbours(nodeB);
		
		neighbours = getOutboundNeighbours(nodeA);
		neighbours.addAll(getInboundNeighbours(nodeA));

		common.retainAll(neighbours);
		
		res.addAll(common);

		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	int singleLaneLength(String node) {
		int res=0;
		
		Set<String> outbound = this.getOutboundNeighboursByName(node);
		
		if(outbound.size()==0) {
			Set<String> inbound = this.getInboundNeighbours(node);
			Set<String> exclude = new HashSet<>();
			exclude.add(node);
			
			Optional<Integer>  pathLength = inbound.stream().map(n -> pathLength(n,exclude)).max(Comparator.naturalOrder());
			if(pathLength.isPresent()) res=pathLength.get();
		}
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	int pathLength(String node, Set<String> exclude) {
		Optional<Integer> pathLength = Optional.empty();
		
	    LOG.debug("pathLength: node=" + node);

	    if(this.getInboundNeighbours(node).size()==1) {
			exclude.add(node);
			pathLength = this.getOutboundNeighboursByName(node).stream()
													.filter(n -> !exclude.contains(n))
													.map(n -> pathLength(n,exclude)).max(Comparator.naturalOrder());
			exclude.remove(node);
	    }
		return pathLength.isPresent() ? 1 + pathLength.get() : 1;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public int getAdditionalNeighbours(Node p, Collection<Node> candidates) {
		Set<Node> s = getNeighbours(p);
		s.removeAll(candidates);
		return s.size();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private String processRawType(JSONObject obj, String key) {
		String res="";
		Object o = obj.opt(key);
		
		if(o instanceof JSONArray)
			res = processRawArrayType(obj.optJSONArray(key));
		else if(o instanceof JSONObject)
			res = processRawObjectType(obj.optJSONObject(key));
		else
			res = obj.opt(key).toString();
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private String processRawObjectType(JSONObject obj) {
		String res="";
		
		res = "###" + obj.toString();
		if(obj.has(TYPE) && obj.getString(TYPE).equals(ARRAY)) {
			res = "test";
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private String processRawArrayType(JSONArray array) {
		String res="";
		
		res = array.toString();

		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	void addRawType(String node, String value, boolean required) {    
		addNode(node);
		Node graphNode = getNode(node);	
		graphNode.otherProperties.add(new RawType(value, required));
	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isConnectedPath(Node from, Node to) {
	    return isConnectedPath(from, to, new ArrayList<>());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isConnectedPath(Node from, Node to, List<Node> exclude) {
	    return isConnectedPath(from, to, exclude, new ArrayList<>(), true);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	boolean isConnectedPath(Node from, Node to, List<Node> exclude, List<Node> seen, boolean first) {
		if(exclude.contains(to) || to.equals(from) || seen.contains(from)) {
			return false;
		} else {
		    seen.add(from);
	
		    Optional<Node> res = getNeighbours(from).stream()
				    					.filter(n -> !exclude.contains(n))
				    					.filter(n -> (n.equals(to) &&!first) || isConnectedPath(n,to,exclude,seen,false))
				    					.findFirst();
		       	
		    return res.isPresent();
		}
	    
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getAllNodes() {
		Set<String> res = new HashSet<>();
		
		Optional<Node> source = getNodeByName(graph, resource);
		
		if(source.isPresent()) {			
			res.addAll( getSubGraph(source.get()).stream().map(Node::getName).collect(toSet()) );
			res.add( source.get().getName() );
		}
				
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getSubGraph(Node node) {
		return getSubGraph(node,node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getSubGraph(Node parent, Node node) {
		Set<Node> seen = new HashSet<>();
		seen.add(parent);
		Set<Node>  res = getSubGraphHelper(node, seen);
		
		res.remove(parent);
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getSubGraphHelper(Node node, Set<Node> seen) {
		Set<Node> neighbours = this.getOutboundNeighbours(node);
		
		Set<Node> res = new HashSet<>();
		
		res.addAll(neighbours);

		seen.add(node);
		
		for(Node n : neighbours) {
			if(!seen.contains(n)) {
				Set<Node> sub = getSubGraphHelper(n,seen);
				res.addAll(sub);
			}
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getReverseSubGraph(Node node) {
		Set<Node> seen = new HashSet<>();
		seen.add(node);
		return getReverseSubGraphHelper(node, seen);	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getReverseSubGraphHelper(Node node, Set<Node> seen) {
		Set<Node> neighbours = this.getInboundNeighbours(node);
		
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
	
	@LogMethod(level=LogLevel.DEBUG)
	public void applyComplexity(Complexity analyser, String resource) {
				
		Set<Node> additionalSimpleTypes = analyser.getSimpleTypes();
		Set<Node> additionalBaseTypes = analyser.getBaseTypes();
		
		LOG.debug("applyComplexity: resource={} simpleTypes={}", resource, additionalSimpleTypes);
		LOG.debug("applyComplexity: resource={} baseTypes={}", resource, additionalBaseTypes);

		this.simpleTypesNames.addAll( getNodeNames(additionalSimpleTypes) );
		this.baseTypesNames.addAll( getNodeNames(additionalBaseTypes) );

	}

	@LogMethod(level=LogLevel.DEBUG)
	public String getResource() {
		return this.resource;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Node getResourceNode() {
		return this.resourceNode;
	}

	static final String CASE_ITEM = "Item";
	static final String CASE_ITEMS = "Items";

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isItemSubResource(Node node, Node toNode) {
		return isItemSubResource(node.getName(), toNode.getName());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isItemSubResource(String node, String toNode) {
				
		boolean res = toNode.endsWith(node + CASE_ITEM) || toNode.endsWith(node + CASE_ITEMS);
		
		if(res) return res;
		
		if(toNode.endsWith(CASE_ITEM)) {
			node = node + CASE_ITEM;
			res = node.endsWith(toNode);
		} else if(toNode.endsWith(CASE_ITEMS)) {
			node = node + CASE_ITEMS;
			res = node.endsWith(toNode);
		}
		
		return res;
	
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isRefOrValueForNode(Node node, Node cand) {		
		return cand.getName().endsWith(node.getName() + REF_OR_VALUE);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> getSubGraphList(Node resource) {
		List<Node> res = new LinkedList<>();
		res.addAll( getSubGraph(resource) );
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isConnectionBetweenNodes(Set<Node> nodes) {
		// TODO
		return false;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isBlockingEdges(Node node, Node nodeAbove) {

		if(nodeAbove==null) return false;
		
		Set<Node> outboundFromNodeAbove = getOutboundNeighbours(nodeAbove);
		Set<Node> edgesWithNode = getInboundNeighbours(node);
		
		edgesWithNode.addAll(getOutboundNeighbours(node));
		
		Set<Node> common = Utils.intersection(outboundFromNodeAbove, edgesWithNode);
		common.remove(node);
		common.remove(nodeAbove);
		
		boolean circlePath = common.stream().allMatch(this::isCirclePath);
		boolean blockingEdges = !common.isEmpty() && !circlePath;
			
		return false; // ; blockingEdges;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void setSimpleTypes(Set<Node> simpleTypes) {
		this.simpleTypes=simpleTypes;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void setBaseTypes(Collection<Node> baseTypes) {
		this.baseTypes=baseTypes;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Collection<Node> getBaseTypes() {
		return this.baseTypes;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		return this.graph.toString();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void filterSimpleTypes() {
		Set<Node> nonSimpleNodes = graph.vertexSet().stream()
									.filter(node -> !node.isSimpleType() || node.getName().contentEquals(this.resource) || node.isEnumNode() )
									.collect(toSet());
		
		LOG.debug("filterSimpleTypes: nonSimpleNodes=" + nonSimpleNodes);
		
		Graph<Node,Edge> subGraph = new AsSubgraph<>(this.graph, nonSimpleNodes);
		
		LOG.debug("filterSimpleTypes: subGraph=" + subGraph.vertexSet());

		LOG.debug("filterSimpleTypes: baseTypes=" + baseTypes);

		if(Config.getSimplifyRefOrValue()) {
			Set<Node> refOrValueNodes = subGraph.vertexSet().stream()
												.filter(node -> isRefOrValueNode(subGraph,node))
												.collect(toSet());
			
			for(Node refOrValue : refOrValueNodes) {
				subGraph.removeAllEdges( subGraph.outgoingEdgesOf(refOrValue));
			}
		}
		
		this.graph = subGraph;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean isRefOrValueNode(Graph<Node, Edge> graph, Node node) {
		return graph.vertexSet().stream().anyMatch(cand -> this.isRefOrValueForNode(cand,node));
	}

	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> getNodesByNames(Collection<String> nodes) {
		nodes.retainAll( getNodes() );
		return getGraphNodes().stream().filter(node -> nodes.contains( node.getName())).collect(toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<String> getNodeNames() {
		return getNodeNames(this.graph);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Collection<String> getNodeNames(Collection<Node> nodes) {
		return nodes.stream().map(Node::getName).collect(toSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPivotNodeAndSimpleStructure(Node node) {
		return  (node.equals(getResourceNode()) && getOutboundNeighbours(node).size()<3) ;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> getIncompleteTypes() {	
		
		Collection<Node> referenced =  graph.vertexSet();
		
		List<String> referencedNonSimple = referenced.stream()
						.map(Node::getProperties)
						.flatMap(List::stream)
						.map(Property::getType)
						.filter(p -> !APIModel.isSimpleType(p))
						.sorted()
						.distinct()
						.collect(toList());  
	
		return getNodesByNames( referencedNonSimple );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isCompositeNode(Node node) {	
				
		final List<String> configSimple = Config.getAllSimpleTypes();
		
		Predicate<String> isNotSimpleType = p -> !APIModel.isSimpleType(p);
				
		Predicate<String> isNotConfigSimple = p -> !configSimple.contains(p);

		List<String> nonSimple = node.getProperties().stream()
									.map(Property::getType)
									.filter(isNotSimpleType)
									.filter(isNotConfigSimple)
									.collect(toList());
			
		List<Node> nonSimpleNodes = getNodesByNames( nonSimple );
		
		LOG.debug("isCompositeNode: node={} nonSimple={} nonSimpleNodes={}", node, nonSimple, nonSimpleNodes);

		return nonSimple.size() != nonSimpleNodes.size();

	}

	@LogMethod(level=LogLevel.DEBUG)
	public Graph<Node,Edge> getGraph() {
		return this.graph;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<EnumNode> getEnumsForNode(Node node) {
		return getEnumsForNode(graph,node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Node getEdgeTarget(Edge edge) {
		return graph.getEdgeTarget(edge);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean existsCommonNeighbours(Node nodeA, Node nodeB) {
		Set<Node> neighbourA = getAllNeighbours(nodeA);
		Set<Node> neighbourB = getAllNeighbours(nodeB);

		Set<Node> allNeighours = neighbourB.stream()
									.map(this::getAllNeighbours)
									.flatMap(Set::stream)
									.filter(node -> !node.isEnumNode())
									.filter(node -> node.equals(nodeB))
									.collect(toSet());

		Set<Node> common = Utils.intersection(allNeighours,  neighbourA);
		
		LOG.debug("existsCommonNeighbours: nodeA=" + nodeA + " nodeB=" + nodeB + " all=" + allNeighours + " common="+ common);
		
		return common.size()>=2;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean notPartOfCircle(Node from) {
		return circles.stream().flatMap(List::stream).noneMatch(from::equals);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<List<Node>> getCircles() {
		return this.circles;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isNodeInCircles(Node node) {
		return this.circles.stream().flatMap(List::stream).anyMatch(node::equals);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Edge> getInboundEdges(Node node) {
		return this.graph.incomingEdgesOf(node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Edge> getOutboundEdges(Node node) {
		return this.graph.outgoingEdgesOf(node);
	}

	
}

