package no.paneon.api.diagram.puml;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

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
	
	boolean vendorExtension = false;
	boolean requiredExtension = false;
	boolean typeExtension = false;

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
		
		this.vendorExtension = property.getVendorExtension();
		this.requiredExtension = property.getRequiredExtension();
		this.typeExtension = property.getTypeExtension();

		if(this.vendorExtension) LOG.debug("vendor extension: property {}", this.name);
		
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
		
		// this.vendorExtension = property.getVendorExtension();
		// this.requiredExtension = property.getRequiredExtension();

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
		
		int nlen = this.name.length()+3;
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
		
		if(this.name.isEmpty()) {
			
			String extensionFormat = "%s";
			if(this.vendorExtension && this.typeExtension) {
				String color = Extensions.getColor();
				extensionFormat = "<color:" + color + ">%s";			
			}
			
			LOG.debug("ClassProperty::property: name={} type extensionFormat={}",  this.name, extensionFormat);

			res=String.format(extensionFormat,stype.toString());
			
		} else {
			String enumLabel = this.enumStatus && !this.values.isEmpty() ? "ENUM " : "";

			boolean useRequiredFormatting = Config.getUseRequiredHighlighting();
			String format = Config.getRequiredFormatting();

			LOG.debug("property: name={} required={} useRequiredHighlighting={} requiredFormatting={}", 
					this.name, this.required, useRequiredFormatting, format);
			
			res = (this.required && useRequiredFormatting) ? String.format(format,name) : name;
			
			if(this.defaultValue==null || this.defaultValue.isEmpty() || Config.getBoolean("keepTypeForDefaultValue")) {
				
				String extensionFormat = "%s";
				if(this.vendorExtension && this.typeExtension) {
					String color = Extensions.getColor();
					extensionFormat = "<color:" + color + ">%s";			
				}
				
				LOG.debug("ClassProperty::property: name={} type extensionFormat={}",  this.name, extensionFormat);

				res = res + " : " + String.format(extensionFormat,enumLabel + type);
								
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
		
		if(this.enumStatus) {
			LOG.debug("ClassProperty:: #1 res={}", res);
		}
		
		LOG.debug("property: name={} visibility={}",  this.name, this.visibility);
		if(this.visibility==INHERITED && Config.getBoolean("keepInheritanceDecoractions")) {
			String format = Config.getString("inheritedFormatting");
			if(!format.isEmpty()) res = String.format(format,res);
		}
		
		
		String cardinalityToShow = Config.showDefaultCardinality() ? this.cardinality : this.cardinality.replace(Config.getDefaultCardinality(),"");
		
		if(cardinalityToShow.isEmpty() && this.required) cardinalityToShow = "1";
		
		if(!cardinalityToShow.isEmpty() && !Config.hideCardinaltyForProperty(cardinalityToShow)) {
			String extensionFormat = "%s";
			if(this.vendorExtension && this.requiredExtension) {
				String color = Extensions.getColor();
				extensionFormat = "<color:" + color + ">%s";			
			}
			
			LOG.debug("ClassProperty::property: name={} extensionFormat={}",  this.name, extensionFormat);

			if(cardinalityToShow.contentEquals("1")) {
				res = "{field}" + res + String.format(extensionFormat," (" + cardinalityToShow + ")");
			} else {
				res = res + String.format(extensionFormat," [" + cardinalityToShow + "]");
			}
		}
		
		if(this.vendorExtension && !hasPartialExtension()) {
			String extensionFormat = "%s";
			if(this.vendorExtension) {
				String color = Extensions.getColor();
				extensionFormat = "<color:" + color + ">%s";			
			}
			
			LOG.debug("ClassProperty::property: name={} extensionFormat={}",  this.name, extensionFormat);

			res = String.format(extensionFormat, res);
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
		
		if(this.enumStatus) {
			LOG.debug("ClassProperty:: #2 res={}", res);
		}
		
		if(!values.isEmpty()) {
			final String BLANKS = "                             ";

			int pos = res.indexOf(':');
			if(pos<0) pos=0;
			if(pos>BLANKS.length()) pos=BLANKS.length();
			
			final String indent = BLANKS.substring(0,pos);
			res = res + "\n";
			res = res + values.stream().map(v -> "{field} //" + indent + v + "//").collect(Collectors.joining("\n"));
		}		
		
		return res;
	}
	
	private boolean hasPartialExtension() {
		return this.requiredExtension || this.typeExtension;
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
	
	public boolean getVendorExtension() {
		return this.vendorExtension;
	}
	
}


