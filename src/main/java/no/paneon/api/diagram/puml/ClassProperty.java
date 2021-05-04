package no.paneon.api.diagram.puml;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import no.paneon.api.graph.Property.Visibility;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;


public class ClassProperty extends Entity {

	String name;
	String type;
	String cardinality;
	boolean required;
	Visibility visibility;
	
	public enum Visibility {
		VISIBLE,
		INHERITED,
		HIDDEN
	}
	
	public static Visibility VISIBLE = Visibility.VISIBLE;
	public static Visibility INHERITED = Visibility.INHERITED;
	public static Visibility HIDDEN = Visibility.HIDDEN;
	
	public ClassProperty(String name, String type, String cardinality, boolean required, Visibility visibility) {
		super();
		this.name = name;
		this.type = type;
		this.cardinality = cardinality;
		this.required=required;
		this.visibility=visibility;
	}

	public ClassProperty(String name, String type, Visibility visibility) {
		this(name, type, "", false, visibility);
	}

	public ClassProperty(String name, String value, boolean required, Visibility visibility) {
		this(name, value, "", required, visibility);
	}

	public ClassProperty(String name, String type, String cardinality, boolean required, List<String> values, boolean enumStatus, Visibility visibility) {
		this(name, type, cardinality, required, visibility);
		this.addValues(values);
		this.enumStatus=enumStatus;

	}

	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public String getName() {
		return name;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String getType() {
		return type;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		
		String res="";
		StringBuilder stype = new StringBuilder();
		
		int nlen = name.length()+3;
		List<String> lines = Utils.splitString(type, Config.getMaxLineLength()-nlen);
		if(lines.size()>1) {
			final String indent = "                                       ".substring(nlen);
			
			stype.append( lines.get(0) + "\n" );
			lines.remove(0);
			lines.stream()
				.map(p -> "{field} //" + indent + p + "//" )
				.forEach(stype::append);

			stype.append("\n");
			
		} else {
			stype.append(type);
		}
		
		if(name.isEmpty()) {
			res=stype.toString();
		} else {
			String enumLabel = this.enumStatus && !this.values.isEmpty() ? "ENUM " : "";

			boolean useRequiredFormatting = Config.getUseRequiredHighlighting();
			String format = Config.getRequiredFormatting();

			LOG.debug("property: name={} required={} useRequiredHighlighting={} requiredFormatting={}", 
					this.name, this.required, useRequiredFormatting, format);

			if(required && useRequiredFormatting) {
				String sname = String.format(format,name);
				res = sname + " : " + enumLabel + type;
			} else {
				res = name + " : " + enumLabel + type;
			}
		}
		
		LOG.debug("property: name={} visibility={}",  this.name, this.visibility);
		if(this.visibility==INHERITED && !Config.getString("inheritedFormatting").isEmpty()) {
			String format = Config.getString("inheritedFormatting");
			res = String.format(format,res);
		}
			
		if(!cardinality.isEmpty() && !Config.hideCardinaltyForProperty(cardinality)) res = res + " [" + cardinality + "]";

		if(!values.isEmpty()) {
			final String indent = "                             ".substring(0,res.indexOf(':'));
			res = res + "\n";
			res = res + values.stream().map(v -> "{field} //" + indent + v + "//").collect(Collectors.joining("\n"));
		}
		
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType() {
		
		List<String> simpleEndings = Config.getSimpleEndings();
		
		boolean simpleEnding = simpleEndings.stream().anyMatch(ending -> this.type.endsWith(ending));
		
		return  simpleEnding 
				|| APIModel.isSpecialSimpleType(this.type) 
				|| APIModel.isSimpleType(this.type) 
				|| Config.getSimpleTypes().contains(this.type) 
				|| APIModel.isEnumType(this.type);
		
	}
	
	
	List<String> values = new LinkedList<>();
	public void addValues(List<String> val) {
		values.addAll(val);
	}
	
	private boolean enumStatus = false;
	
	public void setEnumStatus(boolean status) {
		this.enumStatus = status;
	}

}


