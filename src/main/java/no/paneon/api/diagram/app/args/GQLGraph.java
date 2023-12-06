package no.paneon.api.diagram.app.args;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.Parameter;

public class GQLGraph extends Common {
	
	@Parameter(description = "Files", arity=0)
	public List<String> files = new ArrayList<>();
	
}
	

