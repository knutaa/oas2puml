package no.paneon.api.extensions;

import java.io.File;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.diagram.app.Args;
import no.paneon.api.diagram.puml.Extensions;
import no.paneon.api.generator.GenerateCommon;
import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Property;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;


public class ExtractExtensions extends GenerateCommon {

    static final Logger LOG = LogManager.getLogger(ExtractExtensions.class);

	private Args.ExtractExtensions args;
	
	public ExtractExtensions(Args.ExtractExtensions args) {
		super(args);
		this.args = args;
	}

	public void execute() {
		
		super.execute();
		CoreAPIGraph actualAPI = new CoreAPIGraph();
		
		APIModel.clean();
		args.openAPIFile = args.baseSpecification;
		GenerateCommon.loadAPI(args);

		CoreAPIGraph baseAPI = new CoreAPIGraph();		
		
		JSONObject extensions = new JSONObject();
		
		List<String> allNodes = actualAPI.getNodes();
		
		allNodes.removeAll(baseAPI.getNodes());
		
		JSONArray resourceExtensions = new JSONArray();
		
		allNodes.forEach(node -> resourceExtensions.put(new JSONObject().put(Extensions.EXTENSION_NAME,node)));
	
		extensions.put(Extensions.RESOURCE_EXTENSION, resourceExtensions);
	
		allNodes = actualAPI.getNodes();
		allNodes.retainAll(baseAPI.getNodes());
		
		JSONArray resourceAttributeExtension = new JSONArray();

		actualAPI.getNodesByNames(allNodes).forEach(node -> {
			List<Property> actualProperties = node.getProperties();
			
			List<Property> baseProperties = baseAPI.getNode(node.getName()).getProperties();
			List<String>   basePropertiesName = baseProperties.stream().map(Property::getName).collect(toList());

			LOG.debug("node: {} actual properties: {}", node.getName(), actualProperties);
			LOG.debug("node: {} base properties: {}", node.getName(), baseProperties);
			LOG.debug("node: {} base properties name: {}", node.getName(), basePropertiesName);

			actualProperties = actualProperties.stream()
					.filter(prop -> !basePropertiesName.contains(prop.getName()))
					.collect(toList());
			
			LOG.debug("node: {} vendor properties: {}", node.getName(), actualProperties);

			JSONArray vendorAttributesExtension = new JSONArray();

			actualProperties.forEach(property -> vendorAttributesExtension.put(new JSONObject().put(Extensions.EXTENSION_NAME,property.getName())));

			if(!vendorAttributesExtension.isEmpty()) {
				JSONObject attributesExtension = new JSONObject();
				attributesExtension.put(Extensions.EXTENSION_NAME, node.getName());
				attributesExtension.put(Extensions.ATTRIBUTE_EXTENSION, vendorAttributesExtension);
			
				resourceAttributeExtension.put(attributesExtension);
			}
			
			
		});

		extensions.put(Extensions.RESOURCE_ATTRIBUTE_EXTENSION, resourceAttributeExtension);

		if(args.extensionLabel!=null) {
			extensions.put(Extensions.LEGEND_LABEL, args.extensionLabel);
		} else {
			extensions.put(Extensions.LEGEND_LABEL, Extensions.getLabel());
		}
		
		if(args.extensionColor!=null) {
			extensions.put(Extensions.EXTENSION_COLOR, args.extensionColor);
		} else {
			extensions.put(Extensions.EXTENSION_COLOR, Extensions.getColor());
		}
		
		extensions = new JSONObject().put(Extensions.EXTENSIONS, extensions);
		
		LOG.debug("extensions={}", extensions.toString(2));
		LOG.debug("outputFileName={}", args.outputFileName);

		if(args.outputFileName!=null) {
			saveVendorExtensions(extensions, args.outputFileName, args.targetDirectory);
		}
	}

	private void saveVendorExtensions(JSONObject extensions, String filename, String target) {
	    LOG.debug("saveVendorExtensions: extensions={}",  extensions);
	    
	    String fileName = filename;
	    if(target!=null && !target.isBlank() && !filename.startsWith(File.separator)) {
	    	fileName = target + File.separator + filename;
	    }

	    Utils.saveJSON(extensions, fileName);

		Out.printAlways("... extracted vendor extensions: output={}", filename);

	}

}
