package no.paneon.api.diagram.layout;

import org.apache.logging.log4j.Logger;

import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;

public class Position implements Comparable {
	
    static final Logger LOG = LogManager.getLogger(Position.class);

	private int x=-100;
	private int y=-100;
	boolean positioned=false;
	
	Position() {
		this.setX(10);
		this.setY(10);
		positioned=false;
	}
	
	Position(boolean center) {
		this.setX(10);
		this.setY(10);
		positioned=true;
	}
	
	Position(Position pos) {
		this.setX(pos.getX());
		this.setY(pos.getY());
		positioned=true;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public String toString() {
		return "[x=" + getX() + ", y=" + getY() + "]";
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public boolean isPositioned() {
		return positioned;
	}

	@LogMethod(level=LogLevel.TRACE)
	public int getX() {
		return x;
	}

	@LogMethod(level=LogLevel.TRACE)
	public void setX(int x) {
		this.x = x;
		this.positioned=true;

	}

	@LogMethod(level=LogLevel.TRACE)
	public int getY() {
		return y;
	}

	@LogMethod(level=LogLevel.TRACE)
	public void setY(int y) {
		this.y = y;
		this.positioned=true;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public boolean lessX(Position pos) {
		return getX() < pos.getX();
	}

	@Override
	public int compareTo(Object o) {
		if(!(o instanceof Position)) return -1;
		
		Position pos = (Position) o;
		
		if(this.y <pos.y) 
			return -1;
		else if(this.y > pos.y)
			return 1;
		else {
			if(this.x < pos.x) 
				return -1;
			else if(this.x > pos.x)
				return 1;
		}
		return 0;
	}	
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof Position)) return false;
		
		Position pos = (Position) o;

		return pos.x==this.x && pos.y==this.y;
		
	}	
	
	 @Override
	  public int hashCode() {
		 return this.y*1000+this.x;
	  }

	public void moveTo(Position pos) {
		this.x = pos.x;
		this.y = pos.y;
		this.positioned = true;
	}

	public void move(Place direction) {
		switch(direction) {
		case LEFT:
			this.x = this.x-1;
			break;
			
		case RIGHT:
			this.x = this.x+1;
			break;
			
		case ABOVE:
			this.y = this.y-1;
			break;
			
		case BELOW:
			this.y = this.y+1;
			break;
			
		default:
				
		}
		
		LOG.debug("move:: direction={} new pos={}", direction, this);
		
	}

	
}
