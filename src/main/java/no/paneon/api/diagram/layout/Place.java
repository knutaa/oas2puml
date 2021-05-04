package no.paneon.api.diagram.layout;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import no.panoen.api.logging.LogMethod;
import no.panoen.api.logging.AspectLogger.LogLevel;

public enum Place {
	
	LEFT("placeLeft"),
	RIGHT("placeRight"),
	ABOVE("placeAbove"),
	BELOW("placeBelow"),

	LEFT_LONG("placeLeftLong"),
	RIGHT_LONG("placeRightLong"),
	ABOVE_LONG("placeAboveLong"),
	BELOW_LONG("placeBelowLong"),

	FORCELEFT("forceLeft"),
	FORCERIGHT("forceRight"),
	FORCEABOVE("forceAbove"),
	FORCEBELOW("forceBelow"),
	FORCEFLOAT("forceFloat"),
	
	EMPTY("empty");

    public final String label;
    
    private Place(String label) {
        this.label = label;
    }
    
	@LogMethod(level=LogLevel.DEBUG)
    public boolean isForced() {
    	List<Place> forced = Arrays.asList(Place.FORCELEFT, Place.FORCERIGHT, Place.FORCEABOVE, Place.FORCEBELOW, Place.FORCEFLOAT);
    	return forced.stream()
    			.map(Object::toString)
    			.anyMatch(p -> p.equals(this.toString()));
    }
    
	@LogMethod(level=LogLevel.DEBUG)
    public static List<Place> coreValues() {
    	return Arrays.asList(Place.LEFT, Place.RIGHT, Place.ABOVE, Place.BELOW);
    }
    
	private static Map<Place,Place> mapping = new EnumMap<>(Place.class); 
	private static Map<Place,Place> coreDirection = new EnumMap<>(Place.class); 
			
	static {
		mapping.put(Place.LEFT, Place.RIGHT);
		mapping.put(Place.RIGHT, Place.LEFT);
		mapping.put(Place.ABOVE, Place.BELOW);
		mapping.put(Place.BELOW, Place.ABOVE);
		
		mapping.put(Place.LEFT_LONG, Place.RIGHT_LONG);
		mapping.put(Place.RIGHT_LONG, Place.LEFT_LONG);
		mapping.put(Place.ABOVE_LONG, Place.BELOW_LONG);
		mapping.put(Place.BELOW_LONG, Place.ABOVE_LONG);
	
		coreDirection.put(Place.FORCELEFT, Place.LEFT);
		coreDirection.put(Place.FORCERIGHT, Place.RIGHT);
		coreDirection.put(Place.FORCEABOVE, Place.ABOVE);
		coreDirection.put(Place.FORCEBELOW, Place.BELOW);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Place reverse(Place direction) {
		return getMapping().get(direction);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Map<Place,Place> getMapping() {
		return mapping;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Map<Place,Place> getCoreDirectionMapping() {
		return coreDirection;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static void setMapping(Map<Place,Place> map) {
		mapping = map;
	}

	static Place getReverse(Place direction) {
		return getMapping().get(direction);
	}

	Place reverse() {
		return getReverse(this);
	}
	
}



