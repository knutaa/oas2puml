package no.paneon.api.diagram.app;

import java.awt.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import no.paneon.api.logging.AspectLogger.LogLevel;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.InvalidJsonYamlException;
import no.paneon.api.utils.Out;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

public class Utils {
	
    static final Logger LOG = LogManager.getLogger(Utils.class);

    static final Map<String, Level> levelmap = new HashMap<>();
	static final Map<String,String> formatToType = new HashMap<>();
	static final Map<String,String> typeMapping = new HashMap<>();

    static {
    	
	    levelmap.put("verbose", no.paneon.api.logging.AspectLogger.VERBOSE);
	    levelmap.put("info", Level.INFO);
		levelmap.put("error", Level.ERROR);
		levelmap.put("debug", Level.DEBUG);
		levelmap.put("trace", Level.TRACE);
		levelmap.put("warn", Level.WARN);
		levelmap.put("fatal", Level.FATAL);
		levelmap.put("all", Level.ALL);
		levelmap.put("off", Level.OFF);

		formatToType.put("date-time", "DateTime");
		formatToType.put("date", "Date");
		formatToType.put("float", "Float");
		formatToType.put("uri", "Uri");
		formatToType.put("url", "Url");
		
		typeMapping.put("integer", "Integer");
		typeMapping.put("string", "String");
		typeMapping.put("boolean", "Boolean");
	
    }
	
    private Utils() {	
    }
    
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> convertJSONArrayToList(JSONArray array) {
		List<String> res = new ArrayList<>();
		Iterator<Object> it = array.iterator();
	    while (it.hasNext()) {
	    	res.add((String)it.next());
	    }
	    return res;
	}
	
	private static final String EXCEPTION_MESSAGE  = "exeption: {}";
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject readJSON(String fileName, boolean errorOK) throws InvalidJsonYamlException {
		try {
			String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
	        File file = new File(path);
	        String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
	        return new JSONObject(content); 
		} catch(Exception ex) {
			if(LOG.isDebugEnabled()) LOG.log(Level.DEBUG, EXCEPTION_MESSAGE, ex.getLocalizedMessage() );
			if(!errorOK) throw(new InvalidJsonYamlException());
			return new JSONObject();
		}
    }
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject readYamlAsJSON(String fileName, boolean errorOK) throws InvalidJsonYamlException {
		try {
			String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
	        File file = new File(path);
	        String yaml = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
	        String json = convertYamlToJson(yaml);
	        return new JSONObject(json); 
		} catch(Exception ex) {
			if(LOG.isDebugEnabled()) LOG.log(Level.DEBUG, EXCEPTION_MESSAGE, ex.getLocalizedMessage() );
			if(!errorOK) throw(new InvalidJsonYamlException());
			return new JSONObject();
		}
    }
	
	@LogMethod(level=LogLevel.TRACE)
	public static String convertJsonToYaml(JSONObject json) throws InvalidJsonYamlException {
		try {
			YAMLFactory yamlFactory = new YAMLFactory()	
				 .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES) 
		         .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
		         .enable(YAMLGenerator.Feature.INDENT_ARRAYS)
		         ;
			
			ObjectMapper mapper = new ObjectMapper(yamlFactory);		 
		    SimpleModule module = new SimpleModule();
		    
		    module.setSerializerModifier(new YamlBeanSerializerModifier());
		    mapper.registerModule(module);
		    
		    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
	
		    return mapper.writeValueAsString(json);
		    
		} catch(Exception ex) {
			if(LOG.isDebugEnabled()) LOG.log(Level.DEBUG, EXCEPTION_MESSAGE, ex.getLocalizedMessage() );
			throw(new InvalidJsonYamlException());
		}
		        
	}
	
	@LogMethod(level=LogLevel.TRACE)
    public static String convertYamlToJson(String yaml) throws InvalidJsonYamlException {
				
		try {
		    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
		    Object obj = yamlReader.readValue(yaml, Object.class);
			    
		    ObjectMapper jsonWriter = new ObjectMapper();
	
		    return jsonWriter.writeValueAsString(obj);		    
		    			
		} catch(Exception ex) {
			if(LOG.isDebugEnabled()) LOG.log(Level.DEBUG, EXCEPTION_MESSAGE, ex.getLocalizedMessage() );
			throw(new InvalidJsonYamlException());
		}
	}
	
	
	@LogMethod(level=LogLevel.TRACE)
	public static TreeSet<String> intersection(List<String> al, Set<String> b) {
		Set<String> a = new TreeSet<>(al);
		return intersection(a,b);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static TreeSet<String> intersection(Set<String> a, Set<String> b) {
	    TreeSet<String> results = new TreeSet<>();

	    for (String element : a) {
	        if (b.contains(element)) {
	            results.add(element);
	        }
	    }

	    return results;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static Set<String> difference(List<String> al, List<String> bl) {		
		Set<String> a = new TreeSet<>(al);
		Set<String> b = new TreeSet<>(bl);
		
		a.removeAll(b);

	    return a;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static Set<String> difference(List<String> al, Set<String> b) {
		Set<String> a = new TreeSet<>(al);
		a.removeAll(b);
	    return a;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static Set<String> difference(Set<String> al, Set<String> b) {
		Set<String> a = new TreeSet<>(al);
		a.removeAll(b);
	    return a;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static Set<String> union(List<String> al, List<String> bl) {		
		Set<String> a = new TreeSet<>(al);
		Set<String> b = new TreeSet<>(bl);
		
		a.addAll(b);

	    return a;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static Set<String> union(Set<String> a, Set<String> b) {	
		Set<String> res = new TreeSet<>(a);
		
		res.addAll(b);

	    return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> filterList(List<String> all, Collection<String> retain) {	
		List<String> res = new LinkedList<>(all);
		
		res.retainAll(retain);

	    return res;
	}
		
	@LogMethod(level=LogLevel.TRACE)
	public static String join(List<String> strings) {
		return join(strings,"\n");
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String join(Collection<String> strings, String delim) {
		return strings.stream().collect(Collectors.joining(delim));
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String dump(Collection<String> collection) {
		return dump(collection, " ");
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String dump(String[] collection) {
		return dump(Arrays.asList(collection), " ");
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String dump(Collection<String> collection, String delim) {
		String res="";
		if(collection!=null) res=join(collection,delim);
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String camelCase(String str) {
		return str.substring(0,1).toLowerCase() + str.substring(1);
	}

	@LogMethod(level=LogLevel.TRACE)
	private static JSONObject readJSONOrYaml(String file) {
		JSONObject res = null;

		try {
			if(file.endsWith(".yaml") || file.endsWith(".yml")) 
				res = readYamlAsJSON(file,false);
			else
				res = readJSON(file,false);
		} catch(Exception e) {
			Out.println("... unable to read file " + file + " (error: " + e.getLocalizedMessage() + ")");
			System.exit(1);
		}
		return res;
	}
		
	@LogMethod(level=LogLevel.TRACE)
	public static void saveAsJson(JSONObject obj, String destination) {
		String text = obj.toString(2);
		save(text,destination);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void saveAsYaml(JSONObject obj, String destination) {
		try {
			String text = Utils.convertJsonToYaml(obj);	
						
			// add more space between each first level element 
			// and in front of every second level (assuming indented by two spaces)
			text = text.replaceAll("\n([A-Za-z][^:]*:)", "\n\n$1");
			text = text.replaceAll("\n(  [A-Za-z][^:]*:)", "\n\n$1");

			save(text,destination);
			
		} catch(Exception e) {
			Out.println("error converting to yaml file: ", destination);
			Out.println("error: ", e.getLocalizedMessage());
			System.exit(1);
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static void save(String text, String destination) {
		
		if(destination==null) {
			Out.println(text);
		} else {
			try(FileWriter out = new FileWriter(destination)) {
				out.write(text);
			} catch(Exception e) {
				Out.println("unable to write to file: ", destination);
				System.exit(1);
			}
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String pascalCase(String str) {
		str = str.toLowerCase();
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String upperCaseFirst(String str) {
		if(str.isEmpty())
			return str;
		else
			return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getFilenameWithDirectory(String directory, String fileName) {
		try {
			if(!fileName.isEmpty()) {	
				File file = new File(fileName);
				if (!file.isAbsolute() && !directory.equals(".")) {
					fileName = directory + File.separator + fileName;
				}
			}
		} catch(Exception e) {		
			
		}
		return fileName;
	}

	// get InputStream from either the fileName or embedded through the property
	@LogMethod(level=LogLevel.TRACE)
	public static InputStream openFileStream(String workingDirectory, String fileName, String property) throws IOException {
		
		if(fileName!=null) {
			return openFileStream(workingDirectory, fileName);
		} else {
			fileName = Config.getString(property); 
			return new ClassPathResource(File.separator + fileName).getInputStream();
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static InputStream openFileStream(String workingDirectory, String fileName) throws IOException {
		fileName = Utils.getFilenameWithDirectory(workingDirectory, fileName);	
		return new FileInputStream(new File(fileName));
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getOptString(JSONObject conf, String property) {
		String res="";
		if(conf!=null && conf.has(property)) res = conf.get(property).toString();
		if("null".equals(res)) res = "";
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<Object> merge(List<Object> listA, List<Object> listB) {
		List<Object> res = new LinkedList<>(listA);
		List<Object> tmp = new LinkedList<>(listB);
		
		tmp.removeAll(listA);
		
		res.addAll(tmp);
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Map<String,JSONObject> getJSONObjectMap(JSONObject obj) {
		Map<String,JSONObject> res = new HashMap<>();
		
		if(obj!=null) {
			for(String key : obj.keySet()) {
				JSONObject value = obj.optJSONObject(key);
				if(value!=null) {
					res.put(key, value);
				}
			}
		}		
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static Dimension getImageDim(final String path) {
	    Dimension result = null;
	    String suffix = getFileSuffix(path);
	    Iterator<ImageReader> iter = ImageIO.getImageReadersBySuffix(suffix);
	    if (iter.hasNext()) {
	        ImageReader reader = iter.next();
	        try {
	            ImageInputStream stream = new FileImageInputStream(new File(path));
	            reader.setInput(stream);
	            int width = reader.getWidth(reader.getMinIndex());
	            int height = reader.getHeight(reader.getMinIndex());
	            result = new Dimension(width, height);
	        } catch (IOException e) {
	            LOG.debug(e.getMessage());
	        } finally {
	            reader.dispose();
	        }
	    } else {
	        LOG.debug("No reader found for given format: {}", suffix);
	    }
	    return result;
	}

	@LogMethod(level=LogLevel.TRACE)
	private static String getFileSuffix(final String path) {
	    String result = null;
	    if (path != null) {
	        result = "";
	        if (path.lastIndexOf('.') != -1) {
	            result = path.substring(path.lastIndexOf('.'));
	            if (result.startsWith(".")) {
	                result = result.substring(1);
	            }
	        }
	    }
	    return result;
	}

}
