package no.paneon.api.diagram.puml;

import no.paneon.api.diagram.layout.Place;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.utils.Config;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;


public class AllOfEdge extends HiddenEdge {
	
	public AllOfEdge(Node from, Place place, Node to, boolean first) {
		super(from, place, to, first);
	}

	public AllOfEdge(Node from, Place place, Node to) {
		super(from, place, to, false);
	}

	public AllOfEdge(Place place, Edge edge) {
		super(place, edge);
	}
	
	public AllOfEdge(Node from, Place place, Node to, boolean required, String id, String rule) {
		this(from,place,to);
		addComment(new Comment("'rule: " + rule));
	}

	public AllOfEdge(Place direction, Edge edge, String rule) {
		this(direction, edge);
		addComment(new Comment("'rule: " + rule));
	}

	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		String res="";
		
		String label = edge!=null ? edge.relation : "";
			
		String strLabel = label;
		if(required) {
			String format = Config.getRequiredFormatting();
			strLabel = String.format(format,strLabel);
		} 
	
		switch(place) {
		case LEFT: 
		case FORCELEFT:
			// res = to + " <|-left- " + from + " : " + strLabel + '\n';
			res = from + " <|-left- " + to + " : " + strLabel + '\n';

		    break;
			
		case RIGHT:
		case FORCERIGHT:
		    res = from + " -right-|> " + to + " : " + strLabel + '\n';

		    break;
			
		case ABOVE:
		case FORCEABOVE:
		    res = to + " <|-- " + from + " : " + strLabel + '\n';
			break;
			
		case BELOW:
		case FORCEBELOW:
		    res = from + " --|> " + to + " : " + strLabel + '\n';
			break;
			
		case BELOW_LONG:
		    res = from + " ---|> " + to + " : " + strLabel + '\n';
			break;

		case ABOVE_LONG:
		    res = to + " <|--- " + from + " : " + strLabel + '\n';
			break;

		default:
				
		}
		
		if(Config.getIncludeDebug()) {
			res = getCommentInfo() + res;
		}
	    
	    return res;	
		
	}

}
