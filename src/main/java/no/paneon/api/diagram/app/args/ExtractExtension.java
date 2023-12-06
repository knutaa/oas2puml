package no.paneon.api.diagram.app.args;

import com.beust.jcommander.Parameter;

public class ExtractExtension extends Common {
	
	@Parameter(names = { "--base" }, description = "Base OpenAPI specification")
	public String baseSpecification = "";

	@Parameter(names = { "--extension-label" }, description = "Extension vendor name")
	public String extensionLabel = null;
	
	@Parameter(names = { "--extension-color" }, description = "Extension color")
	public String extensionColor = null;
	
}
	
