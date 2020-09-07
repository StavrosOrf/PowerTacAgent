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

/**
 * This class holds all essentials parameters of the broker
 * 
 * @author Stavros Orfanoudakis
 */
//mvn -Pcli -Dexec.args="--sim --boot-data boot1.xml --brokers st1,mc0"

public class Parameters {
	
	  public static String MyName = "mc0";
	  
	  //retail module Parameters	
	  public static int reevaluationCons = 24;
	  public static int reevaluationStorage = 2*reevaluationCons;
	  public static int reevaluationProduction = 3*reevaluationCons;
	  public static int reevaluationInterruptible = 2 *reevaluationCons;
	  	  
	  public static int initialTariffBound = 365;
	  public static int LATE_GAME = 1800;
	  public static double LATE_GAME_ADDEED_PRICE = -0.8;
	  
	  //Database related parameters
	  public static int NUM_OF_POPULATION = 50;
	  public static double GroundLevelDecayFactor = 0.02;
	  public static double TourLevelDecayFactor = 0.04;
	  
	  //Mutation Constants
	  public static double Ebp = 8;
	  public static double Ep = 0.15;
	  public static double LowerEp = 0.007;
	  public static double LowerEpOffset = 0.002;
	  public static double LowerBoundChangerFees = 50000;
	  public static double LowerBoundStatic = - 0.015;
	  public static double LowerBoundRollChance = 0.33;
	  public static int timeslotMS = 3000;
	  public static int Ecl = timeslotMS* (4* reevaluationCons/5)*10000;
	  public static double Ereg = 0.05;
	  
	  public static double MIN_RATE_VALUE = -0.08; 
	
      //wholesale module Parameters
	  public static int MAX_ITERATIONS = 1000;
	  public static int NUM_OF_ACTIONS = 3; // must be > 1
	  public static int NO_BID = -9999;
	  public static boolean WH_PRINT_ON = false;
	  
	  //Strategys enabled
//	  public static boolean C2_ENABLED = true ; 
	  
	  public static boolean EN_DYNAMIC = true; // whether or not dynamic mcts is enabled
	  public static int[] DYNAMIC_THRESHOLD = {5,10,20,50}; // Threshold in which a new dynamic action 
	  														// will be added to the search space
	   							    
	  //Price predictor generated variables TODO
	  public static double OBSERVED_DEVIATION = 10; //σ, will be changed in future when we implement pPredictor
	  public static double D_MIN = -1; // Δ min minimum price multiplier , TBChanged
	  public static double D_MAX = 1; // Δ max maximum price multiplier , TBChanged
	  
	// max and min offer prices
	  public static double buyLimitPriceMin = - 50;
	  public static double buyLimitPriceMax = - 1;
	  
	  public static double sellLimitPriceMax = 70.0;    // other broker pays
	  public static double sellLimitPriceMin = 10;    // other broker pays
}
