package no.paneon.api.diagram.puml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import no.paneon.api.utils.Config;

public class Extensions  {
	
    static final Logger LOG = LogManager.getLogger(Extensions.class);

	private static final String COLOR = "blue";
	private static final String LABEL = "extension";

	public static final String LEGEND_LABEL = "legendLabel";
	public static final String EXTENSIONS = "extensions";
	public static final String EXTENSION_COLOR = "extensionColor";
	
	public static final String RESOURCE_EXTENSION = "resourceExtension";
	public static final String EXTENSION_NAME = "name";

	public static final String RESOURCE_ATTRIBUTE_EXTENSION = "resourceAttributeExtension";
	public static final String ATTRIBUTE_EXTENSION = "attributesExtension";
	
	public static final String EXCLUDE_AS_EXTENSION = "excludeAsExtension";
	public static final String RESOURCE_ATTRIBUTE_AS_EXTENSION = "resourceAttributeExtensionAsExtensions";
	
	public static final String DISCRIMINATOR_EXTENSION = "discriminatorExtension";
	public static final String RESOURCE_DISCRIMINATOR_EXTENSION = "resourceDiscriminatorExtension";

	public static final String EXTENSION_CARDINALITY = "cardinality";
	public static final String EXTENSION_REQUIRED = "required";
	public static final String EXTENSION_TYPE = "type";
	public static final String EXTENSION_MOVED = "moved";

	public static final String INHERITANCE_EXTENSION = "inheritance";

	public static final String RESOURCE_INHERITANCE_EXTENSION = "resourceInheritance";

	public static String getColor() {
		String color = COLOR;

		JSONObject vendorExtensions = Config.getConfig(EXTENSIONS);
		
		if(vendorExtensions!=null) {
			color = vendorExtensions.optString(EXTENSION_COLOR);
	
			if (color.isEmpty()) color = COLOR;
		}
		
		return color;
	}

	public static String getLabel() {
		String label = LABEL;

		JSONObject extensions = Config.getConfig(EXTENSIONS);
		
		if(extensions!=null) {
			label = extensions.optString(LEGEND_LABEL);	
			if (label.isEmpty()) label = LABEL;
		}
		
		return label;
	}
	
	
	
}
