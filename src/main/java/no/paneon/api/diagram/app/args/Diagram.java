package no.paneon.api.diagram.app.args;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.Parameter;

public class Diagram extends Common {

	@Parameter(names = { "--layout" }, description = "Layout configuration settings")
	public String layout = null;

	@Parameter(names = { "--include-puml-comments" }, description = "Add comments in .puml file")
	public boolean pumlComments = false;

	@Parameter(names = { "--show-all-cardinality" }, description = "Include cardinality details for all properties (including default cardinality)")
	public boolean showAllCardinality = false;

	@Parameter(names = { "--include-description" }, description = "Include description in class diagrams (default false)")
	public boolean includeDescription = false;

	@Parameter(names = { "--highlight-required" }, description = "Highlight required properties and relationships (default false)")
	public boolean highlightRequired = false;

	@Parameter(names = { "--include-orphan-enums" }, description = "Include all enums not linked to any specific resource")
	public boolean includeOrphanEnums = false;

	@Parameter(names = { "--orphan-enum-config" }, description = "Include / show orphan enums for the list of identified resources")
	public String orphanEnumConfig = null;

	@Parameter(names = { "--floating-enums" }, description = "Floating enums - do not place enums close to the referring resource")
	public boolean floatingEnums = false;

	@Parameter(names = { "--remove-prefix" }, description = "Remove prefix from definitions (can be a regexp)")
	public String prefixToRemove = "";

	@Parameter(names = { "--replace-prefix" }, description = "Replace the 'remove' prefix from definitions (can be a regexp)")
	public String prefixToReplace = "";

	@Parameter(names = { "--display-complexity" }, description = "Display diagram complexity and suggested configuration")
	public boolean displayComplexity = false;

	@Parameter(description = "Files", arity=0)
	public List<String> files = new ArrayList<>();
	
	@Parameter(names = { "--keep-technical-edges" }, description = "Keep technical allOf edges (reverse of oneOfs)")
	public boolean keepTechnicalEdges = false;
	
	@Parameter(names = { "--keep-inheritance-decorations" }, description = "Keep inheritance decoractions")
	public boolean keepInheritanceDecoractions = false;

	@Parameter(names = { "--subresources" }, description = "Configuration file for splitting resources into sub-resources")
	public String subResourceConfig = "";
	
	@Parameter(names = { "--generate-images" }, description = "Generate image of .puml files")
	public boolean generateImages = false;

	@Parameter(names = { "--remove-legend" }, description = "Do not include the diagram legend")
	public boolean removeLegend = false;

}
