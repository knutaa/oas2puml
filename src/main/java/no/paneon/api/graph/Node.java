package no.paneon.api.graph;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.diagram.layout.Place;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

public class Node implements Comparable<Object>  {

    static final Logger LOG = LogManager.getLogger(Node.class);

	List<Property> properties;
		
	Map<Place,List<Node>> placements;
	
	String description = "";

	List<OtherProperty> otherProperties;

	String resource = "ANON";
	
	List<String> enums; 
	
	Set<String> inheritance;
	
	Set<String> discriminatorMapping;
	
	static final String ALLOF = "allOf";
	static final String PROPERTIES = "properties";
	static final String TYPE = "type";
	static final String ARRAY = "array";
	static final String ENUM = "enum";

	static final String DESCRIPTION = "description";
	static final String REF = CoreAPIGraph.REF;
	static final String INCLUDE_INHERITED = CoreAPIGraph.INCLUDE_INHERITED;

	static final String EXPAND_INHERITED = "expandInherited";
	static final String EXPAND_ALL_PROPERTIES_FROM_ALLOFS = "expandPropertiesFromAllOfs";
	
	private Node() {
		properties = new LinkedList<>();
		placements = new EnumMap<>(Place.class);
		
		otherProperties = new LinkedList<>();
		
		enums = new LinkedList<>();
		
		inheritance = new HashSet<>();
		discriminatorMapping = new HashSet<>();

	}
	
	public Node(String resource) {
		this();
		this.resource=resource;		
		
		addPropertyDetails(Property.BASE);
		
		addDescription();
		
		Property.Visibility visibility = Config.getBoolean(INCLUDE_INHERITED) ? Property.VISIBLE_INHERITED : Property.HIDDEN_INHERITED;
		
		addAllOfs(visibility);
		
		addDiscriminatorMapping();

	}

	
	@LogMethod(level=LogLevel.DEBUG)
	private void addPropertyDetails(Property.Visibility visibility) {
		JSONObject propObj = APIModel.getPropertyObjectForResource(this.resource);
		addPropertyDetails(propObj, visibility);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addPropertyDetails(JSONObject propObj, Property.Visibility visibility) {
			
		if(propObj.has(TYPE) && ARRAY.equals(propObj.optString(TYPE))) {
			
		} else  {
			for(String propName : propObj.keySet()) {
				JSONObject property = propObj.optJSONObject(propName);
				if(property!=null) {
					String type = APIModel.type(property);
		
					String coreType = APIModel.removePrefix(type);
					
					boolean isRequired = APIModel.isRequired(resource, propName);
					String cardinality = APIModel.getCardinality(property, isRequired);
		
					boolean seen = properties.stream().map(Property::getName).anyMatch(propName::contentEquals);
					
					if(!seen) {
						Property propDetails = new Property(propName, coreType, cardinality, isRequired, property.optString(DESCRIPTION), visibility );
						
						if(property.has(ENUM)) {
							LOG.debug("addPropertyDetails: property={} values={}" , propName, Config.getList(property,ENUM));

							propDetails.addEnumValues( Config.getList(property,ENUM) );

						}
						
						properties.add( propDetails );
					}
					
					if(APIModel.isEnumType(type) && !enums.contains(coreType)) {
						enums.add(coreType);
					}
				} else {
					Out.printAlways("... unexpected property in " + propObj.toString());
				}	
			}
		} 

	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addAllOfs(Property.Visibility visibility) {
		if(Config.getBoolean(EXPAND_ALL_PROPERTIES_FROM_ALLOFS)) {
			JSONArray allOfs = APIModel.getAllOfForResource(this.resource);
			addAllOfs(allOfs, visibility);
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addAllOfs(JSONArray allOfs, Property.Visibility visibility) {
		allOfs.forEach(allOf -> {
			if(allOf instanceof JSONObject) {
				JSONObject definition = (JSONObject) allOf;
				addAllOfObject(definition, visibility);
			}
		});
				
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void addAllOfObject(JSONObject definition, Property.Visibility visibility) {
		
		if(definition.has(REF)) {
			String type = APIModel.getTypeByReference(definition.optString(REF));
				
			if(Config.getBoolean(EXPAND_INHERITED)) {
				this.addInheritance(type);
			}
				
			if(Config.getBoolean(INCLUDE_INHERITED)) {
				JSONObject obj = APIModel.getDefinitionBySchemaObject(definition);
				addAllOfObject(obj,Property.VISIBLE_INHERITED);
			}	
		}
		
		if(definition.has(PROPERTIES)) {
			JSONObject obj = APIModel.getPropertyObjectBySchemaObject(definition);			
			if(obj!=null) {	
				addPropertyDetails(obj,visibility);				
			}
		}
		
		if(definition.has(ALLOF)) {
			addAllOfs(definition.optJSONArray(ALLOF), visibility);
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addDiscriminatorMapping() {		
		JSONObject mapping = APIModel.getMappingForResource(this.resource);
		
		if(mapping!=null) {
			Set<String> mappings = mapping.keySet();
			mappings.remove(this.resource);
			
			this.discriminatorMapping.addAll(mappings);

		}
		

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void resetPlacement() {
		this.placements = new HashMap<>();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addDescription() {   	
	    this.description = APIModel.getDescription(this.resource);
	}

	
	public List<Property> getProperties() {
		return this.properties;
	}

	public List<OtherProperty> getOtherProperties() {
		return this.otherProperties;
	}

	public String getDescription() {
		return this.description;
	}
	
	public String toString() {
		return this.resource;
	}
	
	public int hashCode() {
		int res = this.resource.hashCode();
		LOG.trace("Node::hashCode: node=" + this.toString() + " res=" + res);

		return res;
	}
	
	public boolean equals(Object obj) {
		boolean res=false;
		if(obj instanceof Node) {
			res = ((Node) obj).getName().contentEquals(this.getName());
		} 
		LOG.trace("Node::equals: node=" + this.toString() + " obj=" + obj + " res=" + res);
		return res;
	}
	
	public String getName() {
		return this.resource;
	}

	public List<String> getEnums() {
		return this.enums;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType() {
		return isSimpleType(this.getName()) && !isEnumType(this.getName());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isEnumType(String type) {
		return APIModel.isEnumType(type);
	}


	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType(String type) {
		
		List<String> simpleEndings = Config.getSimpleEndings();
				
		boolean simpleEnding = simpleEndings.stream().anyMatch(type::endsWith);
		
		return  simpleEnding 
				|| APIModel.isSpecialSimpleType(type) 
				|| APIModel.isSimpleType(type) 
				|| Config.getSimpleTypes().contains(type) 
				|| APIModel.isEnumType(type);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Property> getReferencedProperties() {
		return properties.stream()
				.filter(this::isReferenceType)
				.collect(toSet());
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isReferenceType(Property property) {
		return !isSimpleType(property.getType());
	}
	
	public boolean startsWith(Node node) {
		return this.getName().startsWith(node.getName());
	}

	@Override
	public int compareTo(Object o) {
		if(o instanceof Node) {
			Node n = (Node)o;
			return this.getName().compareTo(n.getName());
		} else {
			return -1;
		}	
	}
	
	public String getDetails() {
		StringBuilder res = new StringBuilder();
		
		properties.stream().forEach( prop -> res.append(prop + " ")) ;
		
		return res.toString();
	}
	
	public boolean isEnumNode() {
		return this instanceof EnumNode;
	}

	public boolean inheritsFrom(Graph<Node, Edge> graph, Node candidate) {
		return graph.getAllEdges(this, candidate).stream().anyMatch(edge -> edge instanceof AllOf);
	}

	public void addInheritance(String type) {
		this.inheritance.add(type);
	}

	public Set<String> getInheritance() {
		return this.inheritance;
	}
	
	public Set<String> getDiscriminatorMapping() {
		return this.discriminatorMapping;
	}
	
}


