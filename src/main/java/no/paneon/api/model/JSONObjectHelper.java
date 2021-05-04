package no.paneon.api.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

public class JSONObjectHelper {

	static final Logger LOG = LogManager.getLogger(JSONObjectHelper.class);

	private JSONObject obj;
	private Set<String> properties;
	
	public JSONObjectHelper(JSONObject obj) {
		this.obj = obj;		
		this.properties = obj.keySet();
	}
	
	public JSONObjectHelper(JSONObject obj, Set<String> properties) {
		this.obj = obj;	
		this.properties = properties;
		
	}
	
	
	@LogMethod(level=LogLevel.TRACE)
	public List<JSONObject> getChildElements() {
		return getChildStream().collect(toList());
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public Stream<JSONObject> getChildStream() {
		return obj.keySet().stream()
				 .filter(this::includeKey)
				 .map(this::optJSONObject)
				 .filter(Objects::nonNull);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public JSONObject optJSONObject(String key) {
		return obj.optJSONObject(key);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	private boolean includeKey(String key) {
		return properties.contains(key);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public Set<String> getKeysForContainedJSONObjects() {
		Set<String> res = new HashSet<>();
		for(String s : obj.keySet()) {
			if(obj.optJSONObject(s)!=null) res.add(s);
		}
		return res;
	}
	
	
}
