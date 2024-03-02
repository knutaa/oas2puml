package no.paneon.api.diagram.puml;

import java.util.LinkedList;
import java.util.List;

import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import static java.util.stream.Collectors.toList;

public class Entity extends Core {
	
	private List<Comment> comments;
	
	public Entity() {
		comments = new LinkedList<>();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Entity addComment(Comment c) {
		comments.add(c);
		return this;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getCommentsBefore(int to) {
		return getCommentInfo(Integer.MIN_VALUE, to);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getCommentsAfter(int from) {
		return getCommentInfo(from, Integer.MAX_VALUE);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getCommentInfo(int from, int to) {
		StringBuilder res = new StringBuilder();
		res.append("'sequence: " + this.seq);
		res.append( NEWLINE );
		for(Comment c : comments.stream().filter(c -> c.seq>=from && c.seq<=to).toList()) {
			res.append( c.getComment() );
			res.append( NEWLINE );
		}

		return res.toString();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	protected String getCommentInfo() {
		return getCommentInfo(Integer.MIN_VALUE, Integer.MAX_VALUE);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<Comment> getComments() {
		return this.comments;
	}

	static final String NONAME = "";
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getName() {
		return NONAME;
	}
		
}
