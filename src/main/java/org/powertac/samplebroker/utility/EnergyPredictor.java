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

import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;


import org.powertac.common.WeatherReport;

public class EnergyPredictor {
	
	MultiLayerNetwork model = null ;

    public EnergyPredictor() {
    	String simpleMlp;
		try {
			simpleMlp = new ClassPathResource("FFN_model.h5").getFile().getPath();
	    	model = KerasModelImport.importKerasSequentialModelAndWeights(simpleMlp);	
	    	
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String args[]) {

    	String simpleMlp;
		try {
			simpleMlp = new ClassPathResource("FFN_model.h5").getFile().getPath();
	    	MultiLayerNetwork model = KerasModelImport.importKerasSequentialModelAndWeights(simpleMlp);	
	    	
	    	int inputs = 6;
	    	INDArray features = Nd4j.zeros(1,6);
//	    	INDArray features = Nd4j.zeros(inputs);
	    	for (int i=0; i<inputs; i++) {
	    		

	    		//features.putScalar(new int[] {i}, Math.random() < 0.5 ? 10 : 15);
	    		features.putScalar(i,10.5);
	    		System.out.println(features.getDouble(i));
	    	}
	    	    
	    	// get the prediction
	    	double prediction = model.output(features).getDouble(0);
	    	System.out.println(prediction);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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
    	
//        Process p;
//		try {
////			p = Runtime.getRuntime().exec("python3 dataMWH.py 1 20 11.7 5 330 0.5");
//			p = Runtime.getRuntime().exec("python3 dataMWH.py "+ day + " " +  hour + " " + w.getTemperature() + " " + 
//											w.getWindSpeed() + " " + w.getWindDirection() + " " + w.getCloudCover());
//			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
//			
//			while ((s = stdInput.readLine()) != null) {
////                System.out.println(s);
//
//                s = s.split("\\[",2)[1].split("\\[",2)[1].split("\\]",2)[0].split("\\]",2)[0];
////                System.out.println(s);
//                d =  Double.parseDouble(s);
//			}
//			
//
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
       

    }
}