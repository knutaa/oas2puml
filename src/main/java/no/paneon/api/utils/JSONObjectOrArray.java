package no.paneon.api.utils;

import java.io.File;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;

public class JSONObjectOrArray {

	
	public enum JSONValueType {
		ARRAY,
		OBJECT,
		NONE;
	}
	
	JSONValueType valueType = JSONValueType.NONE;
	
	Optional<JSONObject> objectValue = Optional.empty();
	Optional<JSONArray>  arrayValue  = Optional.empty();
	
	
	public static JSONObjectOrArray readJSONObjectOrArray(String file) {
		
		JSONObjectOrArray res = new JSONObjectOrArray();

		String content = Utils.readFile(file);
				
		try {
			return res.set(new JSONObject(content) );
			
		} catch(Exception e1) {
		
			try {
				return res.set(new JSONArray(content) );
				
			} catch(Exception e2) {
				Out.printAlways("... unable to read JSON from " + Utils.getBaseFileName(file) );
			}
			
		}
				
		return res;
		
	}
	
	public JSONValueType getValueType() {
		return this.valueType;
	}
	
	public boolean isObject() { 
		return objectValue.isPresent(); 
	}
	
	public boolean isArray() { 
		return arrayValue.isPresent(); 
	}
	
	public JSONObject getObjectValue() {
		if(objectValue.isPresent())
			return objectValue.get();
		else
			return new JSONObject();
	}
	
	public JSONArray getArrayValue() {
		if(arrayValue.isPresent())
			return arrayValue.get();
		else
			return new JSONArray();	
	}
	
	public JSONObjectOrArray set(JSONObject value) {
		this.objectValue = Optional.of(value);
		this.valueType = JSONValueType.OBJECT;
		return this;
	}
	
	public JSONObjectOrArray set(JSONArray value) {
		this.arrayValue = Optional.of(value);
		this.valueType = JSONValueType.ARRAY;
		return this;
	}

	public String toString() {
		return toString(0);
	}
	
	public String toString(int indent) {
		String res="";
				
		switch(getValueType()) {
			case ARRAY:
				res = getArrayValue().toString(indent);
				break;
				
			case OBJECT:
				res = getObjectValue().toString(indent);
				break;
		
			default:
		}
		return res;

	}
}
