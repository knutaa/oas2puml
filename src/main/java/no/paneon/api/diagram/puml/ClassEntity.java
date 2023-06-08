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

	static final String SUBRESOURCEREFERENCE = "SubResourceReference";

	static final String SKIP_DISCRIMINATORS = "skipDiscriminators";
	
	List<ClassProperty> classProperties;
	List<EnumEntity> enumEntities;
	List<EdgeEntity> edges;

	static List<String> processedEnums = new LinkedList<>();
	
	String name;
	String stereotype = "";
		
	String description = "";
	
	String inline = "";
	
	Set<String> inheritance;
	Set<String> actualInheritance;

	Set<String> discriminatorMapping;
	Set<String> inheritedDiscriminatorMapping;

	List<String> discriminatorExtension;
	List<String> inheritanceExtension;

	static final String BLANK_LINE = INDENT + "{field}//" + BLANK + "//" + NEWLINE;

	static final String SHOW_ALL_DISCRIMINATORS = "showAllDiscriminators";
	
	private boolean vendorExtension;
	
	private ClassEntity(String name) {
		super();
		this.name = name;
		this.classProperties = new LinkedList<>();
		this.enumEntities = new LinkedList<>();
		this.edges = new LinkedList<>();
		
		this.inheritance = new HashSet<>();
		this.actualInheritance = new HashSet<>();

		this.discriminatorMapping = new HashSet<>();
		this.inheritedDiscriminatorMapping = new HashSet<>();
		
		this.vendorExtension=false;
		this.discriminatorExtension = new LinkedList<>();
		this.inheritanceExtension = new LinkedList<>();

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
		this.actualInheritance = new HashSet<>();

		this.discriminatorMapping = new HashSet<>();
		this.discriminatorExtension = new LinkedList<>();

        LOG.debug("ClassEntity: name: {} seq: {}" , name, seq);
        
	}
	
	public ClassEntity(Node node) {
		this(node.getName());

		this.description = node.getDescription();
		this.inheritance.addAll(node.getInheritance());
		this.actualInheritance.addAll(node.getActualInheritance());

		// this.discriminatorMapping.addAll(node.getDiscriminatorMapping());
		this.discriminatorMapping.addAll(node.getLocalDiscriminators());
		this.inheritedDiscriminatorMapping.addAll(node.getInheritedDiscriminatorMapping());
		
		LOG.debug("ClassEntity: node={} discriminatorMapping={} inheritedDiscriminatorMapping={}", 
				node.getName(), this.discriminatorMapping, this.inheritance);
		
		this.vendorExtension=node.getVendorExtension();
		this.discriminatorExtension=node.getDiscriminatorExtension();
		this.inheritanceExtension=node.getInheritanceExtension();

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
		// Out.debug("ClassEntity::addProperty entity={} property={}",  this.name, c);
		if(c!=null) classProperties.add(c);
	}
	
	public void addProperties(List<ClassProperty> properties) {
		// if(this.name.contentEquals("ProductOfferingRelationship")) Out.debug("ClassEntity::addProperties entity={} properties={}",  this.name, properties);
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

        String vendorExtensionStereoType="";
        
        if(this.vendorExtension) {
        	vendorExtensionStereoType=" <<Extension>>";
        }
        
        String className = APIModel.getMappedResource(this.name);
	    res.append( "class " + className + generateInheritanceDecoration() + " " + this.stereotype + vendorExtensionStereoType + " {" + NEWLINE );
	    	    
	    String desc = description;
	    if(Config.includeDescription()) {
		    if(desc.isEmpty()) {
		    	res.append( BLANK_LINE );
		    } else {
		    	res.append( Utils.formatDescription(description, INDENT) );
		    	res.append( INDENT + "{field}" + NEWLINE );
		    }
	    }
	    
	    LOG.debug("ClassEntity: node={} classProperties={}",  this.name, this.classProperties);
	    	    
	    Set<String> discriminatorsToShow = getDiscriminatorsToShow();
	    
	    if(!discriminatorsToShow.isEmpty() && !Config.getBoolean("keepDefaultValueForAtType")) {
	    	classProperties.stream()
				.filter(ClassProperty::isAtTypeProperty)
				.forEach(ClassProperty::resetDefaultValue);
	    }
	    
	    
	    int maxLineLength = classProperties.stream()
	    						.map(ClassProperty::toString)
	    						.map(s -> s.split(NEWLINE))
	    						.map(Arrays::asList)
	    						.flatMap(List::stream)
	    						.mapToInt(String::length).max().orElse(0);
			
	    if(this.stereotype.contains(SUBRESOURCEREFERENCE) && !Config.getBoolean("showSubResourceProperties")) {
	    	classProperties.clear();
	    }
	    
	    classProperties.stream()
			.sorted(Comparator.comparing(p -> p.name))
			.collect(Collectors.partitioningBy(p -> p.name.startsWith("@")))
			.values().stream()
			.flatMap(List::stream)
	    	.map(p -> INDENT + p.toString(maxLineLength) + NEWLINE )
	    	.forEach(res::append);
	    
	    Set<String> customSimple = classProperties.stream()
	    		.map(ClassProperty::getType)
	    		.filter(APIModel::isCustomSimple)
	    		.collect(Collectors.toSet());
		    
    	if(!customSimple.isEmpty()) Out.debug("customSimple:: node={} customSimple={}", name, customSimple);

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
	    
	    	    
	    if(!discriminatorsToShow.isEmpty()) {
	    	res.append( INDENT + "--" + NEWLINE);
	    	res.append( INDENT + "discriminator:" + NEWLINE);

			String color = Extensions.getColor();

			String vendorExtensionFormat = "<color:" + color + ">%s";
				
	    	discriminatorsToShow.forEach(mapping -> {
	    			    		
	    		if(this.discriminatorExtension.contains(mapping)) {
	    			
		    		LOG.debug("discriminatorsToShow: discriminatorExtension={} mapping={}", this.discriminatorExtension, mapping);

	    			String coloredMapping = String.format(vendorExtensionFormat, mapping);
	    			
		    		LOG.debug("discriminatorsToShow: coloredMapping={}", coloredMapping);

	    			res.append(INDENT + coloredMapping + NEWLINE);
	    		} else {
	    			res.append(INDENT + mapping + NEWLINE);
	    		}
	    	});
	    }
	    
	    
	    List<String> nullableProperties = classProperties.stream()
		    	.filter(ClassProperty::isNullable)
				.map(ClassProperty::getName)
		    	.collect(Collectors.toList());
	    
	    LOG.debug("ClassEntity: node={} nullableProperties='{}'", this.name, nullableProperties);

	    
	    if(!nullableProperties.isEmpty()) {
	    	res.append( INDENT + "--" + NEWLINE);
	    	nullableProperties.forEach(label -> res.append(INDENT + label + " is nullable" + NEWLINE));
	    }

	    if(!this.inline.isEmpty()) LOG.debug("ClassEntity: node={} inline='{}'", this.name, this.inline);

	    if(!this.inline.isEmpty() && !this.inline.contains("object")) {
	    	res.append( INDENT + this.inline + NEWLINE);
	    }
	    
	    res.append( "}" + NEWLINE );
	    
	    return res.toString();
	    
	}

	private Set<String> getDiscriminatorsToShow() {
	   	Set<String> discriminators = new HashSet<>();

	   	List<String> skipDiscriminators = Config.get(SKIP_DISCRIMINATORS);
	   	if(skipDiscriminators.contains(this.name)) {
	   		return discriminators;
	   	}
	   	
    	if(this.discriminatorMapping.size()>1 || this.inheritedDiscriminatorMapping.size()>1) {
    		
    	    if(!this.discriminatorMapping.isEmpty()) 
    	    	discriminators.addAll(this.discriminatorMapping);
    	    else
    	    	discriminators.addAll(this.inheritedDiscriminatorMapping);

    	    LOG.debug("ClassEntity: getDiscriminatorsToShow={}", this.name, discriminators);

    	    boolean showDiscriminator = this.discriminatorMapping.size()==1 && Config.getBoolean(SHOW_ALL_DISCRIMINATORS);
    	    showDiscriminator = showDiscriminator || this.discriminatorMapping.size()>1;
    	    
    	    LOG.debug("ClassEntity: getDiscriminatorsToShow={} showDiscriminator={}", this.name, discriminators, showDiscriminator);

    	    if(!showDiscriminator) discriminators.clear();

    	}
    	
    	LOG.debug("getDiscriminatorsToShow: node={} discriminators={}", this.name, discriminators);
    	
    	return discriminators;
	}
	
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private String generateInheritanceDecoration() {	
		LOG.debug("inheritance: resource={} inheritance={} actual={}", this.name, this.inheritance, this.actualInheritance);	
		
		if(Config.getBoolean("keepInheritanceDecoractions")) {
			StringBuilder res = new StringBuilder();
			if(!this.inheritance.isEmpty()) {
				res.append(" <extends  ");
				inheritance.stream().map(this::formatInheritance).forEach(res::append);
				res.append(">");	
			} else if(!this.actualInheritance.isEmpty()) {
				res.append(" <extends  ");
				actualInheritance.stream().map(this::formatInheritance).forEach(res::append);
				res.append(">");
			}
			return res.toString();
		} else {
			return "";
		}
	}

	
	@LogMethod(level=LogLevel.DEBUG)
	private String formatInheritance(String s) {
		String format = "\\n%s";
		
		LOG.debug("formatInheritance: s={} inheritance={}", s, this.inheritanceExtension);
		if(this.inheritanceExtension.contains(s)) {
			String color = Extensions.getColor();
			format = "\\n<color:" + color + ">%s";
		}
		return String.format(format, s);
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
	
	public boolean getVendorExtension() {
		return this.vendorExtension || this.getProperties().stream().anyMatch(ClassProperty::getVendorExtension);
	}
}
	
