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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import org.apache.logging.log4j.Logger;
import org.joda.time.DateTimeFieldType;
import org.joda.time.Instant;
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
import org.powertac.common.WeatherForecastPrediction;
import org.powertac.common.WeatherReport;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.msg.BalanceReport;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.assistingclasses.WeatherData;
import org.powertac.samplebroker.assistingclasses.WeatherDataWithPeaks;
import org.powertac.samplebroker.assistingclasses.WeatherDataWithUsage;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.ContextManager;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.samplebroker.interfaces.MarketManager;
import org.powertac.samplebroker.interfaces.PortfolioManager;
import org.powertac.samplebroker.utility.Node;
import org.powertac.samplebroker.utility.ObjectToJson;
import org.powertac.samplebroker.utility.EnergyPredictor;
import org.powertac.samplebroker.utility.ExcelWriter;
import org.powertac.samplebroker.utility.TeePrintStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles market interactions on behalf of the broker.
 * @author John Collins, Stavros Orfanoudakis
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
  
  @Autowired
  private ContextManager contextManager;
  
  private EnergyPredictor energyPredictor;
  private ExcelWriter excelWriter;

  // ------------ Configurable parameters --------------
  // max and min offer prices. Max means "sure to trade"
  @ConfigurableValue(valueType = "Double",
          description = "Upper end (least negative) of bid price range")
  private double buyLimitPriceMax = Parameters.buyLimitPriceMax;  // broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Lower end (most negative) of bid price range")
  private double buyLimitPriceMin = Parameters.buyLimitPriceMin;  // broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Upper end (most positive) of ask price range")
  private double sellLimitPriceMax = Parameters.sellLimitPriceMax;    // other broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Lower end (least positive) of ask price range")
  private double sellLimitPriceMin = Parameters.sellLimitPriceMin;    // other broker pays

  @ConfigurableValue(valueType = "Double",
          description = "Minimum bid/ask quantity in MWh")
  private double minMWh = 0.001; // don't worry about 1 KWh or less

  @ConfigurableValue(valueType = "Integer",
          description = "If set, seed the random generator")
  private Integer seedNumber = null;
  
  private static boolean WH_PRINT_ON = Parameters.WH_PRINT_ON;
  // ---------------- local state ------------------
  private Random randomGen; // to randomize bid/ask prices

  // Bid recording
  private HashMap<Integer, Order> lastOrder;
  private double[] marketMWh;
  private double[] marketPrice;
  private double meanMarketPrice = 0.0;
  
  private double clearingPricesWe[] = new double[24];
  private double clearingPricesWd[] = new double[24];
  private int tradesPassedWe[] = new int[24];
  private int tradesPassedWd[] = new int[24];
  private int trainingTimer = 0;
  private CapacityTransaction[] capacityFees = new CapacityTransaction[3];
  
  private double netUsagePredictorWe[] = new double[24];
  private double netUsagePredictorWd[] = new double[24];
  private double netUsageWe[] = new double[24];
  private double netUsageWd[] = new double[24];
  private int netUsageCounterWe[] = new int[24];
  private int netUsageCounterWd[] = new int[24];
  private double totalDistributionCosts = 0;
  private double totalDistributionEnergy = 0;
  private double totalBalancingCosts = 0;
  private double totalBalancingEnergy = 0;
  private double totalWholesaleCosts[] = new double[2];
  private double totalWholesaleEnergy[] = new double[2];
  
//  private double totalPredictedEnergyKWH = 0;
  private WeatherReport prevWeatherReport = null;
  
//  private ArrayList<>
  
  public Competition comp;
  
  public int numberOfBrokers = -1;
  
  private Instant startTime = null;
  
  private ArrayList<WeatherDataWithUsage> weatherDatas;
  private ArrayList<WeatherDataWithPeaks> weatherDatasPeaks;

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
    
    weatherDatas = new ArrayList<WeatherDataWithUsage>();
    weatherDatasPeaks = new ArrayList<WeatherDataWithPeaks>();
    
    for(int i = 0 ; i<24 ; i++) {
    	clearingPricesWe[i] = 30;
    	clearingPricesWd[i] = 30;
    	tradesPassedWe[i] = 1;
    	tradesPassedWd[i] = 1;
    	
    	netUsageWe[i] = 40000;
    	netUsageWd[i] = 30000;
    	netUsageCounterWe[i] = 1;
    	netUsageCounterWd[i] = 1;
    }
    
    
    energyPredictor = new EnergyPredictor();
    energyPredictor.resetModels();
    System.out.println("t");


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
    System.out.println("Competition name: "+ comp.getName());
    System.out.print("Competitors: ");
    for (String s : comp.getBrokers()) {
		System.out.print(s+ " ");
	}
    System.out.println("");
    System.out.println("Latitude: " + comp.getLatitude() + " Timezone Offset: " + comp.getTimezoneOffset());
    this.comp = comp;
    startTime = comp.getSimulationBaseTime();
    
    numberOfBrokers = comp.getBrokers().size() -1;
    
	try {			   
    	String os = System.getProperty("os.name");
    	if(os.equals("Windows 10")) {
    		FileOutputStream file = new FileOutputStream("..\\logs\\" + comp.getName() + ".output.txt");
    	    TeePrintStream tee = new TeePrintStream(file, System.out);
    	    System.setOut(tee);
    	}else {
    		FileOutputStream file = new FileOutputStream("../logs/" + comp.getName() + ".output.txt");
    	    TeePrintStream tee = new TeePrintStream(file, System.out);
    	    System.setOut(tee);
    	}

	    excelWriter = new ExcelWriter(comp.getName());
	    
	} catch (FileNotFoundException e) {		
		System.out.println("error writing to file");
		e.printStackTrace();
	}
	
  }

  /**
   * Handles a BalancingTransaction message.
   */
  public synchronized void handleMessage (BalancingTransaction tx)
  {
//	  System.out.printf("--> Charge: % .2f E \t Energy: % .2f KWh\n",tx.getCharge(),tx.getKWh());
	  totalBalancingEnergy += tx.getKWh();
	  totalBalancingCosts += tx.getCharge();
	  log.info("Balancing tx: " + tx.getCharge());
	  portfolioManager.setBalancingCosts(tx.getCharge());
  }

  /**
   * Handles a ClearedTrade message - this is where you would want to keep
   * track of market prices.
   */
  public synchronized void handleMessage (ClearedTrade ct)
  {
	  int ts = ct.getTimeslotIndex();
	  
	  if(getTimeSlotDay(ts)  <6) {
		  clearingPricesWd[getTimeSlotHour(ts)] += ct.getExecutionPrice();
		  tradesPassedWd[getTimeSlotHour(ts)] ++;
	  }else {
		  clearingPricesWe[getTimeSlotHour(ts)] += ct.getExecutionPrice();
		  tradesPassedWe[getTimeSlotHour(ts)] ++;
	  }
  }

  /**
   * Handles a DistributionTransaction - charges for transporting power
   */
  public synchronized void handleMessage (DistributionTransaction dt)
  {
	  totalDistributionEnergy += dt.getKWh();
	  totalDistributionCosts += dt.getCharge();
	  log.info("Distribution tx: " + dt.getCharge());
  }

  /**
   * Handles a CapacityTransaction - a charge for contribution to overall
   * peak demand over the recent past.
   */
  public synchronized void handleMessage (CapacityTransaction dt)
  {
	for (int j = 0; j < capacityFees.length; j++) {
		if(capacityFees[j] == null) {
			capacityFees[j] = dt;
			break;
		}
	}

	System.out.println("======================================================================================="
			+ "========================================================");
//	System.out.println("ts: " + dt.getPeakTimeslot() + "  " + dt.getBroker().getUsername()
//			+ "  " + dt.getKWh() + "  " + dt.getThreshold() + "  " + dt.getCharge() );
	System.out.printf("CapacityTransaction| peak ts:%5d Energy: %8.2f KWh ThreshHold: %8.2f  Costs: %10.2f €\n", 
						dt.getPeakTimeslot(),dt.getKWh(),dt.getThreshold(), dt.getCharge());
	System.out.println("======================================================================================="
			+ "========================================================");
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
//    System.out.println("Calculated bootstrap data");
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
//	  System.out.println(tx.getTimeslotIndex());
	  if(tx.getPrice() > 0) {
		  totalWholesaleCosts[1] += tx.getPrice();
		  totalWholesaleEnergy[1] += tx.getMWh()*1000;
	  }else{
		  totalWholesaleCosts[0] += tx.getPrice();
		  totalWholesaleEnergy[0] += tx.getMWh()*1000;
	  }
		  
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
	  int hour,day,counter = 0;	  
	  int ts = forecast.getTimeslotIndex();
	  double results[] = new double[24];
	  for(int i = 0; i < 24; i++) {
      	results[i] = -1;
      }
	  
	  ArrayList<WeatherData> weatherlist = new ArrayList<WeatherData>();
	  
	  for(WeatherForecastPrediction f : forecast.getPredictions()) {
		  
		  hour = getTimeSlotHour(ts + f.getForecastTime());
		  day = getTimeSlotDay(ts + f.getForecastTime());
		  
		  weatherlist.add(new WeatherData(day, hour, ts+f.getForecastTime(), f.getTemperature(), f.getWindSpeed(), f.getWindDirection(), f.getCloudCover()));
	  }
	  ObjectToJson.toJSON(forecast);
	  ObjectToJson.toJSONForecast(weatherlist);
	  
	  hour = getTimeSlotHour(ts);
	  day = getTimeSlotDay(ts);

      if(ts > 370 && trainingTimer == 0) {
          results = energyPredictor.predict();
//          if(results[0] == 0) {
//        	  if(energyPredictor.getFailure_counter() < EnergyPredictor.getALLOWED_TIMEOUTS()) {
//        		  System.out.println("FAIL in prediction");
//        	  }                  
//                  results = rndPredictor();
//          }
      }
	  
	  for(WeatherForecastPrediction f : forecast.getPredictions()) {
		  
		  hour = getTimeSlotHour(ts + f.getForecastTime());
		  day = getTimeSlotDay(ts + f.getForecastTime());
		  
//		  int tempValue = 0;
//		  if( results[counter] > portfolioManager.getCurrentThreshold() + Parameters.THRESHOLD_OFFSET) {
//			  tempValue = 1;
//		  }
//		  excelWriter.writeCell(forecast.getTimeslotIndex() + f.getForecastTime() - 360 , f.getForecastTime() + 2 + 28, results[counter],false);
//		  
		  excelWriter.writeCell(forecast.getTimeslotIndex() + f.getForecastTime() - 360 , f.getForecastTime() + 2, results[counter],false);
		  		  
		  if(day < 6) {
			  netUsagePredictorWd[hour] = (1 + results[counter]) * 1000;
			  
		  }else {
			  netUsagePredictorWe[hour] = (1 + results[counter]) * 1000;
		  }
		  counter++;
	  }
  }

  /**
   * Receives a new WeatherReport.
   */
  public synchronized void handleMessage (WeatherReport report)
  {
	  int hour = getTimeSlotHour( report.getTimeslotIndex());
	  int day = getTimeSlotDay( report.getTimeslotIndex());	  
	  
//	  WeatherData w = new WeatherData(day, hour,report.getTimeslotIndex(), report.getTemperature(), report.getWindSpeed(),
//			  									report.getWindDirection(), report.getCloudCover());
//	  
	  WeatherDataWithUsage ww = new WeatherDataWithUsage(day, hour,report.getTimeslotIndex(), report.getTemperature(), report.getWindSpeed(),
														report.getWindDirection(), report.getCloudCover(),0);
	  
	  boolean t = false;
	  WeatherDataWithPeaks www = new WeatherDataWithPeaks(day, hour,report.getTimeslotIndex(), report.getTemperature(),
			  						report.getWindSpeed(),report.getWindDirection(), report.getCloudCover(),0,t);	  
	  
	  if(report.getTimeslotIndex() < 360) {
		  weatherDatas.add(ww);
		  weatherDatasPeaks.add(www);
	  }	  
	  
	  if(report.getTimeslotIndex()< 360) {
		  prevWeatherReport = report;
		  return;
	  }
	  int temp = prevWeatherReport.getTimeslotIndex();
//	  System.out.println("Weather| ts: " + temp +" " + prevWeatherReport.getCloudCover() );
//	  DistributionReport d = contextManager.getReport();
//	  System.out.println("Actual Demand Ts: " + temp + "\t" + contextManager.getUsage(temp) + " KWh");
	  
	  hour = getTimeSlotHour( prevWeatherReport.getTimeslotIndex());
	  day = getTimeSlotDay( prevWeatherReport.getTimeslotIndex());	  
	  
	  ww = new WeatherDataWithUsage(day, hour,prevWeatherReport.getTimeslotIndex(), prevWeatherReport.getTemperature(),
			   prevWeatherReport.getWindSpeed(),prevWeatherReport.getWindDirection(), prevWeatherReport.getCloudCover(),
			   contextManager.getUsage(temp)/1000);
	  
	  
	  ObjectToJson.toJSONFitUsage(ww);
	  
	  
//	  System.out.println("Temp: " + contextManager.getUsage(temp) + " Threshold: " + portfolioManager.getCurrentThreshold() + 5000);
	  if(portfolioManager.getCurrentThreshold() + Parameters.THRESHOLD_OFFSET < contextManager.getUsage(temp)) {		  
		  t = true;
	  }
	  
	  www = new WeatherDataWithPeaks(day, hour,prevWeatherReport.getTimeslotIndex(), prevWeatherReport.getTemperature(),
			   prevWeatherReport.getWindSpeed(),prevWeatherReport.getWindDirection(), prevWeatherReport.getCloudCover()
			   ,contextManager.getUsage(temp)/1000,t);	  
	  
	  if(report.getTimeslotIndex() > 371) {
		  ObjectToJson.toJSONPeaks(www);
		  //TODO call PEAKS from predictor
	  }
	  
	  if(contextManager.getUsage(temp) == 0 && report.getTimeslotIndex() > 371 ) {
		  System.out.println("Error in fitUsage creation");
	  }else if(report.getTimeslotIndex() > 371 && trainingTimer == 0){
		  energyPredictor.fitData();
	  }
	  
//	  portfolioManager.setBatchWeather(w);
	  
	  if((report.getTimeslotIndex() - 360) % 25 == 0 )
		  excelWriter.writeCell(0,0,0,true);

	  DistributionReport dr = contextManager.getReport();

	  excelWriter.writeCell(dr.getTimeslot()-360,0,dr.getTimeslot(),false);
	  excelWriter.writeCell(dr.getTimeslot()-360,1,dr.getTotalConsumption()-dr.getTotalProduction(),false);

	  excelWriter.writeCell(dr.getTimeslot()-360,28,dr.getTotalConsumption(),false);
	  
	  int tempValue = 0;
	  if(dr.getTotalConsumption()-dr.getTotalProduction() > portfolioManager.getCurrentThreshold() + Parameters.THRESHOLD_OFFSET) {
		  tempValue = 1;
	  }
	  excelWriter.writeCell(dr.getTimeslot()-360,30,tempValue,false);
	 
	  
	  prevWeatherReport = report;
  }

  /**
   * Receives a BalanceReport containing information about imbalance in the
   * current timeslot.
   */
  public synchronized void handleMessage (BalanceReport report)
  {
//	  System.out.printf("--> Imbalance:  %10.2f KWh\n" ,report.getNetImbalance());
//	  System.out.printf("--> Imbalance: ts: %d  %10.2f M.P. %10.2f  \t Diff: % .2f\n" ,report.getTimeslotIndex(),report.getNetImbalance() 
//			  	,broker.getBroker().findMarketPositionByTimeslot(report.getTimeslotIndex()).getOverallBalance()*1000,
//			  	report.getNetImbalance()-broker.getBroker().findMarketPositionByTimeslot(report.getTimeslotIndex()).getOverallBalance()*1000); 
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
	updateUsage(portfolioManager.collectUsage(timeslotIndex), timeslotIndex); 
	double neededKWh = 0.0;
	
	  if((timeslotIndex-360) % 168 == 0 && timeslotIndex != 360) {
		  //TODO call retrain
		  System.out.println("Re-train!!!!!");
		  energyPredictor.retraintData((int) Math.round(portfolioManager.getCurrentThreshold()));
		  trainingTimer = 10;
		  
	  }
	
    log.debug(" Current timeslot is " + timeslotIndex);
    System.out.println("\n|------------------------------------|  Current timeslot is " + timeslotIndex 
    			+" |  Day: "+ getTimeSlotDay(timeslotIndex) + "  Hour: " + getTimeSlotHour(timeslotIndex));
    for (Timeslot timeslot : timeslotRepo.enabledTimeslots()) {
      printAboutTimeslot(timeslot);
//      System.out.println("usage record lentgh: " + broker.getUsageRecordLength());
      int index = (timeslot.getSerialNumber()) % broker.getUsageRecordLength();
//      System.out.print("  Index: "+ index);
      neededKWh = portfolioManager.collectUsage(index);
//      System.out.print(" needed KWH: "+ neededKWh);
      submitBidMCTS(neededKWh,timeslotIndex, timeslot.getSerialNumber());
 
    }
    
	  trainingTimer --;
	  if(trainingTimer < 0) {
		  trainingTimer = 0;
	  }
    
  }
  void printAboutTimeslot(Timeslot t) {
	  if(WH_PRINT_ON)
		  System.out.print("Timeslot serial: "+ t.getSerialNumber());
//	  System.out.println("time of day"+ t.slotInDay());
//	  System.out.println("day of week"+ t.dayOfWeek());
//	  System.out.println("stat time"+ t.getStartTime());
//	  System.out.println("stat instant"+ t.getStartInstant());
//	  System.out.println("stat instant"+ t.getEndInstant());
//	  System.out.println(" ");
  }
  
  
  /**MCTS VARIABLES BELOW
   * check Parameters.java for more info
   */
  public static int MAX_ITERATIONS = Parameters.MAX_ITERATIONS;
  public static int NUM_OF_ACTIONS = Parameters.NUM_OF_ACTIONS;
  public static int NO_BID = Parameters.NO_BID;
 
  public static double OBSERVED_DEVIATION = Parameters.OBSERVED_DEVIATION;
  public static double D_MIN = Parameters.D_MIN;
  public static double D_MAX = Parameters.D_MAX;
  
  
  
  
  /**
   * Composes and submits the appropriate order for the given timeslot.
   */
  private void submitBidMCTS (double neededKWh,int currentTimeslot, int timeslotBidding)
  {
    double neededMWh = neededKWh / 1000.0;
    //find how many MWH are already available in given timeslot
    MarketPosition posn = broker.getBroker().findMarketPositionByTimeslot(timeslotBidding);
    double offset = portfolioManager.getParams().MarketManagerOffset;
    
    if (posn != null)
      neededMWh -= posn.getOverallBalance();
    if (Math.abs(neededMWh) <= minMWh) {
      log.info("no power required in timeslot " + timeslotBidding);
      if(WH_PRINT_ON)
    	  System.out.println(" ");
      return;
    }
    if(WH_PRINT_ON)
    	System.out.print("  neededMWH: " + neededMWh);
    //System.out.print("  "+ posn.toString());
    
    Double limitPrice = computeLimitPrice(timeslotBidding, neededMWh);
    //==========================================================================================	
    
    if(neededMWh < 0) {
        log.info("new order for " + neededMWh + " at " + limitPrice + " in timeslot " + timeslotBidding);
        Order order = new Order(broker.getBroker(), timeslotBidding, neededMWh, limitPrice + offset );
        
        lastOrder.put(timeslotBidding, order);
        broker.sendMessage(order);
        return;
    }
    
    double neededMWHTemp ;
    int timeslotBiddingTemp;
    Node curNode;
    ArrayList<Node> visitedNodes;
    double Csim; // Total simulated cost of auctions done
//    double Cbal = 0; // Estimated Balancing cost
    double CbalUnitPrice = - 2* Math.abs(Parameters.buyLimitPriceMin)/2; // May need to reevaluate this!!!!!!!!!!!!!!!!TODO  
    double CavgUnit = 0; // Average Unit Cost 
    
    //Initialize 
	Node root = new Node(0, null, 0, timeslotBidding-currentTimeslot);
	//computeLimitPrice function will be replaced by pPredictor
	root.generateRootsKids(computeLimitPrice(timeslotBidding, neededMWh));
    
	ArrayList<Integer> dynamic_actions_threshold  = new ArrayList<Integer>();
	for (int i : Parameters.DYNAMIC_THRESHOLD) {
		dynamic_actions_threshold.add(MAX_ITERATIONS*i/100);
	}
	
    for (int i = 0; i < MAX_ITERATIONS; i++) {
    	
    	//If true add new action
    	if( dynamic_actions_threshold.get(0) == i && Parameters.EN_DYNAMIC) {
    		dynamic_actions_threshold.remove(0);
    		root.addDynamicAction();
    		
    		if(dynamic_actions_threshold.isEmpty()) {
    			dynamic_actions_threshold.add(0);
    		}
    	}
    	
    	neededMWHTemp = neededMWh; // Get Demand(t,n)
    	timeslotBiddingTemp = timeslotBidding;
    	curNode = root;
    	visitedNodes = new ArrayList<Node>();
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
    				
    			visitedNodes.add(curNode);
    			break;
    			
    		}else {
    			curNode = curNode.getBestUCTChild(CbalUnitPrice);
    			
				if(curNode.actionID != NO_BID) {					
	    			//simulate  
	    			//get a simulated clearing Price
	    			double limitPriceMCTS = computeLimitPrice(timeslotBidding + curNode.hoursAhead,neededMWHTemp);
	    			double clearingPrice = randomGen.nextGaussian()* OBSERVED_DEVIATION + limitPriceMCTS; 
	    			// TODO swap compute limit price with price  value
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
    
    if(bestActionNode == null) {
    	bestActionNode = new Node(limitPrice, null, -1, -1);
    }
//
//    if(timeslotBidding-currentTimeslot == 21) {
//    	
//    	printTree(root);
//    }
    
    //if NO_BID was chosen return
    if(bestActionNode.actionID == NO_BID) {
    	if(WH_PRINT_ON)
    		System.out.println(" ");
    	return;
    }
    
//    if(timeslotBidding - currentTimeslot > 18) {
//    	bestActionNode.actionID += -10;
//    }
    
//   Temp fix for positive selling prices
    
    if( neededMWh > 0 && bestActionNode.actionID > 0) {
    	bestActionNode.actionID = - bestActionNode.actionID;
    }
    if( neededMWh < 0 && bestActionNode.actionID < 0) {
    	bestActionNode.actionID = - bestActionNode.actionID;
    }
    
    if(WH_PRINT_ON)
    	System.out.println("  ------ mcts bid: " + bestActionNode.actionID + "  base: " + limitPrice);
    Order order;
    // ==========================================================================================	
    log.info("new order for " + neededMWh + " at " + limitPrice + " in timeslot " + timeslotBidding);
//    Order order = new Order(broker.getBroker(), timeslotBidding, neededMWh, limitPrice);
    order = new Order(broker.getBroker(), timeslotBidding, neededMWh, bestActionNode.actionID - offset);
 
    lastOrder.put(timeslotBidding, order);
    broker.sendMessage(order);
  }
  
  @SuppressWarnings("unused")
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
    int current = timeslot;
    int remainingTries = (timeslot - current
                          - Competition.currentCompetition().getDeactivateTimeslotsAhead());
    log.debug("remainingTries: " + remainingTries);
    if (remainingTries > 0) { ////!
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
  
  public int getCompetitors() {
	  return numberOfBrokers;
  }
  
  /**
   * Start time of a sim session in the sim world. This is actually the start
   * of the bootstrap session, which is typically 15 days before the start of
   * a normal sim session.
   */
//  public Instant getSimulationBaseTime ()
//  {
//    return simulationBaseTime;
//  }
  
  public int getTimeSlotDay(int t) {

	 int day =  startTime.get(DateTimeFieldType.dayOfWeek()); 
	 
	  return (day + t/24) % 7 + 1;
  }
  
  public int getTimeSlotHour(int t) {
	  
	  int hour =  startTime.get(DateTimeFieldType.hourOfDay());

	  return( t+hour ) % 24;
  }
  
  public double getAvgClearedPrice(int timeslot) {
	  
	  int day = getTimeSlotDay(timeslot);
	  int hour = getTimeSlotHour(timeslot);
	  
	  if(day < 6) {
		  return clearingPricesWd[hour]/tradesPassedWd[hour];
	  }else {
		  return clearingPricesWe[hour]/tradesPassedWe[hour];
	  }
	  
  }
  
  public double[] getAvgClearingPriceWe() {
	  double t[] = new double[24];
	  
	  for (int i = 0; i < t.length; i++) {
		t[i] = clearingPricesWe[i]/tradesPassedWe[i];
	}
 	  return t;
  }
  
  public double[] getAvgClearingPriceWd() {
	  double t[] = new double[24];
	  
	  for (int i = 0; i < t.length; i++) {
		t[i] = clearingPricesWd[i]/tradesPassedWd[i];
	}
 	  return t;
  }
  
  private void updateUsage(double usageKWH,int timeslot) {
	  
	  if(getTimeSlotDay(timeslot)  <6) {
		  netUsageWd[getTimeSlotHour(timeslot)] += usageKWH;
		  netUsageCounterWd[getTimeSlotHour(timeslot)] ++;
	  }else {
		  netUsageWe[getTimeSlotHour(timeslot)] += usageKWH;
		  netUsageCounterWe[getTimeSlotHour(timeslot)] ++;
	  }
  }
  
  public double[] getAvgNetusageWe() {
	  double t[] = new double[24];
	  
	  for (int i = 0; i < t.length; i++) {
		t[i] = netUsageWe[i]/netUsageCounterWe[i];
	}
 	  return t;
  }
  
  public double[] getAvgNetusageWd() {
	  double t[] = new double[24];
	  
	  for (int i = 0; i < t.length; i++) {
		t[i] = netUsageWd[i]/netUsageCounterWd[i];
	}
 	  return t;
  }
  
  public double getDistributionCosts() {
	return totalDistributionCosts;  
  }
  public void setDistributionCosts(double v) {
	  totalDistributionCosts = v;
  }
  public double getBalancingCosts() {
	  return totalBalancingCosts;
  }
  public void setBalancingCosts(double v) {
	  totalBalancingCosts = v;
  }
  
  public double[] getWholesaleCosts() {
	  return totalWholesaleCosts;
  }
  public void setWholesaleCosts(double v) {
	  totalWholesaleCosts[0] = v;
	  totalWholesaleCosts[1] = v;
  }
  
  public double[] getWholesaleEnergy() {
	  return totalWholesaleEnergy;
  }
  public void setWholesaleEnergy(double v) {
	  totalWholesaleEnergy[0] = v;
	  totalWholesaleEnergy[1] = v;
  }

  public Competition getComp() {
	  return comp;
  }

  public double getTotalDistributionEnergy() {
	return totalDistributionEnergy;
  }

  public void setTotalDistributionEnergy(double totalDistributionEnergy) {
	this.totalDistributionEnergy = totalDistributionEnergy;
  }

  public void setComp(Competition comp) {
	  this.comp = comp;
  }

public double getTotalBalancingEnergy() {
	return totalBalancingEnergy;
}

public void setTotalBalancingEnergy(double totalBalancingEnergy) {
	this.totalBalancingEnergy = totalBalancingEnergy;
}

public double[] getNetUsagePredictorWe() {
	return netUsagePredictorWe;
}

public double[] getNetUsagePredictorWd() {
	return netUsagePredictorWd;
}

public CapacityTransaction[] getCapacityFees() {
	return capacityFees;
}

public void resetCapacityFees() {
	for (int i = 0; i < 3; i++) {
		capacityFees[i] = null;
	}
}

public void generateWeatherBootJSON() {
	ObjectToJson.toJSONWeather(weatherDatas);
	ObjectToJson.toJSONPeak(weatherDatasPeaks);
	System.out.println("--");
}

public void setUsageInBoot(double[] usage,double threshold) {
	for(WeatherDataWithUsage w : weatherDatas) {
		w.setNetUsageMWh(-usage[w.getTimeslot()-24]/1000);
	}
	System.out.println("Threshold from Boot: " + threshold);
	boolean t = false;
	for(WeatherDataWithPeaks w : weatherDatasPeaks) {		
//		System.out.println("Timeslot: " +w.getTimeslot() + "  Threshold:" + threshold + "  Demand: " + Math.abs(usage[w.getTimeslot()-24]));
		t = false;
		if(Math.abs(usage[w.getTimeslot()-24]) > (threshold + Parameters.THRESHOLD_OFFSET)) {
			t = true;
		}
		w.setPeak(t);
		w.setNetUsageMWh(-usage[w.getTimeslot()-24]/1000);
	}
}

private double[] rndPredictor() {
	double result[] = new double[24];
	
	for(int i = 0; i < 24 ; i++) {
		result[i] = randomGen.nextDouble()*25000 + 30000;
	}
	return result;
}

public void trainpredictor() {
    energyPredictor.trainBootData();
}
  
  
}
