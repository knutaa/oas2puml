package no.paneon.api.utils;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import no.paneon.api.model.APIModel;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;


public class Config {

    private static final Logger LOG = LogManager.getLogger(Config.class);

    private static final List<String> configFiles = new LinkedList<>();
    
    private static final String NEWLINE = "\n";

    public static void setConfigSources(List<String> files) {
    	configFiles.addAll(files);
    	forceConfig();
    }
    
    private static boolean skipInternalConfiguration=false;
    public static void setSkipInternalConfiguration(boolean val) {
    	skipInternalConfiguration = val;
    }
    
    private static JSONObject json = new JSONObject();

	@LogMethod(level=LogLevel.TRACE)
    public static void getConfig() {
       	Config.init();	
    }
    
	@LogMethod(level=LogLevel.TRACE)
    public static void forceConfig() {
    	initStatus=false;
    	getConfig();
    }
    
    private Config() {    	
    }
    
	@LogMethod(level=LogLevel.TRACE)
	public static void usage() {				
		if(json!=null) {
			Out.println(
					"Default configuration json (--config option):" + "\n" + 
					json.toString(2)
					);
			Out.println();
		}
	}
	
    private static boolean initStatus = false;
    
    public static void init() {
    	if(initStatus) return;
    	initStatus = true;

    	try {
    		InputStream is ;
    		if(!skipInternalConfiguration) {
    			is = new ClassPathResource("configuration.json").getInputStream();
    			addConfiguration(is,"configuration.json");    			
    		} 
    		
    		for(String file : configFiles) {
    			Out.println("... adding configuration from file " + file);

    			is = Utils.openFileStream(workingDirectory, file);
    			
    			addConfiguration(is,file);
 
    		}
    		  	       		
    		Config.readRules();
    	    
		} catch (Exception e) {
			Out.println("Error processing configuration files: " + e);
			System.exit(1);
		}
    }
    
	private static String workingDirectory; 
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setWorkingDirectory(String directory) {
		workingDirectory = directory;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public
    static void readRules() {
    	
		LOG.log(Level.TRACE, "readRules: rulesSource={}", rulesSource);

    	if(rulesSource==null) return;
    	
    	try {
			JSONObject o = Utils.readYamlAsJSON(rulesSource,true);
			// next level, only one containing api attribute
			rules=o.getJSONObject(o.keySet().iterator().next());
			
			if(LOG.isDebugEnabled())
				LOG.log(Level.DEBUG, "setRulesSource: rules={}", rules.toString(2));

		} catch(Exception e) {
			Out.println("... unable to read rules from " + rulesSource);
			
			if(LOG.isDebugEnabled())
				LOG.log(Level.DEBUG, "setRulesSource: exception={}", e.getLocalizedMessage());
		}		
	}

	@LogMethod(level=LogLevel.TRACE)
	private static void addConfiguration(InputStream is, String name) throws InvalidJsonYamlException {
		try {
		    String config = IOUtils.toString(is, StandardCharsets.UTF_8.name());
		    
		    if(name.endsWith("yaml")) config = Utils.convertYamlToJson(config);
		    
		    JSONObject deltaJSON = new JSONObject(config); 
		    
		    addConfiguration(deltaJSON);
		} catch(Exception ex) {
			throw(new InvalidJsonYamlException());
		}
   	
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void addConfiguration(JSONObject deltaJSON) {  	 		
	    for(String key : deltaJSON.keySet()) {	    	
	    	json.put(key, deltaJSON.get(key));
	    }	   	
	}
	
	@LogMethod(level=LogLevel.TRACE)
	private static JSONObject getConfiguration() {  	 		
		return json;   	
	}
	
	public static boolean has(String property) {
		init();
		return json!=null && json.has(property);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> get(String property) {
		try {
			JSONArray array = json.optJSONArray(property);
			return array.toList().stream().map(Object::toString).collect(Collectors.toList());
		} catch(Exception e) {
			return new LinkedList<>();
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean getBoolean(String property) {
		return json.optBoolean(property);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getString(String property) {
		return json.optString(property);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Map<String,String> getStringMap(String property) {
		Map<String,String> res = new HashMap<>();

		JSONObject obj = json.optJSONObject(property);			
		if(obj != null) {
			obj.keySet().forEach(key -> res.put(key, obj.get(key).toString()) );
		}

		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getObject(String property) {
		return json.optJSONObject(property);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONArray getArray(String property) {
		return json.optJSONArray(property);
	}

	private static String rulesSource=null;
	private static JSONObject rules=null;
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setRulesSource(String rules) {
		
		if(rules==null) return;
		
		rulesSource=rules;
		readRules();
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getRules() {
		if(rules==null && rulesSource!=null) readRules();
		return rules;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getStrings(String ... args) {
		List<String> res = new LinkedList<>();
		JSONObject o = getObject(args[0]);
		
		int i=1;
		while(i<args.length-1 && o!=null) {
			o = o.optJSONObject(args[i]);
			
			if(LOG.isDebugEnabled())	
				LOG.log(Level.DEBUG, "getStrings: i={} args={} o={}", i, args[i], ((o!=null) ? o.toString(2) : "null"));

			i++;
		}
		
		if(o==null) return res;

		if(o.optJSONArray(args[i])!=null) {
			res = o.optJSONArray(args[i]).toList().stream().map(Object::toString).collect(Collectors.toList());
			
			if(LOG.isDebugEnabled())
				LOG.log(Level.DEBUG, "getStrings: res={}", Utils.dump(res));
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getStringsByPath(String path, String element) {
		List<String> res = new LinkedList<>();
				
		try {
			Object o = getObjectByPath(getConfiguration(),path);
			if(o instanceof JSONObject) {
				JSONObject jo = (JSONObject)o;
				if(jo.optJSONArray(element)!=null) {
					res = jo.optJSONArray(element).toList().stream().map(Object::toString).collect(Collectors.toList());
					
					if(LOG.isDebugEnabled())
						LOG.log(Level.DEBUG, "getStringsByPath: res={}", Utils.dump(res));
				}
			}
			
		} catch(Exception e) {
			
			LOG.log(Level.DEBUG, "getStringsByPath: exception={}", e.getLocalizedMessage());
			
		}
		
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static Object getObjectByPath(JSONObject config, String path) {
		Object res = null;
		
		try {
			path = "#/" + path.replace(".",  "/");
			res = config.query(path);
			
		} catch(Exception ex) {
			Out.println("... configuration for '" + path + "' not found - exception: " + ex.getLocalizedMessage());
			Out.println("... configutation seen: " + config.toString(2));
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getList(JSONObject config, String key) {
		List<String> res = new LinkedList<>();
		
		if(config==null) return res;
		
		if(config.optJSONArray(key)!=null) {
			res.addAll(config.optJSONArray(key).toList().stream().map(Object::toString).collect(Collectors.toList()));
		}
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getConfig(String key) {
		
		if(LOG.isTraceEnabled()) {
			LOG.log(Level.TRACE, "getConfig: key={} model={}", key, json.toString(2));
		}
	
		return json.optJSONObject(key);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getConfig(JSONObject config, String key) {
		JSONObject res=config;
		
		if(config==null) return res;
		
		JSONObject direct=config.optJSONObject(key);
		if(direct==null) {
			key=config.optString(key);
			if(!key.isEmpty()) res=getConfig(key);
		} else {
			res=direct;
		}
		return res;
	}


	public static void setBoolean(String key, boolean value) {
		json.put(key, value);
	}

	public static Map<String, String> getTypeMapping() {
		return getMap("typeMapping");
	}

	public static Map<String, String> getFormatToType() {
		return getMap("formatToType");
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Map<String,String> getMap(String property) {
		Map<String,String> res = new HashMap<>();
		
		JSONObject obj = json.optJSONObject(property);
		
		obj.keySet().stream().forEach(key -> res.put(key,  obj.opt(key).toString()));

		return res;
	
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getSimpleTypes() {
		if(has("simpleTypes")) {
			return get("simpleTypes");
		} else {
			return new LinkedList<>( Arrays.asList("TimePeriod", "Money", "Quantity", "Tax", 
								 	 "Value", "Any", "object", "Number", "Date") );
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getSimpleEndings() {
		if(has("simpleEndings")) {
			return get("simpleEndings");
		} else {
			return Arrays.asList("Type", "Error");
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getNonSimpleEndings() {
		if(has("nonSimpleEndings")) {
			return get("nonSimpleEndings");
		} else {
			return Arrays.asList("RefType", "TypeRef");
		}
	}  
	
	
	private static String prefix = "";
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setPrefixToRemove(String str) {
		prefix=str;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String getPrefixToRemove() {
		return prefix;
	}
	

	private static String replacePrefix = "";
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setPrefixToReplace(String str) {
		replacePrefix=str;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String getPrefixToReplace() {
		return replacePrefix;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean getCompressCustomTypes() {
		return getBoolean("compressCustomTypes");
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getBaseTypesForResource(String resource) {
		List<String> res = new LinkedList<>();
		JSONObject baseTypeConfig = getJSONObject("baseTypes");
		
		JSONArray config = null;
		if(baseTypeConfig.has(resource)) {
			config = baseTypeConfig.optJSONArray(resource);
		} else {
			config = baseTypeConfig.optJSONArray("common");
		}
	
		if(config!=null) {
			res.addAll(config.toList().stream().map(Object::toString).collect(toList()));
		}
		
		return res;
	
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getLayout() {
		return getJSONObject("layout");
	}

	@LogMethod(level=LogLevel.TRACE)
	private static JSONObject getJSONObject(String label) {
		if(json.optJSONObject(label)!=null) 
			return json.optJSONObject(label);
		else
			return new JSONObject();
	}

	public static JSONObject getBaseTypes() {
		return getJSONObject("baseTypes");
	}
	
	private static Optional<Boolean> includeDebug = Optional.empty();

	@LogMethod(level=LogLevel.TRACE)
	public static void setIncludeDebug(boolean value) {
		LOG.debug("setIncludeDebug: " + value);

		includeDebug=Optional.of(value);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean getIncludeDebug() {
		boolean res=false;
		if(has("includeDebug")) 
			res=getBoolean("includeDebug");
		else
			res=includeDebug.isPresent() && includeDebug.get();
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getRequiredFormatting() {
		//if(optRequiredHighlighting.isPresent() && optRequiredHighlighting.get() && has("requiredHighlighting")) {
		if(getUseRequiredHighlighting()) {
			return getString("requiredHighlighting");
		} else {
			return "%s";
		}
	}
	
	private static Optional<Boolean> optRequiredHighlighting = Optional.empty();
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setRequiredHighlighting(boolean value) {
		if(value) optRequiredHighlighting=Optional.of(value);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean getUseRequiredHighlighting() {
		LOG.debug("useRequiredHighlighting: opt=" + optIncludeDescription);
		if(has("useRequiredHighlighting")) {
			return getBoolean("useRequiredHighlighting");
		} else if(optRequiredHighlighting.isPresent()) {
			return optRequiredHighlighting.get();
		} else {
			return false;
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean includeDescription() {
		if(optIncludeDescription.isPresent()) 
			return optIncludeDescription.get();
		else if(has("includeDescription")) {
			return getBoolean("includeDescription");
		} else {
			return false;
		}
	}
	
	private static Optional<Boolean> optIncludeDescription = Optional.empty();
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setIncludeDescription(boolean value) {
		if(value) optIncludeDescription = Optional.of(value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getOrphanEnums() {
		List<String> res = new LinkedList<>();
		if(has("orphan-enums")) {
			res = get("orphan-enums");
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getOrphanEnums(String resource) {
		List<String> res = new LinkedList<>();
		
		JSONObject config = getJSONObject("orphan-enums-by-resource");
		
		if(config.has(resource)) {
			JSONArray enums = config.optJSONArray(resource);
			if(enums!=null)
				res = enums.toList().stream().map(Object::toString).collect(toList());
		}
				
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean getIncludeOrphanEnums() {
		boolean res = false;
		if(has("include-orphan-enums")) {
			res = getBoolean("include-orphan-enums");
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setOrphanEnums(String configFile) {
		try {
			JSONObject enum_config = null;
			if(configFile!=null) {
				LOG.debug("setOrphanEnums:: configFile=" + configFile);
				enum_config = Config.readJSONOrYaml(configFile);
			
				List<String> resources=new LinkedList<>();
				if(enum_config.has("orphan-enums") && enum_config.optJSONArray("orphan-enums")!=null) {
					resources.addAll( enum_config.getJSONArray("orphan-enums").toList().stream().map(Object::toString).collect(toList()) );
					
				}
				
				JSONObject config = enum_config.optJSONObject("orphan-enums-by-resource");
				if(config!=null) {
					json.put("orphan-enums-by-resource", config);
					resources.addAll( config.keySet().stream()
										.filter(item -> !resources.contains(item))
										.collect(toList()));
										
				}

				if(!resources.isEmpty()) json.put("orphan-enums", resources);

			}
			
		} catch(Exception e) {
			Out.println("error reading file: " + configFile);
			System.exit(0);
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject readJSONOrYaml(String file) {
		JSONObject res = null;
		try {
			if(file.endsWith(".yaml") || file.endsWith(".yml")) 
				res = readYamlAsJSON(file,false);
			else
				res = readJSON(file,false);
		} catch(Exception e) {
			Out.println("... unable to read file " + file + " (error: " + e.getLocalizedMessage() + ")");
			System.exit(0);
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	static JSONObject readJSON(String fileName, boolean errorOK) throws Exception {
		try {
			String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
	        File file = new File(path);
	        String content = FileUtils.readFileToString(file, "utf-8");
	        return new JSONObject(content); 
		} catch(Exception ex) {
			if(!errorOK) throw(ex);
			return new JSONObject();
		}
    }
	
	@LogMethod(level=LogLevel.TRACE)
	static JSONObject readYamlAsJSON(String fileName, boolean errorOK) throws Exception {
		try {
			String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
	        File file = new File(path);
	        String yaml = FileUtils.readFileToString(file, "utf-8");
	        String json = convertYamlToJson(yaml);
	        return new JSONObject(json); 
		} catch(Exception ex) {
			if(!errorOK) throw(ex);
			return new JSONObject();
		}
    }
	
	@LogMethod(level=LogLevel.TRACE)
	static String convertJsonToYaml(JSONObject json) throws Exception {
		YAMLFactory yamlFactory = new YAMLFactory()	
			 .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES) 
	         .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
	         // .enable(YAMLGenerator.Feature.INDENT_ARRAYS)
	         ;
		
		YAMLMapper mapper = new YAMLMapper(yamlFactory);
	    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

		
	    ObjectMapper jsonMapper = new ObjectMapper();
	    jsonMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	    
	    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);

	    JsonNode json2 = mapper.readTree(json.toString());
	    
	    final Object obj = jsonMapper.treeToValue(json2, Object.class);
	    final String jsonString = jsonMapper.writeValueAsString(obj);

	    LOG.debug("convertJsonToYaml: json=" + jsonString);
	    
	    JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
        String jsonAsYaml = mapper.writeValueAsString(jsonNodeTree);
        return jsonAsYaml;
        
	}
	
	@LogMethod(level=LogLevel.TRACE)
    static String convertYamlToJson(String yaml) throws Exception {
	    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
	    Object obj = yamlReader.readValue(yaml, Object.class);

	    ObjectMapper jsonWriter = new ObjectMapper();
	    return jsonWriter.writeValueAsString(obj);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean getFloatingEnums() {
		boolean res=false;
		if(has("floatingEnums")) 
			res=getBoolean("floatingEnums");
		else
			res=floatingEnums.isPresent() && floatingEnums.get();
		return res;
	}

	private static Optional<Boolean> floatingEnums = Optional.empty();

	@LogMethod(level=LogLevel.TRACE)
	public static void setFloatingEnums(boolean value) {
		floatingEnums=Optional.of(value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void setArguments(String argfile) {
		if(argfile!=null) {
			 JSONObject args = readJSONOrYaml(argfile);
			 for(String key : args.keySet() ) {
				 json.put(key, args.get(key));
			 }
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean showDefaultCardinality() {
		if(has("showDefaultCardinality")) {
			return getBoolean("showDefaultCardinality");
		} else {
			return false;
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getDefaultCardinality() {
		if(has("defaultCardinality")) {
			return getString("defaultCardinality");
		} else {
			return "0..1";
		}
	}

	private static Optional<Boolean> showAllCardinality = Optional.empty();
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setShowAllCardinality(boolean value) {
		showAllCardinality=Optional.of(value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean hideCardinalty(String cardinality) {		
		if(showAllCardinality.isPresent() && showAllCardinality.get())  
			return false;
		
		if(showDefaultCardinality()) 
			return false;
		
		return cardinality.equals(getDefaultCardinality());
	}
	
	 
	@LogMethod(level=LogLevel.TRACE)
	public static String getPuml() {
		if(has("puml")) {
			return String.join(NEWLINE, get("puml"));
		} else {
			return "@startuml" + NEWLINE +
			"'default config" + NEWLINE + 
            "hide circle" + NEWLINE +
            "hide methods" + NEWLINE +
            "hide stereotype" + NEWLINE +
            "show <<Enumeration>> stereotype" + NEWLINE +
            "skinparam class {" + NEWLINE +
            "   BackgroundColor<<Enumeration>> #E6F5F7" + NEWLINE +
            "   BackgroundColor<<Ref>> #FFFFE0" + NEWLINE +
            "   BackgroundColor<<Pivot>> #FFFFFFF" + NEWLINE +
            "   BackgroundColor #FCF2E3" + NEWLINE +
            "}" + NEWLINE +
            NEWLINE +
			"skinparam legend {" + NEWLINE +
		    "   borderRoundCorner 0" + NEWLINE +
		    "   borderColor red" + NEWLINE +
		    "   backgroundColor white" + NEWLINE +
			"}" + NEWLINE
            ;
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean includeHiddenEdges() {
		if(has("includeHiddenEdges")) {
			return getBoolean("includeHiddenEdges");
		} else {
			return false;
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static int getMaxLineLength() {
		int res=80;
		if(has("maxLineLength")) {
			res=json.getInt("maxLineLength");
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean hideCardinaltyForProperty(String cardinality) {
		return !getBoolean("showCardinalitySimpleProperties");
	}

	public static List<String> getAllSimpleTypes() {
		List<String> res = getSimpleTypes();
				
		res.addAll( APIModel.getMappedSimpleTypes() );
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean getSimplifyRefOrValue() {
		return getBoolean("simplifyRefOrValue");
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getDefaultStereoType() {
		return getString("defaultStereoType");
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean processComplexity() {
		return getBoolean("processComplexity");
	}

    public static void setDefaults(String file) {
    	if(file!=null) {
     		JSONObject defaults = Utils.readJSONOrYaml(file);		
    		addConfiguration(defaults);
    	}
    }

	@LogMethod(level=LogLevel.TRACE)
	private static void set(String label, Object value) {
		json.put(label,value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void setLayout(String layout) {
		if(layout!=null) {
			JSONObject o = readJSONOrYaml(layout);
			if(!o.isEmpty()) set("layout",o);
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void setConfig(String config) {
		if(config!=null) {
			JSONObject o = readJSONOrYaml(config);
			addConfiguration(o);
		}
	}

	public static List<String> getFlattenInheritance() {
		return get("coreInheritanceTypes");
	}

	public static List<String> getFlattenInheritanceRegexp() {
		return get("coreInheritanceRegexp");
	}
	
	public static List<String> getSubClassesExcludeRegexp() {
		return get("subClassExcludeRegexp");
	}

	public static int getInteger(String property) {
		try {
			String s = getString(property);
			return Integer.valueOf(s);
		} catch(Exception e) {
			Out.debug("... unable to process configuration property '{}' - expecting integer value", property);
			return 0;
		}
	}


}
