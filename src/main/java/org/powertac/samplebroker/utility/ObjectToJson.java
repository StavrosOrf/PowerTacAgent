package org.powertac.samplebroker.utility;

import java.awt.List;
import java.io.IOException;

import org.apache.avro.data.Json.ObjectWriter;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherForecastPrediction; 

public class ObjectToJson { 
	public static void main(String[] a) 
	{ 
		WeatherForecastPrediction w = new WeatherForecastPrediction(1, 10, 10, 10, 10);
		java.util.List<WeatherForecastPrediction> predictions = (java.util.List<WeatherForecastPrediction>) new List();
		predictions.add(w);
		// Creating object of Organisation 
		WeatherForecast f = new WeatherForecast(360,predictions); 
		
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(f);
			System.out.println(json);
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

		// Insert the data into the object 
		/*
		WeatherForecast forecast = getObjectData(f); 

		// Creating Object of ObjectMapper define in Jakson Api 
		ObjectMapper Obj = new ObjectMapper(); 

		try { 

			// get Oraganisation object as a json string 
			String jsonStr = Obj.writeValueAsString(org); 

			// Displaying JSON String 
			System.out.println(jsonStr); 
		} 

		catch (IOException e) { 
			e.printStackTrace(); 
		} 
		*/
	} 
	
	public static void tada(WeatherForecast f) {
		org.codehaus.jackson.map.ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json;
		try {
			json = ow.writeValueAsString(f);
			System.out.println(json);
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

	// Get the data to be inserted into the object 
	public static WeatherForecast getObjectData(WeatherForecast org) 
	{ 

		// Insert the data 
//		org.setName("GeeksforGeeks"); 
//		org.setDescription("A computer Science portal for Geeks"); 
//		org.setEmployees(2000); 

		// Return the object 
		return org; 
	} 
	
}
