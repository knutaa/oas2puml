package no.paneon.api.diagram.app.args;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.Parameter;

public class Common {

	@Parameter(names = { "--explicit-document-details" }, description = "Use explicit document details (specified using the --config option)")
	public boolean explicitDocumentDetails = false;

	@Parameter(names = { "--conformance" }, description = "User defined conformance in JSON or YAML format (file name)")
	public String conformance = null;

	@Parameter(names = { "--defaults" }, description = "Default conformance settings in JSON or YAML format (file name)")
	public String defaults = null;

	@Parameter(names = { "--existing" }, description = "Existing conformance specification (.docx file)")
	public String existingSpecification = null;

	@Parameter(names = { "--rules" }, description = "API rules file")
	public String rulesFile = null;

	@Parameter(names = { "-c", "--config" }, description = "Config files (.json) - one or more")
	public List<String> configs = new ArrayList<>();

	@Parameter(names = { "--ignore-internal-config" }, description = "Do not include the internal configuration file (default=false)")
	public boolean ignoreInternalConfig = false;

	@Parameter(names = { "-o", "--output" }, description = "Output file name - used for both conformance specification (.docx) or extraction (.json or .yaml)")
	public String outputFileName = null;

	@Parameter(names = { "-d", "--debug" }, description = "Debug mode (off,all,info,debug,error,trace,warn,fatal)")
	public String debug = "verbose";

	@Parameter(names = { "--keep-non-printable" }, description = "Keep non-printable characters in extracted text, default is false")
	public boolean keepNonPrintable = false;

	@Parameter(names = { "--no-linebreaks" }, description = "Do not insert linebreaks in extracted text, default is false")
	public boolean noLinebreaks = false;

	@Parameter(names = { "--working-directory" }, description = "Working directory, default is the current directory")
	public String workingDirectory  = ".";

	@Parameter(names = { "--conformance-source-only" }, description = "Only include conformance in explicit --conformance statement")
	public boolean conformanceSourceOnly = false;

	@Parameter(names = { "--silent" }, description = "Do not include progress messages")
	public boolean silentMode = false;

	@Parameter(names = { "--schema-defaults" }, description = "Common schema conformance defaults in JSON or YAML format (file name)")
	public String schemaDefaultsSource = null;

	@Parameter(names = { "--target-directory" }, description = "Target directory for output. Default is the current directory")
	public String targetDirectory  = ".";

	@Parameter(names = { "--generated-target-directory" }, description = "Directory for generated results. Default is the target directory")
	public String generatedTargetDirectory  = null;
	
	@Parameter(names = { "-f", "--file", "--swagger", "--openapi" }, description = "Input OpenAPI file (or optionally as default argument)")
	public String openAPIFile;

	@Parameter(names = { "--resource" }, description = "Specific comma delimited resource(s) to process (default is all)")
	public String resource = null;
	
	@Parameter(names = { "--include-default-resources" }, description = "Include all default resources when specific resources are added (defaults to false")
	public boolean includeDefaultResources = false;
	
	@Parameter(names = { "-s", "--source" }, description = "Include source details in footer (0=no, 1=basic, >1 include filename)")
	public Integer source = 0;

	@Parameter(names = { "-t", "--template" }, description = "Document template (.docx or .mustache file) - defaults to embedded version")
	public String template = null;
	
	@Parameter(names = { "-h", "--help" }, description = "Usage details", help = true)
	public boolean help = false;

	@Parameter(names = { "--time-stamp" }, description = "Include time stamp details")
	public boolean timestamp = false;
	
	@Parameter(names = { "--whitelist" }, description = "Whitelist host for SSL processing")
	public List<String> whitelisting = new ArrayList<>();
	

	
}
