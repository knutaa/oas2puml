package no.paneon.api.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;

import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class APIModel {

	static final Logger LOG = LogManager.getLogger(APIModel.class);

	private static JSONObject swagger;

	static final Map<String,String> formatToType = new HashMap<>();
	static final Map<String,String> typeMapping = new HashMap<>();

	private static JSONObject resourceMapping;
	private static JSONObject reverseMapping;

	public static final List<String> ALL_OPS = Arrays.asList("GET", "POST", "DELETE", "PUT", "PATCH");

	private static String swaggerSource;
	
	static {    	

		formatToType.put("date-time", "DateTime");
		formatToType.put("date", "Date");
		formatToType.put("float", "Float");
		formatToType.put("uri", "Uri");
		formatToType.put("url", "Url");

		typeMapping.put("integer", "Integer");
		typeMapping.put("string", "String");
		typeMapping.put("boolean", "Boolean");
		typeMapping.put("number", "Number");


	}

	private APIModel() {
	}

	public APIModel(JSONObject api) {
		setSwagger(api);
	}

	private APIModel(String swaggerSource) {
		setSwagger(Utils.readJSONOrYaml(swaggerSource));
	}

	public APIModel(String filename, File file) {
		try {
			InputStream is = new FileInputStream(file);
			setSwagger(Utils.readJSONOrYaml(is));
		} catch(Exception ex) {
			Out.println("... unable to read API specification from file '" + filename + "'");
			System.exit(0);
		}
		
	}

	public String toString() {
		return swagger.toString(2);
	}

	public static void clean() {
		allDefinitions = new JSONObject();	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static void setSwagger(JSONObject api) {
		swagger = api;
		
		LOG.debug("setSwagger:: keys={}", swagger.keySet());

		rearrangeDefinitions(swagger);

	}

	private static void rearrangeDefinitions(JSONObject api) {
		
		LOG.debug("rearrangeDefinitions:: keys={}", api.keySet());

		for(String type : getAllDefinitions() ) {
			JSONObject definition = getDefinition(type);
			if(definition!=null && !definition.has(PROPERTIES)) {
				if(definition.has(ALLOF)) {
					JSONArray rewrittenAllOfs = new JSONArray();

					JSONArray allOfs = definition.optJSONArray(ALLOF);
					allOfs.forEach(allOf -> {
						if(allOf instanceof JSONObject) {
							JSONObject obj = (JSONObject) allOf;
							if(obj.has(REF)) {
								rewrittenAllOfs.put(obj);
							} else if(obj.has(PROPERTIES)) {
								definition.put(PROPERTIES, obj.get(PROPERTIES));
								if(obj.has(REQUIRED)) definition.put(REQUIRED, obj.get(REQUIRED));
								if(obj.has(DESCRIPTION)) definition.put(DESCRIPTION, obj.get(DESCRIPTION));
								if(obj.has(TYPE)) definition.put(TYPE, obj.get(TYPE));

								LOG.debug("rearrangeDefinitions:: rearrange type={} obj={}", type, definition.get(PROPERTIES));
							}
						} else {
							rewrittenAllOfs.put(allOf);
							LOG.debug("rearrangeDefinitions:: unexpected array element for type={} element={}", type, allOf);
						}
					});
					
					definition.put(ALLOF, rewrittenAllOfs);
					
				}
				
			}
		}
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static void setSwaggerSource(String filename) {
		swaggerSource = filename;
		// setSwagger(Utils.readJSONOrYaml(swaggerSource));
	}

	private static final String FORMAT = "format";
	private static final String TYPE = "type";
	private static final String ARRAY = "array";
	private static final String OBJECT = "object";
	private static final String REF = "$ref";
	private static final String ITEMS = "items";
	private static final String PATHS = "paths";
	private static final String PROPERTIES = "properties";
	private static final String ENUM = "enum";
	private static final String RESPONSES = "responses";
	private static final String SCHEMA = "schema";
	private static final String DESCRIPTION = "description";
	private static final String MIN_ITEMS = "minItems";
	private static final String MAX_ITEMS = "maxItems";

	private static final String REQUIRED = "properties";

	private static final String NOTIFICATIONS = "notifications";

	private static final String ALLOF = "allOf";
	private static final String ONEOF = "oneOf";

	private static final String DISCRIMINATOR = "discriminator";
	private static final String MAPPING = "mapping";

	private static final String NEWLINE = "\n";

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getResources() {

		List<String> res = getCoreResources(); 
			
		res.addAll( getSubclassesByResources(res) );
		
		LOG.debug("getResources:: {}", res);
		
		return res;
		
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getCoreResources() {

		List<String> res = getAllResponses()
				.map(APIModel::getNormalResponses)
				.flatMap(List::stream)
				.map(APIModel::getResourceFromResponse)
				.flatMap(List::stream)
				.distinct()
				.collect(toList());

		LOG.debug("getCoreResources:: {}", res);
		
		return res;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAllNotifications() {
		return getPaths().stream()
				.filter(x -> x.startsWith("/listener/"))
				.map(x -> x.replaceAll(".*/([A-Za-z0-9.]*)", "$1"))
				.distinct()
				.map(Utils::upperCaseFirst)
				.collect(Collectors.toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Stream<JSONObject> getAllResponses() {
		return getPaths().stream()
				.map(APIModel::getPathObjectByKey)
				.map(APIModel::getChildElements)
				.flatMap(List::stream)
				.filter(APIModel::hasResponses)
				.map(APIModel::getResponseEntity);

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getResponseEntity(JSONObject obj) {
		return obj.optJSONObject(RESPONSES);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getResourceFromResponse(JSONObject obj) {
		List<String> res = new LinkedList<>();

		if(obj.has(REF)) obj = APIModel.getDefinitionByReference(obj.optString(REF));

		JSONObject schema = getSchemaFromResponse(obj);

		if(schema!=null) {
			if(schema.has(REF)) {
				res.add(schema.getString(REF));
			} else {
				Object ref = schema.optQuery( "/" + ITEMS + "/" + REF);
				if(ref!=null) {
					res.add(ref.toString());
				}
			}
		}

		res = res.stream().map(str -> str.replaceAll("[^/]+/","")).collect(toList());

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getSubclassesByResources(List<String> coreResources) {
		List<String> res = coreResources.stream()
							.map(APIModel::getSubclassesByResource) 
							.flatMap(List::stream)
							.collect(toList());
		
		LOG.debug("getSubclassesByResources: coreResources={} res={}", coreResources, res);
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getSubclassesByResource(String resource) {
		List<String> res = new LinkedList<>();

		res = APIModel.getAllDefinitions().stream()
				.filter(subResource -> isSubclass(subResource,resource))
				.filter(APIModel::includeSubclass)
				.collect(toList());

		LOG.debug("getSubclassesByResource: resource={} res={}", resource, res);
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static boolean includeSubclass(String resource) {
		
		List<String> excludePattern = Config.getSubClassesExcludeRegexp();

		boolean exclude = excludePattern.stream().anyMatch(pattern -> resource.matches(pattern));
				
		return !exclude;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isSubclass(String subResource, String resource) {
		boolean res=false;
		
		// JSONObject sub = getDefinition(subResource);
		JSONArray allOfs = APIModel.getAllOfForResource(subResource);
		Iterator<Object> iter = allOfs.iterator();
		while(iter.hasNext()) {
			Object o = iter.next();
			if(o instanceof JSONObject) {
				res = res || isSubclass((JSONObject)o,resource);
			}
		}
		return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isSubclass(JSONObject refs, String resource) {
		boolean res=false;
		
		String ref = refs.optString(REF);
		
		res = !ref.isEmpty() && ref.endsWith("/" + resource);

		return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getSchemaFromResponse(JSONObject respObj) {
		JSONObject res=null;
		// V3 navigation
		if(respObj.has("content")) respObj=respObj.getJSONObject("content");
		Optional<String> application = respObj.keySet().stream().filter(key -> key.startsWith("application/json")).findFirst();
		if(application.isPresent()) {
			respObj=respObj.getJSONObject(application.get());
		}

		if(respObj.has(SCHEMA)) res=respObj.getJSONObject(SCHEMA);
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String removePrefix(String resource) {
		final String prefix = Config.getPrefixToRemove();
		final String replacement = Config.getPrefixToReplace();

		String res = resource.replaceAll(prefix,replacement);

		LOG.debug("removePrefix resource={} prefix={} res={}", resource, prefix, res);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	boolean isSimpleType(String type, String property) {
		boolean res=true;
		JSONObject propertySpecification = getPropertySpecification(type,property);

		if(propertySpecification==null) return res;

		if(propertySpecification.has(TYPE)) {
			String jsonType = propertySpecification.getString(TYPE);
			res = !jsonType.equals(OBJECT) && !jsonType.equals(ARRAY);
		} 

		if(propertySpecification.has(ITEMS)) propertySpecification=propertySpecification.getJSONObject(ITEMS);

		if(propertySpecification.has(REF)) {
			String referencedType = getReferencedType(type, property);
			res = isSimpleType(referencedType);
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isSimpleType(String type) {
		boolean res=true;
		JSONObject definition = getDefinition(type);

		LOG.debug("isSimpleType: type={} definition={}", type, definition);

		if(definition!=null) {
			if(definition.has(TYPE)) {
				String jsonType = definition.getString(TYPE);

				if(jsonType.equals(OBJECT) || jsonType.equals(ARRAY)) res=false;
				
			} else {

				if(definition.has(DISCRIMINATOR)) res=false;
				if(definition.has(ALLOF)) res=false;

				if(definition.has(ITEMS) && definition.optJSONObject(ITEMS)!=null) definition=definition.getJSONObject(ITEMS);

				if(definition.has(REF)) {
					String referencedType = getTypeByReference(definition.optString(REF));
					res = isSimpleType(referencedType);
				}

			}
		} 
		
		LOG.debug("isSimpleType: type={} res={}", type, res);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isCustomSimple(String type) {
		boolean res=false;
		JSONObject definition = getDefinition(type);

		if(definition!=null) {
			res = definition.has(TYPE) && ARRAY.contentEquals(definition.optString(TYPE));

			// res = res || definition.has(ALLOF);
		}
		
		LOG.debug("isCustomSimple: type={} res={} definition={}", type, res, definition);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isArrayType(String type, String property) {
		return isArrayType( getPropertySpecification(type,property) );
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isArrayType(JSONObject property) {
		boolean res=false;

		if(property!=null && property.has(TYPE)) {
			String jsonType = property.optString(TYPE);
			if(jsonType!=null) res = jsonType.equals(ARRAY);
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getReferencedType(String type, String property) {
		JSONObject specification = getPropertySpecification(type,property);
		return getReferencedType(specification,property);	    	    
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getReferencedType(JSONObject specification, String property) {
		String res="";

		if(specification!=null && specification.has(property)) specification = specification.optJSONObject(property);

		if(specification!=null && specification.has(ITEMS)) {
			specification = specification.optJSONObject(ITEMS);
		}
		if(specification!=null && specification.has(REF)) {
			String ref=specification.optString(REF);
			if(ref!=null) {
				String[] parts=ref.split("/");
				res = parts[parts.length-1];
			}
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getTypeByReference(String ref) {
		String[] parts=ref.split("/");
		return parts[parts.length-1];
	}

	@LogMethod(level=LogLevel.DEBUG)
	static JSONObject getDefinitionByReference(String ref) {
		JSONObject res = new JSONObject();

		String[] parts=ref.split("/");

		if(parts[0].contentEquals("#")) res = swagger;

		for(int idx=1; idx<parts.length; idx++) {
			if(res.has(parts[idx])) res = res.optJSONObject(parts[idx]);
		}

		if(res.has(REF)) res = getDefinitionByReference(res.optString(REF));

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	protected static JSONObject getPropertySpecification(String resource, String property) {
		JSONObject res=getPropertyObjectForResource(resource);
		res = res.optJSONObject(property);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	protected static JSONObject getPropertySpecification(JSONObject resource, String property) {
		JSONObject res=null;
		if(resource.has(PROPERTIES)) resource=resource.optJSONObject(PROPERTIES);
		if(resource!=null) res = resource.optJSONObject(property);
		return res;
	}

	private static Map<String, JSONObject> resourcePropertyMap = new HashMap<>();

	@LogMethod(level=LogLevel.DEBUG) 
	public static JSONObject getPropertyObjectForResource(String coreResource) {
		JSONObject res=null;
		if(resourcePropertyMap.containsKey(coreResource)) {
			res = resourcePropertyMap.get(coreResource);
		} else {
			res = getDefinition(coreResource, PROPERTIES);
			if(res!=null) {
				resourcePropertyMap.put(coreResource, res);
			}
		}
		if(res==null) res=new JSONObject();

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG) 
	public static JSONArray getAllOfForResource(String coreResource) {
		return getDefinitions(coreResource, ALLOF);
	}

	@LogMethod(level=LogLevel.DEBUG) 
	public static JSONObject getPropertyObjectForResource(JSONObject resource) {
		if(resource!=null && resource.has(PROPERTIES)) resource=resource.optJSONObject(PROPERTIES);

		if(resource==null) resource=new JSONObject();
		return resource;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getPropertiesForResource(String resource) {
		JSONObject obj = getPropertyObjectForResource(resource);
		return obj.keySet();
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getProperties(JSONObject obj) {
		Set<String> res = new HashSet<>();

		if (obj == null){
			return res;
		}
		if(obj.has(PROPERTIES)) obj=obj.optJSONObject(PROPERTIES);
		if(obj!=null) res = obj.keySet();
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getDefinition(String ... args) {
		JSONObject res = null;
		if(args.length>0) {
			res = getDefinition(args[0]);
			int idx=1;
			while(res!=null && idx<args.length) {
				res = res.optJSONObject(args[idx]);
				idx++;
			}
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static JSONArray getDefinitions(String ... args) {
		JSONObject obj = null;
		JSONArray res = null;

		if(args.length>0) {
			obj = getDefinition(args[0]);
			int idx=1;
			while(obj!=null && idx<args.length-1) {
				obj = obj.optJSONObject(args[idx]);
				idx++;
			}
			if(obj!=null && idx<args.length) res = obj.optJSONArray(args[idx]);
		}

		if(res==null) res = new JSONArray();

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isEnumType(String type) {
		boolean res=false;
		JSONObject definition = getDefinition(type);
		if(definition!=null) {
			res = definition.has(ENUM);
		}
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getPaths() {
		if(swagger!=null && swagger.has(PATHS))
			return swagger.getJSONObject(PATHS).keySet();
		else
			return new HashSet<>();
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static List<JSONObject> getChildElements(JSONObject obj) {
		return new JSONObjectHelper(obj).getChildElements();
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static List<JSONObject> getNormalResponses(JSONObject respObj) {
		if(respObj==null) return new LinkedList<>();

		Set<String> keys = respObj.keySet().stream()
				.filter(resp -> !"default".equals(resp) && Integer.parseInt(resp)<300)
				.collect(toSet());

		return new JSONObjectHelper(respObj, keys).getChildElements(); 


	}


	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getPathObjectByKey(String path) {
		return swagger.getJSONObject(PATHS).getJSONObject(path);
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static boolean hasResponses(JSONObject obj) {
		return obj.has(RESPONSES);
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isOpenAPIv2(JSONObject swagger) {
		return swagger!=null && !swagger.has("openapi");
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getDefinition(String node) {

		JSONObject res;
		JSONObject definitions = getDefinitions();

		if(definitions==null) {
			res=null;
		} else if(definitions.optJSONObject(node)!=null) {
			res = definitions.optJSONObject(node);
		} else if(!Config.getPrefixToRemove().isEmpty()) {
			Optional<String> actualDefinition = definitions.keySet().stream().filter(s -> removePrefix(s).contentEquals(node)).findFirst();
			LOG.debug("getDefinition: node={} actualDefinition={}", node, actualDefinition);

			if(actualDefinition.isPresent()) {
				res = definitions.optJSONObject(actualDefinition.get());
			} else {
				res = null;
			}
		} else {
			res = null;
		}

		LOG.debug("getDefinition: node={} res={}", node, res!=null? res.toString() : null);

		return res;	
	}

	static JSONObject allDefinitions = new JSONObject();

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getDefinitions() {
		if(swagger!=null && allDefinitions.keySet().isEmpty()) {		
			JSONObject res=null;
			if(isOpenAPIv2(swagger))
				res=swagger.optJSONObject("definitions");
			else {
				JSONObject components = swagger.optJSONObject("components");
				if(components!=null) res = components.optJSONObject("schemas");
			}
			if(res!=null) allDefinitions = res;
		}
		return allDefinitions;
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getPaths(String resource, String operation) {
		List<String> res = new LinkedList<>();

		if(swagger==null) return res;

		JSONObject allpaths = swagger.optJSONObject(PATHS);

		String prefix = "/" + resource.toUpperCase();

		List<String> paths = allpaths.keySet().stream()
				.filter(path -> isPathForResource(path,prefix))
				.collect(toList());

		paths.forEach(path -> {
			JSONObject allOps = allpaths.optJSONObject(path);
			if(allOps!=null && allOps.has(operation.toLowerCase())) res.add(path); 
		});

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getPaths(String resource) {
		List<String> res = new LinkedList<>();

		if(swagger==null) return res;

		JSONObject allpaths = swagger.optJSONObject(PATHS);

		String prefix = "/" + resource.toUpperCase();

		return allpaths.keySet().stream()
				.filter(path -> isPathForResource(path,prefix))
				.collect(toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isPathForResource(String path, String prefix) {
		return path.equalsIgnoreCase(prefix) || path.toUpperCase().startsWith(prefix+"/");
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getOperationDescription(String path, String operation) {
		String res="";
		try {
			res = getPathObjectByKey(path).optJSONObject(operation.toLowerCase()).getString(DESCRIPTION);
		} catch(Exception e) {
			LOG.debug(String.format("Unable to find description for path=%s and operation=%s", path, operation));
		}
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	public Map<String, List<String>> getAllNotifications(List<String> resources, JSONObject rules) {
		Map<String,List<String>> res = new HashMap<>();

		if(rules==null) {
			Out.println("... API rules not found - unable to process notification conformance");
			return res;
		}

		for( String resource : resources) {
			String key = "rules " + resource;
			JSONObject rule = rules.optJSONObject(key);
			if(rule!=null) {
				JSONArray notif = rule.optJSONArray(NOTIFICATIONS);
				if(notif!=null) {
					res.put(resource, notif.toList().stream().map(Object::toString).collect(toList()));
				}
			}
		}
		return res;
	}

	private static boolean firstAPImessage=true;
	private static void setSeenAPImessage() {
		firstAPImessage=false;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getNotificationsByResource(String resource, JSONObject rules) {
		List<String> res = new LinkedList<>();

		if(rules==null || rules.isEmpty()) {
			if(firstAPImessage) Out.println("... API rules not found - extracting notification support from API");
			setSeenAPImessage();
			return getNotificationsFromSwagger(resource);
		}

		String key = "rules " + resource;

		JSONObject rule = rules.optJSONObject(key);
		if(rule!=null) {
			JSONArray notif = rule.optJSONArray(NOTIFICATIONS);
			if(notif!=null) {
				res.addAll(notif.toList().stream().map(Object::toString).collect(toList()));
			} else {
				String list = rule.optString(NOTIFICATIONS);
				if(!list.isEmpty()) {
					String[] parts = list.split(",");
					if(parts.length>0) {
						res.addAll(Arrays.asList(parts));
					}
				}
			}
		}

		res = res.stream()
				.map(notification -> getNotificationLabel(resource, notification))
				.collect(toList());

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getNotificationsFromSwagger(String resource) {				
		return getAllDefinitions().stream().filter(x -> x.startsWith(resource) && x.endsWith("Event")).collect(toList());			
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAllDefinitions() {
		return getDefinitions().keySet().stream().collect(toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static String getNotificationLabel(String resource, String notification) {
		return resource + notification.substring(0,1).toUpperCase() + notification.substring(1) + "Event";
	}

	public static JSONObject getInfo() {
		return swagger.optJSONObject("info");
	}

	public static List<String> getEnumValues(String orphanEnum) {
		List<String> res = new LinkedList<>();
		JSONObject def = getDefinition(orphanEnum);
		if(def!=null && def.has("enum")) {
			JSONArray values = def.optJSONArray("enum");
			if(values!=null) res.addAll(values.toList().stream().map(Object::toString).collect(toList()));
		}
		return res;
	}

	public static Collection<String> getAllReferenced() {
		List<String> res = new LinkedList<>();

		for(String resource: getAllDefinitions() ) {
			res.addAll( getAllReferenced(resource) );
		}

		return res.stream().distinct().collect(toList());
	}

	private static List<String> getAllReferenced(String resource) {
		List<String> res = new LinkedList<>();

		JSONObject def = getDefinition(resource);

		if(def!=null && def.has(PROPERTIES) && def.optJSONObject(PROPERTIES)!=null) {

			res.addAll( getAllReferenced( def ));
		}

		return res;		
	}

	private static List<String> getAllReferenced(JSONObject definition) {
		List<String> res = new LinkedList<>();

		for(String property : getProperties(definition) ) {
			JSONObject o = getProperty(definition,property);
			if(o!=null) {
				if(o.has(REF)) {
					String ref = o.optString(REF);
					if(!ref.isEmpty()) res.add( lastElement(ref,"/") );
					//				} else if (o.has(ITEMS) ) {
					//					String ref = o.optJSONObject(ITEMS).optString(REF);
					//					if(!ref.isEmpty()) res.add( lastElement(ref,"/") );
					//				}
				} else {
					Object ref = o.optQuery( "/" + ITEMS + "/" + REF);
					if(ref!=null) {
						String sref = ref.toString();
						if(!sref.isEmpty()) res.add( lastElement(sref,"/") );
					}
				}
			}
		}

		return res;
	}

	private static JSONObject getProperty(JSONObject definition, String property) {
		JSONObject res = null;

		if(definition!=null && definition.has(PROPERTIES) && definition.optJSONObject(PROPERTIES)!=null) {
			JSONObject properties = definition.optJSONObject(PROPERTIES);
			res = properties.optJSONObject(property);
		}

		return res;

	}

	private static String lastElement(String ref, String delim) {
		String[] s = ref.split(delim);
		return s[s.length-1];
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String type(JSONObject property, String ref) {
		if(ref!=null) {
			return ref;
		} else {
			return type(property);
		}
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String type(JSONObject property) {

		String res="";

		if(property==null) {
			return res;
		} else if(property.has(FORMAT)) {
			String format = property.getString(FORMAT);
			String formatMapping = formatToType.get(format);

			if(formatMapping!=null) {
				res=formatMapping;

			} else if (Config.getFormatToType().containsKey(format)) {
				res = Config.getFormatToType().get(format);

			} else {
				LOG.warn("... ... format: {} has no mapping, using type and format", format);
				res = property.getString(TYPE) + '/' + format;
			}

		} else if(property.has(REF)) {

			res = getReference(property);

		} else if(property.has(ITEMS)) {

			res = type( property.optJSONObject(ITEMS) );

		} else if(property.has(TYPE)) {

			String type = property.optString(TYPE);
			if(typeMapping.containsKey(type)) {
				res = typeMapping.get(type);
			} else if(Config.getTypeMapping().containsKey(type)) {
				res = Config.getTypeMapping().get(type);
			} else {
				res = type;
			}

		} else {
			res = property.toString(); // should not really happen
		}

		return res;

	}


	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getType(JSONObject property) {
		if(property.has(ITEMS) && property.optJSONObject(ITEMS)!=null) {
			property = property.optJSONObject(ITEMS);
			return getType(property);
		}
		return property;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getTypeName(JSONObject property) {

		String res;
		if(property==null) {
			res = "";
		} else if(property.has(ITEMS)) {
			property = property.optJSONObject(ITEMS);
			res = getTypeName(property);
		} else if(property.has(REF)) {
			res = getReference(property); 
		} else {
			res = property.optString(TYPE);
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCardinality(JSONObject property, boolean isRequired) {
		return getCardinality(property,isRequired, Optional.empty(), Optional.empty());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCardinality(JSONObject property, boolean isRequired, Optional<Integer> minItems, Optional<Integer> maxItems) {

		int min=0;
		String max="*";

		if(minItems.isPresent()) {
			min = minItems.get(); 
		} else if(property.has(MIN_ITEMS)) {
			min=property.optInt(MIN_ITEMS);
		} else if(isRequired) {
			min=1;
		}

		if(maxItems.isPresent()) {
			max = Integer.toString(maxItems.get());
		} else if(property.has(MAX_ITEMS)) {
			int optMax=property.optInt(MAX_ITEMS);
			max = Integer.toString(optMax);
		}

		String res="";
		if( isArrayType(property) ) { 
			res = Integer.toString(min) +  ".." + max;
		} else {
			res = (min==1) ? "1" : "";
		}

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCardinality(Optional<Integer> minItems, Optional<Integer> maxItems) {

		int min=0;
		String max="*";

		if(minItems.isPresent()) {
			min = minItems.get(); 
		} 

		if(maxItems.isPresent()) {
			max = Integer.toString(maxItems.get());
		} 
		String res="";
		res = Integer.toString(min) +  ".." + max;

		return res;

	}


	@LogMethod(level=LogLevel.DEBUG)
	private static String getReference(JSONObject property, String items, String ref) {
		return property.getJSONObject(items).getString(ref).replaceAll(".*/([A-Za-z0-9.]*)", "$1");
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getReference(JSONObject property) {
		if(property.has(ITEMS)) {
			property = property.optJSONObject(ITEMS);
			return getReference(property);
		} else {
			return property.getString(REF).replaceAll(".*/([A-Za-z0-9.]*)", "$1");
		}
	}	

	@LogMethod(level=LogLevel.DEBUG)
	public static Collection<String> getMappedSimpleTypes() {
		Collection<String> res = new HashSet<>();

		res.addAll( APIModel.typeMapping.values() );
		res.addAll( APIModel.formatToType.values() );

		Map<String,String> configTypeMapping = Config.getTypeMapping();
		res.addAll( configTypeMapping.values() );

		Map<String,String> configFormatToType = Config.getFormatToType();
		res.addAll( configFormatToType.values() );

		LOG.debug("getMappedSimpleTypes: res=" + res);
		
		return res;
	}	

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isSpecialSimpleType(String type) {
		return type.contains("/");
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isRequired(String resource, String property) {
		boolean res=false;

		JSONObject definition = getDefinition(resource);
		if(definition!=null) {	
			JSONArray required = definition.optJSONArray("required");

			if(required!=null) {
				res = required.toList().stream().filter(o -> o instanceof String).map(o -> (String)o).anyMatch(s -> s.equals(property));
			}
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static APIModel loadAPI(String file) {

		try {

			if(!file.endsWith(".json") && !file.endsWith(".yaml") && !file.endsWith(".yml")) {
				Out.println("file " + file + " is not of expected type (.json or .yaml/.yml)");
				System.exit(2);
			}

			return new APIModel(file);

		} catch(Exception ex) {
			Out.println("Exception: " + ex.getLocalizedMessage());
			System.exit(1);
		}

		return null;

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static APIModel loadAPI(String filename, File file) {
		
		if(file==null) {
			Out.println("... API file ´" + filename + "´ not found");
			System.exit(0);
			
		} else if(!file.exists()) {
			Out.println("... API file ´" + filename + "´ does not exist");
			System.exit(0);
			
		}

		try {
			return new APIModel(filename, file);
			
		} catch(Exception ex) {
			Out.printAlways("... error processing API specification: exception=" + ex.getLocalizedMessage() );
			System.exit(1);
		}

		return null;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getPropertyObjectBySchemaObject(JSONObject obj) {
		JSONObject res = getDefinitionBySchemaObject(obj);
		if(res.has(PROPERTIES)) res = res.optJSONObject(PROPERTIES);
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getDefinitionBySchemaObject(JSONObject obj) {
		JSONObject res = null;
		if(obj.has(REF)) {
			res = getDefinitionByReference(obj.optString(REF));
		} else if(obj.has(PROPERTIES)) {
			res = obj;
		} else {
			res = new JSONObject();
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getDescription(String resource) {
		String res="";
		JSONObject obj = getDefinition(resource);
		if(obj!=null) res = obj.optString(DESCRIPTION);
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCustomPuml(String type) {
		return getCustomPuml(type, Optional.empty(), Optional.empty());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCustomPuml(String type, Optional<Integer> minItems, Optional<Integer> maxItems) {
		String res;

		LOG.debug("getCustomPuml: type={}", type);

		if(typeMapping.containsKey(type)) {
			res = typeMapping.get(type);
		} else {
			JSONObject definition = getDefinition(type);

			LOG.debug("getCustomPuml: type={} definition={}", type, definition);

			res = getCustomPuml(definition, minItems, maxItems);

		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCustomPuml(JSONObject definition, Optional<Integer> minItems, Optional<Integer> maxItems) {
		StringBuilder res = new StringBuilder();

		boolean isRequired=false;
		if(definition==null) return res.toString();

		LOG.debug("getCustomPuml: definition={} minItems={} maxItems={}", definition, minItems, maxItems);

		if(definition.has(TYPE) && ARRAY.contentEquals(definition.optString(TYPE)) && definition.has(ITEMS)) {

			minItems = APIModel.updateMinMaxItems(definition, minItems, "minItems");
			maxItems = APIModel.updateMinMaxItems(definition, maxItems, "maxItems");

			definition = definition.optJSONObject(ITEMS);
			res.append( getCustomPuml(definition, minItems, maxItems) );

		} else if(definition.has(REF)) {
			String type = getTypeByReference(definition.optString(REF));
			if(Config.getCompressCustomTypes()) {
				res.append( getCustomPuml(type, minItems, maxItems) );
			} else {
				String cardinality = "[" + getCardinality(minItems, maxItems) + "]";
				res.append( type + " " + cardinality + NEWLINE); 
				res.append( type + " : " + getCustomPuml(type, Optional.empty(), Optional.empty()) );
			}

		} else if(definition.has(TYPE)) {

			String type = definition.optString(TYPE);
			if(typeMapping.containsKey(type)) {
				res.append( typeMapping.get(type) );
			} else {
				res.append( type );
			}
			if(minItems.isPresent() || maxItems.isPresent()) {
				String cardinality = "[" + getCardinality(minItems, maxItems) + "]";
				res.append( " " + cardinality);
			}

		} else if(definition.has(ALLOF) && definition.optJSONArray(ALLOF)!=null) {
			JSONArray allOfs = definition.optJSONArray(ALLOF);
			Optional<Integer> allOfMinItems = APIModel.getMinMaxItems(definition, "minItems");
			Optional<Integer> allOfMaxItems = APIModel.getMinMaxItems(definition, "maxItems");

			for(Object allof : allOfs) {
				if(allof instanceof JSONObject) {
					definition = (JSONObject) allof;
					if(definition.has(REF)) {
						String type = getTypeByReference(definition.optString(REF));
						// res.append( getCustomPuml(type, allOfMinItems, allOfMaxItems) );

						if(Config.getCompressCustomTypes()) {
							res.append( getCustomPuml(type, allOfMinItems, allOfMaxItems) );
						} else {
							definition = getDefinition(type);
							// String cardinality = "[" + getCardinality(definition, isRequired, allOfMinItems, allOfMaxItems) + "]";
							// res.append( type + " " + cardinality + NEWLINE); 
							// res.append( type + " : " + getCustomPuml(type, Optional.empty(), Optional.empty()) );
							res.append( getCustomPuml(definition, allOfMinItems, allOfMaxItems) );
						}
					} 
				}
			}
		}

		return res.toString();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Optional<Integer> getMinMaxItems(JSONObject definition, String key) {
		Optional<Integer> res=Optional.empty();

		if(definition!=null) {
			if(definition.has(key)) {
				Integer cardinality = definition.optInt(key);	    		
				res = Optional.of(cardinality);

			} else if(definition.has(ALLOF) && definition.optJSONArray(ALLOF)!=null) {
				JSONArray allOfs = definition.optJSONArray(ALLOF);
				for(Object allof : allOfs) {
					if(allof instanceof JSONObject) {
						definition = (JSONObject) allof;
						res = getMinMaxItems(definition,key);
						if(res.isPresent()) break;
					}
				}
			}
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Optional<Integer> updateMinMaxItems(JSONObject definition, Optional<Integer> minMax, String key) {
		Optional<Integer> res=minMax;

		if(definition!=null && definition.has(key) && !minMax.isPresent()) {
			Integer cardinality = definition.optInt(key);	    		
			res = Optional.of(cardinality);
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCustomPuml_old(JSONObject definition) {
		String res="";

		boolean isRequired=false;
		if(definition!=null) {
			if(definition.has(TYPE) && ARRAY.contentEquals(definition.optString(TYPE)) && definition.has(ITEMS)) {
				String cardinality = "[" + getCardinality(definition, isRequired) + "]";
				definition = definition.optJSONObject(ITEMS);
				res = getCustomPuml(definition, Optional.empty(), Optional.empty()) + " " + cardinality;

			} else if(definition.has(REF)) {
				res = getTypeByReference(definition.optString(REF));

			} else if(definition.has(TYPE)) {
				res = definition.optString(TYPE);
				if(typeMapping.containsKey(res)) res = typeMapping.get(res);

			} else if(definition.has(ALLOF) && definition.optJSONArray(ALLOF)!=null) {
				JSONArray allOfs = definition.optJSONArray(ALLOF);
				for(Object allof : allOfs) {
					if(allof instanceof JSONObject) {
						definition = (JSONObject) allof;
						if(definition.has(REF)) {
							res = getTypeByReference(definition.optString(REF));
						} 
					}
				}
			}
		}

		return res;
	}
	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAllCustomSimpleTypes(List<String> customSimpleTypes) {
		List<String> res = new LinkedList<>(customSimpleTypes);
		final List<String> referenced = new LinkedList<>();

		for(String type : customSimpleTypes) {
			if(!typeMapping.containsKey(type)) {

				JSONObject definition = getDefinition(type);

				LOG.debug("getAllCustomSimpleTypes: type={} definition={}", type, definition);

				if(definition==null) continue;

				if(definition.has(TYPE) && ARRAY.contentEquals(definition.optString(TYPE)) && definition.has(ITEMS)) {
					definition = definition.optJSONObject(ITEMS);
				}

				if(definition.has(REF)) {
					type = getTypeByReference(definition.optString(REF));
					if(!res.contains(type)) referenced.add(type); 
				} 


				if(definition.has(ALLOF) && definition.optJSONArray(ALLOF)!=null) {
					JSONArray allOfs = definition.optJSONArray(ALLOF);
					for(Object allof : allOfs) {
						if(allof instanceof JSONObject) {
							definition = (JSONObject) allof;
							if(definition.has(REF)) {
								type = getTypeByReference(definition.optString(REF));
								if(!res.contains(type)) referenced.add(type); 
							}  
						}
					}
				}

			}
		}

		LOG.debug("getAllCustomSimpleTypes: #1 referenced={}",  referenced);

		referenced.removeAll(res);
		if(!referenced.isEmpty()) {
			List<String> refres = getAllCustomSimpleTypes(referenced);
			refres.removeAll(referenced);
			referenced.addAll( refres );

			LOG.debug("getAllCustomSimpleTypes: #2 referenced={}",  referenced);

		}

		referenced.removeAll(res);
		res.addAll(referenced);

		LOG.debug("getAllCustomSimpleTypes: res={}",  res);

		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getOperationsByResource(String resource) {

		List<String> allPaths = getPathsForResource(resource);

		List<String> res = allPaths.stream()
				.map(APIModel::getPath)
				.map(JSONObject::keySet)
				.flatMap(Set::stream)
				.map(String::toUpperCase)
				.distinct()
				.collect(Collectors.toList());

		// the first part will not find DELETE operations
		// look for paths of the form /.../{..} where we have seen the first part, i.e. /.../
		getPaths().forEach( path ->  {
			String corePath = path.replaceAll("/\\{[^}]+\\}$", "");

			if(!allPaths.contains(corePath)) return;

			res.addAll( getOperationsForPath(path) );

		});

		return res.stream().distinct().collect(Collectors.toList());

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getOperationsForPath(String path) {
		List<String> res=new LinkedList<>();
		JSONObject pathObj = getPathObjectByKey(path);
		if(pathObj!=null) {
			res.addAll( pathObj.keySet().stream()
					.map(String::toUpperCase)
					.collect(Collectors.toList()) );
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getPath(String path) {
		return swagger.getJSONObject(PATHS).optJSONObject(path);
	}

	static Map<String,List<String>> pathsForResources = new HashMap<>();

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getPathsForResource(String resource) {

		if(!pathsForResources.containsKey(resource)) {
			pathsForResources.put(resource, new LinkedList<>());

			getPaths().forEach( path ->  {

				List<String> foundResources = getResponseResourcesByPath(path);

				foundResources.forEach(found -> {
					if(!pathsForResources.containsKey(found)) {
						pathsForResources.put(found, new LinkedList<>());
					}
					pathsForResources.get(found).add(path);
				});

			});

		}

		return pathsForResources.get(resource);

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getResponseResourcesByPath(String path) {

		return getChildStream(getPathObjectByKey(path))
				.filter(APIModel::hasResponses)
				.map(APIModel::getResponseEntity)
				.map(APIModel::getNormalResponses)
				.flatMap(List::stream)
				.map(APIModel::getResourceFromResponse)
				.flatMap(List::stream)
				.map(APIModel::getMappedResource)
				.collect(Collectors.toList());

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getMappedResource(String resource) {
		String res=resource;
		if(resourceMapping!=null && resourceMapping.has(resource) && resourceMapping.optString(resource)!=null) {
			res = resourceMapping.getString(resource);
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getReverseResourceMapping(String resource) {
		String res=resource;
		if(reverseMapping!=null && reverseMapping.has(resource) && reverseMapping.optString(resource)!=null) {
			res = reverseMapping.getString(resource);
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject generateReverseMapping(JSONObject map) {
		JSONObject res = new JSONObject();

		for(String key : map.keySet()) {
			if(map.optString(key)!=null) {
				res.put(map.getString(key), key);
			}
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Stream<JSONObject> getChildStream(JSONObject obj) {
		return new JSONObjectHelper(obj).getChildStream();
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getProperties(String resource) {
		Set<String> res = new HashSet<>();
		JSONObject obj = getPropertyObjectForResource(resource);
		res.addAll(obj.keySet());
		return res;
	}       


	@LogMethod(level=LogLevel.DEBUG)
	public static Map<String,String> getMandatoryOptional(JSONObject resource) {
		Map<String,String> res = new HashMap<>();

		JSONObject core = getPropertyObjectForResource( resource );

		for(String property : core.keySet()) {
			String coreCondition = getMandatoryOptionalHelper(resource, property);
			if(coreCondition.contains("M")) {
				res.put(property, coreCondition);

			}
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getMandatoryOptionalHelper(JSONObject definition, String property) {
		boolean required = false;

		String res="O";

		if(definition==null) return res;

		if(definition.optJSONArray("required")!=null) {

			JSONArray requiredProperties = definition.optJSONArray("required");

			required = requiredProperties.toList().stream()
					.map(Object::toString)
					.anyMatch(s -> s.equals(property));

		}

		if(!required) {
			JSONObject propertyDef = getPropertySpecification(definition, property);
			if(propertyDef!=null && propertyDef.optInt("minItems")>0) required=true;
		}

		return required ? "M" : "O";

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Map<String,String> getMandatoryOptional(String resource) {
		Map<String,String> res = new HashMap<>();

		JSONObject coreResource = getDefinition(resource);

		JSONObject core = getPropertyObjectForResource( coreResource );

		if(core==null) return res;

		LOG.debug("getMandatoryOptional: resource={}",  resource);
		
		JSONObject createResource = getDefinition( getReverseResourceMapping(resource) + "_Create");
		JSONObject inputResource = getDefinition( getReverseResourceMapping(resource) + "Input");

		createResource = (createResource!=null) ? createResource : inputResource;

		JSONObject create = getPropertyObjectForResource( createResource );

		Set<String> coreProperties = getPropertyKeys( core );
		Set<String> createProperties = getPropertyKeys( create );

		for(String property : core.keySet()) {

			String coreCondition = getMandatoryOptionalHelper(coreResource, property);
			String createCondition = getMandatoryOptionalHelper(createResource, property);

			if(coreCondition.contains("M")) {
				res.put(property, coreCondition);

			} else if(createCondition.contains("M")) {
				res.put(property, createCondition);

			} else if(createProperties!=null && !createProperties.isEmpty()) {

				Set<String> setByServer = Utils.difference(coreProperties, createProperties);
				List<String> globals = Config.get("globalsSetByServer");  // Arrays.asList("href", "id");

				if(setByServer.contains(property) && globals.contains(property)) {
					res.put(property, Config.getString("setByServerRule"));
				}
			}
		}

		return res;
	}

	private static Set<String> getPropertyKeys(JSONObject obj) {
		Set<String> res = new HashSet<>();
		if(obj!=null) res=obj.keySet();
		return res;
	}

	public static JSONObject getResourceForPost(String resource) {
		JSONObject res = null;

		res = getDefinition( getReverseResourceMapping(resource) + "_Create");

		if(res==null) res = getDefinition( getReverseResourceMapping(resource) + "Input");

		return res;
	}

	public static JSONObject getResourceForPatch(String resource) {
		return getDefinition( getReverseResourceMapping(resource) + "_Update");
	}




	@LogMethod(level=LogLevel.DEBUG)
	private static List<JSONObject> getPathObjs() {
		return getPaths().stream().map(APIModel::getPathObjectByKey).collect(Collectors.toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAllOperations() {
		List<String> res = new LinkedList<>();

		if(swagger==null) {
			LOG.info("... missing API specification (swagger)");
			return res;
		}

		swagger.getJSONObject(PATHS).keySet().forEach( path ->  {
			JSONObject pathObj = swagger.getJSONObject(PATHS).getJSONObject(path);
			pathObj.keySet().forEach( op ->
			res.add(op.toUpperCase())
					);
		});

		return res.stream().distinct().collect(Collectors.toList());

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getDocumentDetails() {
		JSONObject res = new JSONObject();
		JSONObject variables = new JSONObject();

		if(swagger==null) return res;

		JSONObject info = swagger.optJSONObject("info");

		if(info!=null) {
			variables.put("ApiName", info.get("title"));
			variables.put("DocumentVersion", info.get("version"));

			String description = info.optString(DESCRIPTION);

			String documentNumber = "";

			Pattern pattern = Pattern.compile("[^:]*[ ]*TMF[^0-9]*([0-9]+)[.]*");
			Matcher matcher = pattern.matcher(description);
			if (matcher.find()) {
				documentNumber = matcher.group(1);
			}

			if(!documentNumber.isEmpty()) variables.put("DocumentNumber", "TMF" + documentNumber);

			LocalDate localDate = LocalDate.now();
			int year  = localDate.getYear();
			String month = Utils.pascalCase(localDate.getMonth().name());
			String date = month + " " + year;

			variables.put("Year", year);
			variables.put("Date", date);

			pattern = Pattern.compile("Release[^0-9]*([0-9]+.[0-9]+.?[0-9]?)");
			matcher = pattern.matcher(description);
			if (matcher.find()) {
				String release = matcher.group(1).trim();
				if(!release.isEmpty())      variables.put("Release", release);

			}

		}

		String basePath = swagger.optString("basePath");
		if(!basePath.isEmpty()) variables.put("basePath", basePath);

		if(!variables.isEmpty()) res.put("variables", variables);

		return res;

	}


	@LogMethod(level=LogLevel.DEBUG)
	public static String getResourceByPath(String path) {
		String res=null;

		Optional<String> optResource = getResponseResourcesByPath(path).stream().distinct().findFirst();

		if(optResource.isPresent()) res = optResource.get().replace("#/definitions/", "").replace("#/components/schemas/","");

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<JSONObject> getOperationsDetailsByResource(String resource, String operation) {
		List<JSONObject> res = new LinkedList<>();

		if(swagger==null) return res;

		JSONObject allpaths = swagger.optJSONObject(PATHS);

		String prefix = "/" + resource.toUpperCase();

		List<String> paths = allpaths.keySet().stream()
				.filter(path -> isPathForResource(path,prefix))
				.sorted()
				.distinct()
				.collect(toList());

		paths.forEach(path -> {
			JSONObject ops = allpaths.optJSONObject(path);					
			if(ops!=null && ops.has(operation.toLowerCase())) res.add(ops.optJSONObject(operation.toLowerCase())); 
		});

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getOperationsDetailsByPath(String path, String op) {
		JSONObject res = null;

		if(swagger==null) return res;

		JSONObject allPaths = swagger.optJSONObject(PATHS);

		if(allPaths.has(path) && allPaths.optJSONObject(path)!=null) {
			JSONObject endpoint = allPaths.optJSONObject(path);
			if(endpoint.has(op.toLowerCase())) {
				res = endpoint.optJSONObject(op.toLowerCase());
			}
		}

		return res;

	}

	public static String getSuccessResponseCode(String path, String op) {
		String res="";
		JSONObject operation = APIModel.getOperationsDetailsByPath(path, op);
		
		if(operation!=null && operation.has(RESPONSES)) {
			Set<String> responseCodes = operation.optJSONObject(RESPONSES).keySet().stream().filter(v -> v.startsWith("2")).collect(toSet());
//			if(responseCodes.size()==1) {
//				res = responseCodes.iterator().next();
//			} else if(!responseCodes.isEmpty()) {
//				res = responseCodes.stream().sorted().distinct().findFirst().get();
//			} else {
//				Out.printAlways("... unable to extract unique success response code for " + path + " - found alternatives: " + responseCodes);
//			}
			if(responseCodes.isEmpty()) {
				Optional<String> optRes = responseCodes.stream().sorted().distinct().findFirst();
				if(optRes.isPresent()) res = optRes.get();
			}
		}
		
		if(res.isEmpty()) {
			Out.printAlways("... unable to extract unique success response code for " + path + " and operation " + op);
		}

		return res;
	}

	public static JSONObject getMappingForResource(String resource) {
		
		return getDefinition(resource, DISCRIMINATOR, MAPPING);

	}


}
