package no.panoen.api.logging;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

@Aspect
public class AspectLogger {

	static final Logger LOG = LogManager.getLogger(AspectLogger.class);
	
	public enum LogLevel {
	    OFF(Level.OFF),
	    INFO(Level.INFO),
	    DEBUG(Level.DEBUG),
	    FATAL(Level.FATAL),
	    ERROR(Level.ERROR),
	    WARN(Level.WARN),
	    ALL(Level.ALL),
	    TRACE(Level.TRACE);
		
		public final Level level;
		
		LogLevel(Level level) {
			this.level = level;
		}
		
	}
	
	public static final Level VERBOSE = Level.forName("VERBOSE", 50);
	
	private static final int LINESIZE = 120;
	
	private static Level globalLevel = AspectLogger.VERBOSE;
	
	
	public static void setGlobalDebugLevel(Level level) {		
		globalLevel = level;
	}
	
    @Around("execution(* *(..)) && @annotation(LogMethod)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

    	Level level = getLevel(joinPoint);
    	if(activeLogging(level)) {
    		
	    	String args = getArgumentsAsString(joinPoint);

	    	Logger currentLogger = getActiveLogger(joinPoint);   
	    		    	
	    	String logSource = joinPoint.getSignature().getDeclaringTypeName();
	    	
	    	long before = logBefore(joinPoint, level, args, currentLogger, logSource);
	    	
	        Object result = joinPoint.proceed();
        
	        logAfter(joinPoint, level, before, result, args, currentLogger, logSource);
	        
	        return result;
		       
        } else {
    	
        	return joinPoint.proceed();
        	
        }
    }

	private void logAfter(ProceedingJoinPoint joinPoint, Level level, long before, Object result, String args, Logger currentLogger, String logSource) {
        if(level!=Level.OFF) {
        	
	        long after = System.currentTimeMillis();
	        long time = (after - before);

    		String res = getResultAsString(result);
	        	
	        String msg = "";
	    	
	    	if(time>0) {		    	
		    	msg = " <-- "
		    			+ joinPoint.getSignature().getName() 
		    			+ " (" + time + ")"
		    			+ (!args.isEmpty() ? " :: args = " + args : "");
		    	
		        logging(currentLogger, level, msg, logSource);
	    	}
	    	
	    	msg = " <-- "
	    			+ joinPoint.getSignature().getName() 
	    			+ " (" + time + ")"
	    			+ (!res.isEmpty() ? " :: res = " + res : "");

	        logging(currentLogger, level, msg, logSource);
        }		
	}

	private long logBefore(ProceedingJoinPoint joinPoint, Level level, String args, Logger activeLogger, String logSource) {
		
        long before = System.currentTimeMillis();
        
    	if(level!=Level.OFF) { 

    		String msg =  " --> " 
    					  + joinPoint.getSignature().getName()
	    				  + (!args.isEmpty() ? " :: args = " + args : "");
		
	        logging(activeLogger, level, msg, logSource);
	    	
    	}
    	return before;
		
	}

	private Logger getActiveLogger(ProceedingJoinPoint joinPoint) {
		Logger activeLogger = null;
		
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Class<?> clazz = methodSignature.getDeclaringType();
        
        if(LogManager.getLogger(clazz)!=null) activeLogger = LogManager.getLogger(clazz);
                
		return activeLogger;
	}

	private Level getLevel(ProceedingJoinPoint joinPoint) {
		Level res = Level.OFF;

		try {
	        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
	        Class<?> clazz = methodSignature.getDeclaringType();
	        Method method = clazz.getDeclaredMethod(methodSignature.getName(), methodSignature.getParameterTypes());
	    	LogMethod argumentAnnotation;
			Annotation[] annotations = method.getAnnotations();
			
	    	if(annotations!=null ) {
		        for (Annotation ann : annotations) {
		        	if(ann instanceof LogMethod) {
		                argumentAnnotation = (LogMethod) ann;
		                res=argumentAnnotation.level().level;
		            }
		        }
	        }	
		} catch(Exception e) {
			LOG.log(Level.ERROR, "getLevel exception: {}", e.getLocalizedMessage());
			res = Level.OFF;
		}
		
		return res;
	}

	private String getResultAsString(Object result) {
		String res="null";
        
		if(result!=null) {
	        if(result instanceof JSONObject) {
	        	JSONObject r = (JSONObject) result;
	        	res = r.toString();
	        } else if(result instanceof JSONArray) {
	        	JSONArray a = (JSONArray) result;
	        	res = a.toString();
	        } else {
	        	res = result.toString();
	        }
			if(res.length()>LINESIZE) res = res.substring(0,LINESIZE) + " ...";
        } 
        
		return res;
	}

	private String getArgumentsAsString(ProceedingJoinPoint joinPoint) {
		String res="";
    	if(joinPoint.getArgs()!=null) {
     	   res = Arrays.asList(joinPoint.getArgs()).stream()
     			   .map(obj -> {
     				   String arg = (obj!=null) ? obj.toString() : "null"; 
     				   if(arg.length()>LINESIZE) arg = arg.substring(0,LINESIZE) + "...";
     				   return arg;
     				   })
     			   .collect(Collectors.joining(", "));
     	} else {
     		res="(no args)";
     	}
		return res;
	}
   

	private void logging(Logger logger, Level level, String msg, String logSource) {
				
    	if(logger==null) {
	    	msg = logSource + "  - " + msg;
    		log(LOG, level, msg);
    	}
    	else {
    		log(logger, level, msg);	
    	}
 	
	}

	private void log(Logger logger, Level level, String msg) {
				
		if((level.equals(AspectLogger.VERBOSE) 
				|| level.equals(org.apache.logging.log4j.Level.DEBUG))
			&& (msg.length()>LINESIZE)) {
			
			msg = msg.substring(0,LINESIZE) + "...";
		}
					
		logger.log(level, msg);		
		
	}
	
	private boolean activeLogging(Level level) {
				
		boolean res = (globalLevel != AspectLogger.VERBOSE) && (globalLevel != Level.OFF);
				
		// could also include filtering based on globalLevel: (level.intLevel()<=globalLevel.intLevel());
		
		return res;
		
	}
	
}