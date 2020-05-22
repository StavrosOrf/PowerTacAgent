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

import java.awt.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.powertac.common.BalancingTransaction;
import org.powertac.common.CapacityTransaction;
import org.powertac.common.ClearedTrade;
import org.powertac.common.Competition;
import org.powertac.common.DistributionTransaction;
import org.powertac.common.MarketPosition;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Order;
import org.powertac.common.Orderbook;
import org.powertac.common.Timeslot;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherReport;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.msg.BalanceReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.samplebroker.interfaces.MarketManager;
import org.powertac.samplebroker.interfaces.PortfolioManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles market interactions on behalf of the broker.
 * @author John Collins
 */
@Service
public class MarketManagerService 
implements MarketManager, Initializable, Activatable
{
  static private Logger log = LogManager.getLogger(MarketManagerService.class);
  
  private BrokerContext broker; // broker

  // Spring fills in Autowired dependencies through a naming convention
  @Autowired
  private BrokerPropertiesService propertiesService;

  @Autowired
  private TimeslotRepo timeslotRepo;
  
  @Autowired
  private PortfolioManager portfolioManager;

  // ------------ Configurable parameters --------------
  // max and min offer prices. Max means "sure to trade"
  @ConfigurableValue(valueType = "Double",
          description = "Upper end (least negative) of bid price range")
  private double buyLimitPriceMax = -1.0;  // broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Lower end (most negative) of bid price range")
  private double buyLimitPriceMin = -70.0;  // broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Upper end (most positive) of ask price range")
  private double sellLimitPriceMax = 70.0;    // other broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Lower end (least positive) of ask price range")
  private double sellLimitPriceMin = 0.5;    // other broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Minimum bid/ask quantity in MWh")
  private double minMWh = 0.001; // don't worry about 1 KWh or less

  @ConfigurableValue(valueType = "Integer",
          description = "If set, seed the random generator")
  private Integer seedNumber = null;

  // ---------------- local state ------------------
  private Random randomGen; // to randomize bid/ask prices

  // Bid recording
  private HashMap<Integer, Order> lastOrder;
  private double[] marketMWh;
  private double[] marketPrice;
  private double meanMarketPrice = 0.0;

  public MarketManagerService ()
  {
    super();
  }

  /* (non-Javadoc)
   * @see org.powertac.samplebroker.MarketManager#init(org.powertac.samplebroker.SampleBroker)
   */
  @Override
  public void initialize (BrokerContext broker)
  {
    this.broker = broker;
    lastOrder = new HashMap<>();
    propertiesService.configureMe(this);
    System.out.println("  name=" + broker.getBrokerUsername());
    if (seedNumber != null) {
      System.out.println("  seeding=" + seedNumber);
      log.info("Seeding with : " + seedNumber);
      randomGen = new Random(seedNumber);
    }
    else {
      randomGen = new Random();
    }
  }

  // ----------------- data access -------------------
  /**
   * Returns the mean price observed in the market
   */
  @Override
  public double getMeanMarketPrice ()
  {
    return meanMarketPrice;
  }
  
  // --------------- message handling -----------------
  /**
   * Handles the Competition instance that arrives at beginning of game.
   * Here we capture minimum order size to avoid running into the limit
   * and generating unhelpful error messages.
   */
  public synchronized void handleMessage (Competition comp)
  {
    minMWh = Math.max(minMWh, comp.getMinimumOrderQuantity());
  }

  /**
   * Handles a BalancingTransaction message.
   */
  public synchronized void handleMessage (BalancingTransaction tx)
  {
    log.info("Balancing tx: " + tx.getCharge());
  }

  /**
   * Handles a ClearedTrade message - this is where you would want to keep
   * track of market prices.
   */
  public synchronized void handleMessage (ClearedTrade ct)
  {
  }

  /**
   * Handles a DistributionTransaction - charges for transporting power
   */
  public synchronized void handleMessage (DistributionTransaction dt)
  {
    log.info("Distribution tx: " + dt.getCharge());
  }

  /**
   * Handles a CapacityTransaction - a charge for contribution to overall
   * peak demand over the recent past.
   */
  public synchronized void handleMessage (CapacityTransaction dt)
  {
    log.info("Capacity tx: " + dt.getCharge());
  }

  /**
   * Receives a MarketBootstrapData message, reporting usage and prices
   * for the bootstrap period. We record the overall weighted mean price,
   * as well as the mean price and usage for a week.
   */
  public synchronized void handleMessage (MarketBootstrapData data)
  {
    marketMWh = new double[broker.getUsageRecordLength()];
    marketPrice = new double[broker.getUsageRecordLength()];
    double totalUsage = 0.0;
    double totalValue = 0.0;
    for (int i = 0; i < data.getMwh().length; i++) {
      totalUsage += data.getMwh()[i];
      totalValue += data.getMarketPrice()[i] * data.getMwh()[i];
      if (i < broker.getUsageRecordLength()) {
        // first pass, just copy the data
        marketMWh[i] = data.getMwh()[i];
        marketPrice[i] = data.getMarketPrice()[i];
      }
      else {
        // subsequent passes, accumulate mean values
        int pass = i / broker.getUsageRecordLength();
        int index = i % broker.getUsageRecordLength();
        marketMWh[index] =
            (marketMWh[index] * pass + data.getMwh()[i]) / (pass + 1);
        marketPrice[index] =
            (marketPrice[index] * pass + data.getMarketPrice()[i]) / (pass + 1);
      }
    }
    meanMarketPrice = totalValue / totalUsage;
    System.out.println("Calculated bootstrap data");
  }

  /**
   * Receives a MarketPosition message, representing our commitments on 
   * the wholesale market
   */
  public synchronized void handleMessage (MarketPosition posn)
  {
    broker.getBroker().addMarketPosition(posn, posn.getTimeslotIndex());
  }
  
  /**
   * Receives a new MarketTransaction. We look to see whether an order we
   * have placed has cleared.
   */
  public synchronized void handleMessage (MarketTransaction tx)
  {
    // reset price escalation when a trade fully clears.
    Order lastTry = lastOrder.get(tx.getTimeslotIndex());
    if (lastTry == null) // should not happen
      log.error("order corresponding to market tx " + tx + " is null");
    else if (tx.getMWh() == lastTry.getMWh()) // fully cleared
      lastOrder.put(tx.getTimeslotIndex(), null);
  }
  
  /**
   * Receives market orderbooks. These list un-cleared bids and asks,
   * from which a broker can construct approximate supply and demand curves
   * for the following timeslot.
   */
  public synchronized void handleMessage (Orderbook orderbook)
  {
  }
  
  /**
   * Receives a new WeatherForecast.
   */
  public synchronized void handleMessage (WeatherForecast forecast)
  {
  }

  /**
   * Receives a new WeatherReport.
   */
  public synchronized void handleMessage (WeatherReport report)
  {
  }

  /**
   * Receives a BalanceReport containing information about imbalance in the
   * current timeslot.
   */
  public synchronized void handleMessage (BalanceReport report)
  {
  }

  // ----------- per-timeslot activation ---------------

  /**
   * -----------Compute needed quantities for each open timeslot, then submit orders
   * ---------------for those quantities.
   *
   * @see org.powertac.samplebroker.interfaces.Activatable#activate(int)
   */
  @Override
  public synchronized void activate (int timeslotIndex)
  {
    double neededKWh = 0.0;
    log.debug("Current timeslot is " + timeslotRepo.currentTimeslot().getSerialNumber());
    System.out.println("=========== \n Current timeslot is " + timeslotRepo.currentTimeslot().getSerialNumber());
    for (Timeslot timeslot : timeslotRepo.enabledTimeslots()) {
      printAboutTimeslot(timeslot);
//      System.out.println("usage record lentgh: " + broker.getUsageRecordLength());
      int index = (timeslot.getSerialNumber()) % broker.getUsageRecordLength();
//      System.out.print("  Index: "+ index);
      neededKWh = portfolioManager.collectUsage(index);
//      System.out.print(" needed KWH: "+ neededKWh);
      submitBidMCTS(neededKWh,timeslotRepo.currentTimeslot().getSerialNumber(), timeslot.getSerialNumber());
 
    }
    
  }
  void printAboutTimeslot(Timeslot t) {
	  System.out.print("Timeslot serial: "+ t.getSerialNumber());
//	  System.out.println("time of day"+ t.slotInDay());
//	  System.out.println("day of week"+ t.dayOfWeek());
//	  System.out.println("stat time"+ t.getStartTime());
//	  System.out.println("stat instant"+ t.getStartInstant());
//	  System.out.println("stat instant"+ t.getEndInstant());
//	  System.out.println(" ");
  }
  
  
  /**MCTS VARIABLES BELOW
   * 
   */
  public static int MAX_ITERATIONS = 100;
  public static int NUM_OF_ACTIONS = 3; // must be > 1
  public static int NO_BID = -9999;
  
  //Price predictor generated variables TODO
  public static double OBSERVED_DEVIATION = 10; //σ, will be changed in future when we implement pPredictor
  public static double D_MIN = -1; // Δ min minimum price multiplier , TBC
  public static double D_MAX = 1; // Δ max maximum price multiplier , TBC
  
  // A Node 
  public class Node{
	  public double actionID;
	  public int visitCount;
	  public int hoursAhead;
	  public double avgUnitCost;
	  public Node parent;
	  public ArrayList<Node> children;
	  
	  public Node(double id, Node parent,int visitcount,int hoursAhead) {
		  this.actionID = id;
		  this.parent = parent;
		  this.visitCount = visitcount;
		  this.hoursAhead = hoursAhead; 
		  avgUnitCost = 0;
		  children = new ArrayList<MarketManagerService.Node>();
		  
	  }
	  
	  public void generateRootsKids(double limitPrice) {
		  double minPrice = limitPrice + D_MIN*OBSERVED_DEVIATION;
		  double maxPrice = limitPrice + D_MAX*OBSERVED_DEVIATION;
		  
//		  //temporary fix, may need to  change
//		  if(minPrice >= 0 && maxPrice >= 0) {
//			  minPrice -= maxPrice;
//			  maxPrice = -1;
//		  }
		  
		  double step =  (maxPrice-minPrice)/(NUM_OF_ACTIONS-1);
		  
		  for (int i = 0; i < NUM_OF_ACTIONS; i++) {
			  double bid = minPrice + step*i;
			  
			  Node n = new Node(bid, this, 0, hoursAhead);
			  children.add(n);
		  }
		  // add a NO_BID action
		  Node n = new Node(NO_BID, this, 0, hoursAhead);
		  children.add(n);
	  }
	  public void generateNodeKids(double limitPrice) {
		  double minPrice = limitPrice + D_MIN*OBSERVED_DEVIATION;
		  double maxPrice = limitPrice + D_MAX*OBSERVED_DEVIATION;
		  
		  double step =  (maxPrice-minPrice)/(NUM_OF_ACTIONS-1);
		  
		  for (int i = 0; i < NUM_OF_ACTIONS; i++) {
			  double bid = minPrice + step*i;
			  if(bid > 0 ) {
				  continue;
			  }

			  Node n = new Node(bid, this, 0, hoursAhead-1);
			  children.add(n);
		  }
		  
		  // add a NO_BID action
		  Node n = new Node(NO_BID, this, 0, hoursAhead);
		  children.add(n);
	  }
	  
	  public boolean hasUnvisitedKidNodes() {
		  for (Node c : this.children) {
			  if(c.visitCount == 0) {
				 return true;
			  }
		  }
		  
		  return false;
	  }
	  
	  //Return a random unvisited child node
	  public Node getRandomUnvisitedChild() {
		  if( children.isEmpty()) {
			  //this.generateNodeKids(limitPrice);
		  }
		  
		  ArrayList<Node> tempList = new ArrayList<MarketManagerService.Node>();
		  
		  for (Node c : this.children) {
			  if(c.visitCount == 0) {
				 tempList.add(c);
			  }
		  }
		  
		  if(tempList.size() > 1 ) {
			  Random rand = new Random(); 
			  return tempList.get(rand.nextInt(tempList.size()));
		  }
		  
		  return tempList.get(0);
		  
	  }
	  //return the Child Node with the Highest UCT value
	  public Node getBestUCTChild(double CbalUnitPrice) {
		  Node bestNode = null;
		  double uctMax = -100;
		  
		  double t,uct;
		  int sm;
		  
		  for(Node n : children) {
			  t = 1 - (n.avgUnitCost/CbalUnitPrice);
			  
			  if(n.visitCount == 0) {
				  sm = 1;
			  }else {
				  sm = 0;
			  }
			// may need to add a small + e for ties
			  uct = t + Math.sqrt((2*Math.log10(n.parent.visitCount + sm))/(n.visitCount + sm)); 
			  if(uct > uctMax) {
				  uctMax = uct;
				  bestNode = n;
			  }
		  }
		  return bestNode;
	  }
	  
	  public String toString() {
		  String tmp = String.valueOf(this.hashCode()).substring(0,4);
		  
		  
		  if(parent == null) {
			  return "Node: " + tmp + " level: "+ hoursAhead + " parent: No parent"   + " actionId: "+ actionID 
					  + " visitCount: "+ visitCount + " avgUnitCost: " + avgUnitCost;
		  }
		  String tmpParent = String.valueOf(this.parent.hashCode()).substring(0,4);
		  
		  return "Node: " + tmp + " level: "+ hoursAhead + " parent: " + tmpParent  + " actionId: "+ actionID 
				  + " visitCount: "+ visitCount + " avgUnitCost: " + avgUnitCost;
	  }
  }
  
  
  /**
   * Composes and submits the appropriate order for the given timeslot.
   */
  private void submitBidMCTS (double neededKWh,int currentTimeslot, int timeslotBidding)
  {
    double neededMWh = neededKWh / 1000.0;
    //find how many MWH are already available in given timeslot
    MarketPosition posn = broker.getBroker().findMarketPositionByTimeslot(timeslotBidding);
    
    
    if (posn != null)
      neededMWh -= posn.getOverallBalance();
    if (Math.abs(neededMWh) <= minMWh) {
      log.info("no power required in timeslot " + timeslotBidding);
      System.out.println(" ");
      return;
    }
    System.out.print("  neededMWH: " + neededMWh);
    //System.out.print("  "+ posn.toString());
    
    Double limitPrice = computeLimitPrice(timeslotBidding, neededMWh);
    // ==========================================================================================	
    
    //TODO check if needed is negative or positive
    double neededMWHTemp ;
    int timeslotBiddingTemp;
    Node curNode;
    ArrayList<Node> visitedNodes;
    double Csim; // Total simulated cost of auctions done
    double Cbal = 0; // Estimated Balancing cost
    double CbalUnitPrice = 2* Math.abs(buyLimitPriceMin); // May need to reevaluate this!!!!!!!!!!!!!!!!TODO  
    double CavgUnit = 0; // Average Unit Cost 
    
    //Initialize 
	Node root = new Node(0, null, 0, timeslotBidding-currentTimeslot);
	//computeLimitPrice function will be replaced by pPredictor
	root.generateRootsKids(computeLimitPrice(timeslotBidding, neededMWh));
    
    for (int i = 0; i < MAX_ITERATIONS; i++) {
    	
    	
    	neededMWHTemp = neededMWh; // Get Demand(t,n)
    	timeslotBiddingTemp = timeslotBidding;
    	curNode = root;
    	visitedNodes = new ArrayList<MarketManagerService.Node>();
    	visitedNodes.add(root); 
    	
    	Csim = 0;
    	
    	while(neededMWHTemp > minMWh && curNode.hoursAhead > 0 ) {
    		
    		if(curNode.children.isEmpty()) {
    			curNode.generateNodeKids(computeLimitPrice(timeslotBiddingTemp, neededMWHTemp));
    		}
    		
    		if(curNode.hasUnvisitedKidNodes()) {
    			//select one random unvisited kid and expand
    				curNode = curNode.getRandomUnvisitedChild();
    			//rollout 
    			//play randmomly (without adding nodes) till the game ends and simulate Cbal
    			int tempHoursAhead = curNode.hoursAhead;
    			
    			while(neededMWHTemp > minMWh && tempHoursAhead > 0 ) {
    				
    				//choose a random action( Bid or NO_Bid)
    				int p = randomGen.nextInt(NUM_OF_ACTIONS + 1);
    				if( p == NUM_OF_ACTIONS) { //NoBid action was chosen
    					tempHoursAhead --;
    				}else {
            			double limitPriceMCTS = computeLimitPrice(timeslotBidding + tempHoursAhead,neededMWHTemp);
            			double clearingPrice = randomGen.nextGaussian()* OBSERVED_DEVIATION + limitPriceMCTS; 
            			// TODO swap compute limit price with price predictor value
            			if(limitPriceMCTS > clearingPrice) {
            				Csim += neededMWHTemp * clearingPrice;
            				neededMWHTemp = 0;
            				// At this point i may want to consider different bid sizes(like 50% of total need)
            			}else {
            				tempHoursAhead --;
            			}
    				}

    			}
    				
    			//may needed
    			visitedNodes.add(curNode);
    			break;
    			
    		}else {
    			curNode = curNode.getBestUCTChild(CbalUnitPrice);
    			
				if(curNode.actionID != NO_BID) {					
	    			//simulate  
	    			//get a simulated clearing Price
	    			double limitPriceMCTS = computeLimitPrice(timeslotBidding + curNode.hoursAhead,neededMWHTemp);
	    			double clearingPrice = randomGen.nextGaussian()* OBSERVED_DEVIATION + limitPriceMCTS; 
	    			// TODO swap compute limit price with price predictor value
	    			if(limitPriceMCTS > clearingPrice) {
	    				Csim += neededMWHTemp * clearingPrice;
	    				neededMWHTemp = 0;
	    				// At this point i may want to consider different bid sizes(like 50% of total need)
	    			}
				}

    		}
    		
    		visitedNodes.add(curNode);
    	}
    	Csim += CbalUnitPrice * neededMWHTemp;
    	CavgUnit = Csim / neededMWh;
    	//backpropagate and update variable counters 
    	for(Node n : visitedNodes) {
    		n.visitCount ++;
    		if(n.avgUnitCost != 0 ) {
    			n.avgUnitCost = (n.avgUnitCost + CavgUnit)/2 ;// get the mean ,may need to change TBI TODO
    		}else {
    			n.avgUnitCost = CavgUnit;
    		}		
    	}
		
	}
    
    //get best action from MCTS using τ metric
    
    double bestActionT = 0.0;
    Node bestActionNode = null;
    
    for(Node n : root.children) {
    	if(1-(n.avgUnitCost/CbalUnitPrice) > bestActionT) {
    		bestActionNode = n;
    		bestActionT = 1 -(n.avgUnitCost/CbalUnitPrice);
    	}
    }
    
    System.out.println("  ------ mcts bid: " + bestActionNode.actionID + "  w/out: " + limitPrice);
    
//    if(timeslotBidding-currentTimeslot == 4) {
//    	
//    	printTree(root);
//    }
    
    //if NO_BID was chosen return
    if(bestActionNode.actionID == NO_BID)
    	return;
    
    
//    if(timeslotBidding - currentTimeslot > 18) {
//    	bestActionNode.actionID += -20;
//    }
    // ==========================================================================================	
    log.info("new order for " + neededMWh + " at " + limitPrice + " in timeslot " + timeslotBidding);
//    Order order = new Order(broker.getBroker(), timeslotBidding, neededMWh, limitPrice);
    Order order = new Order(broker.getBroker(), timeslotBidding, neededMWh, bestActionNode.actionID);
    lastOrder.put(timeslotBidding, order);
    broker.sendMessage(order);
  }
  
  private void printTree(Node n) {
	  if(n == null || n.children.isEmpty())
		  return;
	  if(n.parent == null) {
		  System.out.println(n.toString());
	  }
	  System.out.println("---");
	  for(Node child : n.children) {
		  System.out.println(child.toString());
	  }
	  
	  for(Node child : n.children) {
		  printTree(child);
	  }
	  
	  
	  
  }
  /**
   * Computes a limit price with a random element. 
   */
  private Double computeLimitPrice (int timeslot,
                                    double amountNeeded)
  {
    log.debug("Compute limit for " + amountNeeded + 
              ", timeslot " + timeslot);
    // start with default limits
    Double oldLimitPrice;
    double minPrice;
    if (amountNeeded > 0.0) {
      // buying
      oldLimitPrice = buyLimitPriceMax;
      minPrice = buyLimitPriceMin;
    }
    else {
      // selling
      oldLimitPrice = sellLimitPriceMax;
      minPrice = sellLimitPriceMin;
    }
    // check for escalation
    Order lastTry = lastOrder.get(timeslot);
    if (lastTry != null)
      log.debug("lastTry: " + lastTry.getMWh() +
                " at " + lastTry.getLimitPrice());
    if (lastTry != null
        && Math.signum(amountNeeded) == Math.signum(lastTry.getMWh())) {
      oldLimitPrice = lastTry.getLimitPrice();
      log.debug("old limit price: " + oldLimitPrice);
    }

    // set price between oldLimitPrice and maxPrice, according to number of
    // remaining chances we have to get what we need.
    double newLimitPrice = minPrice; // default value
    int current = timeslotRepo.currentSerialNumber();
    int remainingTries = (timeslot - current
                          - Competition.currentCompetition().getDeactivateTimeslotsAhead());
    log.debug("remainingTries: " + remainingTries);
    if (remainingTries > 0) {
      double range = (minPrice - oldLimitPrice) * 2.0 / (double)remainingTries;
      log.debug("oldLimitPrice=" + oldLimitPrice + ", range=" + range);
      double computedPrice = oldLimitPrice + randomGen.nextDouble() * range; 
      return Math.max(newLimitPrice, computedPrice);
    }
    else
    	//System.out.print("*#*");
    	return 0.0;
      //return null; // market order
  }
}
