package no.paneon.api.extensions;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.diagram.app.Args;
import no.paneon.api.diagram.app.args.ExtractExtension;
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

	private ExtractExtension args;
	
	public ExtractExtensions(ExtractExtension args) {
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
		
		List<String> allNodes = actualAPI.getNodes().stream().map(APIModel::getMappedResource).collect(toList());
		List<String> baseAPINodes = baseAPI.getNodes().stream().map(APIModel::getMappedResource).collect(toList());
	
		allNodes.removeAll(baseAPINodes);
		
		JSONArray resourceExtensions = new JSONArray();
		
		allNodes.forEach(node -> resourceExtensions.put(new JSONObject().put(Extensions.EXTENSION_NAME,node)));
	
		extensions.put(Extensions.RESOURCE_EXTENSION, resourceExtensions);
			
		allNodes = actualAPI.getNodes();
		allNodes.retainAll(baseAPINodes);
		
		JSONArray resourceAttributeExtension = new JSONArray();
		JSONArray resourceDiscriminatorExtension = new JSONArray();
		JSONArray resourceInheritanceExtension = new JSONArray();

		Out.printAlways("... extractExtensions");

		actualAPI.getNodesByNames(allNodes).forEach(node -> {
			
			List<String> inherited = node.getInheritedProperties(actualAPI.getCompleteGraph()).stream().map(Property::getName).collect(toList());			
			List<Property> actualProperties = node.getProperties();
			
			Node baseNode = baseAPI.getNode(APIModel.getReverseResourceMapping(node.getName()));
			List<String> baseInherited = baseNode.getInheritedProperties(baseAPI.getCompleteGraph()).stream().map(Property::getName).collect(toList());
			
			List<Property> baseInheritedProperties = baseNode.getInheritedProperties(baseAPI.getCompleteGraph());

			List<Property> baseProperties = baseNode.getProperties().stream().filter(p->!baseInherited.contains(p.getName())).collect(toList());
			List<String>   basePropertiesName = baseProperties.stream().map(Property::getName).collect(toList());

			LOG.debug("ExtractExtension:: node={} actual properties={}", node.getName(), actualProperties);
			LOG.debug("ExtractExtension:: node={} actual inherited: {}", node.getName(), inherited);

			LOG.debug("node: {} base properties: {}", node.getName(), baseProperties);
			LOG.debug("node: {} base inherited: {}", node.getName(), baseInherited);

			LOG.debug("node: {} base properties name: {}", node.getName(), basePropertiesName);

			
			actualProperties = actualProperties.stream()
				.filter(prop -> {
					// boolean diff =!basePropertiesName.contains(prop.getName());
					
					Optional<Property> optProp = baseProperties.stream().filter(p -> p.getName().contentEquals(prop.getName())).findFirst();
					
					boolean diff = optProp.isEmpty();
					if(optProp.isPresent()) {
						Property opt = optProp.get();
						boolean a = !opt.getName().contentEquals(prop.getName());
						boolean b = !opt.getType().contentEquals(prop.getType());
						boolean c = (opt.isRequired() != prop.isRequired() );
						boolean d = !prop.getCardinality().contentEquals(opt.getCardinality());
						
						diff = a || b || c || d;
						
						LOG.debug("filter: name={} prop={} a={} b={} c={} d={}", node.getName(), prop, a, b, c, d);

					}
					
					LOG.debug("filter: name={} prop={} diff={}", node.getName(), prop.getName(), diff);
					if(diff) LOG.debug("... filter: name={} prop={} optProp={}", node.getName(), prop, optProp);

					return diff;
				})
				.collect(toList());
			
			LOG.debug("node: {} extension properties: {}", node.getName(), actualProperties);

			JSONArray attributesExtension = new JSONArray();

			actualProperties.forEach(property -> {
				JSONObject ext = new JSONObject(); 
				ext.put(Extensions.EXTENSION_NAME,property.getName());
				
				Optional<Property> optProp = baseProperties.stream().filter(p -> p.getName().contentEquals(property.getName())).findFirst();
				
				
				if(optProp.isPresent()) {
					
					Property p = optProp.get();
					
					LOG.debug("node: {} property={} type={} actCard={} basedCard={}", 
							node.getName(), p.getName(), p.getType(),
							property.getCardinality(), p.getCardinality() 
							);

					if(!property.getCardinality().contentEquals(p.getCardinality())) {
						ext.put(Extensions.EXTENSION_CARDINALITY, true);
					}
					
					if(property.isRequired() != p.isRequired()) {
						ext.put(Extensions.EXTENSION_REQUIRED, true); // property.isRequired());
					}
					
					if(!property.getType().contentEquals(p.getType())) {
						ext.put(Extensions.EXTENSION_TYPE, true);
					}
					
				} else {
					optProp = baseInheritedProperties.stream().filter(p -> p.getName().contentEquals(property.getName())).findFirst();
					if(optProp.isPresent()) {
						
						Out.debug("node: {} MOVED property={}", node.getName(), property.getName());

						ext.put(Extensions.EXTENSION_MOVED, true);
					}

				}
				
				attributesExtension.put(ext);

			});

			if(!attributesExtension.isEmpty()) {
				JSONObject extension = new JSONObject();
				extension.put(Extensions.EXTENSION_NAME, node.getName());
				extension.put(Extensions.ATTRIBUTE_EXTENSION, attributesExtension);
			
				resourceAttributeExtension.put(extension);
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

		LOG.debug("... extracted extensions: output={}", filename);

	}

}
