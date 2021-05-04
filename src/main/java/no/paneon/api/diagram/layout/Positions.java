package no.paneon.api.diagram.layout;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static java.util.stream.Collectors.toList;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import no.paneon.api.graph.Node;
import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;


public class Positions {
	
    static final Logger LOG = LogManager.getLogger(Positions.class);

	Map<Node,Position> position;
	
	public Positions() {
		this.position = new HashMap<>();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	void setPosition(Node node) {
		position.put(node, new Position(true));
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void positionToLeft(Node from, Node to) {
						
		Position pos=getPosition(from);
		if(pos.isPositioned()) {
			pos=new Position(pos);
			pos.setX(pos.getX() - 1);
			position.put(to, pos);
		} else {
			pos=getPosition(to);
			pos=new Position(pos);
			pos.setX(pos.getX() + 1);
			position.put(from, pos);
		}
		
		LOG.trace("positionToLeft: from={} to={} positionFrom={} positionTo={}", from, to, getPosition(from), getPosition(to));
		
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	public void positionToRight(Node from, Node to) {
		
		Position pos=getPosition(from);
		if(pos.isPositioned()) {
			pos=new Position(pos);
			pos.setX(pos.getX() + 1);
			position.put(to, pos);
		} else {
			pos=getPosition(to);
			pos=new Position(pos);
			pos.setX(pos.getX() - 1);
			position.put(from, pos);
		}
		
		LOG.trace("positionToRight: from={} to={} positionFrom={} positionTo={}", from, to, getPosition(from), getPosition(to));

	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	public void positionAbove(Node from, Node to) {
		
		Position fromPos=getPosition(from);
		Position toPos=getPosition(to);
		if(fromPos.isPositioned()) {
			if(!toPos.isPositioned()) {
				Position pos=new Position(fromPos);
				pos.setY(pos.getY() - 1);
				position.put(to, pos);
			} else {
				Position pos=new Position(toPos);
				pos.setY(pos.getY() + 1);
				position.put(from, pos);
			}
		} else {
			Position pos=new Position(toPos);
			pos.setY(pos.getY() + 1);
			position.put(from, pos);
		}
		
		LOG.trace("positionToAbove: from={} to={} positionFrom={} positionTo={}", from, to, getPosition(from), getPosition(to));

	}
		
	@LogMethod(level=LogLevel.DEBUG)
	public void positionBelow(Node from, Node to) {
		
		Position pos=getPosition(from);
		if(pos.isPositioned()) {
			pos=new Position(pos);
			pos.setY(pos.getY() + 1);
			position.put(to, pos);
		} else {
			pos=getPosition(to);
			pos=new Position(pos);
			pos.setY(pos.getY() - 1);
			position.put(from, pos);
		}
		
		LOG.trace("positionToBelow: from={} to={} positionFrom={} positionTo={}", from, to, getPosition(from), getPosition(to));

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Position getPosition(Node node) {
		if(!position.containsKey(node)) {
			position.put(node,new Position());
		}
		return position.get(node);
	}
	
	 
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isAtSameColumn(Node nodeA, Node nodeB) {
	    Position posA = getPosition(nodeA);
	    Position posB = getPosition(nodeB);
	    return posA.getX()==posB.getX();	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isAtSameLevel(Node nodeA, Node nodeB) {
	    Position posA = getPosition(nodeA);
	    Position posB = getPosition(nodeB);
	    return posA.getY()==posB.getY();	
	}
	
		
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPositionedAbove(Node nodeA, Node nodeB) { 
	    Position posA = getPosition(nodeA);
	    Position posB = getPosition(nodeB);
	    boolean res = posA.getY()<posB.getY();
	    
	    LOG.debug("isPositionedAbove: nodeA={} nodeB={} posA={} posB={} res={}", nodeA, nodeB, posA, posB, res);
	    return res;
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPositionedBelow(Node nodeA, Node nodeB) { 
	    Position posA = getPosition(nodeA);
	    Position posB = getPosition(nodeB);
	    boolean res = posA.getY()>posB.getY();
	    
	    LOG.debug("isPositionedBelow: nodeA={} nodeB={} posA={} posB={} res={}", nodeA, nodeB, posA, posB, res);
	    return res;
	}
	
		
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPositionedToRight(Node nodeA, Node nodeB) { 
	    Position posA = getPosition(nodeA);
	    Position posB = getPosition(nodeB);
	    boolean res = posA.getX()>posB.getX();
	    
	    LOG.debug("isPositionedToRight: nodeA={} nodeB={} posA={} posB={} res={}", nodeA, nodeB, posA, posB, res);
	    return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPositionedToLeft(Node nodeA, Node nodeB) { 
	    Position posA = getPosition(nodeA);
	    Position posB = getPosition(nodeB);
	    boolean res = posA.getX()<posB.getX();
	    
	    LOG.debug("isPositionedToLeft: nodeA={} nodeB={} posA={} posB={} res={}", nodeA, nodeB, posA, posB, res);
	    return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	public int currentlyPlacedAtLevel(Node node) {
		return currentlyPlacedAtLevel(node,0); 
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public int currentlyPlacedAtLevel(Node node, int offset) {
		int res=0;
		if(position.containsKey(node)) {
			Position pos = getPosition(node);
			List<Node> nodes = position.keySet().stream()
									.filter(n -> getPosition(n).getY()==pos.getY()+offset)
									.distinct().collect(toList());
			
            LOG.debug("currentlyPlacedAtLevel: node={} offset={} nodes={}", node, offset, nodes);
            
            res = nodes.size();
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> placedAtLevel(Node node) {
		List<Node> res = new LinkedList<>();
		if(position.containsKey(node)) {
			Position pos = getPosition(node);
			res = position.keySet().stream()
						.filter(n -> getPosition(n).getY()==pos.getY())
						.collect(toList());
		}
		
        LOG.debug("placedAtLevel: node={} res={}", node, res);
        res.forEach(n -> LOG.debug("placedAtLevel: node={} n={} pos={}", node, n, getPosition(n)) );

		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isUnbalancedLevel(Node node) {
		long levelWidth = currentlyPlacedAtLevel(node,-1); // KJ WAS +1
		long aboveWidth = currentlyPlacedAtLevel(node,+1); // KJ WAS -1
		return levelWidth>aboveWidth;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void position(Node from, Node to, Place direction) {
				
		Position posFrom = this.getPosition(from);
		Position posTo = this.getPosition(to);
		
		if(!posTo.isPositioned()) {
			
			posTo.moveTo(posFrom);
			posTo.move(direction);		
			
			LOG.debug("position: from={} to={} direction={}", from, to, direction);

		} else if(!posFrom.isPositioned()) {
			
			posFrom.moveTo(posTo);
			posFrom.move(direction.reverse());		
			
			LOG.debug("position: from={} to={} direction={}", from, to, direction);

		} else {
			// posTo.moveTo(posFrom);
			// posTo.move(direction);				
		}
		
//		switch(direction) {
//		case LEFT: 
//		    this.positionToLeft(from, to);
//			break;
//			
//		case RIGHT: 
//		    this.positionToRight(from,to);
//			break;			
//			
//		case ABOVE: 
//		    this.positionAbove(from,to);
//			break;
//			
//		case BELOW:
//		    this.positionBelow(from,to);
//			break;
//			
//		default:
//			break;
//		
//		}
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> currentlyPlacedBetween(Node nodeA, Node nodeB) {
		List<Node> res = new LinkedList<>();
		if(!isAtSameLevel(nodeA,nodeB)) return res;
		Position posA = getPosition(nodeA);
		Position posB = getPosition(nodeB);
		res = placedAtLevel(nodeA).stream()
				.filter(n -> {
					Position p = getPosition(n);
			        LOG.debug("currentlyPlacedBetween: n={} p={} posA={} posB={}", n, p, posA, posB);
					if(posA.getX()<posB.getX()) return posA.getX()<p.getX() && p.getX()<posB.getX();
					if(posB.getX()<posA.getX()) return posB.getX()<p.getX() && p.getX()<posA.getX();
					return false;
				}).collect(toList());
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> currentPlaced(Node node, Place direction) {
		return placedAtLevel(node).stream()
				.filter(n -> !n.equals(node))
				.filter(n -> isPlaced(n,node,direction)) 
				.collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean isPlaced(Node nodeA, Node nodeB, Place direction) {
		Position posA = getPosition(nodeA);
		Position posB = getPosition(nodeB);

		boolean res=false;
		switch(direction) {
		case LEFT: 
			res = posA.getX()<posB.getX();
			break;

		case RIGHT: 
			res = posA.getX()>posB.getX();
			break;			

		case ABOVE: 
			res = posA.getY()<posB.getY();
			break;

		case BELOW:
			res = posA.getY()>posB.getY();
			break;
		default:
		}

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> currentPlacedRightOf(Node node) {
		Position pos = getPosition(node);
		return placedAtLevel(node).stream()
				.filter(n -> !n.equals(node))
				.filter(n -> { Position p = getPosition(n); return p.getX()>=pos.getX();}) 
				.collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> currentPlacedLeftOf(Node node) {
		Position pos = getPosition(node);
		return placedAtLevel(node).stream()
				.filter(n -> !n.equals(node))
				.filter(n -> { Position p = getPosition(n); return p.getX()<=pos.getX();}) 
				.collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> currentPlacedAbove(Node node) {
		Position pos = getPosition(node);
		return placedAtLevel(node).stream()
				.filter(n -> !n.equals(node))
				.filter(n -> { Position p = getPosition(n); return p.getY()<=pos.getY();}) 
				.collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> currentPlacedBelow(Node node) {
		Position pos = getPosition(node);
		return placedAtLevel(node).stream()
				.filter(n -> !n.equals(node))
				.filter(n -> { Position p = getPosition(n); return p.getY()>=pos.getY();}) 
				.collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isPlaced(Node node, Place direction) {
		List<Node> placed = currentPlaced(node,direction);
	

		return !placed.isEmpty();

	}
	
}
