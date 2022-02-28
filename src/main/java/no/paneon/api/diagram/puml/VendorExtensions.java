package no.paneon.api.diagram.puml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import no.paneon.api.utils.Config;

public class VendorExtensions  {
	
    static final Logger LOG = LogManager.getLogger(VendorExtensions.class);

	private static final String COLOR = "blue";
	private static final String VENDOR = "vendor extension";

	public static String getColor() {
		JSONObject vendorExtensions = Config.getConfig("vendorExtensions");
		String color = vendorExtensions.optString("extensionColor");

		if (color.isEmpty()) color = COLOR;
		
		return color;
	}

	public static String getVendor() {
		JSONObject vendorExtensions = Config.getConfig("vendorExtensions");
		String vendor = vendorExtensions.optString("vendorName");

		if (vendor.isEmpty()) vendor = VENDOR;

		return vendor;
	}
	
	
	
}
