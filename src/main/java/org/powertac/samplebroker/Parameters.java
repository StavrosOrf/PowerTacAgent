/*
 * Copyright (c) 2012-2014 by the original author
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
package org.powertac.samplebroker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * This class holds all essentials parameters of the broker
 * 
 * @author Stavros Orfanoudakis
 */

@Configuration
@PropertySource("classpath:static.properties")
public class Parameters {
		  
	  public static int Predictor_Port = 8098;
	  public static int timeslotMS = 5000;
	  
	  @Value("${printRatesEnabled}")
	  public int printRatesEnabled = 0;
	  
	  public static int batchSize = 24;
	  
	  //retail module Parameters	
	  public static int reevaluationCons = 6;
	  public static int reevaluationStorage = 250*reevaluationCons;
	  public static int reevaluationProduction = 4*reevaluationCons;
	  public static int reevaluationInterruptible = 200 *reevaluationCons;
	  	    
	  //Mutation Constants
	  public static double Ebp = 4;
	  public static double Ep = 0.15;
	  public static double LowerEp = 0.003;
	  public static double LowerEpOffset = 0.002;
//	  public static double LowerEpOffset = 0.014;
	  public static double LowerBoundChangerFees = 50000;
	  public static double LowerBoundStatic = - 0.15;
	  @Value("${LowerBoundStaticAbsolute}")
	  public double LowerBoundStaticAbsolute = - 0.115;
	  @Value("${newRateCalculatorEnabled}")
	  public int newRateCalculatorEnabled = 0;
	  @Value("${productionTariffsEnabled}")
	  public int productionTariffsEnabled = 1;
	  public static double EwpBound = - 12;
	  public static double UpperBoundStatic = - 0.4;
	  public static double LowerBoundRollChance = 0.85;
	  public static double InterRateSpread = 0.015;	  	
	  public static double UpperBoundProduction =  0.015;
	  
	  //Percentages % (0-100)
	  @Value("${CONS_COUNT_UPPER_BOUND}")
	  public double CONS_COUNT_UPPER_BOUND = 92.5;
	  @Value("${CONS_COUNT_MIDDLE_BOUND}")
	  public double CONS_COUNT_MIDDLE_BOUND = 65;
	  @Value("${CONS_COUNT_EQUAL_BOUND}")
	  public double CONS_COUNT_EQUAL_BOUND = 55;
	  @Value("${CONS_COUNT_LOWER_BOUND}")
	  public double CONS_COUNT_LOWER_BOUND = 45;
	  @Value("${CONS_COUNT_LOWEST_BOUND}")
	  public double CONS_COUNT_LOWEST_BOUND = 35;
	  @Value("${STATE_CHANGE_INTERVAL}")
	  public double STATE_CHANGE_INTERVAL = 35;
	 
	  public static int Ecl = timeslotMS* (4* reevaluationCons/5)*10000;
	  public static double Ereg = 0.05;
	  	
      //wholesale module Parameters
	  public static int MAX_ITERATIONS = 1000;
	  public static int NUM_OF_ACTIONS = 3; // must be > 1
	  public static int NO_BID = -9999;
	  public static boolean WH_PRINT_ON = false;
	  
	  //Strategies enabled
//	  public static boolean C2_ENABLED = true ; 
	  
	  public static boolean EN_DYNAMIC = true; // whether or not dynamic mcts is enabled
	  public static int[] DYNAMIC_THRESHOLD = {5,10,20,50}; // Threshold in which a new dynamic action 
	  														// will be added to the search space
	   							    
	  //Price predictor generated variables TODO
	  public static double OBSERVED_DEVIATION = 10; //σ, will be changed in future when we implement pPredictor
	  public static double D_MIN = -1; // Δ min minimum price multiplier , TBChanged
	  public static double D_MAX = 1; // Δ max maximum price multiplier , TBChanged
	  
	  @Value("${MarketManagerOffset}")
	  public double MarketManagerOffset = 30;
	// max and min offer prices
	  public static double buyLimitPriceMin = - 70;
	  public static double buyLimitPriceMax = - 1;
	  
	  public static double sellLimitPriceMax = 70.0;    // other broker pays
	  public static double sellLimitPriceMin = 0.5;    // other broker pays
	  
	  //Database related parameters
	  public static int NUM_OF_POPULATION = 50;
	  public static double GroundLevelDecayFactor = 0.02;
	  public static double TourLevelDecayFactor = 0.04;

}

