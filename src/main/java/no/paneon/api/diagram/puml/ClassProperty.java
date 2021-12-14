package no.paneon.api.diagram.puml;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import no.paneon.api.graph.OtherProperty;
import no.paneon.api.graph.Property;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;


public class ClassProperty extends Entity {

	String name;
	String type;
	String cardinality;
	boolean required;
	Visibility visibility;
	List<String> values;

	String defaultValue;
	
	boolean isNullable;
	
	public enum Visibility {
		VISIBLE,
		INHERITED,
		HIDDEN
	}
	
	public static Visibility VISIBLE = Visibility.VISIBLE;
	public static Visibility INHERITED = Visibility.INHERITED;
	public static Visibility HIDDEN = Visibility.HIDDEN;
	
	private static String ATTYPE = "@type";
	
	public ClassProperty(Property property, Visibility visibility) {
		super();
		
		this.name = property.getName();
		this.type = property.getType();
		this.cardinality = property.getCardinality();
		this.required = property.isRequired();
		this.isNullable = property.isNullable();
		
		this.values = new LinkedList<>();
		this.enumStatus = property.isEnum();
		
		this.addValues(property.getValues());

		this.visibility=visibility;
		
		this.defaultValue = property.getDefaultValue();
		
	}
	
	public ClassProperty(OtherProperty property, Visibility visibility) {
		super();
		
		this.name = property.getName();
		this.type = property.getValue();
		this.cardinality = "";
		this.required = property.isRequired();
		
		this.isNullable = property.isNullable();
		this.values = new LinkedList<>();
		this.enumStatus = false;
		
		this.visibility=visibility;
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
		return toString(0);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String toString(int maxLength) {
		
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
			
			res = (required && useRequiredFormatting) ? String.format(format,name) : name;
			
			if(this.defaultValue==null || this.defaultValue.isEmpty() || Config.getBoolean("keepTypeForDefaultValue")) {
				res = res + " : " + enumLabel + type;
				
			} else if(maxLength>0 && !Config.getBoolean("keepTypeForDefaultValue")) {
				int length = res.length();
				
				if(length+this.defaultValue.length()>maxLength) {
					res = res + NEWLINE;
					res = res + INDENT + "{field}" + "\"\"" + INDENT_SMALL + "\"\"";
				}
				res = res + " = " + this.defaultValue;
				
				return res;
				
			} 
			
		}
		
		LOG.debug("property: name={} visibility={}",  this.name, this.visibility);
		if(this.visibility==INHERITED && Config.getBoolean("keepInheritanceDecoractions")) {
			String format = Config.getString("inheritedFormatting");
			if(!format.isEmpty()) res = String.format(format,res);
		}
		
		
		String cardinalityToShow = Config.showDefaultCardinality() ? this.cardinality : this.cardinality.replace(Config.getDefaultCardinality(),"");
		
		if(cardinalityToShow.isEmpty() && this.required) cardinalityToShow = "1";
		
		if(!cardinalityToShow.isEmpty() && !Config.hideCardinaltyForProperty(cardinalityToShow)) {
			if(cardinalityToShow.contentEquals("1")) {
				res = "{field}" + res + " (" + cardinalityToShow + ")";
			} else {
				res = res + " [" + cardinalityToShow + "]";
			}
		}
		
		if(this.defaultValue!=null)  {
			LOG.debug("property: name={} defaultValue={}",  this.name, this.defaultValue);
			int length = res.replace("{field}","").length();
			
			if(length+this.defaultValue.length()>maxLength) {
				res = res + NEWLINE;
				res = res + INDENT + "{field}" + "\"\"" + INDENT_SMALL + "\"\"";
			}
			res = res + " = " + this.defaultValue;
		}
		
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
	
	
	public void addValues(List<String> val) {
		values.addAll(val);
	}
	
	private boolean enumStatus = false;
	
	public void setEnumStatus(boolean status) {
		this.enumStatus = status;
	}
	
	public boolean isNullable() {
		return this.isNullable;
	}

	public boolean isAtTypeProperty() {
		return this.name.contentEquals(ATTYPE);
	}
	
	public void resetDefaultValue() {
		this.defaultValue=null;
	}
	
	public boolean isRequired() {
		return this.required;
	}
	
}


