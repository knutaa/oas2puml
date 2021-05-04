package no.paneon.api.diagram.app;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
    
public class YamlBeanSerializerModifier extends BeanSerializerModifier {
    	
    @SuppressWarnings("unchecked")
	@Override
    public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDesc, JsonSerializer<?> serializer) {
 
        if (beanDesc.getBeanClass().equals(JSONObject.class)) {
            return new CustomJSONObjectSerializer((JsonSerializer<Object>) serializer);
        }
 
        if (beanDesc.getBeanClass().equals(JSONArray.class)) {
            return new CustomJSONArraySerializer((JsonSerializer<Object>) serializer);
        }
        
        return serializer;
    }

}

