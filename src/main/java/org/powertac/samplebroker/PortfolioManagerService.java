/*
 * Copyright (c) 2012-2019 by the original author
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.joda.time.Instant;
import org.powertac.common.Broker;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.RegulationRate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TimeService;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.BalancingControlEvent;
import org.powertac.common.msg.BalancingOrder;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.msg.TariffRevoke;
import org.powertac.common.msg.TariffStatus;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.assistingclasses.Customer;
import org.powertac.samplebroker.assistingclasses.CustomerUsage;
import org.powertac.samplebroker.assistingclasses.TimeslotUsage;
import org.powertac.samplebroker.assistingclasses.WeatherData;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.ContextManager;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.samplebroker.interfaces.MarketManager;
import org.powertac.samplebroker.interfaces.PortfolioManager;
import org.powertac.samplebroker.utility.ExcelWriter;
import org.powertac.samplebroker.utility.ObjectToJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Handles portfolio-management responsibilities for the broker. This
 * includes composing and offering tariffs, keeping track of customers and their
 * usage, monitoring tariff offerings from competing brokers.
 * 
 * 
 * @author John Collins , Stavros Orfanoudakis
 */
@Service // Spring creates a single instance at startup
public class PortfolioManagerService 
implements PortfolioManager, Initializable, Activatable
{
  static Logger log = LogManager.getLogger(PortfolioManagerService.class);
  
  private BrokerContext brokerContext; // master

  // Spring fills in Autowired dependencies through a naming convention
  @Autowired
  private BrokerPropertiesService propertiesService;
  
  @Autowired
  private TimeslotRepo timeslotRepo;

  @Autowired
  private TariffRepo tariffRepo;
  
  @Autowired
  private CustomerRepo customerRepo;

  @Autowired
  private MarketManager marketManager;
  
  @Autowired
  private ContextManager contextManager;
  
  @Autowired
  private TimeService timeService;

  // ---- Portfolio records -----
  // Customer records indexed by power type and by tariff. Note that the
  // CustomerRecord instances are NOT shared between these structures, because
  // we need to keep track of subscriptions by tariff.
  private Map<PowerType, Map<CustomerInfo, CustomerRecord>> customerProfiles;
  private Map<TariffSpecification, Map<CustomerInfo, CustomerRecord>> customerSubscriptions;
  private Map<PowerType, List<TariffSpecification>> competingTariffs;
  private Map<TariffSpecification,Double> tariffCharges;
  private Map<TariffSpecification,Integer> tariffCustomerCount;

  private int consumptionCustomers = 0;
  private int eVCustomers = 0;
  private int storageCustomers = 0;
  private int interruptibleCustomers = 0;
  private int tHCCustomers = 0;
  private int solarCustomers = 0;
  private int windCustomers = 0;
  private int BatteryCustomers = 0;
  private int otherProducers = 0;
  
  private int consumptionCustomersTotal = 0;
  private int windCustomersTotal = 0;
  private int solarCustomersTotal = 0;
  private int thermalCustomersTotal = 0;

  private int interruptibleCustomersPrev = 0;

  private double totalProfits = 0;
  private double totalConsumptionProfits = 0;
  private double totalSolarProfits = 0;
  private double totalWindProfits = 0;
  private double totalThermalProfits = 0;
  
  private double totalBalancingCosts = 0;
  private double totalAssessmentBalancingCosts = 0;
  
  private double weightWe[] = new double[24];
  private double weightWd[] = new double[24];
  
  private double balancingCosts = 0;
  private double balancingEnergy = 0;
  
  private double middleBoundOffset = 0;
  private double lowerBoundOffset = 0;
  
  private int timer = 0 ;
  private int timer2 = 0;
  
  private boolean triggerEvaluation = false;
  private int triggerEvaluationTS = 0;
  private boolean enableStorage = true;
//  private boolean enableProduction = true;
  private boolean enableInterruptible = true;
  
  private boolean enableGConsumption = true;
  private boolean enableGProduction = true;
  private boolean enableGInterruptible = false;
  private boolean enableGStorage = true;
  
  private double[] bootsrapUsage = new double[360];
  private double[] totalDemand = new double[2500];
  private double[] totalEnergyUsed = new double[5000];
  private int totalDemandLength = 0;
  private double[] peakDemand = new double[3];
  private int[] peakDemandTS = new int[3];
  private double gamaParameter = 1.25;
  
  private double bestEWP = 0;
  private double currentThreshold = 0;
  
  private double totalEnergyused = 0;
  
  private boolean thermal_Passed = false;
  private boolean solar_Passed = false;
  private boolean wind_Passed = false;
  
  private boolean secondaryTariffEn = false;
  
  private ArrayList<CustomerUsage> subscriptionList ;
  private ArrayList<Customer> customerList ;
  private ArrayList<TimeslotUsage> trainingBatch;
  int trainingCounter = 0;
  
  Parameters params;
  
  enum State {
	  NORMAL,
	  LOW_PERCENTAGE
	}
  
  State state = State.NORMAL;
  int state_duration = 0;
  
  private double LowerBoundABS ;
  private double LowerBound = Parameters.LowerBoundStatic;
  private double UpperBound = Parameters.UpperBoundStatic;
  
  public ExcelWriter excelWriter;
    
  // These customer records need to be notified on activation
  private List<CustomerRecord> notifyOnActivation = new ArrayList<>();

  // Configurable parameters for tariff composition
  // Override defaults in src/main/resources/config/broker.config
  // or in top-level config file
  @ConfigurableValue(valueType = "Double",
          description = "target profit margin")
  private double defaultMargin = 0.5;

  @ConfigurableValue(valueType = "Double",
          description = "Fixed cost/kWh")
  private double fixedPerKwh = -0.06; 

  @ConfigurableValue(valueType = "Double",
          description = "Default daily meter charge")
  private double defaultPeriodicPayment = -1.0;

private ApplicationContext ctx;
  
  /**
   * Default constructor.
   */
  public PortfolioManagerService ()
  {
    super();
  }

  /**
   * Per-game initialization. Registration of message handlers is automated.
   */
  @Override // from Initializable
  public void initialize (BrokerContext context)
  {
    this.brokerContext = context;
    propertiesService.configureMe(this);
    customerProfiles = new LinkedHashMap<>();
    customerSubscriptions = new LinkedHashMap<>();
    tariffCharges = new LinkedHashMap<TariffSpecification, Double>();
    tariffCustomerCount = new LinkedHashMap<TariffSpecification, Integer>();
    competingTariffs = new HashMap<>();
    
	ctx = new AnnotationConfigApplicationContext(Parameters.class);
	params = ctx.getBean(Parameters.class);
	
	LowerBoundABS= params.LowerBoundStaticAbsolute;
	customerList = new ArrayList<Customer>();
	trainingBatch = new ArrayList<TimeslotUsage>();
	subscriptionList = new ArrayList<CustomerUsage>();
	
    Random ran = new Random();
    timer = -ran.nextInt(Parameters.reevaluationCons + 2) + 2;
    state = State.NORMAL;
    
    for(int i=0;i<360;i++)
    	bootsrapUsage[i] = 0;
    
    notifyOnActivation.clear();
    
    for(int i = 0 ; i < totalEnergyUsed.length ; i++) {
    	totalEnergyUsed[i] = 0;
    }
  }
  
  // -------------- data access ------------------
  
  /**
   * Returns the CustomerRecord for the given type and customer, creating it
   * if necessary.
   */
  CustomerRecord getCustomerRecordByPowerType (PowerType type,
                                               CustomerInfo customer)
  {
    Map<CustomerInfo, CustomerRecord> customerMap =
        customerProfiles.get(type);
    if (customerMap == null) {
      customerMap = new LinkedHashMap<>();
      customerProfiles.put(type, customerMap);
    }
    CustomerRecord record = customerMap.get(customer);
    if (record == null) {
      record = new CustomerRecord(customer);
      customerMap.put(customer, record);
    }
    return record;
  }
  
  /**
   * Returns the customer record for the given tariff spec and customer,
   * creating it if necessary. 
   */
  CustomerRecord getCustomerRecordByTariff (TariffSpecification spec,CustomerInfo customer)
  {
    Map<CustomerInfo, CustomerRecord> customerMap =
        customerSubscriptions.get(spec);
    if (customerMap == null) {
      customerMap = new LinkedHashMap<>();
      customerSubscriptions.put(spec, customerMap);
      tariffCharges.put(spec, 0d);
      tariffCustomerCount.put(spec, 0);
    }
    CustomerRecord record = customerMap.get(customer);
    if (record == null) {
      // seed with the generic record for this customer
      record =
          new CustomerRecord(getCustomerRecordByPowerType(spec.getPowerType(),
                                                          customer));
      customerMap.put(customer, record);
      // set up deferred activation in case this customer might do regulation
      record.setDeferredActivation();
    }
    return record;
  }
  
  /**
   * Finds the list of competing tariffs for the given PowerType.
   */
  List<TariffSpecification> getCompetingTariffs (PowerType powerType)
  {
    List<TariffSpecification> result = competingTariffs.get(powerType);
    if (result == null) {
      result = new ArrayList<TariffSpecification>();
      competingTariffs.put(powerType, result);
    }
    return result;
  }

  /**
   * Adds a new competing tariff to the list.
   */
  private void addCompetingTariff (TariffSpecification spec)
  {
    getCompetingTariffs(spec.getPowerType()).add(spec);
  }

  /**
   * Returns total usage for a given timeslot (represented as a simple index).
   */
  @Override
  public double collectUsage (int index)
  {
    double result = 0.0;
    for (Map<CustomerInfo, CustomerRecord> customerMap : customerSubscriptions.values()) {
      for (CustomerRecord record : customerMap.values()) {
        result += record.getUsage(index);
      }
    }
    return -result; // convert to needed energy account balance
  }

  // -------------- Message handlers -------------------
  /**
   * Handles CustomerBootstrapData by populating the customer model 
   * corresponding to the given customer and power type. This gives the
   * broker a running start.
   */
  public synchronized void handleMessage (CustomerBootstrapData cbd)
  {
	  CustomerInfo customer = customerRepo.findByNameAndPowerType(cbd.getCustomerName(),cbd.getPowerType());
	  CustomerRecord record = getCustomerRecordByPowerType(cbd.getPowerType(), customer);
	  int subs = record.subscribedPopulation;
	  record.subscribedPopulation = customer.getPopulation();
	  for (int i = 0; i < cbd.getNetUsage().length; i++) {
    	record.produceConsume(cbd.getNetUsage()[i], i);
	  }
	  record.subscribedPopulation = subs;
	  
	  customerList.add(new Customer(cbd.getCustomerName(), cbd.getPowerType(), cbd.getNetUsage()));	  
	  
	  for(int i=0; i<360 && i<cbd.getNetUsage().length;i++) {
		  bootsrapUsage[i] += cbd.getNetUsage()[i];
	  }
	  //calculating customer number of each power type
		 switch(customer.getPowerType().toString()) {
	    	case "CONSUMPTION":
	    		consumptionCustomers += customer.getPopulation();
	    		break;
	    	case "STORAGE":
	    		storageCustomers += customer.getPopulation();
	    		break;
	    	case "INTERRUPTIBLE_CONSUMPTION":
	    		interruptibleCustomers += customer.getPopulation();
	    		break;
	    	case "THERMAL_STORAGE_CONSUMPTION":
	    		tHCCustomers += customer.getPopulation();
	    		break;
	    	case "SOLAR_PRODUCTION":
	    		solarCustomers += customer.getPopulation();
	    		break;
	    	case "WIND_PRODUCTION":
	    		windCustomers += customer.getPopulation();
	    		break;
	    	case "BATTERY_STORAGE":
	    		BatteryCustomers += customer.getPopulation();
	    		break;
	    	case "ELECTRIC_VEHICLE":
	    		eVCustomers += customer.getPopulation();
	    		break;
	    	default:
	    		otherProducers += customer.getPopulation();
	    		
	    }		  
		 
	   
  }

  /**
   * Handles a TariffSpecification. These are sent by the server when new tariffs are
   * published. If it's not ours, then it's a competitor's tariff. We keep track of 
   * competing tariffs locally, and we also store them in the tariffRepo.
   */
  public synchronized void handleMessage (TariffSpecification spec)
  {
    Broker theBroker = spec.getBroker();
    if (brokerContext.getBrokerUsername().equals(theBroker.getUsername())) {
      if (theBroker != brokerContext.getBroker())
        // strange bug, seems harmless for now
        log.info("Resolution failed for broker " + theBroker.getUsername());
      // if it's ours, just log it, because we already put it in the repo
      TariffSpecification original = tariffRepo.findSpecificationById(spec.getId());
      if (null == original)
        log.error("Spec " + spec.getId() + " not in local repo");
      log.info("published " + spec);
    }
    else {
      // otherwise, keep track of competing tariffs, and record in the repo
      addCompetingTariff(spec);      
      tariffRepo.addSpecification(spec);
//      triggerEvaluation = true;
      
      System.out.print("New Tariff: ");
	  printTariff(spec);	
    }
  }

  /**
   * Handles a TariffStatus message. This should do something when the status
   * is not SUCCESS.
   */
  public synchronized void handleMessage (TariffStatus ts)
  {
    log.info("TariffStatus: " + ts.getStatus());
  }

  /**
   * Handles a TariffTransaction. We only care about certain types: PRODUCE,
   * CONSUME, SIGNUP,PERIODIC and WITHDRAW.
   */
  public synchronized void handleMessage(TariffTransaction ttx)
  {
    // make sure we have this tariff
    TariffSpecification newSpec = ttx.getTariffSpec();
    if (newSpec == null) {
      log.error("TariffTransaction type=" + ttx.getTxType()
                + " for unknown spec");
    }
    else {
      TariffSpecification oldSpec =
              tariffRepo.findSpecificationById(newSpec.getId());
      if (oldSpec != newSpec) {
        log.error("Incoming spec " + newSpec.getId() + " not matched in repo");
      }
    }
    TariffTransaction.Type txType = ttx.getTxType();
    CustomerRecord record = getCustomerRecordByTariff(ttx.getTariffSpec(), ttx.getCustomerInfo());
    
    if (TariffTransaction.Type.WITHDRAW == txType) {
    	totalEnergyUsed[ttx.getPostedTimeslotIndex()] += ttx.getKWh();
      // customers presumably found a better deal
      record.withdraw(ttx.getCustomerCount());
      removeCustomer(ttx);
    }
//    else if (ttx.isRegulation()) {
//      // Regulation transaction -- we record it as production/consumption
//      // to avoid distorting the customer record. 
//    	totalEnergyUsed[ttx.getPostedTimeslotIndex()] += ttx.getKWh();
//      log.debug("Regulation transaction from {}, {} kWh for {}",
//                ttx.getCustomerInfo().getName(),
//                ttx.getKWh(), ttx.getCharge());
//      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
//    }
    else if (TariffTransaction.Type.PRODUCE == txType) {
      // if ttx count and subscribe population don't match, it will be hard
      // to estimate per-individual production
        totalEnergyUsed[ttx.getPostedTimeslotIndex()] += ttx.getKWh();
      if (ttx.getCustomerCount() != record.subscribedPopulation) {
        log.warn("production by subset {}  of subscribed population {}",
                 ttx.getCustomerCount(), record.subscribedPopulation);
      }
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
      double currentCharge = tariffCharges.get(ttx.getTariffSpec());
      tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
      
      log.info("TTX Produce: " +ttx.getCharge() + " , " + ttx.getId() 
      	+ " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
      
      CustomerUsage c = new CustomerUsage(ttx.getCustomerInfo().getName(), ttx.getKWh()/1000, ttx.getCustomerCount(),
    		  							ttx.getCustomerInfo().getPopulation(), ttx.getCustomerInfo().isMultiContracting());
      for ( TimeslotUsage t : trainingBatch) {
    	  if(t.getTimeslot() == ttx.getPostedTimeslot().getSerialNumber()) {
    		  t.getC().add(c);
    		  break;
    	  }
      }
//      System.out.printf("Produce! Name: %35s , Count: %7d , Charge: %7.2f , Energy: % 10.2f Total: %7d\n",
//	  			ttx.getCustomerInfo().getName(),ttx.getCustomerCount(),ttx.getCharge(),ttx.getKWh(),ttx.getCustomerInfo().getPopulation());
    }
    else if (TariffTransaction.Type.CONSUME == txType) {
    	totalEnergyUsed[ttx.getPostedTimeslotIndex()] += ttx.getKWh();
      if (ttx.getCustomerCount() != record.subscribedPopulation) {
        log.warn("consumption by subset {} of subscribed population {}",
                 ttx.getCustomerCount(), record.subscribedPopulation);
      }
      log.info("TTX Consume: " +ttx.getCharge() + " , " + ttx.getId() 
      + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
      double currentCharge = tariffCharges.get(ttx.getTariffSpec());
      tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
      
      CustomerUsage c = new CustomerUsage(ttx.getCustomerInfo().getName(), ttx.getKWh()/1000, ttx.getCustomerCount(),
				ttx.getCustomerInfo().getPopulation(), ttx.getCustomerInfo().isMultiContracting());
      
      for ( TimeslotUsage t : trainingBatch) {
    	  if(t.getTimeslot() == ttx.getPostedTimeslot().getSerialNumber()) {
    		  t.getC().add(c);
    	  		break;
    	  }
      }
      
//      System.out.printf("Consume| Name: %35s , Count: %7d , Charge: %7.2f , Energy: % 10.2f Total: %7d\n",
//	  			ttx.getCustomerInfo().getName(),ttx.getCustomerCount(),ttx.getCharge(),ttx.getKWh(),ttx.getCustomerInfo().getPopulation());
    }
    else if (TariffTransaction.Type.PERIODIC == txType) {
    	totalEnergyUsed[ttx.getPostedTimeslotIndex()] += ttx.getKWh();
    	log.info("TTX Periodic: " +ttx.getCharge() + " , " + ttx.getId() 
        + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
        record.produceConsume(ttx.getKWh(), ttx.getPostedTime()); 
        double currentCharge = tariffCharges.get(ttx.getTariffSpec());
        tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
        
    }
    else if (TariffTransaction.Type.REVOKE == txType) {
    	totalEnergyUsed[ttx.getPostedTimeslotIndex()] += ttx.getKWh();
    	log.info("TTX Revoke: " +ttx.getCharge() + " , " + ttx.getId() 
        + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
//    	System.out.println("TTX Revoke: " +ttx.getCharge() + " , " + ttx.getTariffSpec().getId() 
//      + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
        record.produceConsume(ttx.getKWh(), ttx.getPostedTime()); 
        double currentCharge = tariffCharges.get(ttx.getTariffSpec());
        tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
    }
    else if (TariffTransaction.Type.SIGNUP == txType) {
    	addCustomer(ttx);
        record.signup(ttx.getCustomerCount());        
    	totalEnergyUsed[ttx.getPostedTimeslotIndex()] += ttx.getKWh();
    	log.info("TTX Signup: " +ttx.getCharge() + " , " + ttx.getId() 
        + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
        record.produceConsume(ttx.getKWh(), ttx.getPostedTime()); 
        double currentCharge = tariffCharges.get(ttx.getTariffSpec());
        tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());

    }
    else if (TariffTransaction.Type.REFUND == txType) {
    	totalEnergyUsed[ttx.getPostedTimeslotIndex()] += ttx.getKWh();
    	log.info("TTX Refund: " +ttx.getCharge() + " , " + ttx.getId() 
        + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
        record.produceConsume(ttx.getKWh(), ttx.getPostedTime()); 
        double currentCharge = tariffCharges.get(ttx.getTariffSpec());
        tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
    }
    else if (TariffTransaction.Type.PUBLISH == txType) {
//    	System.out.println("Publish: " + ttx.getCharge() + " " + ttx.getKWh() + " "  + ttx.toString());
    	totalEnergyUsed[ttx.getPostedTimeslotIndex()] += ttx.getKWh();
        double currentCharge = tariffCharges.get(ttx.getTariffSpec());
        tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
    }
  }

  /**
   * Handles a TariffRevoke message from the server, indicating that some
   * tariff has been revoked.
   */
  public synchronized void handleMessage (TariffRevoke tr)
  {

	  Broker source = tr.getBroker();
	  log.info("Revoke tariff " + tr.getTariffId()
             + " from " + tr.getBroker().getUsername());
	  // if it's from some other broker, we need to remove it from the
	  // tariffRepo, and from the competingTariffs list
	  if (!(source.getUsername().equals(brokerContext.getBrokerUsername()))) {
		  log.info("clear out competing tariff");
		  TariffSpecification original =
              tariffRepo.findSpecificationById(tr.getTariffId());
      if (null == original) {
    	  log.warn("Original tariff " + tr.getTariffId() + " not found");
    	  return;
      }
      tariffRepo.removeSpecification(original.getId());
      List<TariffSpecification> candidates =
              competingTariffs.get(original.getPowerType());
      if (null == candidates) {
        log.warn("Candidate list is null");
        return;
      }
	  triggerEvaluation = true; 
      competingTariffs.get(original.getPowerType()).remove(original);
      System.out.print("REVOKE: " + tr.getTariffId() + "  ");
      printTariff(tariffRepo.findSpecificationById(tr.getTariffId()));
      
      candidates.remove(original);
    }
  }

  /**
   * Handles a BalancingControlEvent, sent when a BalancingOrder is
   * exercised by the DU.
   */
  public synchronized void handleMessage (BalancingControlEvent bce)
  {
//	  System.out.printf("={ Balancing: %14s , Energy: %7.2f KWh , Cost: %4.2f € \n", bce.getTariffId(),bce.getKwh(), bce.getPayment());
	  balancingCosts += bce.getPayment();
	  balancingEnergy += bce.getKwh();
	  log.info("BalancingControlEvent " + bce.getKwh());
  }

  // --------------- activation -----------------
  /**
   * Called after TimeslotComplete msg received. Note that activation order
   * among modules is non-deterministic.
   */
  @Override // from Activatable
  public synchronized void activate (int timeslotIndex) {
	  
	  try {
		  calcCapacityFees(timeslotIndex);
		  
		  if (triggerEvaluation == true && timeslotIndex > 370) {
			  triggerEvaluationTS = timeslotIndex ;
			  triggerEvaluation = false;
		  }
		  
		  if(timeslotIndex == 361) {
			  double[] d = new double[2];
			  d = calcDemandMeanDeviation();			
			  currentThreshold = (d[0] + gamaParameter*d[1]);
			  
			  ObjectToJson.toJSON(customerList);
			  marketManager.setUsageInBoot(bootsrapUsage,currentThreshold);
			  marketManager.generateWeatherBootJSON();
			  
			  marketManager.trainpredictor();
			  // call the initial predictor trainer
		  }

		  //Reevaluate Power and thermal tariffs
		  if((timeslotIndex-360 + 70) % 168 == 0) { // && timeslotIndex != 458) {
			  secondaryTariffEn = true;
			  System.out.println("\n\n\nMIDDLE-evaluation!!!!!\n\n\n");			 		 
		  }
		  
		  //call total demand predictor 
		  //call our subscription demand predictor
		  ObjectToJson.toJSONSubs(subscriptionList);
		  		  
//		  System.out.printf("-->Energy Usage: %.2f KWh   ts %d \n",totalEnergyUsed[timeslotIndex-1],timeslotIndex-1);
		  totalEnergyused += totalEnergyUsed[timeslotIndex-1];
		  
		  //Check if we need to update our tariffs and Print stats
	      if (customerSubscriptions.size() == 0) {
	    	  createInitialTariffs();
	      } else {    	  
	    	  if (timer >= Parameters.reevaluationCons || timeslotIndex == triggerEvaluationTS) {
	    	   		triggerEvaluation = false;
	    	   		
	    	   		generalTariffStrategy(timeslotIndex);
	    	   		
	        		timer = 0;
	    	  }
	    	  
	    	  if (trainingCounter <= 0) {
	    		  ObjectToJson.toJSONBatch(trainingBatch);
	    		  setUpTrainingBatch(timeslotIndex);
	    		  trainingCounter = Parameters.batchSize;
	    		  if (timeslotIndex > 361) {
	    			  
	    			// call online trainer  
	    		  }
	    	  }
	    	  
	    	  trainingCounter --;
	    	  timer ++;
	    	  timer2 ++;
	      }	
	  } catch (Exception e) {
		  System.out.println(e.toString());
		  System.out.println("ERROR in MAIN !!!!!!!!!!!\n\n\n\n\n\n\n\n\n\n\n");
		  
	  }
      
      for (CustomerRecord record: notifyOnActivation)
        record.activate();
      
      System.out.println("-");
  }  
  
  	private void generalTariffStrategy(int timeslotIndex) {
	  	TariffSpecification tempTariff ;
 		printDemandPeaks();        		 
		calculateWeightsPredictor();
		    		
		if(timer2 == Parameters.reevaluationInterruptible) {
			enableInterruptible = true;
		}else {
			enableInterruptible = false;
		}
		if(timer2 == Parameters.reevaluationStorage) {
			enableStorage = true;
			timer2 = 0;
		}else {
			enableStorage = false;
		}
		
		printBalanceStats();		
		calculateCustomerCounts();
		determineState();		
		
		for (TariffSpecification spec : customerSubscriptions.keySet()) {
			
			if(tariffCharges.get(spec) == null) {
				continue;
			}    				    			
						
			tempTariff = spec;
			
			//Check if Consumption tariff needs to be revoked
			if(spec.getPowerType() == PowerType.CONSUMPTION) {
				revokeConsumptionTariff(spec);
			}
			
//			if(spec.getPowerType().isProduction()) {
//				revokeProductionTariff(spec);
//			}
			
			if(spec.getPowerType().isStorage() && enableStorage && enableGStorage){  				
//				tempTariff = mutateStorageTariff(tempTariff);   				
//	  			tariffCharges.remove(spec);
//	  			
//	  			tempTariff.addSupersedes(spec.getId());
//	  	        tariffRepo.addSpecification(tempTariff);
//	  	        tariffCharges.put(tempTariff,0d);
//	  	        tariffCustomerCount.put(tempTariff,0);
//	  	        brokerContext.sendMessage(tempTariff);
//	  	        
//	  	        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
//	  	        brokerContext.sendMessage(revoke);
//	  	        
			} else if (spec.getPowerType() == PowerType.INTERRUPTIBLE_CONSUMPTION && enableInterruptible && enableGInterruptible) {
				tempTariff = mutateInterruptibleConsTariff(tempTariff);
				tariffCharges.remove(spec);

				tempTariff.addSupersedes(spec.getId());
				tariffRepo.addSpecification(tempTariff);
				tariffCharges.put(tempTariff,0d);
				brokerContext.sendMessage(tempTariff);
				
		        BalancingOrder order = new BalancingOrder(brokerContext.getBroker(),tempTariff, 0.3,calculateAvgRate(tempTariff,false) * 0.9);
		        brokerContext.sendMessage(order);
				

		        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
		        brokerContext.sendMessage(revoke);
			}
		}
		System.out.println("-- Total Solar Consumers: \t" + solarCustomersTotal + " / " + solarCustomers + " ");
		System.out.println("-- Total Wind  Consumers: \t" + windCustomersTotal + " / " + windCustomers + " ");
		System.out.println("-- Total Cons  Consumers: \t" + consumptionCustomersTotal + " / " + consumptionCustomers + " \n");
		
		consumptionTariffStrategy();  
				
		if(marketManager.getCompetitors() < 7) {
			setSecondaryTariffsEnablers();
		}
		
		if(secondaryTariffEn && marketManager.getCompetitors() < 7) {
			System.out.println("Solar: " + solar_Passed );
			System.out.println("Wind: " + wind_Passed );
			System.out.println("Thermal: " + thermal_Passed );
			secondaryTariffStrategy();
		}
		
//		System.out.print("=======================================");
//		System.out.println("-- Total Wind Consumers: \t" + windCustomersTotal + " / " + windCustomers + " \n");
//		tempTariff = findMyBestTariff(PowerType.WIND_PRODUCTION);
//		tempTariff = mutateProductionTariff(tempTariff,false,0,false);  	  				        			
//		
//		if(checkIfAlreadyExists(tempTariff)){
//			System.out.println("Not publishing");        			
//		}else {
//			tariffRepo.addSpecification(tempTariff);
//	        tariffCharges.put(tempTariff,0d);
//	        tariffCustomerCount.put(tempTariff,0);
//	        brokerContext.sendMessage(tempTariff);
//		}    			
//		System.out.print("=======================================");
//		System.out.println("-- Total Solar Consumers: \t" + solarCustomersTotal + " / " + solarCustomers + " \n");
//		tempTariff = findMyBestTariff(PowerType.SOLAR_PRODUCTION);
//		tempTariff = mutateProductionTariff(tempTariff,false,0,false);  	  				        			
//		
//		if(checkIfAlreadyExists(tempTariff)){
//			System.out.println("Not publishing");        			
//		}else {
//			tariffRepo.addSpecification(tempTariff);
//	        tariffCharges.put(tempTariff,0d);
//	        tariffCustomerCount.put(tempTariff,0);
//	        brokerContext.sendMessage(tempTariff);
//		}
//		System.out.println("=======================================");
		printCompTariffs(timeslotIndex);
		
		resetGlobalCounters();
  }
  	
  private void secondaryTariffStrategy() {
	  TariffSpecification tempTariff;
	  
	  if(!thermal_Passed) {
		  tempTariff = findMyBestTariff(PowerType.THERMAL_STORAGE_CONSUMPTION);
	
		  tempTariff = mutateStorageTariff(tempTariff,false,true);   				
			if(tempTariff != null) {
				tariffRepo.addSpecification(tempTariff);
				tariffCharges.put(tempTariff,0d);
				tariffCustomerCount.put(tempTariff,0);
	        	brokerContext.sendMessage(tempTariff);	
			}			
	  }
	  
	  if(!solar_Passed) {
		  //publish new better solar tariff
		  tempTariff = findMyBestProdTariff(PowerType.SOLAR_PRODUCTION);
		  System.out.print("best Solar tariff");
		  printTariff(tempTariff);
		  tempTariff = mutateProductionTariff(tempTariff,false,true);
		  
		  if(tempTariff != null) {
			  tariffRepo.addSpecification(tempTariff);
			  tariffCharges.put(tempTariff,0d);
			  tariffCustomerCount.put(tempTariff,0);
			  brokerContext.sendMessage(tempTariff);	
		  }	
		  
	  }else if(calculatePercentage(solarCustomers, solarCustomersTotal) > 75) {
		  //revoke cheapest energy tariff 
		  tempTariff = findMyBestProdTariff(PowerType.SOLAR_PRODUCTION);
		  if(remainingActiveTariff(PowerType.SOLAR_PRODUCTION) > 1) {
				tariffCharges.remove(tempTariff);
				tariffCustomerCount.remove(tempTariff);
		        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), tempTariff);
		        brokerContext.sendMessage(revoke);
		  }
		  
	  }
	  
	  if(!wind_Passed) {
		  //publish new better wind tariff
		  tempTariff = findMyBestProdTariff(PowerType.WIND_PRODUCTION);
		  System.out.print("best Wind tariff");
		  printTariff(tempTariff);
		  tempTariff = mutateProductionTariff(tempTariff,false,true);
		  
		  if(tempTariff != null) {
			  tariffRepo.addSpecification(tempTariff);
			  tariffCharges.put(tempTariff,0d);
			  tariffCustomerCount.put(tempTariff,0);
			  brokerContext.sendMessage(tempTariff);	
		  }			  
	  }else if(calculatePercentage(windCustomers, windCustomersTotal) > 75) {
		  //revoke cheapest energy tariff 
		  tempTariff = findMyBestProdTariff(PowerType.WIND_PRODUCTION);
		  if(remainingActiveTariff(PowerType.WIND_PRODUCTION) > 1) {
				tariffCharges.remove(tempTariff);
				tariffCustomerCount.remove(tempTariff);
		        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), tempTariff);
		        brokerContext.sendMessage(revoke);
		  }
	  }
	  
	  secondaryTariffEn = false;
	  thermal_Passed = false;
	  solar_Passed = false;
	  wind_Passed = false;
  }
  
  private void setSecondaryTariffsEnablers() {
	  
	  if(calculatePercentage(solarCustomers, solarCustomersTotal) > 50) {
		  solar_Passed = true;
	  }
	  if(calculatePercentage(windCustomers, windCustomersTotal) > 30) {
		  wind_Passed = true;
	  }
	  if(calculatePercentage(tHCCustomers, thermalCustomersTotal) > 40) {
		  thermal_Passed = true;
	  }
  }
    
  private void removeCustomer(TariffTransaction ttx) {
	  CustomerUsage temp = null;
	  for(CustomerUsage c : subscriptionList) {
		  if(c.getCustomerName().equals(ttx.getCustomerInfo().getName())) {
			  c.setCount(c.getCount() - ttx.getCustomerCount());
			  
			  if(c.getCount() > 0) {
				  return;
			  }
			  temp = c;
		  }
	  }
	  
	  if (temp != null) {
		  subscriptionList.remove(temp);
		  return;
	  }
	  
	  System.out.println("Customer Not FOUND!!!!");
  }
  
  private void addCustomer(TariffTransaction ttx) {
	  for(CustomerUsage c : subscriptionList) {
		  if(c.getCustomerName().equals(ttx.getCustomerInfo().getName())) {
			  c.setCount(c.getCount() + ttx.getCustomerCount());
			  return;
		  }
	  }
	  subscriptionList.add(new CustomerUsage(ttx.getCustomerInfo().getName(), 0, ttx.getCustomerCount(),
			  				ttx.getCustomerInfo().getPopulation(), ttx.getCustomerInfo().isMultiContracting()));	    
  }
  
  private void setUpTrainingBatch(int timeslot) {
	  trainingBatch.clear();
	  int t = timeslot;
	  for(int i = 0 ; i < Parameters.batchSize ; i++) {
		  t++;
		  trainingBatch.add(new TimeslotUsage(t));
	  }
  }
  
  public void setBatchWeather(WeatherData w) {
	  
      for ( TimeslotUsage t : trainingBatch) {
    	  if(t.getTimeslot() == w.getTimeslot()) {
    		  t.setWeather(w);    		  
    		  return;
    	  }
      }
      System.out.println("setbatch weather NOT FOUND!!!!!!!!!");
  }
  
  private void resetGlobalCounters() {
		totalEnergyused = 0;
		marketManager.setTotalBalancingEnergy(0);
		marketManager.setTotalDistributionEnergy(0);
		marketManager.setWholesaleEnergy(0);
		marketManager.setBalancingCosts(0);
		marketManager.setDistributionCosts(0);
		marketManager.setWholesaleCosts(0);
		consumptionCustomersTotal = 0;
		solarCustomersTotal = 0;
		thermalCustomersTotal = 0;
		windCustomersTotal = 0;
		totalProfits = 0;
		
		balancingCosts = 0;
		balancingEnergy = 0;
		    		
		for( TariffSpecification spec : tariffCharges.keySet()) {
			tariffCharges.put(spec, 0d);
		}
  }
  
//  private void revokeProductionTariff(TariffSpecification spec) {	
//  		TariffSpecification m = findBestCompProductionTariff(spec.getPowerType()); 
//		double enemyBestRate = calculateAvgProdRate(m,calculateAvgRate(m, false));  
//		double myRate = calculateAvgProdRate(spec,calculateAvgRate(spec, false));     				
//		int activeTariffs = remainingActiveTariff(spec.getPowerType());
//		
//		if ( params.productionTariffsEnabled == 0) {
//			System.out.println("Revoked");
//	        
//			tariffCharges.remove(spec);
//			tariffCustomerCount.remove(spec);
//	        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
//	        brokerContext.sendMessage(revoke);
//	        return;
//		}
//		
//		if((myRate - enemyBestRate) > 0.02  && activeTariffs != 1) {
//			System.out.println("Revoked");
//	        
//			tariffCharges.remove(spec);
//			tariffCustomerCount.remove(spec);
//	        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
//	        brokerContext.sendMessage(revoke);
//	        
//	        //check that there is always a better tariff available
//			TariffSpecification tempTariff = mutateProductionTariff(spec,false,0,false);  				
//			        				
//			if(!checkIfAlreadyExists(tempTariff)) {        					                			                	
//				tariffRepo.addSpecification(tempTariff);
//				tariffCharges.put(tempTariff,0d);
//				tariffCustomerCount.put(tempTariff,0);
//				brokerContext.sendMessage(tempTariff);
//			}else {
//				System.out.println("Not publishing");
//			}            	        
//		}
//  }
  
  private void revokeConsumptionTariff(TariffSpecification spec) {
		double enemyBestRate = findBestCompetitiveTariff(spec.getPowerType(),false);
		double myRate = calculateAvgRate(spec, false);     				
		int activeTariffs = remainingActiveTariff(spec.getPowerType());
		
		if((myRate - enemyBestRate) > 0.025  && enemyBestRate != -1 && activeTariffs != 1) {
			System.out.println("Revoked: " + spec.getId());
	        // revoke the old one
			tariffCharges.remove(spec);
			tariffCustomerCount.remove(spec);
	        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
	        brokerContext.sendMessage(revoke);
	        
	        //check that there is always a better tariff available
			TariffSpecification tempTariff = mutateConsumptionTariff(spec,false,0,false);   				
			        				
			if(!checkIfAlreadyExists(tempTariff)) {        					                			                	
				tariffRepo.addSpecification(tempTariff);
				tariffCharges.put(tempTariff,0d);
				tariffCustomerCount.put(tempTariff,0);
				brokerContext.sendMessage(tempTariff);
			}else {
				System.out.println("Not publishing");
			}            	        
		}
  }
  
  private int publishNewConsumptionTariff() {

		double myBestRate = calculateAvgRate(findMyBestTariff(PowerType.CONSUMPTION),false);
		double enemyBestRate = findBestCompetitiveTariff(PowerType.CONSUMPTION,false);
		TariffSpecification tempTariff = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);

		if(calculatePercentage(consumptionCustomers,consumptionCustomersTotal) > params.CONS_COUNT_EQUAL_BOUND 
				&& (Math.abs(enemyBestRate - myBestRate) <0.01 || enemyBestRate == -1) 
				&& remainingActiveTariff(PowerType.CONSUMPTION ) !=  0){

			return -1;
		}
		

		tempTariff = mutateConsumptionTariff(tempTariff,false,0,false);   				
		
		if(checkIfAlreadyExists(tempTariff)){
			System.out.println("Not publishing");
			return -1;
		}
			
		//commit the new tariff
      tariffRepo.addSpecification(tempTariff);
      tariffCharges.put(tempTariff,0d);
      tariffCustomerCount.put(tempTariff,0);
      brokerContext.sendMessage(tempTariff);
      
      return 0;
  }
  
  /* This function dictates how our agent corresponds 
   * to the curent state of the market. This means it either 
   * publishes or revokes tariffs or do nothing.
   */
  private void consumptionTariffStrategy() {
	  TariffSpecification tempTariff;
	  double customerPercentage = calculatePercentage(consumptionCustomers, consumptionCustomersTotal) ;
	  double eb = findBestCompetitiveTariff(PowerType.CONSUMPTION,false);    	
	  System.out.println("Best Enemy AVG Cons: " + eb);
	  
      //when we have  the monopoly revoke all tariffs that are below LowerBound
      if(customerPercentage > params.CONS_COUNT_UPPER_BOUND  ) {
      	ArrayList<TariffSpecification> list = new ArrayList<TariffSpecification>();
      	double enemyBestRate = findBestCompetitiveTariff(PowerType.CONSUMPTION,false);
      	for (TariffSpecification t : tariffCharges.keySet()) {
      		if (t.getPowerType() == PowerType.CONSUMPTION && calculateAvgRate(t, false) > LowerBound ||
      			((-calculateAvgRate(t, false) + enemyBestRate) < -0.0025  && enemyBestRate != -1 && PowerType.CONSUMPTION == t.getPowerType())) {
  				System.out.println("Revoking under Bound Tariff");
  			
  				list.add(t);
  				tariffCustomerCount.remove(t);
  				TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), t);
      	        brokerContext.sendMessage(revoke);      	        
      		}
      	}
      	for(TariffSpecification t : list) {
      		tariffCharges.remove(t);
      	}
      }
		
      boolean publishTariff = true;
		//when we have  the monopoly revoke the cheapest tariff

      if ( customerPercentage > params.CONS_COUNT_MIDDLE_BOUND + middleBoundOffset  ) {
			
			TariffSpecification spec = findMyBestTariff(PowerType.CONSUMPTION);
			if (spec != null ) {
//				System.out.println("-- " + calculateAvgRate(spec, false) + "  |  -- " +  findBestCompetitiveTariff(PowerType.CONSUMPTION, false ));
				if ((-calculateAvgRate(spec, false) + findBestCompetitiveTariff(PowerType.CONSUMPTION, false )) < -0.0025){
					System.out.println("Revoking Cheapest Tariff");
					
					tariffCharges.remove(spec);
					tariffCustomerCount.remove(spec);
	    	        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
	    	        brokerContext.sendMessage(revoke);
	   	        
	    	        //check that there is always a better tariff available
	    	        tempTariff = mutateConsumptionTariff(spec,false,calculateAvgRate(spec, false),false);   								
					
					if(!checkIfAlreadyExists(tempTariff)) {				
	        			//commit the new tariff
	        	        tariffRepo.addSpecification(tempTariff);
	        	        tariffCharges.put(tempTariff,0d);
	        	        tariffCustomerCount.put(tempTariff,0);
	        	        brokerContext.sendMessage(tempTariff);
					}else {
						System.out.println("Not publishing");
					}
				}else {
					if ((-calculateAvgRate(spec, false) + findBestCompetitiveTariff(PowerType.CONSUMPTION, false )) > 0.0075) {
						publishTariff = true;							
					}else {
						publishTariff = false;
					}					
				}
			}
      }
      
		
      if( customerPercentage < params.CONS_COUNT_LOWER_BOUND + lowerBoundOffset  ) {
//		  System.out.println("Under 45% Bound:");
    	  double enemyBestRate = findBestCompetitiveTariff(PowerType.CONSUMPTION,false);    
    	  double multiplier = 1;
    	  if(state == State.LOW_PERCENTAGE) {
    		  multiplier = 2;
    	  }
//		  System.out.println("Best Enemy AVG: " + enemyBestRate);
			
    	  TariffSpecification spec = findMyBestTariff(PowerType.CONSUMPTION);
    	  if(calculateAvgRate(spec, false) > enemyBestRate) {
    		  tempTariff = mutateConsumptionTariff(spec,false,calculateAvgRate(spec, false),true);
    	  }else {
    		  tempTariff = mutateConsumptionTariff(spec,false,enemyBestRate,true);    				
    	  }
    	  
    	  if(!checkIfAlreadyExists(tempTariff) && ((-enemyBestRate + calculateAvgRate(tempTariff, false )) < 0.01 * multiplier)) {		
    		  publishTariff = false;        			
    		  tariffRepo.addSpecification(tempTariff);
    		  tariffCharges.put(tempTariff,0d);
    		  tariffCustomerCount.put(tempTariff,0);
    		  brokerContext.sendMessage(tempTariff);
    	  }else {
    		  System.out.println("Not publishing");
    	  }	           											
      }
		
      if(publishTariff) {
    	  publishNewConsumptionTariff();
      } 		
		

  	}

  private void determineState() {
	  double customerPercentage = calculatePercentage(consumptionCustomers, consumptionCustomersTotal) ;
      if (customerPercentage > 50) {
    	  state = State.NORMAL;
    	  state_duration = 0;
    	  return;
      }
      
      if( customerPercentage < params.CONS_COUNT_LOWEST_BOUND + lowerBoundOffset  ) {
    	  if(state == State.NORMAL) {
    		  state_duration += Parameters.reevaluationCons;  
    		  System.out.println("UNDER 30%: " + state_duration + " timeslots");
    		  if(state_duration > params.STATE_CHANGE_INTERVAL) {
    			  state = State.LOW_PERCENTAGE;    			  
    		  }
    	  }    	  
      }
      if(state != State.NORMAL) {
			System.out.println("-State : \t" + state.toString());	
		}
  }
  
  private void calculateCustomerCounts() {
	  for (TariffSpecification spec : customerSubscriptions.keySet()) {
			if(tariffCharges.get(spec) == null) {
				continue;
			}    				    			
			
			printCustomerInfo(spec);
	  }
  }

private int remainingActiveTariff(PowerType pt) {
	  int counter = 0;
	  for(TariffSpecification t : tariffCharges.keySet()) {
		  if(t.getPowerType() == pt)
			  counter ++;
	  }
	  
	  return counter;
  }
  
  private boolean checkIfAlreadyExists(TariffSpecification spec) {
	  double avgrate = calculateAvgRate(spec, false);
	  double multiplier = 1;
	  if(state == State.LOW_PERCENTAGE) {
		  multiplier = 0.5;
	  }
	  
	  for (TariffSpecification t : tariffCharges.keySet())
	  {
		  //(tariffCharges.get(t) <= 1 && tariffCharges.get(t) >= -1) ||
		  if ( t.getPowerType() != spec.getPowerType())
			  continue;
		  
		  if (Math.abs(avgrate - calculateAvgRate(t, false)) < 0.005*multiplier && spec.getPowerType() == PowerType.CONSUMPTION)
			  return true;
		  
		  if (Math.abs(avgrate - calculateAvgRate(t, false)) < 0.0035 && spec.getPowerType().isProduction())
			  return true;		
		  
		  if (Math.abs(avgrate - calculateAvgRate(t, false)) < 0.0035 && spec.getPowerType() == PowerType.THERMAL_STORAGE_CONSUMPTION)
			  return true;	
	  }	  
	  return false;
  }
  
  private double calculatePercentage(double total,double current) {
	  	  return (100*current)/total;
  }
  
  //calculate capacity fees
  private void calcCapacityFees(int timeslotIndex) {
	  
	  
	  double realThreshold = 0;
	  if(timeslotIndex == 361) {
		  for(int i = 0; i<336;i++) {
			  totalDemand[i] = -bootsrapUsage[i];
		  }
		  totalDemandLength = 336;
	  }
	  
	  if((timeslotIndex - 1 - 360) % 168 == 0 && timeslotIndex != 361) {
		  peakDemand[0] = 0;
		  peakDemand[1] = 0;
		  peakDemand[2] = 0;
		  
	  }
	  
	  if(timeslotIndex >= 360) {
		  DistributionReport dr = contextManager.getReport();
		  double demand = dr.getTotalConsumption() - dr.getTotalProduction(),max = -1;
		  totalDemand[totalDemandLength] = demand;
		  double [] d ;
		  d = peakDemand.clone();
		  Arrays.sort(d);
		  
		  if(demand > d[0]) {
		  		max = d[0];
		  }
				  			    	
		  if(max != -1) {
			  for(int i = 0; i<3;i++) {
				  if(max == peakDemand[i]) {
					  peakDemand[i] = demand;
					  peakDemandTS[i] = dr.getTimeslot();
					  break;
				  }
			  }
		  }
		  totalDemandLength ++;
	  }
	  
	  if((timeslotIndex-360) % 168 == 0 && timeslotIndex != 360) {
		  double[] d = new double[2];
		  d = calcDemandMeanDeviation();
		  double threshold = 0;
		  
		  if(timeslotIndex == 360+168) {
			  if(marketManager.getCapacityFees()[0] != null) {
				  threshold =  marketManager.getCapacityFees()[0].getThreshold();
				  if(d[1] == 0)
					  d[1] = 13000;
				  gamaParameter = ( threshold - d[0])/d[1];
			  }
		  }
		  if(marketManager.getCapacityFees()[0] != null)
			  realThreshold = marketManager.getCapacityFees()[0].getThreshold();
		  
		  threshold = d[0] + gamaParameter*d[1];
		  double fees = 0;
		  System.out.printf("Mean: %.2f \t Deviation: %.2f \t Threshold Prediction: %.2f\n",d[0],d[1],threshold);		
		 
		  double mineEnergy = 1 , total = 0,totalFeesMine = 0;
		  for(int i = 0; i<3;i++) {
			  mineEnergy = 1;
			  System.out.printf("Peak: %.2f KWh \t Diff: %.2f KWh \tTS:%4d ",peakDemand[i],peakDemand[i]-realThreshold,peakDemandTS[i]);
			  if(peakDemand[i] > realThreshold) {
				  for(int j = 0 ;j<3;j++) {
					  if(marketManager.getCapacityFees()[j] != null) { 					
						  if(marketManager.getCapacityFees()[j].getPeakTimeslot() == peakDemandTS[i]) {
							  mineEnergy = marketManager.getCapacityFees()[j].getKWh();
							  totalFeesMine += marketManager.getCapacityFees()[j].getCharge();
							  break;
						  }
					  }
//					  else {
//						  mineEnergy = 0;
//					  }
				  }
				  if(mineEnergy == 1) {
					  System.out.println(" ");
					  break;
				  }  
				  //total energy of all brokers at that timeslot
				  total =  (totalEnergyUsed[peakDemandTS[i]]*(peakDemand[i]-realThreshold))/mineEnergy;
				  System.out.printf("\tFees: % 10.2f € \t Total Energy of all brokers: % .2f \n" , 18*(peakDemand[i]-realThreshold),total);
				  fees += 18*(peakDemand[i]-realThreshold);
			  }			  
		  }
		  System.out.printf("Total capacity fees: %.2f € \t Total Mine: %.2f €\t Threshold: %.2f\n",fees,totalFeesMine,realThreshold);
		  
		  if(Math.abs(totalFeesMine) < Parameters.LowerBoundChangerFees) {
			  LowerBound += 0.005; 			  
		  }else {
			  LowerBound -= 0.005;			  
			  if(LowerBound < Parameters.LowerBoundStatic && timeslotIndex < 1500) {
				  LowerBound = Parameters.LowerBoundStatic; 
			  }			  	
		  }
		  
		  ctx = new AnnotationConfigApplicationContext(Parameters.class);
		  params = ctx.getBean(Parameters.class);
		  LowerBoundABS = params.LowerBoundStaticAbsolute;
		  
		  if(timeslotIndex > 1500) {
			  LowerBound -= 0.005;
			  LowerBoundABS -= 0.005;
			  middleBoundOffset = - 7.5;
			  lowerBoundOffset = - 10;
		  }
  
		  System.out.printf("LowerBoundABS: %.3f \tLower Bound: %.3f \t Upper Bound: %.3f \t TS: %d\n",LowerBoundABS,LowerBound,UpperBound,timeslotIndex);		  
		  System.out.println(params.CONS_COUNT_LOWER_BOUND + " Offset: " + lowerBoundOffset);
		  System.out.println(params.CONS_COUNT_MIDDLE_BOUND + " Offset: " + middleBoundOffset);
		  System.out.println(params.CONS_COUNT_UPPER_BOUND);
		  System.out.printf("Total Cons Profits: \t % 11.2f\n",totalConsumptionProfits);
		  System.out.printf("Total Wind Profits: \t % 11.2f\n",totalWindProfits);
		  System.out.printf("Total Solar Profits: \t % 11.2f\n",totalSolarProfits);
		  System.out.printf("Total Thermal Profits: \t % 11.2f\n",totalThermalProfits);
		  System.out.printf("Total Balancing Costs: \t % 11.2f\n",totalBalancingCosts);
		  System.out.printf("Priod Balancing Costs: \t % 11.2f\n",totalAssessmentBalancingCosts);
		  totalAssessmentBalancingCosts = 0;
		  System.out.println(" ");
	  }

	  marketManager.resetCapacityFees();
  }
  
  //calculate  capacity fees' mean and deviation
  private double[] calcDemandMeanDeviation() {
	  double mean = 0;
	  double deviation = 0;
	  
	  for(int i=0 ; i < totalDemandLength ;i++) {
		  mean += totalDemand[i];		  
	  }
	  mean = mean / totalDemandLength;
	  
	  for(int i=0 ; i < totalDemandLength ;i++) {
		  deviation += Math.pow(totalDemand[i] - mean ,2);		  
	  }
	  deviation = Math.sqrt(deviation/totalDemandLength);
	  double[] d = new double[2];
	  d[0] = mean;
	  d[1] = deviation;
	  return d;
  }
  
  //calculate avg rate of a tariff weighted or normal average
  private double calculateAvgRate(TariffSpecification t,boolean weighted) {
	  double[][] rates = new double[7][24];
	  for (int i = 0; i < 7; i++) {
		  for (int j = 0; j < 24; j++) {
			  rates[i][j] = -1;
		  }
	  }

	  double sum = 0;
	  if(t == null) 
		  return 0;
	  
	  if(t.getRates() == null) 
		  return 0;
	  
	  if(params.newRateCalculatorEnabled == 0) {
		  for (Rate r : t.getRates()) {
				sum += r.getMinValue();
		  }
			  
			  int n1 = t.getRates().size();
			  return sum / n1;
	  }

//	  try {
		  for (Rate r : t.getRates()) {
			  if (r.getWeeklyBegin() == -1){
				  for (int i = 1; i < 7 + 1; i++) {
					  if (r.getDailyBegin() == -1){
						  for (int j = 0; j < 24; j++) {
							  rates[i-1][j] = r.getMinValue();
						  }
					  }
					  for (int j = r.getDailyBegin(); j < r.getDailyEnd() ; j++) {
						  rates[i-1][j] = r.getMinValue();
					  }
				  }

			  }else {
				  for (int i = r.getWeeklyBegin(); i < r.getWeeklyEnd() + 1; i++) {
					  if (r.getDailyBegin() == -1){
						  for (int j = 0; j < 24; j++) {
							  rates[i-1][j] = r.getMinValue();
						  }
					  }
					  for (int j = r.getDailyBegin(); j < r.getDailyEnd() ; j++) {
						  rates[i-1][j] = r.getMinValue();
					  }
				  }  
			  }
			  
		  }		  
		  int counter = 0;
		  for (int i = 1; i < 8; i++) {
			  for (int j = 0; j < 24; j++) {			  
				  if(rates[i-1][j] == -1 ) {
					  rates[i-1][j] = sum / counter;				  
				  }
				  counter ++;
//				  System.out.printf(" %.2f", rates[i-1][j]);
				  sum += rates[i-1][j];
			  }
		  }
//		  System.out.println("Avg " + sum/168);
		  if(Double.isFinite(sum/168)) {
			  return sum/168;  
		  }else {
			  for (Rate r : t.getRates()) {
					sum += r.getMinValue();
			  }
				  
				  int n1 = t.getRates().size();
				  return sum / n1;
		  }
		  
//	  }catch (Exception e) {
//
//		  System.out.println("Exception in Calc AVG");
//	  }

//	  if (t.getRates().get(0).getDailyBegin() == -1) {
//		  return t.getRates().get(0).getMinValue() / weightWd[0];
//	  }
//	  
//	  if(t.getRates().get(0).getWeeklyBegin() < 6) {		
//		  return t.getRates().get(0).getMinValue() / weightWd[t.getRates().get(0).getDailyBegin()];
//	  } else {
//		  return t.getRates().get(0).getMinValue() / weightWe[t.getRates().get(0).getDailyBegin()];
//	  }
		  	  	  
	  
  }
  /*
  //calculate the weights for the tou rates
  private void calculateWeights() {
	  
	  double clearingPriceWd[] = marketManager.getAvgClearingPriceWd();
	  double clearingPriceWe[] = marketManager.getAvgClearingPriceWe();
	  double netUsageWd[] = marketManager.getAvgNetusageWd();
	  double netUsageWe[] = marketManager.getAvgNetusageWe();
	  double wWe[] = new double[24];
	  double wWd[] = new double[24];
	  double sumWe = 0, sumWd = 0;
	  //TOU formula
	  for(int i = 0; i<24 ; i++) {
		  wWd[i] = clearingPriceWd[i]*netUsageWd[i];
		  wWe[i] = clearingPriceWe[i]*netUsageWe[i];
		  
		  sumWe += wWe[i];
		  sumWd += wWd[i];  
	  }
	  //normalize
	  for(int i = 0; i<24 ; i++) {
		  wWd[i] *= 24/sumWd;
		  wWe[i] *= 24/sumWe;
	  }  
	  weightWe = wWe;
	  weightWd = wWd;
  }
  */
  
  //calculate the weights for the tou rates using predictor
  private void calculateWeightsPredictor() {
	  
	  double clearingPriceWd[] = marketManager.getAvgClearingPriceWd();
	  double clearingPriceWe[] = marketManager.getAvgClearingPriceWe();
	  double netUsageWd[] = marketManager.getNetUsagePredictorWd();
	  double netUsageWe[] = marketManager.getNetUsagePredictorWe();
	  double wWe[] = new double[24];
	  double wWd[] = new double[24];
	  double sumWe = 0, sumWd = 0;
	  
//	  for(int i = 0; i<24 ;i++) {
//		  System.out.printf(" %.2f |",netUsageWd[i]/1000);
//	  }
//	  for(int i = 0; i<24 ;i++) {
//		  System.out.printf(" %.2f |",netUsageWe[i]/1000);
//	  }
//	  System.out.println("");
//	  for(int i = 0; i<24 ;i++) {
//		  System.out.printf(" %.2f |",clearingPriceWd[i]);
//	  }
//	  for(int i = 0; i<24 ;i++) {
//		  System.out.printf(" %.2f |",clearingPriceWe[i]);
//	  }
//	  System.out.println("");
	  
	  //TOU formula
	  for(int i = 0; i<24 ; i++) {
		  if(netUsageWd[i] == 0) {
			  netUsageWd[i] = 30000;  
		  }
		  if(netUsageWe[i] == 0) {
			  netUsageWe[i] = 30000;  
		  }
		  
		  wWd[i] = clearingPriceWd[i]*netUsageWd[i];
		  wWe[i] = clearingPriceWe[i]*netUsageWe[i];
		  
		  sumWe += wWe[i];
		  sumWd += wWd[i];  
	  }
	  //normalize
	  for(int i = 0; i<24 ; i++) {
		  wWd[i] *= 24/sumWd;
		  wWe[i] *= 24/sumWe;
	  }  
	  weightWe = wWe;
	  weightWd = wWd;
  }
  
  private double [] scaleDownRates(double[] weights,double avg) {
	  
	  boolean flag = true;
	  
	  for(int j = 0 ; j < weights.length ; j++) {
		  weights[j] = avg * weights[j];
	  }
	  
	  for(int i = 0 ; i < 5 && flag ; i++) {
		  flag = false;
		  for(int j = 0 ; j < weights.length ; j++) {
			  weights[j] =  weights[j] + (avg - weights[j]) / 2;
			  if(Math.abs(avg - weights[j]) > Parameters.InterRateSpread)
				  flag = true;
		  }
	  }
	  
	  return weights;
  }


//Function producing time of use rates for the given tariff
  private TariffSpecification produceTOURates(TariffSpecification t, double avg,double maxCurtailment){
	  
	  TariffSpecification spec = new TariffSpecification(t.getBroker(),t.getPowerType());
	  spec.withEarlyWithdrawPayment(t.getEarlyWithdrawPayment());
	  spec.withMinDuration(t.getMinDuration());
	  spec.withPeriodicPayment(t.getPeriodicPayment());
	  spec.withSignupPayment(t.getSignupPayment());
	  if(t.getExpiration() != null) {
		  spec.withExpiration(t.getExpiration());		  
	  }

	  double wWe[] = new double[24];
	  double wWd[] = new double[24];
	  
	  calculateWeightsPredictor();
	  wWe = weightWe;
	  wWd = weightWd;

	  wWe = scaleDownRates(wWe, avg);
	  wWd = scaleDownRates(wWd, avg);
	  
	  for(int i = 0; i < 24 ; i++) {
		  Rate r = new Rate();
		  r.withWeeklyBegin(1);
		  r.withWeeklyEnd(5);
		  r.withDailyBegin(i);
		  if(i != 23)
			  r.withDailyEnd(i+1);
		  else
			  r.withDailyEnd(0);
		  
		  r.withMinValue(wWe[i]);		
//		  r.withMinValue(avg);		

		  
		  r.withMaxCurtailment(maxCurtailment);
//		  System.out.println(avg*wWe[i]);
		  spec.addRate(r);
	  }
	  
	  for(int i = 0; i<24 ; i++) {
		  Rate r = new Rate();
		  r.withWeeklyBegin(6);
		  r.withWeeklyEnd(7);
		  r.withDailyBegin(i);
		  if(i != 23)
			  r.withDailyEnd(i+1);
		  else
			  r.withDailyEnd(0);
		  
		  r.withMinValue(wWd[i]);
//		  r.withMinValue(avg);	s
		  r.withMaxCurtailment(maxCurtailment);
//		  System.out.println(avg*wWd[i]);
		  spec.addRate(r);
	  }
	  return spec ;
  }
  
  private TariffSpecification mutateProductionTariff(TariffSpecification spec,boolean isInitial,boolean cheapen) {
	  Random rnd = new Random();
	  	  
	  double temp;
 	 
	  TariffSpecification newTariff = new TariffSpecification(brokerContext.getBroker(), spec.getPowerType());
	  	  
	  newTariff.withSignupPayment(0);
	  
	  newTariff.withEarlyWithdrawPayment( -2*rnd.nextDouble()*Parameters.Ebp - 5);
	  
	  newTariff.withMinDuration(Parameters.timeslotMS * (Parameters.reevaluationProduction - 2)*100);
	  
	  Rate r = new Rate();
	  
	  if(isInitial) {
		  double perPay = (rnd.nextInt(5) + 10.0) / 100.0; 	  
		  perPay = - 0.012 / perPay;	  
		  		 
		  newTariff.withPeriodicPayment(perPay);
		  
		  r.withMinValue(0.012 + 0.015 -perPay/20);
	  }else {
		  newTariff.withPeriodicPayment(spec.getPeriodicPayment());
		  if(cheapen) {
			  temp = calculateAvgRate(spec, false) + rnd.nextDouble()*Parameters.LowerEp + Parameters.LowerEpOffset*1.75 ;
			  if(temp > 0.05) {
				  temp = 0.05 + rnd.nextDouble()*Parameters.LowerEp;
			  }
		  }else {
			  temp = calculateAvgRate(spec, false) - rnd.nextDouble()*Parameters.LowerEp - Parameters.LowerEpOffset;
		  }
		  r.withMinValue(temp);
	  }
	  
	  newTariff.addRate(r);
	  	  
	  System.out.print("My NEW Tariff: ");
	  printTariff(newTariff);
	  
	  if(checkIfAlreadyExists(newTariff)) {
		  System.out.println("Already Exists");
		  return null;
	  }
	  
	  return newTariff;
  }
  
  //Default function to mutate a tariff 
  private TariffSpecification mutateStorageTariff(TariffSpecification spec,boolean isInitial,boolean cheapen) {
	  Random rnd = new Random();
	  
	  double eReg = Parameters.Ereg;
	  double temp;
 	 
	  TariffSpecification newTariff = new TariffSpecification(brokerContext.getBroker(), spec.getPowerType());
	  	  
	  newTariff.withSignupPayment(0);
	  
	  newTariff.withEarlyWithdrawPayment( - rnd.nextDouble()*10 - 10);
	  
	  newTariff.withMinDuration(Parameters.timeslotMS * (5*Parameters.reevaluationStorage-2));
	  
	  newTariff.withPeriodicPayment(0);
	  
	  if(isInitial) {
		  temp = -rnd.nextDouble()*0.015 - 0.09;  
	  }else {
		  if(cheapen) {
			  temp = calculateAvgRate(spec, false) + rnd.nextDouble()*Parameters.LowerEp + Parameters.LowerEpOffset*1.75 ;
			  if(temp > -0.0825) {
				  temp = -0.0825 + rnd.nextDouble()*Parameters.LowerEp;
			  }
		  }else {
			  temp = calculateAvgRate(spec, false) - rnd.nextDouble()*Parameters.LowerEp - Parameters.LowerEpOffset;
		  }
	  }
	  
	  newTariff = produceTOURates(newTariff,temp,0);	  
	  
	  double downReg,upReg;
	  //Mutate RegRates
	  
	  if(isInitial) {
		  downReg = -0.02 - eReg/10 + 2 * eReg/10 * rnd.nextDouble();
		  upReg = 0.1 - eReg + 2 * eReg * rnd.nextDouble();
		  if(downReg > 0) {
			  downReg = - downReg;
		  }
		  if(upReg < 0) {
			  upReg = - upReg;
		  }
	  }else {
		  downReg = spec.getRegulationRates().get(0).getDownRegulationPayment();
		  upReg = spec.getRegulationRates().get(0).getUpRegulationPayment();
	  }
	  
	  RegulationRate reg = new RegulationRate();
	  
	  reg.withDownRegulationPayment(downReg);
	  reg.withUpRegulationPayment(upReg);
	  
	  newTariff.addRate(reg);
	  
	  System.out.print("My NEW Tariff: ");
	  printTariff(newTariff);
	  if(checkIfAlreadyExists(newTariff)) {
		  System.out.println("Already Exists");
		  return null;
	  }
	  return newTariff;
  }
  
  private TariffSpecification mutateConsumptionTariff(TariffSpecification spec,boolean beginning,double avg,boolean mutateDown) {
	  
	  Random rnd = new Random();
	  double ep = Parameters.Ep;
	  double temp ;  	 
	  
	  if (spec == null) {
		  spec = new TariffSpecification(brokerContext.getBroker(),PowerType.CONSUMPTION);
	  }
	  
	  //Mutating Early withdrawal payment
 
	  if (bestEWP < -1) {
		  temp = Math.round(bestEWP) - 1;
	  } else {
		  temp = -rnd.nextDouble()*Parameters.Ebp - 4;
	  }
	  
	  if (temp < -Parameters.EwpBound ) {
		  spec.withEarlyWithdrawPayment(temp);  
	  }else {
		  spec.withEarlyWithdrawPayment(temp + rnd.nextDouble()*5);
	  }
	    	  
	  	  	  
	  
	  
	  spec.withMinDuration(Parameters.timeslotMS * (Parameters.reevaluationCons - 2)*375);
//	  spec.withMinDuration(0);
	  
	  //Mutating avg rate value
	  
	  if(!beginning) {

		  temp = findBestCompetitiveTariff(spec.getPowerType(),true);
		  ep = Parameters.LowerEp;
		  double roll = rnd.nextDouble();
		  boolean flag = false;
//		  System.out.println("Current minimum comp tariff avg: "+ temp);
		  if(temp == -1 ) { // || timeslotRepo.currentSerialNumber() < 370) { 
			  temp = 1.2*LowerBound; 
		  }else if( temp > LowerBound && roll > Parameters.LowerBoundRollChance) {
			  temp = LowerBound; 
		  }else if( temp > LowerBound && roll < Parameters.LowerBoundRollChance) {
			  flag = true;
		  }

		  roll = rnd.nextDouble();
		  
		  //when far from bound , double mutation step
//		  if(Math.abs(temp-LowerBound) > 0.05)
//			  ep = 4 * ep;

		  //always mutate down if avg is bigger than bound
		  if(roll < 0.85 || temp < LowerBound || flag) {
			  temp = temp + rnd.nextDouble()*ep + Parameters.LowerEpOffset;
		  }else {
			  temp = temp - rnd.nextDouble()*ep;
		  }
		  
		  if(avg != 0 ) {
			  if(mutateDown == true) {
				  temp = avg + rnd.nextDouble()*ep/2 + 0.005;
			  }else {
				  temp = avg - rnd.nextDouble()*ep/2 - 0.0065;  
			  }			  
		  }
		  
		  // check upper bound
		  if(UpperBound> temp) {
			  temp = UpperBound + rnd.nextDouble()*ep ;
//			  temp = Parameters.UpperBoundStatic + ep - 2*rnd.nextDouble()*ep;
		  }
		  
		  
		  
	  }else {
		  temp = Parameters.LowerBoundStatic - rnd.nextDouble()*0.01; 
	  }
	  
	  //if temp < lowerBound apply expiration
//	  if(temp > LowerBound + 2*Parameters.LowerEpOffset ) {
//		  Instant now = Instant.now();
////		  Instant now = timeslotRepo.currentTimeslot().getStartInstant();
//		  now = now.withMillis(now.getMillis() + Parameters.timeslotMS*0);
//		  spec = spec.withExpiration(now);
//	  }

//	  System.out.println(spec.getExpiration());
	  
//	  if(temp > LowerBoundABS) {
//		  temp = LowerBoundABS - rnd.nextDouble()* Parameters.LowerEpOffset;
//	  }

	  if(temp > LowerBoundABS) {
		  temp = LowerBoundABS - rnd.nextDouble()* Parameters.LowerEpOffset;
	  }
	  
	  spec = produceTOURates(spec,temp,0);
	  
	  spec.withPeriodicPayment(0);
	  System.out.print("My NEW Tariff: ");
	  printTariff(spec);	  	  
	  return spec;
  }
  

  private TariffSpecification mutateInterruptibleConsTariff(TariffSpecification spec) {
	  Random rnd = new Random();
	  System.out.println("Before Mutation---");

	  printTariff(spec);
	  double ep = Parameters.Ep;
	  double ebp = Parameters.Ebp;
//	  int ecl = Parameters.Ecl;
	  double temp ;
  	  
	  //Mutating signup payment
	  temp = spec.getSignupPayment();
	  if(temp == 0) {
		  temp = -ebp;
	  }
	  temp = temp + ebp +  (temp -  ebp  - (temp  + ebp ))*rnd.nextDouble();
//	  spec.withSignupPayment(temp);
	  
	  //Mutating Early withdrawal payment
	  temp = spec.getEarlyWithdrawPayment();
	  if(temp == 0) {
		  temp = -ebp;
	  }
	  temp = temp + ebp +  (temp -  ebp  - (temp  + ebp ))*rnd.nextDouble();
//	  spec.withEarlyWithdrawPayment(temp);
	  
	  //Mutating contract length
//	  int tmp = (int)spec.getMinDuration();
//	  if(tmp == 0) {
//		  tmp = rnd.nextInt(ecl);
//	  }
//	  if(ecl >= tmp) {
//		  ecl = tmp/2;
//	  }
//	  tmp = tmp - ecl + rnd.nextInt(2*ecl);
//	  spec.withMinDuration(ecl);
	  spec.withMinDuration(Parameters.timeslotMS * (Parameters.reevaluationInterruptible-2));
	  
	  //Mutating avg rate value and maxCurtailment
	  double a1 = 0;
	  double maxC = 0;
	  temp = 0;
	  if(spec.getRates().size() != 0) { 
		  for (Rate r : spec.getRates()) {
			a1 += r.getMinValue();
			maxC = r.getMaxCurtailment();
		  }
		  
		  int n1 = spec.getRates().size();
		  temp = a1 / n1;
	  }
	  
	  if(temp == 0) {
		  temp = -0.1;
		  maxC = 0.4;
	  }
	  
	  temp = temp * (1 - ep )+  (temp * (1 +  ep ) - temp * (1 - ep ))*rnd.nextDouble();
	  maxC = maxC * (1 - ep )+  (maxC * (1 +  ep ) - maxC * (1 - ep ))*rnd.nextDouble();
	  	  
	  spec = produceTOURates(spec,temp,maxC);
	  
	  spec.withPeriodicPayment(0);
	  printTariff(spec);
	  System.out.println("After Mutation---");
	  return spec;
  }
  
  
  private TariffSpecification findMyBestTariff(PowerType pt) {
	  
	  double min = -10000,tmp = 0;
	  TariffSpecification minTariff = null;

	  for(TariffSpecification t : tariffCharges.keySet()) {

		  if(t.getPowerType() != pt)
			  continue;

		  tmp = calculateAvgRate(t,false);	  		 
		  
		  if(tmp > min) {
			  min = tmp;
			  minTariff = t;
		  }
	  }

	  return minTariff;
  }
  
  private TariffSpecification findMyBestProdTariff(PowerType pt) {
  
	  double max = -10000;
	  double tmp = 0;
	  TariffSpecification maxTariff = null;

	  for (TariffSpecification t : tariffCharges.keySet()) {

		  if (t.getPowerType() != pt)
			  continue;		  
		  tmp = calculateAvgRate(t,false);	  		 
		  
		  if (tmp > max) {
			  max = tmp;
			  maxTariff = t;
		  }
	  }
	  return maxTariff;
  }
  
  private double findBestCompetitiveTariff(PowerType pt,boolean print_en) {
	  
	  bestEWP = 0;
	  double min = -10000,tmp = 0;
	  TariffSpecification minTariff = null;
	  List<TariffSpecification> l = competingTariffs.get(pt);
	  
	  if(pt == PowerType.THERMAL_STORAGE_CONSUMPTION) {
		  if(l != null)
			  l.addAll(competingTariffs.get(PowerType.STORAGE));
		  else
			  l = competingTariffs.get(PowerType.STORAGE);
	  }
	  
	  if(l == null)
		  return -1;
	  
	  for(TariffSpecification t : l) {
		  // check if tariff is valid()
		  if(t.getEarlyWithdrawPayment() + t.getSignupPayment() > 0 || t.getEarlyWithdrawPayment() + t.getSignupPayment() < -30) {
			  if(print_en)
				  System.out.println("Bait Tariff + " + t.getId());
			  continue;
		  }
		  tmp = calculateAvgRate(t,false);
		  
//		  if(t.getPeriodicPayment() < 5 *tmp ) {
//			  if(print_en)
//				  System.out.println("Bait Tariff + " + t.getId());
//			  continue;			  			  
//		  }
		  if (t.getPeriodicPayment() < 0 && t.getPeriodicPayment() > -2.5) {			  
			  tmp = tmp + t.getPeriodicPayment()/20- 0.015;
		  }

		  if(tmp > min) {
			  min = tmp;
			  minTariff = t;
		  }
	  }
	  
	  if(minTariff == null || min == -0.5)
		  return -1;
	  
	  bestEWP = minTariff.getEarlyWithdrawPayment();
	  return min;
  }
  
private TariffSpecification findBestCompProductionTariff(PowerType pt) {
	  	  
	  double max = -10000;
	  double tmp = 0;
	  TariffSpecification maxTariff = null;
	  
	  if(competingTariffs.get(pt) != null) {
		  for(TariffSpecification t : competingTariffs.get(pt)) {
			  // check if tariff is valid()
			  if(t.getEarlyWithdrawPayment() + t.getSignupPayment() > 0 ) {
				  System.out.println("Bait Tariff + " + t.getId());
				  continue;
			  }
			  
			  tmp = calculateAvgRate(t,false);		  
//			  if(t.getPeriodicPayment() < 5 *tmp ) {
//				  if(print_en)
//					  System.out.println("Bait Tariff + " + t.getId());
//				  continue;			  			  
//			  }  
			  if (t.getPeriodicPayment() < 0 && t.getPeriodicPayment() > -2.5) {			  
				  tmp = calculateAvgProdRate(t,tmp);	  
			  }

			  if(tmp > max) {
				  max = tmp;
				  maxTariff = t;
			  }
		  }
	  }
		  
	  if( competingTariffs.get(PowerType.PRODUCTION) == null)
		  return maxTariff;
	  
	  for(TariffSpecification t : competingTariffs.get(PowerType.PRODUCTION)) {
		  // check if tariff is valid()
		  if(t.getEarlyWithdrawPayment() + t.getSignupPayment() > 0 ) {
			  System.out.println("Bait Tariff + " + t.getId());
			  continue;
		  }
		  
		  tmp = calculateAvgRate(t,false);		  
//		  if(t.getPeriodicPayment() < 5 *tmp ) {
//			  if(print_en)
//				  System.out.println("Bait Tariff + " + t.getId());
//			  continue;			  			  
//		  }  
		  if (t.getPeriodicPayment() < 0 && t.getPeriodicPayment() > -2.5) {			  
			  tmp = calculateAvgProdRate(t,tmp);	  
		  }

		  if(tmp > max) {
			  max = tmp;
			  maxTariff = t;
		  }
	  }
	  
	  return maxTariff;
  }
	private double calculateAvgProdRate(TariffSpecification t , double avg) {		
		if(t == null) {
			System.out.println("CHECKKKK");
			return avg;
		}
		double tmp = avg + t.getPeriodicPayment()/20- 0.015;			  
		return tmp;
	}
	
	public void setBalancingCosts(double imbalance) {
		totalAssessmentBalancingCosts += imbalance;
		totalBalancingCosts += imbalance;
	}

  
  private void printCustomerInfo(TariffSpecification spec) {	  	 
		Map<CustomerInfo, CustomerRecord> m = customerSubscriptions.get(spec);
		int count = 0;
		double charge = 0;
		
		for(CustomerRecord r : m.values()) {
			count += r.subscribedPopulation;
		}
		charge = tariffCharges.get(spec);
		System.out.printf("-Profit: % 9.2f ",charge);	
		
	    switch(spec.getPowerType().toString()) {
    	case "CONSUMPTION":
    		System.out.print( "< "+count+" /"+ consumptionCustomers +" >  +/- "+ (count-tariffCustomerCount.get(spec)) +"\\");    		    		
    		tariffCustomerCount.put(spec,count);
    		consumptionCustomersTotal += count;
    		totalConsumptionProfits += charge;
    		break;
    	case "STORAGE":
    		System.out.print( "< "+count+" / "+ storageCustomers +" > +/- "+ (count-tariffCustomerCount.get(spec)) +" \\ ");
    		tariffCustomerCount.put(spec,count);
    		break;
    	case "INTERRUPTIBLE_CONSUMPTION":
    		System.out.print( "< "+count+" / "+ interruptibleCustomers+" > +/- "+ (count-interruptibleCustomersPrev) +" \\ ");
    		interruptibleCustomersPrev = count;
    		break;
    	case "THERMAL_STORAGE_CONSUMPTION":
    		System.out.print( "< "+count+" / "+ tHCCustomers +" > +/- "+ (count-tariffCustomerCount.get(spec)) +" \\ ");
    		tariffCustomerCount.put(spec,count);
    		thermalCustomersTotal += count;
    		totalThermalProfits += charge;
    		break;
    	case "SOLAR_PRODUCTION":
    		System.out.print( "< "+count+" / "+ solarCustomers +" > +/- "+ (count-tariffCustomerCount.get(spec)) +" \\ ");
    		tariffCustomerCount.put(spec,count);
    		solarCustomersTotal += count;
    		totalSolarProfits += charge;
    		break;
    	case "WIND_PRODUCTION":
    		System.out.print( "< "+count+" / "+ windCustomers +" > +/- "+ (count-tariffCustomerCount.get(spec)) +" \\ ");
    		tariffCustomerCount.put(spec,count);
    		windCustomersTotal += count;
    		totalWindProfits += charge;
    		break;
    	case "BATTERY_STORAGE":
    		System.out.print( "< "+count+" / "+ BatteryCustomers +" > +/- "+ (count-tariffCustomerCount.get(spec)) +" \\ ");
    		tariffCustomerCount.put(spec,count);
    		break;
    	case "ELECTRIC_VEHICLE":
    		System.out.print( "< "+count+" / "+ eVCustomers +" > +/- "+ (count-tariffCustomerCount.get(spec)) +" \\ ");
    		tariffCustomerCount.put(spec,count);
    		break;
    	default:
    		System.out.print( "< "+count+" / "+ otherProducers +" > +/- "+ (count-tariffCustomerCount.get(spec)) +" \\ ");
    		tariffCustomerCount.put(spec,count);
    		
	    }
//	    System.out.print("\t");
	    
		printTariff(spec);
  }
  
  public void printTariff(TariffSpecification t) {
	  
	  int n1 = -1,n2 = -1;
	  double a1 = 0;
//	  long n4 = 0;
	  String s = "0";
	  if(t.getRates() != null) {
		  for (Rate r : t.getRates()) {
			a1 += r.getMinValue();
		  }
		  
		  n1 = t.getRates().size();
		  a1 = a1 / n1;
	  }
	  if(t.getRegulationRates() != null) {
		  n2 = t.getRegulationRates().size();
	  }
	  if(t.getExpiration() != null) {
//		  n4 = t.getExpiration().getMillis()/1000;
		  s = t.getExpiration().toString();
	  }
	  
      System.out.printf(" \tID:%10d %30s, %20s| Pr: % 5.3f Sup:% 7.2f Ewp:% 7.2f CL:%10d ts | R:%3d Avg:% 3.4f RR:%3d |"
      		+ "Exp: %s|\t ",
      t.getId(), t.getPowerType().toString(),t.getBroker().getUsername(),t.getPeriodicPayment(),
      t.getSignupPayment(),t.getEarlyWithdrawPayment(),t.getMinDuration()/Parameters.timeslotMS,n1,a1,n2,s);
      
//      System.out.printf(" \tID:%10d %30s, %20s| Prdic: % 5.4f Sgup:% 7.2f Ewp:% 7.2f CL:%10d ts | R:%3d Avg:% 3.4f N: % 3.4f  RegR:%3d |"
//        		+ "Exp: %s|\t ",
//        t.getId(), t.getPowerType().toString(),t.getBroker().getUsername(),t.getPeriodicPayment(),
//        t.getSignupPayment(),t.getEarlyWithdrawPayment(),t.getMinDuration()/Parameters.timeslotMS,n1,a1,calculateAvgRate(t, false),n2,s);
//        
      
      if(params.printRatesEnabled == 1) {
          if(t.getRegulationRates().size() >= 1) {
        	  for(RegulationRate r : t.getRegulationRates())
        		  System.out.printf("UpReg % .4f DownReg % .4f| ",r.getUpRegulationPayment(),r.getDownRegulationPayment());
          }
      }

//      if (t.getBroker().getUsername().equals("a")) {
//    	  if(t.getPowerType() == PowerType.CONSUMPTION) {
//        	  double we[] = new double[24];
//        	  double wd[] = new double[24];
//        	  double sume = 0;
//        	  double sumd = 0;
//        	  
//        	  if(t.getRates().size() == 48) {
//            	  for(Rate r : t.getRates()) {
//            		  if(r.getWeeklyBegin()<6) {
//            			  we[r.getDailyBegin()] = r.getMinValue();
//            		  }else {
//            			  wd[r.getDailyBegin()] = r.getMinValue();
//            		  }
//            	  }
//            	  for(int i=0 ;i<24;i++) {
//            		  System.out.printf(" % 1.3f|",we[i]);
//            		  sume += we[i];
//            	  }
////            	  System.out.print("\t | ");
////            	  for(int i=0 ;i<24;i++) {
////            		  System.out.printf(" % 1.5f|",we[i]/sume);            		  
////            	  }
//            	  for(int i=0 ;i<24;i++) {
//            		  System.out.printf(" % 1.3f|",wd[i]);
//            		  sumd += wd[i];
//            	  }
////            	  System.out.print("\t | ");
////            	  for(int i=0 ;i<24;i++) {
////            		  System.out.printf(" % 1.5f|",wd[i]/sumd);            		  
////            	  }
//        	  }
//        	  System.out.println("");
//        	  return ;
//    	  }
//      }
      
      if(params.printRatesEnabled == 1) {
          
          if(t.getPowerType() == PowerType.CONSUMPTION) {
        	  double we[] = new double[24];
        	  double wd[] = new double[24];
        	  
        	  if(t.getRates().size() == 48) {
            	  for(Rate r : t.getRates()) {
            		  if(r.getWeeklyBegin()<6) {
            			  we[r.getDailyBegin()] = r.getMinValue();
            		  }else {
            			  wd[r.getDailyBegin()] = r.getMinValue();
            		  }
            	  }
            	  for(int i=0 ;i<24;i++) {
            		  System.out.printf(" % 1.3f|",we[i]);
            	  }
            	  for(int i=0 ;i<24;i++) {
            		  System.out.printf(" % 1.3f|",wd[i]);
            	  }
        	  }else if (t.getRates().size() != 96) {
        		  for(Rate r : t.getRates()) {
        			  System.out.printf("%d %d, %2d %2d, % 1.3f|",r.getWeeklyBegin(),r.getWeeklyEnd(),r.getDailyBegin(),r.getDailyEnd(),r.getMinValue());
        		  }		  
        	  }
          }else {
    		  for(Rate r : t.getRates()) {
    			  System.out.printf("%d %d, %2d %2d, % 1.3f|",r.getWeeklyBegin(),r.getWeeklyEnd(),r.getDailyBegin(),r.getDailyEnd(),r.getMinValue());
    		  }	
          }
      }
      
      
      System.out.println(" ");
      if(t.getSignupPayment() < 0) {
    	  System.out.println("ERROR");
    	  System.out.println("ERROR");
      }
  }
  
  private void printCompTariffs(int timeslotIndex) {
		System.out.println("\nOther Tariffs-----------");
//		if(timeslotIndex < 1500) {
//	  		int cc = 0;
	  		for (PowerType pt : competingTariffs.keySet()) {
	  			for (TariffSpecification spec : competingTariffs.get(pt)) {
//	      			if(pt == PowerType.CONSUMPTION && spec.getBroker().getUsername().equals("a")) {
//	      				cc ++;
//	      				if(cc > 2) {
//	      					continue;
//	      				}
//	      			}
	  				printTariff(spec);      			
	      		}
	  		}	
//		}else {
//	  		for (PowerType pt : competingTariffs.keySet()) {	  			
//	  			for (TariffSpecification spec : competingTariffs.get(pt)) {
//	  				printTariff(spec);      			
//	      		}
//	  		}
//		}
  }
  
  private void printBalanceStats() {
		double t1,t2;
		for (TariffSpecification spec : customerSubscriptions.keySet()) {
			if(tariffCharges.get(spec) == null)
				continue;    
			totalProfits += tariffCharges.get(spec);
		}
		System.out.printf("Current Imbalance: % .2f \n",totalAssessmentBalancingCosts);
		double tariffprofits = totalProfits;
		totalProfits = 0;
		t1 = balancingCosts;
		t2 = balancingEnergy;
		totalProfits += t1;
		System.out.printf("Balancing costs Interruptible \t: % 9.2f € \t Energy: % .2f KWh     \t Avg: % .2f €/MWh\n", t1,t2,1000*t1/t2);
		t1 = marketManager.getBalancingCosts();
		t2 = marketManager.getTotalBalancingEnergy();
		totalProfits += t1;
		System.out.printf("Balancing costs MM	\t: % 9.2f € \t Energy: % .2f KWh        \t Avg: % .2f €/MWh\n", t1,t2,t1/(t2/1000));
		t1 = marketManager.getDistributionCosts();
		t2 = marketManager.getTotalDistributionEnergy();
		totalProfits += t1;
//		System.out.printf("Distribution costs	\t: % .2f € \t Energy: % .2f KWh\t\t Avg: % .2f €/KWh\n",t1,t2,t2/t1);
		System.out.printf("Distribution costs	\t: % 9.2f € \n",t1);
		t1 = marketManager.getWholesaleCosts()[0];
		t2 = marketManager.getWholesaleEnergy()[0];
		totalProfits += t1;
		System.out.printf("Wholesale market buying \t: % 9.2f € \t Energy: % .2f KWh     \t Avg: % .2f €/MWh \t Energy Used: % .2f MWh\n",
							t1,t2,1000*t1/t2,totalEnergyused/1000 );
		t1 = marketManager.getWholesaleCosts()[1];
		t2 = marketManager.getWholesaleEnergy()[1];
		totalProfits += t1;
		System.out.printf("Wholesale market selling\t: % 9.2f € \t Energy: % .2f KWh     \t Avg: % .2f €/MWh\n",t1,t2,-1000*t1/t2);
//		System.out.printf("Market based profits total\t: % .2f € \t Tariff based profits total\t: % .2f €  \t",totalProfits,tariffprofits);
		totalProfits += tariffprofits;
		System.out.printf("Total profit from this period\t: % 9.2f € \n\n",totalProfits);
  }
  
  private void printDemandPeaks() {
  	System.out.println("Date: " + timeslotRepo.currentTimeslot().getStartInstant().toString());
		double[] d = new double[2];
		d = calcDemandMeanDeviation();
	
		System.out.printf("Current| Threshold: %.2f \t Peaks| ",  (d[0] + gamaParameter*d[1]) );
		currentThreshold = (d[0] + gamaParameter*d[1]);
		for(int p = 0; p < 3 ; p++) { 
			if(peakDemand[p] != 0) {
				System.out.printf("\t Ts: %d  %.2f KWh",peakDemandTS[p],peakDemand[p]); 
			}    			
		}
		System.out.println("");
  }
  
  public double getCurrentThreshold() {
	  return currentThreshold;
  }
  
  private void createInitialTariffs() {	  
	  TariffSpecification tempTariff;
	  System.out.println("Creating Initial Tariffs....");
	  for (PowerType pt : customerProfiles.keySet()) {
		  
			if( pt == PowerType.CONSUMPTION && enableGConsumption) {
				tempTariff = null;
				tempTariff = mutateConsumptionTariff(tempTariff,true,0,false);   			    			
    			//commit the new tariff
			    customerSubscriptions.put(tempTariff, new LinkedHashMap<>());
    	        tariffRepo.addSpecification(tempTariff);
    	        tariffCharges.put(tempTariff,0d);
    	        tariffCustomerCount.put(tempTariff,0);
    	        brokerContext.sendMessage(tempTariff); 	        
			}else if(pt.isStorage() && enableGStorage){
				if(pt != PowerType.THERMAL_STORAGE_CONSUMPTION) {
					continue;
				}
				tempTariff = new TariffSpecification(brokerContext.getBroker(), PowerType.THERMAL_STORAGE_CONSUMPTION);
				tempTariff = mutateStorageTariff(tempTariff,true,false);   				
				
				customerSubscriptions.put(tempTariff, new LinkedHashMap<>());
				tariffRepo.addSpecification(tempTariff);
				tariffCharges.put(tempTariff,0d);
				tariffCustomerCount.put(tempTariff,0);
  	        	brokerContext.sendMessage(tempTariff);
			}else if(pt.isProduction() && enableGProduction) {
				tempTariff = new TariffSpecification(brokerContext.getBroker(), pt);
				tempTariff = mutateProductionTariff(tempTariff,true,true);   				
 
				customerSubscriptions.put(tempTariff, new LinkedHashMap<>());
				tariffRepo.addSpecification(tempTariff);
				tariffCharges.put(tempTariff,0d);
				tariffCustomerCount.put(tempTariff,0);
				brokerContext.sendMessage(tempTariff);
			}
//			else if(pt == PowerType.INTERRUPTIBLE_CONSUMPTION && enableGInterruptible) {
//				tempTariff = mutateInterruptibleConsTariff(tempTariff);   				
//				// DELETE from db selected tariffs
//				customerSubscriptions.put(tempTariff, new LinkedHashMap<>());
//				tariffRepo.addSpecification(tempTariff);
//				tariffCharges.put(tempTariff,0d);
//				brokerContext.sendMessage(tempTariff);
//			}
	  }
	  
  }
  
  public Parameters getParams() {
	  return params;
  }
  
  // ------------- test-support methods ----------------
  double getUsageForCustomer (CustomerInfo customer,
                              TariffSpecification tariffSpec,
                              int index)
  {
    CustomerRecord record = getCustomerRecordByTariff(tariffSpec, customer);
    return record.getUsage(index);
  }
  
  // test-support method
  HashMap<PowerType, double[]> getRawUsageForCustomer (CustomerInfo customer)
  {
    HashMap<PowerType, double[]> result = new HashMap<>();
    for (PowerType type : customerProfiles.keySet()) {
      CustomerRecord record = customerProfiles.get(type).get(customer);
      if (record != null) {
        result.put(type, record.usage);
      }
    }
    return result;
  }

  // test-support method
  HashMap<String, Integer> getCustomerCounts()
  {
    HashMap<String, Integer> result = new HashMap<>();
    for (TariffSpecification spec : customerSubscriptions.keySet()) {
      Map<CustomerInfo, CustomerRecord> customerMap = customerSubscriptions.get(spec);
      for (CustomerRecord record : customerMap.values()) {
        result.put(record.customer.getName() + spec.getPowerType(), 
                    record.subscribedPopulation);
      }
    }
    return result;
  }

  //-------------------- Customer-model recording ---------------------
  /**
   * Keeps track of customer status and usage. Usage is stored
   * per-customer-unit, but reported as the product of the per-customer
   * quantity and the subscribed population. This allows the broker to use
   * historical usage data as the subscribed population shifts.
   */
  class CustomerRecord
  {
    CustomerInfo customer;
    int subscribedPopulation = 0;
    double[] usage;
    double alpha = 0.3;
    boolean deferredActivation = false;
    double deferredUsage = 0.0;
    int savedIndex = 0;

    /**
     * Creates an empty record
     */
    CustomerRecord (CustomerInfo customer)
    {
      super();
      this.customer = customer;
      this.usage = new double[brokerContext.getUsageRecordLength()];
    }

    CustomerRecord (CustomerRecord oldRecord)
    {
      super();
      this.customer = oldRecord.customer;
      this.usage = Arrays.copyOf(oldRecord.usage, brokerContext.getUsageRecordLength());
    }

    // Returns the CustomerInfo for this record
    CustomerInfo getCustomerInfo ()
    {
      return customer;
    }

    // Adds new individuals to the count
    void signup (int population)
    {
      subscribedPopulation = Math.min(customer.getPopulation(),
                                      subscribedPopulation + population);
    }

    // Removes individuals from the count
    void withdraw (int population)
    {
      subscribedPopulation -= population;
    }

    // Sets up deferred activation
    void setDeferredActivation ()
    {
      deferredActivation = true;
      notifyOnActivation.add(this);
    }

    // Customer produces or consumes power. We assume the kwh value is negative
    // for production, positive for consumption
    void produceConsume (double kwh, Instant when)
    {
      int index = getIndex(when);
      produceConsume(kwh, index);
    }

    // stores profile data at the given index
    void produceConsume (double kwh, int rawIndex)
    {
      if (deferredActivation) {
        deferredUsage += kwh;
        savedIndex = rawIndex;
      }
      else
        localProduceConsume(kwh, rawIndex);
    }

    // processes deferred recording to accomodate regulation
    void activate ()
    {
      //PortfolioManagerService.log.info("activate {}", customer.getName());
      localProduceConsume(deferredUsage, savedIndex);
      deferredUsage = 0.0;
    }

    private void localProduceConsume (double kwh, int rawIndex)
    {
      int index = getIndex(rawIndex);
      double kwhPerCustomer = 0.0;
      if (subscribedPopulation > 0) {
        kwhPerCustomer = kwh / (double)subscribedPopulation;
      }
      double oldUsage = usage[index];
      if (oldUsage == 0.0) {
        // assume this is the first time
        usage[index] = kwhPerCustomer;
      }
      else {
        // exponential smoothing
        usage[index] = alpha * kwhPerCustomer + (1.0 - alpha) * oldUsage;
      }
      //PortfolioManagerService.log.debug("consume {} at {}, customer {}", kwh, index, customer.getName());
    }

    double getUsage (int index)
    {
      if (index < 0) {
        PortfolioManagerService.log.warn("usage requested for negative index " + index);
        index = 0;
      }
      return (usage[getIndex(index)] * (double)subscribedPopulation);
    }

    // we assume here that timeslot index always matches the number of
    // timeslots that have passed since the beginning of the simulation.
    int getIndex (Instant when)
    {
      int result = (int)((when.getMillis() - timeService.getBase()) /
                         (Competition.currentCompetition().getTimeslotDuration()));
      return result;
    }

    private int getIndex (int rawIndex)
    {
      return rawIndex % usage.length;
    }
    
    
  }
}
