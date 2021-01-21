package org.powertac.samplebroker.utility;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.powertac.common.WeatherForecast;
import org.powertac.samplebroker.assistingclasses.Customer;
import org.powertac.samplebroker.assistingclasses.CustomerUsage;
import org.powertac.samplebroker.assistingclasses.TimeslotUsage;
import org.powertac.samplebroker.assistingclasses.WeatherData;
import org.powertac.samplebroker.assistingclasses.WeatherDataWithPeaks;
import org.powertac.samplebroker.assistingclasses.WeatherDataWithUsage; 

public class ObjectToJson { 
	
	public static void toJSONForecast(ArrayList<WeatherData> f) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(f);
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\forecast24.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/forecast24.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void toJSON(WeatherForecast f) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(f);
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\forecast.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/forecast.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void toJSONFitUsage(WeatherDataWithUsage f) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(f);
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\fit.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/fit.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void toJSONPeaks(WeatherDataWithPeaks f) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(f);
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\peak.batch.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/peak.batch.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
		
	public static void toJSON(ArrayList<Customer> c) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(c);			
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\customers.boot.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/customers.boot.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}			
		    
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void toJSONPeaks(ArrayList<WeatherDataWithPeaks> f) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(f);
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\peak.batch.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/peak.batch.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void toJSONSubs(ArrayList<CustomerUsage> c) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(c);			
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\subs.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/subs.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}			
		    
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void toJSONWeather(ArrayList<WeatherDataWithUsage> c) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(c);			
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\weather.boot.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/weather.boot.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}			
		    
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void toJSONPeak(ArrayList<WeatherDataWithPeaks> c) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(c);			
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\peaks.boot.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/peaks.boot.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}			
		    
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void toJSONBatch(ArrayList<TimeslotUsage> c) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(c);			
	    	String os = System.getProperty("os.name");
	    	if(os.equals("Windows 10")) {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles\\batch.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}else {
	    		BufferedWriter writer = new BufferedWriter(new FileWriter("tempFiles/batch.online.json"));
	    		writer.write(json);		    
			    writer.close();
	    	}			
		    
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
