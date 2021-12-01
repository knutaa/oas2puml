package no.paneon.api.diagram.puml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.paneon.api.diagram.layout.Place;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.utils.Config;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class EdgeEntity extends Entity {
			
    static final Logger LOG = LogManager.getLogger(EdgeEntity.class);

	Node from = null;
	Node to = null;
	Edge edge = null;
	Place place = null;
	boolean required = false;

	Entity containedIn;
	
	boolean isMarked = false;
		
	public EdgeEntity(Node from, Place place, Node to, boolean required) {
		super();
		this.from=from;
		this.to=to;
		this.place=place;
		this.required=required;
		this.containedIn = null;
	}

	public EdgeEntity(Node from, Place place, Node to, boolean required, String id) {
		super();
		this.from=from;
		this.to=to;
		this.place=place;
		this.required=required;
		this.containedIn = null;
		
	}

	public EdgeEntity(Place place, Edge edge) {
		super();
		this.place=place;
		this.edge = edge;
		this.required=edge.isRequired();
		this.to = edge.related;
		this.from = edge.node;
	}
	
	public EdgeEntity(Node from, Place place, Node to, boolean required, String id, String rule) {
		this(from,place,to,required,id);
		addComment(new Comment("'rule: " + rule));
	}

	public EdgeEntity(Place direction, Edge edge, String rule) {
		this(direction, edge);
		addComment(new Comment("'rule: " + rule));
		
		this.isMarked = edge.isMarked;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		String res="";
		
		String cardinality = edge!=null ? edge.cardinality : "";
		
		if(cardinality.length()>0) {
			cardinality = "\"" + cardinality + "\"";
		}
		
		String label = edge!=null ? edge.relation : "";
		
		if(label.contains("xor")) {
			return res;
		}
		
		String strLabel = label;
		if(required) {
			String format = Config.getRequiredFormatting();
			strLabel = String.format(format,strLabel);
		} 
		
		switch(place) {
		case LEFT: 
		case FORCELEFT:
		    // res = to + " " + cardinality + " <-left-* " + from + " : " + strLabel + '\n';
			res = from + " *-left-> " + cardinality + " " + to + " : " + strLabel + '\n';
			break;
			
		case RIGHT:
		case FORCERIGHT:
			res = from + " *-right-> " + cardinality + " " + to + " : " + strLabel + '\n';
			break;
			
		case ABOVE:
		case FORCEABOVE:
		    res = to + " " + cardinality + " <--* " + from + " : " + strLabel + '\n';
			break;
			
		case BELOW:
		case FORCEBELOW:
		    res = from + " *--> " + " " + cardinality + " " + to + " : " + strLabel + '\n';
			break;
			
		case BELOW_LONG:
		    res = from + " *---> " + " " + cardinality + " " + to + " : " + strLabel + '\n';
			break;

		case ABOVE_LONG:
		    res = to + " " + cardinality + " <---* " + from + " : " + strLabel + '\n';
			break;

		default:
				
		}
		
		if(Config.getIncludeDebug()) {
			res = getCommentInfo() + res;
		}
	    
	    return res;	
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Entity getContainingEntity() {
		return containedIn;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void setContainingEntity(Entity entity ) {
		this.containedIn = entity;
	}
		
}
	