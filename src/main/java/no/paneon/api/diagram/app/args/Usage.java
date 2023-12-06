package no.paneon.api.diagram.app.args;


import com.beust.jcommander.Parameter;

public class Usage {

	@Parameter(names = { "-h", "--help" }, description = "Usage details", help = true)
	public boolean help = false;


}
