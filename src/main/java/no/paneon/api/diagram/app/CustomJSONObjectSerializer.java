package no.paneon.api.diagram.app;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import no.paneon.api.utils.Config;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CustomJSONObjectSerializer extends StdSerializer<JSONObject> {
       
	static final Logger LOG = LogManager.getLogger(CustomJSONObjectSerializer.class);

	private static final long serialVersionUID = 1L;
	
	private transient JsonSerializer<Object> defaultSerializer;
                
    public CustomJSONObjectSerializer(JsonSerializer<Object> defaultSerializer) {
        super(JSONObject.class);
        this.defaultSerializer = defaultSerializer;
        
        // the following is just to have a silly use of the defaultSerializer ... 
        if(LOG.isTraceEnabled()) {
        	LOG.log(Level.TRACE, "defaultSerializer: {}", this.defaultSerializer);
        }
     }
 
    private static final List<String> ORDER = Arrays.asList(
    										"condition", "comment", 
    										"operations", "resources", "notifications", 
    										"attributes", "operations-details",
    										"conformance", "layout", "default_conformance");
    
    @Override
    public void serialize(JSONObject value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      	    	
        gen.writeStartObject();
    
        //
        // order the JSONObject fields according to the custom ordering, either based on the ORDER constant here
        // or specified in the configuration
        //
        // the ordering is currently global and the same for all JSONObjects (independent of hierarchy or parent)
        //
        
        List<String> fieldOrder = ORDER;
        if(Config.has("yamlOrdering")) fieldOrder = Config.get("yamlOrdering");
        
        List<String> properties = new LinkedList<>(value.keySet());
        List<String> ordering = new LinkedList<>(fieldOrder);
        ordering.retainAll(properties);
        
        properties.removeAll(ordering);
        
        properties = properties.stream().sorted().collect(Collectors.toList());
        
        ordering.addAll(properties);
        
    	for(String field : ordering) {	  
    		
    		if(isNullValue(value,field)) {			
    			provider.defaultSerializeField(field, "", gen);
    		} else if (hasValue(value, field)) {
    			provider.defaultSerializeField(field, value.get(field), gen);
    		}  
    	}
    	
        gen.writeEndObject();
        
    }

	private boolean isNullValue(JSONObject value, String field) {
		
		if(value.optJSONObject(field)!=null || value.optJSONArray(field)!=null) {
			return false;
		} else if(JSONObject.NULL.equals(value.get(field))) {
			return true;
		} else {
			String s = value.get(field).toString();			
			return  "null".contentEquals(s);
		}
		
	}

	private boolean hasValue(JSONObject value, String field) {				
		return (value.optJSONObject(field)!=null || value.optJSONArray(field)!=null)
				|| (
				!(JSONObject.NULL.equals(value.get(field))) && 
				(value.optJSONObject(field)==null || !value.optJSONObject(field).isEmpty()));
	}
}

