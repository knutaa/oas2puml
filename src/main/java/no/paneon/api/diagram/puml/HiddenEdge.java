package no.paneon.api.diagram.puml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.paneon.api.diagram.layout.Place;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.utils.Config;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class HiddenEdge extends EdgeEntity {

    static final Logger LOG = LogManager.getLogger(HiddenEdge.class);

    boolean first=true;
    
	public HiddenEdge(Node from, Place place, Node to) {
		super(from, place, to, false);
		this.first=true;
	}
	
	public HiddenEdge(Node from, Place place, Node to, boolean first, String rule) {
		super(from, place, to, false, "", rule);
		this.first=first;
	}

	public HiddenEdge(Node from, Place place, Node to, boolean first) {
		super(from, place, to, false);
		this.first=first;
	}
	
	public HiddenEdge(Node from, Place place, Node to, String rule) {
		super(from, place, to, false, "", rule);
		LOG.debug("HiddenEdge:: from={} place={} to={}",  from, place, to);
	}

	public HiddenEdge(Place place, Edge edge) {
		super(place, edge);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String toString() {
		String res="";
				
		String from = Utils.quote(this.from.getName());
		String to = Utils.quote(this.to.getName());

		switch(place) {
		case LEFT: 
		    // res = to + " <-left[hidden]- " + from + NEWLINE;
		    res = from + " -left[hidden]- " + to + NEWLINE;
			break;
			
		case RIGHT:
		    res = from + " -right[hidden]-> " + to + NEWLINE;
			break;
			
		case ABOVE:
		    res = to + " <-[hidden]- " + from  + NEWLINE;
			break;
			
		case BELOW:
		    res = from + " -[hidden]-> " + to + NEWLINE;
			break;
		
		default:
			LOG.error("HiddenEdge with unexpected place: " + place);
		}
	    
		if(!first) {
			res = res.replace("<-", "-").replace("->", "-");
		}
		
		LOG.debug("HiddenEdge: comment={}", getCommentInfo());
		
		if(Config.getIncludeDebug()) {
			res = getCommentInfo() + res;
		}
	
	    return res;	
	}
	
}
