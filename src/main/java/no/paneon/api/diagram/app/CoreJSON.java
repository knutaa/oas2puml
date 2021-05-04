package no.paneon.api.diagram.app;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
 
public class CoreJSON {
	
	public Map<String,JSONObject> getConfigByPattern(JSONObject json, String pattern) {
		Map<String,JSONObject> res = new HashMap<>();
		
		if(json==null) return res;
		
		json.keySet().forEach(key -> {
			if(key.contains(pattern)) {
				JSONObject obj = json.optJSONObject(key);
				String label = key.replace(pattern, "").trim();
				if(label.startsWith("'")) label = label.replace("'", "");
				if(obj!=null) res.put(label,obj);
			}
		});
		return res;
	}
}
