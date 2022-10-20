package no.paneon.api.extensions;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.diagram.app.Args;
import no.paneon.api.diagram.puml.Extensions;
import no.paneon.api.generator.GenerateCommon;
import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Node;
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
		JSONArray resourceDiscriminatorExtension = new JSONArray();
		JSONArray resourceInheritanceExtension = new JSONArray();

		actualAPI.getNodesByNames(allNodes).forEach(node -> {
			
			List<String> inherited = node.getInheritedProperties(actualAPI.getCompleteGraph()).stream().map(Property::getName).collect(toList());			
			List<Property> actualProperties = node.getProperties();

			Node baseNode = baseAPI.getNode(node.getName());
			List<String> baseInherited = baseNode.getInheritedProperties(baseAPI.getCompleteGraph()).stream().map(Property::getName).collect(toList());
			List<Property> baseProperties = baseNode.getProperties().stream().filter(p->!baseInherited.contains(p.getName())).collect(toList());
			List<String>   basePropertiesName = baseProperties.stream().map(Property::getName).collect(toList());

			LOG.debug("node: {} actual inherited: {}", node.getName(), inherited);
			LOG.debug("node: {} base inherited: {}", node.getName(), baseInherited);

			LOG.debug("node: {} actual properties: {}", node.getName(), actualProperties);
			LOG.debug("node: {} base properties: {}", node.getName(), baseProperties);
			LOG.debug("node: {} base properties name: {}", node.getName(), basePropertiesName);

			actualProperties = actualProperties.stream()
				.filter(prop -> {
					// boolean diff =!basePropertiesName.contains(prop.getName());
					
					Optional<Property> optProp = baseProperties.stream().filter(p -> p.getName().contentEquals(prop.getName())).findFirst();
					
					boolean diff = optProp.isEmpty();
					if(optProp.isPresent()) {
						Property opt = optProp.get();
						diff = diff || !opt.getName().contentEquals(prop.getName());
						diff = diff || !opt.getType().contentEquals(prop.getType());
						diff = diff || (opt.isRequired() != prop.isRequired() );
					}
					
					LOG.debug("filter: name={} prop={} diff={}", node.getName(), prop.getName(), diff);
					if(diff) LOG.debug("filter: name={} prop={} optProp={}", node.getName(), prop, optProp);

					return diff;
				})
				.collect(toList());
			
			LOG.debug("node: {} vendor properties: {}", node.getName(), actualProperties);

			JSONArray vendorAttributesExtension = new JSONArray();

			actualProperties.forEach(property -> {
				JSONObject ext = new JSONObject(); 
				ext.put(Extensions.EXTENSION_NAME,property.getName());
				
				Optional<Property> optProp = baseProperties.stream().filter(p -> p.getName().contentEquals(property.getName())).findFirst();
				
				if(optProp.isPresent()) {
					
					LOG.debug("node: {} property={} type={} actCard={} basedCard={}", 
							node.getName(), optProp.get().getName(), optProp.get().getType(),
							property.getCardinality(), optProp.get().getCardinality() 
							);

					if(!property.getCardinality().contentEquals(optProp.get().getCardinality())) {
						ext.put(Extensions.EXTENSION_CARDINALITY, true);
					}
				}
				
				vendorAttributesExtension.put(ext);

			});

			if(!vendorAttributesExtension.isEmpty()) {
				JSONObject attributesExtension = new JSONObject();
				attributesExtension.put(Extensions.EXTENSION_NAME, node.getName());
				attributesExtension.put(Extensions.ATTRIBUTE_EXTENSION, vendorAttributesExtension);
			
				resourceAttributeExtension.put(attributesExtension);
			}
			
			Set<String> actualDiscriminators = node.getAllDiscriminatorMapping();
			Set<String> baseDiscriminators   = baseNode.getAllDiscriminatorMapping();

			LOG.debug("node: {} actualDiscriminators: {}", node.getName(), actualDiscriminators);
			LOG.debug("node: {} baseDiscriminators: {}", node.getName(), baseDiscriminators);

			actualDiscriminators.removeAll(baseDiscriminators);
			if(!actualDiscriminators.isEmpty()) {
				JSONObject discriminatorExtension = new JSONObject();
				discriminatorExtension.put(Extensions.EXTENSION_NAME, node.getName());
				discriminatorExtension.put(Extensions.DISCRIMINATOR_EXTENSION, actualDiscriminators);
			
				resourceDiscriminatorExtension.put(discriminatorExtension);	
			}
				
			Set<String> actualInheritance = node.getActualInheritance();
			Set<String> baseInheritance   = baseNode.getActualInheritance();

			LOG.debug("node: {} actualInheritance: {}", node.getName(), actualInheritance);
			LOG.debug("node: {} baseInheritance: {}", node.getName(), baseInheritance);

			actualInheritance.removeAll(baseInheritance);
			if(!actualInheritance.isEmpty()) {
				JSONObject inheritanceExtension = new JSONObject();
				inheritanceExtension.put(Extensions.EXTENSION_NAME, node.getName());
				inheritanceExtension.put(Extensions.INHERITANCE_EXTENSION, actualInheritance);
			
				resourceInheritanceExtension.put(inheritanceExtension);	
			}
			
		});

		extensions.put(Extensions.RESOURCE_ATTRIBUTE_EXTENSION, resourceAttributeExtension);
		extensions.put(Extensions.RESOURCE_DISCRIMINATOR_EXTENSION, resourceDiscriminatorExtension);
		extensions.put(Extensions.RESOURCE_INHERITANCE_EXTENSION, resourceInheritanceExtension);

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
