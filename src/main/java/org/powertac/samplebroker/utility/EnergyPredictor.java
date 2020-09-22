/*
 * Copyright (c) 2012 by the original author
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.powertac.samplebroker.utility;

import java.net.*;
import java.io.*;

import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherForecastPrediction;
import org.powertac.common.WeatherReport;
import org.powertac.samplebroker.Parameters;

public class EnergyPredictor {
	
	MultiLayerNetwork model = null ;

    public EnergyPredictor() {
    	String simpleMlp;
		try {
//			simpleMlp = new ClassPathResource("LSTM_EP500_BS16.h5").getFile().getPath();
//	    	model = KerasModelImport.importKerasSequentialModelAndWeights(simpleMlp);	
//	    	
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String args[]) {
//		double d = 0;
		double result[] = new double[24];
	    Socket clientSocket;
	    PrintWriter out;
	    BufferedReader in;
	    int i= 0,counter = 0;
	    while(i < 1) {
	    counter = 0;
		try {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("count: " + i);
			clientSocket = new Socket("localhost", Parameters.Predictor_Port);
			clientSocket.setSoTimeout(1500);
		    out = new PrintWriter(clientSocket.getOutputStream(), true);
		    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		    System.out.println("sending");
	        out.println("7 15 " + i);
	        System.out.println("waiting..");
	        String resp;
	        while((resp = in.readLine()) != null) {
//		        System.out.println("====" + resp);
		        if(resp.equals("-")) {
		        	System.out.println("Exiting..");
		        	clientSocket.close();
		        	if(out != null) {
		        		out.close();
		        		System.out.print("c1");
		        	}
		        	if(in != null) {
		        		in.close();
		        		System.out.print("c2");
		        	}
		        	break;
//		        	return;
		        }else {
		        	resp = resp.replaceAll("\\[", "");
		        	resp = resp.replaceAll("\\]", "");
		        	resp = resp.trim();		        	
		        	
		        	String arr[] = resp.split("\\s+");
		        	for(String s : arr) {
		        		result[counter] = Double.parseDouble(s);
		        	    counter ++;
		        	}
		        	
//		        	if(counter == 0) {
//		        		resp = resp.split("\\[",2)[1].split("\\[",2)[1];
//		        		
//		        		for(int k = 0 ; k < 7 ; k++) {
//		        			result[k + 0] = Double.parseDouble(resp.split("\\s+")[k]);
////		        			System.out.println(result[k]);
//		        		}
//		        	}else if(counter == 1) {		        		
//		        		resp = resp.trim();
//		        		for(int k = 0 ; k < 7 ; k++) {
//		        			result[k + 7] = Double.parseDouble(resp.split("\\s+")[k]);
////		        			System.out.println(result[k+7]);
//		        		}		        		
//		        	}else if(counter == 2) {
//		        		resp = resp.trim();
//		        		for(int k = 0 ; k < 7 ; k++) {
//		        			result[k + 14] = Double.parseDouble(resp.split("\\s+")[k]);
////		        			System.out.println(result[k+14]);
//		        		}
//		        	}else if(counter == 3) {
//		        		resp = resp.trim();
//		        		resp = resp.split("\\]",2)[0].split("\\]",2)[0];
//		        		for(int k = 0 ; k < 3 ; k++) {
//		        			result[k + 21] = Double.parseDouble(resp.split("\\s+")[k]);
////		        			System.out.println(result[k+21]);
//		        		}
//
//		        	}   
	                
	                
//	                d =  Double.parseDouble(s);
		        }

	        }


		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		i++;
	    }
	    
		  for(double dd : result) {
			  System.out.print(dd + "| ");
		  }
	    System.out.println("It seems like read timed out");

		/*
    	String simpleMlp;
		try {
			simpleMlp = new ClassPathResource("LSTM_EP500_BS16.h5").getFile().getPath();
	    	MultiLayerNetwork model = KerasModelImport.importKerasSequentialModelAndWeights(simpleMlp);	
//			MultiLayerNetwork model = KerasModelImport.importKerasModelAndWeights(simpleMlp);
	    	
	    	int inputs = 6;
//	    	INDArray features = Nd4j.zeros(1,6);
	    	INDArray features  = Nd4j.zeros(24,6,1);
	    	Random random = new Random();
	    	    	
//	    	INDArray features = Nd4j.zeros(inputs);
	    	for(int j = 0; j < 24 ; j++) {
		    	for (int i=0; i<inputs; i++) {
		    		

		    		//features.putScalar(new int[] {i}, Math.random() < 0.5 ? 10 : 15);
//		    		features.putScalar(j,i,10.5);
		    		
		    		if(i == 0 ) {
//		    			d = random.nextInt(7);
		    			d = 1;
		    		}else if(i == 1 ) {
//		    			d = random.nextInt(23);
		    			d = j;
		    		}else if(i == 2 ) {
		    			d = random.nextInt(30);
//		    			d = 15;
		    		}else if(i == 3 ) {
		    			d = random.nextInt(8);
//		    			d = 7;
		    		}else if(i == 4 ) {
		    			d = random.nextInt(360);
//		    			d = 0;
		    		}else if(i == 5 ) {
		    			d = random.nextDouble();
//		    			d = 1;
		    		}
		    		
		    		features.putScalar(j,i ,0, d);
		    		System.out.print("\t " +features.getDouble(j,i));
		    	}
		    	System.out.println(" ");
	    	}
	    	
//	    	System.out.println("");
//	    	System.out.println(model.output(features).get);
	    	
	    	INDArray f  ;//= Nd4j.zeros(24,1);
	    	f = model.output(features);
	    	System.out.println(f.toString());

	    	// get the prediction
//	    	for(int j = 0; j < 24 ; j++) {
//	    		System.out.println(model.output(features).getDouble(23,1)*100*1000);
	    		
//	    		System.out.println(model.output(features));
//	    		model.output(features).getDo
//	    	}
//	    	double prediction = model.output(features).getDouble(1);
//	    	System.out.println(prediction);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
*/
    }
	
	public double[] getKWhPredictionLSTMClient(int hour, int day) {
		double result[] = new double[24];
		
	    Socket clientSocket;
	    PrintWriter out;
	    BufferedReader in;
	    int counter = 0;

		try {
			clientSocket = new Socket("localhost", Parameters.Predictor_Port);
			clientSocket.setSoTimeout(1500);
		    out = new PrintWriter(clientSocket.getOutputStream(), true);
		    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//		    System.out.println("sending");
	        out.println(day + " "+ hour);
//	        System.out.println("waiting..");
	        String resp;
	        while((resp = in.readLine()) != null) {
//		        System.out.println(resp);
		        if(resp.equals("-")) {
		        	clientSocket.close();
		        	if(out != null) {
		        		out.close();
//		        		System.out.print("c1");
		        	}
		        	if(in != null) {
		        		in.close();
//		        		System.out.print("c2");
		        	}
		        	return result;
		        }else {
		        	resp = resp.replaceAll("\\[", "");
		        	resp = resp.replaceAll("\\]", "");
		        	resp = resp.trim();		        
		        	
		        	String arr[] = resp.split("\\s+");
		        	for(String s : arr) {
		        		result[counter] = Double.parseDouble(s);
		        	    counter ++;
		        	}		    
		        }
	        }
		    

		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
		
		return result;
	}
	
	public double[] getKWhPredictorLSTM(int h , int d, WeatherForecast forecast) {
		double result[] = new double[24];
    	INDArray features  = Nd4j.zeros(24,6,1);
    	
		int counter = 0,hour = h , day = d;
		
		for(WeatherForecastPrediction w : forecast.getPredictions()) {
			hour ++;
			if(hour >= 24) {
				hour = 0;
				day++;
				if(day >7) {
					day = 1;
				}
			}
//			System.out.print(" "+w.getForecastTime());
		   	features.putScalar(counter,0,0,day);
		   	features.putScalar(counter,1,0,hour);
		   	features.putScalar(counter,2,0,w.getTemperature());
		   	features.putScalar(counter,3,0,w.getWindSpeed());
		   	features.putScalar(counter,4,0,w.getWindDirection());
		   	features.putScalar(counter,5,0,w.getCloudCover());
		   	
		   	counter ++;
		 }
		
    	for(int j = 0; j < 24 ; j++) {
    		result[j] = model.output(features).getDouble(0,j)*100*1000;
    	}
		return result;
	}
    
    public double getKWhPredictor(int day,int hour,WeatherReport w) {
    	
    	
//    	String s = null;

    	INDArray features = Nd4j.zeros(1,6);
//    	INDArray features = Nd4j.zeros(inputs);
    	features.putScalar(0,day);
    	features.putScalar(1,hour);
    	features.putScalar(2,w.getTemperature());
    	features.putScalar(3,w.getWindSpeed());
    	features.putScalar(4,w.getWindDirection());
    	features.putScalar(5,w.getCloudCover());
    	
//    	System.out.println(features.toStringFull());
    	
    	// get the prediction
    	double prediction = model.output(features).getDouble(0);
//    	System.out.println(prediction);
    	return prediction*1000;
    	
//        
       

    }
    
    public void getKWhPredictorScript() {
    	Process p;
		try {
//			p = Runtime.getRuntime().exec("python3 dataMWH.py 1 20 11.7 5 330 0.5");
			p = Runtime.getRuntime().exec("python prediction.py ");
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String s ;
//			double d;
//			System.out.println(stdInput.readLine());
//			stdInput.readLine();
			while ((s = stdInput.readLine()) != null) {							
                System.out.print(" " + s);

//                s = s.split("\\[",2)[1].split("\\[",2)[1].split("\\]",2)[0].split("\\]",2)[0];
//                System.out.println(s);
//                d =  Double.parseDouble(s);
			}
			
			stdInput.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println();
    }
}