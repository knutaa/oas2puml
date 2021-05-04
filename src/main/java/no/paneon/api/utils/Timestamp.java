package no.paneon.api.utils;

public class Timestamp {

	
	static long lastTime = System.currentTimeMillis();
	static long startTime = lastTime;

	static boolean active = false;
	
	enum Mode {
		FROM_START,
		FROM_LAST
	}
	
	static public Mode FROM_START = Mode.FROM_START;
	static public Mode FROM_LAST = Mode.FROM_LAST;

	public static void timeStamp(String message) {
		timeStamp(message, FROM_LAST);
	}
	
	public static void timeStamp(String message, Mode mode) {
		long now = System.currentTimeMillis();
		
		long used = mode==FROM_START ? now - startTime : now - lastTime;
		
		if(active) {
			Out.debug("... time used: {} {}",  used, message);
		}
		
		lastTime = now;
	}
	
}
