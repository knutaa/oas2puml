package no.paneon.api.diagram.puml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import no.paneon.api.utils.Config;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class Utils {

    static final Logger LOG = LogManager.getLogger(Utils.class);

	private Utils() {
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String formatDescription(String s, String indent) {
		String res=s;
		int idx = s.indexOf(':');
		if(idx>0) {
			res = s.substring(idx+1);
			res = res.replaceFirst("[ ]+", "");
		}
		List<String> parts = splitString(res,getMaxLineLength());
		res = parts.stream()
				.map(p -> { return indent + "{field} //" + p + "//"; })
				.collect(Collectors.joining("\n")) + "\n";

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
    private static int getMaxLineLength() {
    	return Config.getMaxLineLength();
	}
    
	@LogMethod(level=LogLevel.DEBUG)
    public static List<String> splitString(String msg, int lineSize) {
        List<String> res = new ArrayList<>();

        Pattern p = Pattern.compile("\\b.{1," + (lineSize-1) + "}\\b\\W?");
        Matcher m = p.matcher(msg);
        
        while(m.find()) {
        	res.add(m.group());
        }
        return res;
    }

	@LogMethod(level=LogLevel.DEBUG)
	public static String replaceParagraph(String paragraph, Map<String, String> variables) {
		String text=paragraph;
		
		for (Entry<String,String> variable : variables.entrySet()) {
			String find = "${" + variable.getKey() + "}";
			if (!text.contains(find))
				continue;
			
			text = text.replace(find, variable.getValue());
			
		}
		
		return text;
	
	}

	public static String quote(String name) {
		if(!Config.getBoolean("noPumlQuoting")) {
			String res = "\"" + name + "\"";
			return res;
		} else {
			return name;
		}
	}

	public static String unQuote(String s) {
		if(s.startsWith("\"")) s = s.substring(1);
		if(s.endsWith("\"")) s = s.substring(0, s.length()-1);
		return s;
		
	}
}
