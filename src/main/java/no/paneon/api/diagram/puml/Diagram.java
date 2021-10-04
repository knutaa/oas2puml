package no.paneon.api.diagram.puml;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.commons.io.IOUtils;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;

import no.paneon.api.diagram.app.Args;
import no.paneon.api.graph.APIGraph;
import no.paneon.api.graph.complexity.Complexity;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class Diagram extends Entity {

    static final Logger LOG = LogManager.getLogger(Diagram.class);

	Map<String,ClassEntity> classes; 
	List<EdgeEntity> edgeEntities;
	Args.Diagram args;
	String file;
	Map<String,String> variables;
	String resource;
	
	List<Comment> comments;

	Complexity analyser;
	
	List<Diagram> subDiagrams;
	Diagram parentDiagram;
	
	Collection<String> baseTypes;
	Collection<String> simpleTypes;

	public Diagram(Args.Diagram args, String file, String resource) {
		this.args = args;
		this.file = file;
		this.resource = resource;
		
		this.comments = new LinkedList<>();
		this.subDiagrams = new LinkedList<>();
		this.baseTypes = new HashSet<>();
		this.simpleTypes = new HashSet<>();

		this.analyser = null;
		
		this.classes = new HashMap<>();
		this.edgeEntities = new LinkedList<>();
		
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
		
		this.variables = new HashMap<>();

		this.variables.put("RESOURCE", resource);
		this.variables.put("DATE", formatter.format(date) );
				
		this.variables.put("FILE", new File(file).getName());
		
		Core.reset();
		ClassEntity.clear();			

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Diagram addClass(ClassEntity c) {
        if(c!=null) classes.put(c.name,c);
		return this;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getResources() {
		return classes.keySet();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void setComplexityDetails(Complexity analyser) {
		this.analyser = analyser;		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Diagram addComment(Comment c) {
		comments.add(c);
		return this;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static void usage() {
	 	try {
    		InputStream is = new ClassPathResource("layout.json").getInputStream();
    	    String config = IOUtils.toString(is, StandardCharsets.UTF_8.name());
    	    JSONObject json = new JSONObject(config); 
    	    
    		Out.println(
					"Example layout configuration json (--config option):" + "\n" + 
					json.toString(2)
					);
			Out.println();

		} catch (Exception e) {

		}
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
						
    	String footer="";
	    if(args.source>0) {
	    	footer = "right footer ";
        	final JSONObject info = APIModel.getInfo();
        	if(!info.isEmpty()) {
        		if(info.has("title")) footer = footer + info.getString("title");
        		if(info.has("version")) footer = footer + " v" + info.getString("version");
        	}
        	if(footer.length()>0 && args.source>1) {
        		File f = new File(file);
        		String basename = f.getName();
        		footer = footer + "  - file: " + basename;
        	}
	    }
	    if(footer.length()>0) footer = footer + NEWLINE;
	    		
	    String puml = Config.getPuml();
	    
	    puml = Utils.replaceParagraph(puml, variables);
	    
	    
	    StringBuilder res = new StringBuilder();
	    
	    res.append( puml );
		res.append( footer ); 
		
		if(Config.processComplexity()) {
			addComplexityNote(res);
			addComplexityComments(res);
		}
		
		getPumlForClasses(res);
						
		getPumlForEdges(res);
		
		res.append( NEWLINE );
		
		if(Config.getIncludeDebug()) {
			comments.forEach(line -> {
				res.append( line );
				res.append( NEWLINE );
	
			});
		}
		
		if(Config.getBoolean("includeDiagramLegend")) {
			getLegend(res);			
		}

		res.append( "@enduml" );
				
		return res.toString();
	
	}

	private void getLegend(StringBuilder puml) {
		List<String> legendPrefix = Config.get("legendPrefix");
		String legendBody = Config.getString("legendBody");
		List<String> legendPostfix = Config.get("legendPostfix");

		List<String> legendSequence = Config.get("legendSequence");
		JSONObject legendConfig = Config.getConfig("legendConfig");

		String content = puml.toString();
		
		String[] lines = content.split("\n");
		
		StringBuilder legends = new StringBuilder();
		
		int legendCount=0;
		
		for(String stereoType : legendSequence) {
			for(String line : lines) {
				if(line.contains(stereoType) && line.contains("class ")) {
					JSONObject config = Config.getConfig(legendConfig,  stereoType);
					if(config!=null) {
						String item = legendBody.replace("$COLOR", config.optString("color"));
						item = item.replace("$TEXT", config.optString("text"));
						
						if(legends.length()>0) legends.append(NEWLINE);
						legends.append(item);
						legendCount++;
						break;
					}
				}
			}
		}

		if(legends.length()>0 && legendCount>1) {
			legends.insert(0,  legendPrefix.stream().collect(Collectors.joining(NEWLINE)) );
			legends.append(NEWLINE);

			legends.append( legendPostfix.stream().collect(Collectors.joining(NEWLINE)) );
			legends.append(NEWLINE); 

			puml.append(NEWLINE);
			puml.append(legends);
			puml.append(NEWLINE);
		
		}
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void getPumlForEdges(StringBuilder res) {
		
		List<EdgeEntity> edges = getClassEntitiesSortedBySequence().stream()
										.map(ClassEntity::getEdges)
										.flatMap(List::stream)
										.sorted(Comparator.comparingInt(Core::getSeq))
										.collect(toList());
			
		LOG.debug("getPumlForEdges:: {}", edges);
		
		Set<String> seenEntities = new HashSet<>();
		for(EdgeEntity edge : edges) {
				
			Entity containedIn = edge.getContainingEntity();
			if(!seenEntities.contains(containedIn.getName())) {
				res.append( containedIn.getCommentsBefore(edge.getSeq()) );
				seenEntities.add( containedIn.getName());
			}
		
			res.append( edge.toString() );
			res.append( NEWLINE );
		}
		
		res.append( NEWLINE );

	}

	@LogMethod(level=LogLevel.DEBUG)
	private void getPumlForClasses(StringBuilder res) {
		
		List<String> processed = new LinkedList<>();
		
		for(ClassEntity entity : getClassEntitiesSortedBySequence() ) {
			                                  
			res.append(entity.toString());
			res.append(NEWLINE);
			
			for(EnumEntity e : entity.enumEntities) {
				if(!processed.contains(e.type)) {
	
		 			res.append( e.toString() );
					res.append( NEWLINE );
					
					processed.add(e.type);

				}
			}		
		}		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private List<ClassEntity> getClassEntitiesSortedBySequence() {
		return classes.values().stream()
			.sorted(Comparator.comparingInt(ClassEntity::getSeq))
			.collect(toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addComplexityComments(StringBuilder res) {
		if(analyser!=null) {
			analyser.getComments(res);
		}	
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addComplexityNote(StringBuilder res) {

		if(analyser==null) return;
		
		Collection<String> baseTypes = APIGraph.getNodeNames( analyser.getBaseTypes() );
		Collection<String> simpleTypes = APIGraph.getNodeNames( analyser.getSimpleTypes() );

		Collection<String> usedSimpleTypes = getTypesUsedAsSimpleTypes();
		
		simpleTypes.retainAll( usedSimpleTypes );
		
		if(baseTypes.isEmpty() && simpleTypes.isEmpty()) return;
		
		List<String> complexityLegend = Config.get("complexityLegend");
		
		res.append(NEWLINE);
		for(String line : complexityLegend) {
			res.append(line);
			res.append(NEWLINE);

		}

	}

	@LogMethod(level=LogLevel.DEBUG)
	private Set<String> getTypesUsedAsSimpleTypes() {
		
		return classes.entrySet().stream()
					.map(Map.Entry::getValue)
					.map(ClassEntity::getUsedTypes)
					.flatMap(Set::stream)
					.collect(toSet());
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public ClassEntity getClassEntityForResource(String resource) {
		return classes.get(resource);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void addEnum(EnumEntity enumEntity) {		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addSubDiagram(Diagram subDiagram) {
		subDiagram.setParent(this);
		this.subDiagrams.add(subDiagram);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void setParent(Diagram parent) {
		this.parentDiagram=parent;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private Diagram getParent() {
		return this.parentDiagram;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Diagram getTopParent() {
		if(this.parentDiagram==null)
			return this;
		else
			return this.parentDiagram.getTopParent();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Diagram> getSubDiagrams() {
		return this.subDiagrams;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getLabel() {
		return this.resource;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getDiagramNames() {
		Set<String> res = new HashSet<>();
		res.add( getLabel() );
		
		res.addAll( getSubDiagrams().stream().map(Diagram::getDiagramNames).flatMap(Set::stream).collect(toSet()) );
		
		return res;
	}

	
	@LogMethod(level=LogLevel.DEBUG)
	public Collection<ClassEntity> getClasses() {
		return this.classes.values();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Collection<ClassEntity> getAllClasses() {
		Collection<ClassEntity> seenClasses = new HashSet<>();
		seenClasses.addAll( classes.values() );
		
		for(Diagram sub : getSubDiagrams() ) {
			seenClasses.addAll( sub.getAllClasses() );
		}
		return seenClasses;
	}


	@LogMethod(level=LogLevel.DEBUG)
	public boolean isResourceUsedAsSimpleType(String label) {
		return getTypesUsedAsSimpleTypes().contains(label);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void setBaseTypes(Collection<String> collection) {
		this.baseTypes = collection;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Collection<String> getBaseTypes() {
		return baseTypes;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean incompleteInSomeDiagram(String type) {
			
		return  getSubDiagrams().stream()
			        .map(Diagram::getTypesUsedAsSimpleTypes)
					.flatMap(Set::stream)
					.anyMatch(simpleType -> simpleType.contentEquals(type));

	}

	@LogMethod(level=LogLevel.DEBUG)
	public void setSimpleTypes(Collection<String> collection) {
		this.simpleTypes = collection;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Collection<String> getSimpleTypes() {
		return simpleTypes;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<String> getSubResourcesWithIncompleteDiagram() {
		Set<String> used = new HashSet<>();
		
		for(ClassEntity entity : getClasses() ) {
			Set<String> usedTypes = entity.getUsedTypes();						
			used.addAll( usedTypes );			
		}
		
		for(Diagram subDiagram : getSubDiagrams() ) {
			used.addAll( subDiagram.getSubResourcesWithIncompleteDiagram() );
		}
		
		Set<String> missingDiagram = new HashSet<>();
		
		missingDiagram.addAll( used);		
		missingDiagram.removeAll( getDiagramNames() );		
		missingDiagram.removeAll( Config.getAllSimpleTypes() );
				
		Set<String> res = new HashSet<>();

		for(ClassEntity entity : getClasses() ) {
			Set<String> usedTypes = entity.getUsedTypes();						
			usedTypes.removeAll( getDiagramNames() );		
			usedTypes.removeAll( Config.getAllSimpleTypes() );
			
			usedTypes.retainAll( missingDiagram );
			
			if(!usedTypes.isEmpty() ) res.add( entity.getName() ); 

		}

		res.addAll( missingDiagram );
		
		Predicate<String> isEnumType = APIModel::isEnumType;
		Predicate<String> isSimpleType = APIModel::isSimpleType;

		res = res.stream()
				.filter(isEnumType.negate())
				.filter(isSimpleType.negate())
				.collect(toSet());
		
		LOG.debug("getSubResourcesWithIncompleteDiagram: diagram={} res={}", this.resource, res);
		
		return res;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isUsedAsSimpleType(String subResource) {
		
		Predicate<ClassProperty> isSimpleType = ClassProperty::isSimpleType;
				
		return  getAllClasses().stream()
				.filter(cls -> cls.getName().contentEquals(subResource))
				.map(ClassEntity::getProperties)
				.flatMap(Collection::stream)
				.anyMatch(isSimpleType.negate());
		
	}

	Set<String> incomplete = new HashSet<>();
	public void addIncomplete(String name) {
		incomplete.add(name);
	}

	public void removeIncomplete(String name) {
		incomplete.remove(name);
	}
	
	public Set<String> getIncomplete() {
		return incomplete;
	}

	public String getResource() {
		return resource;
	}
}
