package no.paneon.api.gql;

import org.json.JSONObject;

import no.paneon.api.diagram.app.Args;
import no.paneon.api.diagram.app.args.GQLGraph;
import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.graph.Property;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GQLGenerator 
{
		
    static final Logger LOG = LogManager.getLogger(GQLGenerator.class);

    static final String NEWLINE = "\n";
    
    GQLGraph args;
    
	JSONObject layoutConfig;
	
	String file;
	String target;

	List<String> resources;
		
	Set<String> processed = new HashSet<>();

	public GQLGenerator(GQLGraph args, String file, String target) {
		this.args = args;	
		this.layoutConfig = Config.getLayout();
		
		this.file = file;    
		this.target = target;
				
		LOG.debug("GQLGenerator() resources={}", this.resources);
		
	}
		
	
	@LogMethod(level=LogLevel.DEBUG)
	public String generateGQLGraph() {
		
		CoreAPIGraph coreGraph = new CoreAPIGraph();
		
		String subClassExcludeRegexp = Config.getString("subClassExcludeRegexp");
		
		Set<Node> nodes = coreGraph.getCompleteGraph().vertexSet();
		
		if(subClassExcludeRegexp!=null && !subClassExcludeRegexp.isEmpty()) {
			nodes = nodes.stream().filter(node -> !node.getName().matches(subClassExcludeRegexp)).collect(toSet());
		}
		
		Set<Node> allnodes = new HashSet<>(nodes);
		
		Set<Edge> edges = coreGraph.getCompleteGraph().edgeSet().stream()
							.filter(edge -> allnodes.contains(edge.node) && allnodes.contains(edge.getRelated()))
							.collect(toSet());
		
		List<String> vertices = new LinkedList<>();
		List<String> relations = new LinkedList<>();
		
		for(Node node : nodes) {
			LOG.debug("generateGQLGraph: node={}", node);
			generateGQL(vertices, relations, node);
		}
		
		for(Edge edge : edges) {
			LOG.debug("generateGQLGraph: edge={}", edge);
			relations.add( generateGQL(relations, edge) );
		}
		
		StringBuilder res = new StringBuilder();
		vertices.forEach(v -> res.append(v + NEWLINE));
		relations.forEach(r -> res.append(r + NEWLINE));

		return res.toString();

	}


	private void generateGQL(List<String> vertices, List<String> relations, Node node) {
		StringBuilder res = new StringBuilder();

		String name = node.getName();
		res.append("CREATE (" + name + ":" + "Resource" + " { name: " + quoatedString(name) + ", type: " + quoatedString("object") + "})" + NEWLINE);

		vertices.add(res.toString());
		
		node.getProperties().stream().forEach( property -> generateGQLRelation(vertices,relations,node,property) );
			
	}

	private String generateGQL(List<String> relations, Edge edge) {
		StringBuilder res = new StringBuilder();

		res.append( "CREATE (" + edge.node.getName() + ") -[:" + edge.getRelationship() + "]-> (" + edge.getRelated().getName() + ")" + NEWLINE);
	
		return res.toString();
	}


	private String quoatedString(String str) {
		return "\"" + str + "\"";
	}


	private String generateGQL(Property p, Node node) {
		StringBuilder res = new StringBuilder();
		
		String nodeName = node.getName();
		String name = p.getName();
		res.append("CREATE (" + generateCleanPropertyName(name) + "_" + nodeName + ":Property { name: " + quoatedString(name) + ", type: " + quoatedString(p.getType()) + "})" );
		
		return res.toString();
	}
	
	private String generateCleanPropertyName(String name) {
		return name.replace("@", "at");
	}


	private void generateGQLRelation(List<String> vertices, List<String> relations, Node node, Property p) {	

		StringBuilder res = new StringBuilder();
		if(APIModel.isSimpleType(p.getType())) {
			res.append( generateGQL(p,node) + " <-[:HAS_PROPERTY]-(" + node.getName() + ")" + NEWLINE);
		}
		
		relations.add( res.toString() );
		
	}
	
}
	
