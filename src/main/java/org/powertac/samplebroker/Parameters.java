package org.powertac.samplebroker;

import org.powertac.common.config.ConfigurableValue;

//mvn -Pcli -Dexec.args="--sim --boot-data boot1.xml --brokers st1,mc0"

public class Parameters {
	  //retail module Parameters	
	  public static int reevaluation = 20;
	  public static int NUM_OF_POPULATION = 50;
	
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
