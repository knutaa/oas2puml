package no.paneon.api.generator;

import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Timestamp;
import no.paneon.api.utils.Utils;
import no.paneon.api.utils.WhitelistVerifier;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.diagram.app.args.Common;
import no.paneon.api.logging.AspectLogger;
import no.paneon.api.logging.AspectLogger.LogLevel;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class GenerateCommon {
	
	protected Common common;
	
    static final Logger LOG = LogManager.getLogger(GenerateCommon.class);

	public GenerateCommon(Common common)  {
		this.common = common;
		
		if(!common.whitelisting.isEmpty()) {	
			LOG.debug("setTrustManager");
			setTrustManager();
		}
		
		loadAPI(common);
		
		Out.silentMode = common.silentMode;
		
		setLogLevel( Utils.getLevelmap().get(common.debug));

        Config.setConfigSources(common.configs);
        
    	if(common.debug!=null && Utils.getLevelmap().containsKey(common.debug)) {
			setLogLevel( Utils.getLevelmap().get(common.debug) );
    	}
        
    	if(common.openAPIFile!=null) {
    		APIModel.setSwaggerSource(common.openAPIFile);
    	}
 
    	if(common.rulesFile!=null) {
    		Config.setRulesSource(common.rulesFile);
    	}
    	
    	if(common.conformanceSourceOnly) {
    		Config.setBoolean("conformanceSourceOnly",true);
    	}
    	
    	if(common.timestamp) {
    		Timestamp.setActive();  		
    	}

    	   
	}
	
	
	private void setTrustManager() {
		
		TrustManager[] trustAllCerts = new TrustManager[] {
			new X509TrustManager() {

				public X509Certificate[] getAcceptedIssuers1() {
					LOG.debug("X509Certificate #1");
					return null;
				}

				public void checkClientTrusted1(X509Certificate[] certs, String authType) {
					LOG.debug("X509Certificate #2");
				}

				public void checkServerTrusted1(X509Certificate[] certs, String authType) {
					LOG.debug("X509Certificate #3");
				}

				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					LOG.debug("X509Certificate #4 chain");
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					LOG.debug("X509Certificate #5 chain");
				}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					LOG.debug("X509Certificate #6");
					return null;
				}
			}
		};

	    try {
	    	SSLContext sc = SSLContext.getInstance("SSL");
	    	sc.init(null, trustAllCerts, new java.security.SecureRandom());
	    	HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	    	
			final WhitelistVerifier verifier = new WhitelistVerifier();
			verifier.setValues(common.whitelisting);

			HttpsURLConnection.setDefaultHostnameVerifier(verifier);

			LOG.debug("verifier: {}", verifier.getValues());

	    } catch(Exception e) {
	    	e.printStackTrace();
	    }
				
	}


	public static void loadAPI(Common args) {
		List<String> dirs = getDirectories(args.workingDirectory);
		
		if(args.openAPIFile==null) {
			Out.println("... missing input file argument - try --help for usage information");
			System.exit(0);
		}
		
		try {
			APIModel.loadAPI(args.openAPIFile, Utils.getSource(args.openAPIFile, dirs));
		
			Timestamp.timeStamp("api specification loaded");
			
		} catch(Exception ex) {
			Out.println("... unable to read API specification from " + args.openAPIFile);
			Out.println("... app error: " + ex.getLocalizedMessage());	
			// ex.printStackTrace();
			System.exit(0);
		}
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getDirectories(String baseDir) {
		List<String> res = new LinkedList<>();
		if(baseDir!=null) {
			res.add(baseDir);
			if(!baseDir.isEmpty()) {
				res.add(baseDir + "/swaggers" );
			}
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	protected void setLogLevel(org.apache.logging.log4j.Level level) {		
		
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration config = context.getConfiguration();
		LoggerConfig rootConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
		rootConfig.setLevel(level);

		AspectLogger.setGlobalDebugLevel(level);
			
	}

	@LogMethod(level=LogLevel.DEBUG)
	protected void execute() {
							
		Timestamp.timeStamp("common execute finished");


	}
	
}
