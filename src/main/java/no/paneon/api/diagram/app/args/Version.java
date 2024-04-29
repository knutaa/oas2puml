package no.paneon.api.diagram.app.args;


import com.beust.jcommander.Parameter;


public class Version {

	@Parameter(names = { "-v", "--version" }, description = "Tooling version details", help = true)
	public boolean version = false;

}