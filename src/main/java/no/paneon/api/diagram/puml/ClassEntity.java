package no.paneon.api.diagram.puml;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import java.util.stream.Collectors;
import static java.util.stream.Collectors.toSet;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.graph.Node;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class ClassEntity extends Entity {
	
    static final Logger LOG = LogManager.getLogger(ClassEntity.class);

	List<ClassProperty> classProperties;
	List<EnumEntity> enumEntities;
	List<EdgeEntity> edges;

	static List<String> processedEnums = new LinkedList<>();
	
	String name;
	String stereotype;
		
	String description = "";
	
	String inline = "";
	
	Set<String> inheritance;
	Set<String> discriminatorMapping;
	Set<String> inheritedDiscriminatorMapping;

	static final String BLANK_LINE = INDENT + "{field}//" + BLANK + "//" + NEWLINE;

	private ClassEntity(String name) {
		super();
		this.name = name;
		this.classProperties = new LinkedList<>();
		this.enumEntities = new LinkedList<>();
		this.edges = new LinkedList<>();
		
		this.inheritance = new HashSet<>();
		this.discriminatorMapping = new HashSet<>();
		this.inheritedDiscriminatorMapping = new HashSet<>();

        LOG.debug("ClassEntity: name: {} seq: {}" , name, seq);
        
	}
	private ClassEntity(String name, String stereotype) {
		super();
		this.name = name;
		this.stereotype = stereotype;
		this.classProperties = new LinkedList<>();
		this.enumEntities = new LinkedList<>();
		this.edges = new LinkedList<>();
		
		this.inheritance = new HashSet<>();
		this.discriminatorMapping = new HashSet<>();

        LOG.debug("ClassEntity: name: {} seq: {}" , name, seq);
        
	}
	
	public ClassEntity(Node node) {
		this(node.getName());

		this.description = node.getDescription();
		this.inheritance.addAll(node.getInheritance());
		
		this.discriminatorMapping.addAll(node.getDiscriminatorMapping());
		this.inheritedDiscriminatorMapping.addAll(node.getInheritedDiscriminatorMapping());

	}
	
	public ClassEntity(String name, List<ClassProperty> properties, String stereotype, String description, Set<String> inheritance, Set<String> mapping) {
		this(name,stereotype);
		this.classProperties.addAll( properties );
		this.description=description;
		this.inheritance.addAll(inheritance);
		this.discriminatorMapping.addAll(mapping);
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addProperty(ClassProperty c) {
		if(c!=null) classProperties.add(c);
	}
	
	public void addProperties(List<ClassProperty> properties) {
		classProperties.addAll(properties);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean addEnum(EnumEntity c) {
		if(c!=null) {
			enumEntities.add(c);
			processedEnums.add(c.type);
		}
		return c!=null;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<EnumEntity> getEnums() {
		return enumEntities;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean addEdge(EdgeEntity c) {
		if(c!=null) {
			edges.add(c);
			c.setContainingEntity(this);		
			
			LOG.debug("addEdge: {}", c);
		}
		return c!=null;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<EdgeEntity> getEdges() {
		return edges;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		
		StringBuilder res = new StringBuilder();
		
        LOG.debug("Diagram: processing for entity={}", name);

        if(Config.getIncludeDebug()) {
            res.append( getCommentsBefore(this.seq) );
        }

	    res.append( "class " + this.name + generateInheritance() + " " + this.stereotype + " {" + NEWLINE );
	    	    
	    String desc = description;
	    if(Config.includeDescription()) {
		    if(desc.isEmpty()) {
		    	res.append( BLANK_LINE );
		    } else {
		    	res.append( Utils.formatDescription(description, INDENT) );
		    	res.append( INDENT + "{field}" + NEWLINE );
		    }
	    }
	    
	    classProperties.stream()
			.sorted(Comparator.comparing(p -> p.name))
			.collect(Collectors.partitioningBy(p -> p.name.startsWith("@")))
			.values().stream()
			.flatMap(List::stream)
	    	.map(p -> INDENT + p.toString() + NEWLINE )
	    	.forEach(res::append);
		    
	    List<String> customSimple = classProperties.stream()
	    		.map(ClassProperty::getType)
	    		.filter(APIModel::isCustomSimple)
	    		.collect(Collectors.toList());
		    
    	LOG.debug("customSimple:: node={} customSimple={}", name, customSimple);

	    if(!customSimple.isEmpty()) {
	    		    	
	    	LOG.debug("customSimple:: node={} customSimple={}", name, customSimple);
	    	
	    	res.append(INDENT + "--" + NEWLINE);
	    	customSimple.forEach( property -> {
	    		String customPuml = APIModel.getCustomPuml(property);
	    		customPuml = Arrays.asList(customPuml.split(NEWLINE)).stream().collect(Collectors.joining(NEWLINE + INDENT));
	    		res.append( INDENT + property + " : " + customPuml + NEWLINE );
	    	});
	  
	    }
	    
	    if(classProperties.isEmpty() && customSimple.isEmpty() && !Config.includeDescription()) {
		    res.append( BLANK_LINE );

	    }
	    
	    
	    if(!this.discriminatorMapping.isEmpty() || !this.inheritedDiscriminatorMapping.isEmpty()) {
	    	
	    	res.append( INDENT + "--" + NEWLINE);
	    	res.append( INDENT + "mapping (discriminator):" + NEWLINE);

	    	this.discriminatorMapping.forEach(mapping -> res.append(INDENT + mapping + NEWLINE));

	    	String format = Config.getString("inheritedFormatting");
			String finalFormat = format.isEmpty() ? "%s" : format;
						
	    	this.inheritedDiscriminatorMapping.forEach(mapping -> res.append(INDENT + String.format(finalFormat,mapping) + NEWLINE));
	    }
	    
	    List<String> nullableProperties = classProperties.stream()
		    	.filter(ClassProperty::isNullable)
				.map(ClassProperty::getName)
		    	.collect(Collectors.toList());
	    
	    if(!nullableProperties.isEmpty()) {
	    	res.append( INDENT + "--" + NEWLINE);
	    	nullableProperties.forEach(label -> res.append(INDENT + label + " is nullable" + NEWLINE));
	    }

	    if(!inline.isEmpty()) {
	    	res.append( INDENT + inline + NEWLINE);
	    }
	    
	    res.append( "}" + NEWLINE );
	    
	    return res.toString();
	    
	}

	@LogMethod(level=LogLevel.DEBUG)
	private String generateInheritance() {	
		if(Config.getBoolean("keepInheritanceDecoractions")) {
			StringBuilder res = new StringBuilder();
			if(!this.inheritance.isEmpty()) {
				res.append(" <extends  ");
				inheritance.stream().map(ClassEntity::formatInheritance).forEach(res::append);
				res.append(">");
	
			}
			return res.toString();
		} else {
			return "";
		}
	}

	
	@LogMethod(level=LogLevel.DEBUG)
	private static String formatInheritance(String s) {
		return "\\n" + s + " ";
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public int getEdgeCount() {
		return edges.size();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isEnumProcessed(String type) {
		return processedEnums.contains(type);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addDescription(String description) {
		this.description = description;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static void clear() {
		processedEnums.clear();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	Set<String> getUsedTypes() {
		return classProperties.stream()
					.map(ClassProperty::getType)
					.filter(type -> !APIModel.isSpecialSimpleType(type))
					.collect(toSet());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void setStereoType(String stereoType) {
		this.stereotype=stereoType;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String getStereoType() {
		return this.stereotype;
	}
	
	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public String getName() {
		return name;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Collection<ClassProperty> getProperties() {
		return classProperties;
	}

	public void setInline(String inline) {
		this.inline=inline;
	}
	
}
	
