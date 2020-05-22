package org.powertac.samplebroker;

public class Parameters {
	
	  public static int MAX_ITERATIONS = 1000;
	  public static int NUM_OF_ACTIONS = 3; // must be > 1
	  public static int NO_BID = -9999;
	  							    
	  //Price predictor generated variables TODO
	  public static double OBSERVED_DEVIATION = 10; //σ, will be changed in future when we implement pPredictor
	  public static double D_MIN = -1; // Δ min minimum price multiplier , TBC
	  public static double D_MAX = 1; // Δ max maximum price multiplier , TBC
}
