package no.paneon.api.graph;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;

import no.paneon.api.diagram.layout.Place;
import no.paneon.api.model.APIModel;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;

public class EnumNode extends Node {
	
    static final Logger LOG = LogManager.getLogger(EnumNode.class);

	String node;
	String type;
	
	Node placedByNode;
	Place placedInDirection;
	
	List<String> values;

	public EnumNode(String type) {
		super(type);
		
		this.type=type;
		this.values = new LinkedList<>();
		
		processEnum();
	}
	
	@LogMethod(level=LogLevel.TRACE)
	void setPlacement(Node node, Place direction) {
		this.placedByNode = node;
		this.placedInDirection = direction;
	}
			
	@LogMethod(level=LogLevel.TRACE)
	public void addValue(String value) {
		this.values.add(value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public void addValues(List<String> enumValues) {
		enumValues.forEach(this::addValue);
	}

	@LogMethod(level=LogLevel.TRACE)
	public void setFloatingPlacement() {
		// TODO Auto-generated method stub
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getType() {
		return type;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<String> getValues() {
		return this.values;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void processEnum() {

	    if(!APIModel.isEnumType(type)) return;
	    	    				
	    JSONArray enumValues = APIModel.getDefinition(type).getJSONArray("enum");
	    	    
	    enumValues.forEach(v -> addValue(v.toString()));
	        
	}
	
	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType() {
		return false;
	}
}
