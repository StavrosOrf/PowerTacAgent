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
import org.powertac.common.msg.EconomicControlEvent;
import org.powertac.common.msg.TariffRevoke;
import org.powertac.common.msg.TariffStatus;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
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
 * Handles portfolio-management responsibilities for the broker. This
 * includes composing and offering tariffs, keeping track of customers and their
 * usage, monitoring tariff offerings from competing brokers.
 * 
 * A more complete broker implementation might split this class into two or
 * more classes; the keys are to decide which messages each class handles,
 * what each class does on the activate() method, and what data needs to be
 * managed and shared.
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
  
//  @Autowired
//  private ContextManagerService contextmanager;

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
  private Database tariffDB;

  private int consumptionCustomers = 0;
  private int eVCustomers = 0;
  private int storageCustomers = 0;
  private int interruptibleCustomers = 0;
  private int tHCCustomers = 0;
  private int solarCustomers = 0;
  private int windCustomers = 0;
  private int BatteryCustomers = 0;
  private int otherProducers = 0;
  
  private int consumptionCustomersPrev = 0;
  private int eVCustomersPrev = 0;
  private int storageCustomersPrev = 0;
  private int interruptibleCustomersPrev = 0;
  private int tHCCustomersPrev = 0;
  private int solarCustomersPrev = 0;
  private int windCustomersPrev = 0;
  private int BatteryCustomersPrev = 0;
  private int otherProducersPrev = 0;
  
  private double weightWe[] = new double[24];
  private double weightWd[] = new double[24];
  
  private double balancingCosts = 0;
  private double balancingEnergy = 0;
  
  private int timer = 0 ;
  private int timer2 = 0;
  private boolean enableStorage = true;
  private boolean enableProduction = true;
  private boolean enableInterruptible = true;
  private boolean enableConsumption = true;
  
  private boolean isLateGame = false;
  
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
    competingTariffs = new HashMap<>();
    tariffDB = new Database();
    
    tariffDB.resetGameLevel(2);
    
    notifyOnActivation.clear();
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
	  CustomerInfo customer =
            customerRepo.findByNameAndPowerType(cbd.getCustomerName(),
                                                cbd.getPowerType());
	  CustomerRecord record = getCustomerRecordByPowerType(cbd.getPowerType(), customer);
	  int subs = record.subscribedPopulation;
	  record.subscribedPopulation = customer.getPopulation();
	  for (int i = 0; i < cbd.getNetUsage().length; i++) {
    	record.produceConsume(cbd.getNetUsage()[i], i);
	  }
	  record.subscribedPopulation = subs;
    
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
      TariffSpecification original =
              tariffRepo.findSpecificationById(spec.getId());
      if (null == original)
        log.error("Spec " + spec.getId() + " not in local repo");
      log.info("published " + spec);
    }
    else {
      // otherwise, keep track of competing tariffs, and record in the repo
      addCompetingTariff(spec);
      tariffRepo.addSpecification(spec);
      
      boolean isInitial = false;
      
      if(timeslotRepo.currentTimeslot().getSerialNumber() < Parameters.initialTariffBound) {
    	  isInitial = true;
      }
      
      for (int i = 0; i <3; i++) {
    	  tariffDB.addTariff(spec.getBroker().getUsername(),spec.getPowerType(),(int)spec.getMinDuration(),
				spec.getEarlyWithdrawPayment(),spec.getSignupPayment(), spec.getPeriodicPayment(),calculateAvgRate(spec,false),
				-1,i, spec.getRates(),spec.getRegulationRates(),timeslotRepo.currentTimeslot().getSerialNumber()
				,marketManager.getCompetitors(),isInitial);
      }
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
    
    if (TariffTransaction.Type.SIGNUP == txType) {
      // keep track of customer counts
      record.signup(ttx.getCustomerCount());
    }
    else if (TariffTransaction.Type.WITHDRAW == txType) {
      // customers presumably found a better deal
      record.withdraw(ttx.getCustomerCount());
    }
    else if (ttx.isRegulation()) {
      // Regulation transaction -- we record it as production/consumption
      // to avoid distorting the customer record. 
      log.debug("Regulation transaction from {}, {} kWh for {}",
                ttx.getCustomerInfo().getName(),
                ttx.getKWh(), ttx.getCharge());
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
    }
    else if (TariffTransaction.Type.PRODUCE == txType) {
      // if ttx count and subscribe population don't match, it will be hard
      // to estimate per-individual production
      if (ttx.getCustomerCount() != record.subscribedPopulation) {
        log.warn("production by subset {}  of subscribed population {}",
                 ttx.getCustomerCount(), record.subscribedPopulation);
      }
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
      double currentCharge = tariffCharges.get(ttx.getTariffSpec());
      tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
      
      log.info("TTX Produce: " +ttx.getCharge() + " , " + ttx.getId() 
      	+ " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
    }
    else if (TariffTransaction.Type.CONSUME == txType) {
      if (ttx.getCustomerCount() != record.subscribedPopulation) {
        log.warn("consumption by subset {} of subscribed population {}",
                 ttx.getCustomerCount(), record.subscribedPopulation);
      }
      log.info("TTX Consume: " +ttx.getCharge() + " , " + ttx.getId() 
      + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
      double currentCharge = tariffCharges.get(ttx.getTariffSpec());
      tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
    }
    else if (TariffTransaction.Type.PERIODIC == txType) {
    	log.info("TTX Periodic: " +ttx.getCharge() + " , " + ttx.getId() 
        + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
        record.produceConsume(ttx.getKWh(), ttx.getPostedTime()); 
        double currentCharge = tariffCharges.get(ttx.getTariffSpec());
        tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
    }
    else if (TariffTransaction.Type.REVOKE == txType) {
    	log.info("TTX Revoke: " +ttx.getCharge() + " , " + ttx.getId() 
        + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
        record.produceConsume(ttx.getKWh(), ttx.getPostedTime()); 
        double currentCharge = tariffCharges.get(ttx.getTariffSpec());
        tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
    }
    else if (TariffTransaction.Type.SIGNUP == txType) {
    	log.info("TTX Signup: " +ttx.getCharge() + " , " + ttx.getId() 
        + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
        record.produceConsume(ttx.getKWh(), ttx.getPostedTime()); 
        double currentCharge = tariffCharges.get(ttx.getTariffSpec());
        tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
    }
    else if (TariffTransaction.Type.REFUND == txType) {
    	log.info("TTX Refund: " +ttx.getCharge() + " , " + ttx.getId() 
        + " , " + ttx.getBroker() + " , " + ttx.getTariffSpec().getPowerType().getGenericType());
        record.produceConsume(ttx.getKWh(), ttx.getPostedTime()); 
        double currentCharge = tariffCharges.get(ttx.getTariffSpec());
        tariffCharges.put(ttx.getTariffSpec(),currentCharge+ ttx.getCharge());
    }
    else if (TariffTransaction.Type.PUBLISH == txType) {
//    	System.out.println("Publish: " + ttx.getCharge() + " " + ttx.getKWh() + " "  + ttx.toString());
    }
    else if (TariffTransaction.Type.WITHDRAW == txType) {
    	System.out.println("Withdraw: " + ttx.getCharge() + " " + ttx.getKWh() + " "  + ttx.toString());
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
  

  //TODO
  @Override // from Activatable
  public synchronized void activate (int timeslotIndex)
  {
	  
	  ArrayList<TariffSpecification> t ;
	  TariffSpecification tempTariff ;
//	  System.out.printf("Energy Usage: %.2f KWh   ts %d \n", collectUsage(timeslotIndex),timeslotIndex);
      if (customerSubscriptions.size() == 0) {
        // we (most likely) have no tariffs
        createInitialTariffs();
      } 
      else {
    	 
    	 if(timeslotRepo.currentTimeslot().getSerialNumber() > Parameters.LATE_GAME) {
    		 isLateGame = true;
    	 }else {
    		 isLateGame = false;
    	 }
    	 
//    	 enableConsumption = false;
    	 
    	 if(timer == Parameters.reevaluationCons) {
//    		calculateWeights();
    		calculateWeightsPredictor();
    		
    		if(timer2 == Parameters.reevaluationInterruptible) {
    			enableInterruptible = true;
    		}else {
    			enableInterruptible = false;
    		}
    		if(timer2 == Parameters.reevaluationStorage) {
    			enableStorage = true;
    		}else {
    			enableStorage = false;
    		}
    		if(timer2 == Parameters.reevaluationProduction) {
    			enableProduction = true;
    			timer2 = 0;
    		}else {
    			enableProduction = false;
    		}   		
    		
    		System.out.println("Its time");
    		printBalanceStats();
    		System.out.println("");

    		tariffDB.decayFittnessValue(0);
    		tariffDB.decayFittnessValue(1);
    		
    		for (TariffSpecification spec : customerSubscriptions.keySet()) {
    			if(tariffCharges.get(spec) == null) {
//    				System.out.print("NULL + |");
//    				printTariff(spec);
    				continue;
    			}
    				
    			
    			Map<CustomerInfo, CustomerRecord> m = customerSubscriptions.get(spec);
    			int count = 0;
    			
    			for(CustomerRecord r : m.values()) {
    				count += r.subscribedPopulation;
    			}
    			// Print info about customers
//    			if(tariffCharges.get(spec) != 0) {
    			if(true) {
    				System.out.printf("-- Profit: % 8.2f ",tariffCharges.get(spec));	
    				
    			    switch(spec.getPowerType().toString()) {
    		    	case "CONSUMPTION":
    		    		System.out.print( "< "+count+" / "+ consumptionCustomers +" >  +/- "+ (count-consumptionCustomersPrev) +" \\ ");
    		    		consumptionCustomersPrev = count;
    		    		break;
    		    	case "STORAGE":
    		    		System.out.print( "< "+count+" / "+ storageCustomers +" > +/- "+ (count-storageCustomersPrev) +" \\ ");
    		    		storageCustomersPrev = count;
    		    		break;
    		    	case "INTERRUPTIBLE_CONSUMPTION":
    		    		System.out.print( "< "+count+" / "+ interruptibleCustomers+" > +/- "+ (count-interruptibleCustomersPrev) +" \\ ");
    		    		interruptibleCustomersPrev = count;
    		    		break;
    		    	case "THERMAL_STORAGE_CONSUMPTION":
    		    		System.out.print( "< "+count+" / "+ tHCCustomers +" > +/- "+ (count-tHCCustomersPrev) +" \\ ");
    		    		tHCCustomersPrev = count;
    		    		break;
    		    	case "SOLAR_PRODUCTION":
    		    		System.out.print( "< "+count+" / "+ solarCustomers +" > +/- "+ (count-solarCustomersPrev) +" \\ ");
    		    		solarCustomersPrev = count;
    		    		break;
    		    	case "WIND_PRODUCTION":
    		    		System.out.print( "< "+count+" / "+ windCustomers +" > +/- "+ (count-windCustomersPrev) +" \\ ");
    		    		windCustomersPrev = count;
    		    		break;
    		    	case "BATTERY_STORAGE":
    		    		System.out.print( "< "+count+" / "+ BatteryCustomers +" > +/- "+ (count-BatteryCustomersPrev) +" \\ ");
    		    		BatteryCustomersPrev = count;
    		    		break;
    		    	case "ELECTRIC_VEHICLE":
    		    		System.out.print( "< "+count+" / "+ eVCustomers +" > +/- "+ (count-eVCustomersPrev) +" \\ ");
    		    		eVCustomersPrev = count;
    		    		break;
    		    	default:
    		    		System.out.print( "< "+count+" / "+ otherProducers +" > +/- "+ (count-otherProducersPrev) +" \\ ");
    		    		otherProducersPrev = count;
    		    		
    		    }
    				
    				printTariff(spec);
    			}
    			//add tariff to Ground Level
    			if(tariffCharges.get(spec) != 0) {
    				
    				boolean isInitial = false;
    				if(timeslotRepo.currentTimeslot().getSerialNumber() == 361 + Parameters.reevaluationCons)		
    					isInitial = true;
    				
    				for (int i = 0; i <3; i++) {
        				tariffDB.addTariff(spec.getBroker().getUsername(),spec.getPowerType(),(int)spec.getMinDuration(),
            					spec.getEarlyWithdrawPayment(),spec.getSignupPayment(), spec.getPeriodicPayment(),calculateAvgRate(spec,false),
            					tariffCharges.get(spec),i, spec.getRates(),spec.getRegulationRates(),timeslotRepo.currentTimeslot().getSerialNumber()
            					,marketManager.getCompetitors(),isInitial);						
					}
    			}
    			
    			PowerType pt = spec.getPowerType();
    			if(tariffDB.getNumberOfRecords(pt,brokerContext.getBroker().getUsername(),1,false,marketManager.getCompetitors()) < 2) {

    				if(tariffDB.getNumberOfRecords(pt,brokerContext.getBroker().getUsername(),0,false,marketManager.getCompetitors()) < 2) {
    					continue;
    				}else{
    					t = tariffDB.getBestTariff(2, pt,brokerContext.getBroker(),0,false,marketManager.getCompetitors());
    				}
    			}else{
    					t = tariffDB.getBestTariff(2, pt,brokerContext.getBroker(),1,false,marketManager.getCompetitors());

    			}
    			
//    			t = tariffDB.getBestTariff(2, spec.getPowerType(),spec.getBroker(),1,false,marketManager.getCompetitors());
//    			
//    			if(tariffDB.getNumberOfRecords(spec.getPowerType(),Parameters.MyName,1,false,marketManager.getCompetitors()) < 2)
//    				continue; 			
    			
    			if(spec.getPowerType() == PowerType.CONSUMPTION  && enableConsumption) {
    				tempTariff = crossoverTariffs(t);
    				tempTariff = mutateConsumptionTariff(tempTariff);   				
        			tariffCharges.remove(spec);
    				
        			//commit the new tariff
        			tempTariff.addSupersedes(spec.getId());
        	        tariffRepo.addSpecification(tempTariff);
        	        tariffCharges.put(tempTariff,0d);
        	        brokerContext.sendMessage(tempTariff);
        	        
        	        // revoke the old one
        	        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
        	        brokerContext.sendMessage(revoke);
    			} else if(spec.getPowerType().isStorage() && enableStorage){
    				tempTariff = crossoverTariffs(t);
    				tempTariff = mutateStorageTariff(tempTariff);   				
        			tariffCharges.remove(spec);
        			
        			tempTariff.addSupersedes(spec.getId());
        	        tariffRepo.addSpecification(tempTariff);
        	        tariffCharges.put(tempTariff,0d);
        	        brokerContext.sendMessage(tempTariff);
        	        // revoke the old one
        	        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
        	        brokerContext.sendMessage(revoke);
        	        
    			} else if(spec.getPowerType().isProduction() && enableProduction) {
    				tempTariff = crossoverTariffs(t);
    				tempTariff = mutateProductionTariff(tempTariff);   				
        			tariffCharges.remove(spec);
        			
        			tempTariff.addSupersedes(spec.getId());
        	        tariffRepo.addSpecification(tempTariff);
        	        tariffCharges.put(tempTariff,0d);
        	        brokerContext.sendMessage(tempTariff);
        	        // revoke the old one
        	        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
        	        brokerContext.sendMessage(revoke);
        	        
    			}else if(spec.getPowerType() == PowerType.INTERRUPTIBLE_CONSUMPTION && enableInterruptible) {
    				tempTariff = crossoverTariffs(t);
    				tempTariff = mutateInterruptibleConsTariff(tempTariff);
    				tariffCharges.remove(spec);

    				tempTariff.addSupersedes(spec.getId());
    				tariffRepo.addSpecification(tempTariff);
    				tariffCharges.put(tempTariff,0d);
    				brokerContext.sendMessage(tempTariff);
    				
    		        BalancingOrder order = new BalancingOrder(brokerContext.getBroker(),tempTariff, 0.3,calculateAvgRate(tempTariff,false) * 0.9);
    		        brokerContext.sendMessage(order);
    				
        	        // revoke the old one
        	        TariffRevoke revoke = new TariffRevoke(brokerContext.getBroker(), spec);
        	        brokerContext.sendMessage(revoke);
    			}
    		}
    		System.out.println("Other Tariffs-----------");
    		int cc = 0;
    		for (PowerType pt : competingTariffs.keySet()) {
    			for (TariffSpecification spec : competingTariffs.get(pt)) {
        			if(pt == PowerType.CONSUMPTION && spec.getBroker().getUsername().equals("AgentUDE17")) {
        				cc ++;
        				if(cc > 2) {
        					continue;
        				}
        			}
    				printTariff(spec);      			
        		}
    		}
    		marketManager.setTotalBalancingEnergy(0);
    		marketManager.setTotalDistributionEnergy(0);
    		marketManager.setWholesaleEnergy(0);
    		marketManager.setBalancingCosts(0);
    		marketManager.setDistributionCosts(0);
    		marketManager.setWholesaleCosts(0);
    		timer = 0;
    		balancingCosts = 0;
    		balancingEnergy = 0;
    	}
    	timer ++;
        timer2 ++;
      }
      // Exercise economic controls every 4 timeslots BETA
      if ((timeslotIndex % 4) == 3) {
        List<TariffSpecification> candidates = tariffRepo.findTariffSpecificationsByPowerType(PowerType.INTERRUPTIBLE_CONSUMPTION);
        for (TariffSpecification spec: candidates) {
          EconomicControlEvent ece = new EconomicControlEvent(spec, 0.2, timeslotIndex + 1);
          brokerContext.sendMessage(ece);
        }
      }
      
      
      for (CustomerRecord record: notifyOnActivation)
        record.activate();
      
      System.out.println("-");
  }
  
  //calculate avg rate of a tariff weighted or normal average
  private double calculateAvgRate(TariffSpecification t,boolean weighted) {
	  
	  double sum = 0;
	  if(t.getRates() != null) {
		  if(!weighted) {
			  for (Rate r : t.getRates()) {
					sum += r.getMinValue();
				  }
				  
				  int n1 = t.getRates().size();
				  return sum / n1;
		  }else {
			  if(t.getRates().get(0).getWeeklyBegin() < 6)
				  return t.getRates().get(0).getMinValue() / weightWd[t.getRates().get(0).getDailyBegin()];
			  else
				  return t.getRates().get(0).getMinValue() / weightWe[t.getRates().get(0).getDailyBegin()];
		  }

	  }
	  
	  return 0;
  }
  
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
	  //TODO check that all weights are adove a value so no very small rates would be applied
	  weightWe = wWe;
	  weightWd = wWd;
  }
  
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
//		  System.out.printf(" %.2f |",netUsageWd[i]);
//	  }
//	  for(int i = 0; i<24 ;i++) {
//		  System.out.printf(" %.2f |",netUsageWe[i]);
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
	  //TODO check that all weights are adove a value so no very small rates would be applied
	  weightWe = wWe;
	  weightWd = wWd;
  }
  
  //Function producing time of use rates for the given tariff
  private TariffSpecification produceTOURates(TariffSpecification t, double avg,double maxCurtailment){
	  
	  TariffSpecification spec = new TariffSpecification(t.getBroker(),t.getPowerType());
	  spec.withEarlyWithdrawPayment(t.getEarlyWithdrawPayment());
	  spec.withMinDuration(t.getMinDuration());
	  spec.withPeriodicPayment(t.getPeriodicPayment());
	  spec.withSignupPayment(t.getSignupPayment());
	 
	  double wWe[] = new double[24];
	  double wWd[] = new double[24];

	  calculateWeightsPredictor();
	  wWe = weightWe;
	  wWd = weightWd;
	  
	  for(int i = 0; i<24 ; i++) {
		  Rate r = new Rate();
		  r.withWeeklyBegin(1);
		  r.withWeeklyEnd(5);
		  r.withDailyBegin(i);
		  if(i != 23)
			  r.withDailyEnd(i+1);
		  else
			  r.withDailyEnd(0);
		  
//		  if(avg*wWe[i] < Parameters.MIN_RATE_VALUE)
//			  r.withMinValue(Parameters.MIN_RATE_VALUE); 
//		  else
			  r.withMinValue(avg*wWe[i]);			  
		  
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
		  
		  r.withMinValue(avg*wWd[i]);
		  r.withMaxCurtailment(maxCurtailment);
//		  System.out.println(avg*wWd[i]);
		  spec.addRate(r);
	  }
	  return spec ;
  }
//Default function to crossover two tariffs 
  private TariffSpecification crossoverTariffs(ArrayList<TariffSpecification> t) {
	  Random rnd  = new Random();
	  if(t.size() == 1)
		  return t.get(0);
	  
	  TariffSpecification t1 = t.get(0);
	  TariffSpecification t2 = t.get(1);
	  
	  
	  int point = rnd.nextInt(5) ; //0-4 points in a tariff specification
	  
	  if(point > 0) {
		  t1.withPeriodicPayment(t.get(1).getPeriodicPayment());
		  t2.withPeriodicPayment(t.get(0).getPeriodicPayment());
		  point --;
	  }
	  if(point > 0) {
		  t1.withMinDuration(t.get(1).getMinDuration());
		  t2.withMinDuration(t.get(0).getMinDuration());
		  point --;
	  }
	  if(point > 0) {
		  t1.withEarlyWithdrawPayment(t.get(1).getEarlyWithdrawPayment());
		  t2.withEarlyWithdrawPayment(t.get(0).getEarlyWithdrawPayment());
		  point --;
	  }
	  if(point > 0) {
		  t1.withSignupPayment(t.get(1).getSignupPayment());
		  t2.withSignupPayment(t.get(0).getSignupPayment());
		  point --;
	  }
	  
	  int r = rnd.nextInt(2);
	  if (r == 1)
		  return t1;
	  else
		  return t2;
	  
  }
  
  private TariffSpecification mutateProductionTariff(TariffSpecification t) {
	  Random rnd = new Random();
	  System.out.println("Before Mutation---");
	  printTariff(t);
	  
	  TariffSpecification spec = new TariffSpecification(t.getBroker(),t.getPowerType());
	  spec.withEarlyWithdrawPayment(t.getEarlyWithdrawPayment());
	  spec.withMinDuration(t.getMinDuration());
	  spec.withPeriodicPayment(t.getPeriodicPayment());
	  spec.withSignupPayment(t.getSignupPayment());
	  
	  double ep = Parameters.Ep;
	  double ebp = Parameters.Ebp;
	  int ecl = Parameters.Ecl;
	  
	  //Mutating periodic payment
	  double temp = spec.getPeriodicPayment();
	  if(temp == 0) {
		  temp = -6.5;
	  }
	  temp = temp * (1 - ep )+  (temp * (1 +  ep ) - temp * (1 - ep ))*rnd.nextDouble();
	  spec.withPeriodicPayment(temp);
	  	  
	  //Mutating signup payment
	  temp = spec.getSignupPayment();
	  if(temp == 0) {
		  temp = ebp;
	  }
	  temp = temp + ebp +  (temp -  ebp  - (temp  + ebp ))*rnd.nextDouble();
	  if(temp < 0) {
		  temp = - temp;
	  }
	  
	  spec.withSignupPayment(temp);
	  
	  //Mutating Early withdrawal payment
	  spec.withEarlyWithdrawPayment(-2*temp);
	  
	  //Mutating contract length
//	  int tmp = (int)spec.getMinDuration();
//	  if(tmp < ecl/2) {
//		  tmp = rnd.nextInt(ecl)+2;
//	  }
//	  if(ecl >= tmp) {
//		  ecl = tmp/2;
//	  }
//	  if(ecl<0) {
//		  ecl = - ecl;
//	  }
//	  tmp = tmp - ecl + rnd.nextInt(2*ecl);
//	  if(t.getPowerType() == PowerType.SOLAR_PRODUCTION) {
//		  spec.withMinDuration(0);
//	  }else {
//		  spec.withMinDuration(ecl);
//	  }
	  spec.withMinDuration(ecl);
	  
	  temp = t.getRates().get(0).getMinValue();
	  if(temp == 0) {
		  temp =0.01;
	  }
	  temp = temp * (1 - ep )+  (temp * (1 +  ep ) - temp * (1 - ep ))*rnd.nextDouble();
	
	  Rate r = new Rate().withMinValue(temp);
	  spec.addRate(r);
	  
	  printTariff(spec);
	  System.out.println("After Mutation---");
	  return spec;
  }
  
  //Default function to mutate a tariff 
  private TariffSpecification mutateStorageTariff(TariffSpecification spec) {
	  Random rnd = new Random();
	  System.out.println("Before Mutation---");
	  printTariff(spec);
	  double ep = Parameters.Ep;
	  double ebp = Parameters.Ebp;
	  double eReg = Parameters.Ereg;
	  int ecl = Parameters.Ecl;
	  
	  //Mutating periodic payment
	  double temp = spec.getPeriodicPayment();
	  if(temp != 0) {
		  temp = 0;
	  }

	  //Mutating signup payment
	  temp = spec.getSignupPayment();
	  if(temp == 0) {
		  temp = ebp;
	  }
	  temp = temp + ebp +  (temp -  ebp  - (temp  + ebp ))*rnd.nextDouble();
	  if(temp < 0) {
		  temp = - temp;
	  }
	  spec.withSignupPayment(temp);
	  
	  //Mutating Early withdrawal payment
	  spec.withEarlyWithdrawPayment(-2*temp);
	  
	  //Mutating contract length
//	  int tmp = (int)spec.getMinDuration();
//	  if(tmp < ecl/2) {
//		  tmp = rnd.nextInt(ecl)+1;
//	  }
//	  if(ecl >= tmp) {
//		  ecl = tmp/2;
//	  }
//	  tmp = tmp - ecl + rnd.nextInt(2*ecl);
	  spec.withMinDuration(ecl);
	  
	  double a1 = 0;
	  temp = 0;
	  if(spec.getRates() != null) {
		  for (Rate r : spec.getRates()) {
			a1 += r.getMinValue();
		  }
		  
		  int n1 = spec.getRates().size();
		  temp = a1 / n1;
	  }
	  if(temp == 0) {
		  temp = -0.1;
	  }
	  temp = temp * (1 - ep )+  (temp * (1 +  ep ) - temp * (1 - ep ))*rnd.nextDouble();
	
	  spec = produceTOURates(spec,temp,0);
	  spec.withPeriodicPayment(0);
	  
	  double downReg,upReg;
	  //Mutate RegRates
	  if(spec.getRegulationRates().size() != 0) {
		  downReg = spec.getRegulationRates().get(0).getDownRegulationPayment();
		  upReg = spec.getRegulationRates().get(0).getUpRegulationPayment();
		  
		  downReg = downReg - eReg/10 + 2 * eReg/10 * rnd.nextDouble();
		  upReg = upReg - eReg + 2 * eReg * rnd.nextDouble();
		  if(downReg > 0) {
			  downReg = - downReg;
		  }
		  if(upReg <0) {
			  upReg = - upReg;
		  }
		  spec.getRegulationRates().get(0).withDownRegulationPayment(downReg);
		  spec.getRegulationRates().get(0).withUpRegulationPayment(upReg);
		  
	  }else{//create a regRate
		  downReg = -0.02 - eReg/10 + 2 * eReg/10 * rnd.nextDouble();
		  upReg = 0.1 - eReg + 2 * eReg * rnd.nextDouble();
		  if(downReg > 0) {
			  downReg = - downReg;
		  }
		  if(upReg <0) {
			  upReg = - upReg;
		  }
		  RegulationRate reg = new RegulationRate();
		  
		  reg.withDownRegulationPayment(downReg);
		  reg.withUpRegulationPayment(upReg);
		  
		  spec.addRate(reg);
	  }
	  
	  printTariff(spec);
	  System.out.println("After Mutation---");
	  return spec;
  }
  
  private TariffSpecification mutateConsumptionTariff(TariffSpecification spec) {
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
	  
	  //Mutating avg rate value
	  double a1 = 0;
	  temp = 0;
	  if(spec.getRates() != null) {
		  for (Rate r : spec.getRates()) {
			a1 += r.getMinValue();
		  }

		  int n1 = spec.getRates().size();
		  temp = a1 / n1;
	  }
//	  temp = calculateAvgRate(spec,false);
	  
	  if(temp == 0) {
		  temp = -0.1;
	  }
	  
//	  if(isLateGame)
//		  temp += Parameters.LATE_GAME_ADDEED_PRICE;
	  
	  temp = temp * (1 - ep )+  (temp * (1 +  ep ) - temp * (1 - ep ))*rnd.nextDouble();
	
	  spec = produceTOURates(spec,temp,0);
	  
	  spec.withPeriodicPayment(0);
	  printTariff(spec);
	  System.out.println("After Mutation---");
	  return spec;
  }
  

  private TariffSpecification mutateInterruptibleConsTariff(TariffSpecification spec) {
	  Random rnd = new Random();
	  System.out.println("Before Mutation---");
	  printTariff(spec);
	  double ep = Parameters.Ep;
	  double ebp = Parameters.Ebp;
	  int ecl = Parameters.Ecl;
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
	  spec.withMinDuration(ecl);
	  
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
  
  public void printTariff(TariffSpecification t) {
	  
	  int n1 = -1,n2 = -1;
	  double a1 = 0;
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
	  
      System.out.printf(" \tID:%d %30s, %15s| Periodic: % 5.4f Signup:% 5.2f Ewp:% 5.2f ContractL:%10d ts | Rates:%3d Avg:% 3.4f  RegRates:%3d \t",
      t.getId(), t.getPowerType().toString(),t.getBroker().getUsername(),t.getPeriodicPayment(),
      t.getSignupPayment(),t.getEarlyWithdrawPayment(),t.getMinDuration()/5000,n1,a1,n2);
      
      if(t.getRegulationRates().size() >= 1) {
    	  for(RegulationRate r : t.getRegulationRates())
    		  System.out.printf("UpReg % .4f DownReg % .4f| ",r.getUpRegulationPayment(),r.getDownRegulationPayment());
      }
      
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
    			  System.out.printf("% d %2d  % 1.3f|",r.getWeeklyBegin(),r.getDailyBegin(),r.getMinValue());
    		  }		  
    	  }
      }
      System.out.println(" ");
      if(t.getSignupPayment() < 0) {
    	  System.out.println("ERROR");
    	  System.out.println("ERROR");
      }
  }
  
  private void printBalanceStats() {
		double t1,t2;
		t1 = balancingCosts;
		t2 = balancingEnergy;
		System.out.printf("Balancing costs BETA	\t: % .2f € \t Energy: % .2f KWh     \t Avg: % .2f €/KWh\n", t1,t2,t2/t1);
		t1 = marketManager.getBalancingCosts();
		t2 = marketManager.getTotalBalancingEnergy();
		System.out.printf("Balancing costs MM	\t: % .2f € \t Energy: % .2f KWh     \t Avg: % .2f €/KWh\n", t1,t2,t2/t1);
		t1 = marketManager.getDistributionCosts();
		t2 = marketManager.getTotalDistributionEnergy();
		System.out.printf("Distribution costs	\t: % .2f € \t Energy: % .2f KWh     \t Avg: % .2f €/KWh\n",t1,t2,t2/t1);
		t1 = marketManager.getWholesaleCosts()[0];
		t2 = marketManager.getWholesaleEnergy()[0];
		System.out.printf("Wholesale market buying \t: % .2f € \t Energy: % .2f KWh     \t Avg: % .2f €/KWh\n",t1,t2,t2/t1 );
		t1 = marketManager.getWholesaleCosts()[1];
		t2 = marketManager.getWholesaleEnergy()[1];
		System.out.printf("Wholesale market selling\t: % .2f € \t Energy: % .2f KWh     \t Avg: % .2f €/KWh\n",t1,t2,t2/t1);
  }
  
  private void createInitialTariffs() {
	  ArrayList<TariffSpecification> t;
	  TariffSpecification tempTariff;
	  System.out.println("Creating Initial Tariffs....");
	  for (PowerType pt : customerProfiles.keySet()) {
		  	//check level 1 of db for initial tariffs
			if(tariffDB.getNumberOfRecords(pt,brokerContext.getBroker().getUsername(),1,true,marketManager.getCompetitors()) < 2) {
//				System.out.println("lvl 1: " + tariffDB.getNumberOfRecords(pt,brokerContext.getBroker().getUsername(),1,true,marketManager.getCompetitors()) );
//				System.out.println(pt.toString());
				if(tariffDB.getNumberOfRecords(pt,brokerContext.getBroker().getUsername(),0,true,marketManager.getCompetitors()) < 2) {
					//call the default createInitialTariffs
//					System.out.println("lvl 0" + tariffDB.getNumberOfRecords(pt,brokerContext.getBroker().getUsername(),0,true,marketManager.getCompetitors()));
//					System.out.println(pt.toString());
					tempTariff = createInitialTariffSampleBroker(pt);
				}else{
					t = tariffDB.getBestTariff(2, pt,brokerContext.getBroker(),0,true,marketManager.getCompetitors());
					tempTariff = crossoverTariffs(t);
				}
			}else{
					t = tariffDB.getBestTariff(2, pt,brokerContext.getBroker(),1,true,marketManager.getCompetitors());
					tempTariff = crossoverTariffs(t);
			}
			
				   			
			if( pt == PowerType.CONSUMPTION) {
				tempTariff = mutateConsumptionTariff(tempTariff);   				
    			// TODO DELETE from db selected tariffs
    			//commit the new tariff
			    customerSubscriptions.put(tempTariff, new LinkedHashMap<>());
    	        tariffRepo.addSpecification(tempTariff);
    	        tariffCharges.put(tempTariff,0d);
    	        brokerContext.sendMessage(tempTariff); 	        
			}else if(pt.isStorage()){
				tempTariff = mutateStorageTariff(tempTariff);   				
				// DELETE from db selected tariffs
				customerSubscriptions.put(tempTariff, new LinkedHashMap<>());
				tariffRepo.addSpecification(tempTariff);
				tariffCharges.put(tempTariff,0d);
  	        	brokerContext.sendMessage(tempTariff);
			}else if(pt.isProduction()) {
				tempTariff = mutateProductionTariff(tempTariff);   				
				// DELETE from db selected tariffs
				customerSubscriptions.put(tempTariff, new LinkedHashMap<>());
				tariffRepo.addSpecification(tempTariff);
				tariffCharges.put(tempTariff,0d);
				brokerContext.sendMessage(tempTariff);
			}else if(pt == PowerType.INTERRUPTIBLE_CONSUMPTION) {
				tempTariff = mutateInterruptibleConsTariff(tempTariff);   				
				// DELETE from db selected tariffs
				customerSubscriptions.put(tempTariff, new LinkedHashMap<>());
				tariffRepo.addSpecification(tempTariff);
				tariffCharges.put(tempTariff,0d);
				brokerContext.sendMessage(tempTariff);
			}else {
				System.exit(1);
			}
	  }
	  
  }
  private TariffSpecification createInitialTariffSampleBroker(PowerType pt) {
	  double marketPrice = marketManager.getMeanMarketPrice() / 1000.0;
	  double rateValue = ((marketPrice + fixedPerKwh) * (1.0 + defaultMargin));
      double periodicValue = defaultPeriodicPayment;
      if (pt.isProduction()) {
        rateValue = -2.0 * marketPrice;
        periodicValue /= 2.0;
      }
      if (pt.isStorage()) {
        rateValue *= 0.9; // Magic number
        periodicValue = 0.0;
      }
      if (pt.isInterruptible()) {
        rateValue *= 0.7; // Magic number!! price break for interruptible
      }
      TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), pt).withPeriodicPayment(periodicValue);
      Rate rate = new Rate().withValue(rateValue);
      if (pt.isInterruptible() && !pt.isStorage()) {
        // set max curtailment
        rate.withMaxCurtailment(0.4);
      }
      if (pt.isStorage()) {
        // add a RegulationRate
        RegulationRate rr = new RegulationRate();
        rr.withUpRegulationPayment(-rateValue * 1.2)
            .withDownRegulationPayment(rateValue * 0.2); // magic numbers
        spec.addRate(rr);
      }
      spec.addRate(rate);
	  
	   return spec;
  }
  // Creates initial tariffs for the main power types. These are simple
  // fixed-rate two-part tariffs that give the broker a fixed margin.
  @SuppressWarnings("unused")
private void createInitialTariffsSampleBroker ()
  {
    // remember that market prices are per mwh, but tariffs are by kwh
    double marketPrice = marketManager.getMeanMarketPrice() / 1000.0;
    // for each power type representing a customer population,
    // create a tariff that's better than what's available
    for (PowerType pt : customerProfiles.keySet()) {
      // we'll just do fixed-rate tariffs for now
      double rateValue = ((marketPrice + fixedPerKwh) * (1.0 + defaultMargin));
      double periodicValue = defaultPeriodicPayment;
      if (pt.isProduction()) {
        rateValue = -2.0 * marketPrice;
        periodicValue /= 2.0;
      }
      if (pt.isStorage()) {
        rateValue *= 0.9; // Magic number
        periodicValue = 0.0;
      }
      if (pt.isInterruptible()) {
        rateValue *= 0.7; // Magic number!! price break for interruptible
      }
      //log.info("rateValue = {} for pt {}", rateValue, pt);
      log.info("Tariff {}: rate={}, periodic={}", pt, rateValue, periodicValue);
      TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), pt).withPeriodicPayment(periodicValue);
      Rate rate = new Rate().withValue(rateValue);
      if (pt.isInterruptible() && !pt.isStorage()) {
        // set max curtailment
        rate.withMaxCurtailment(0.4);
      }
      if (pt.isStorage()) {
        // add a RegulationRate
        RegulationRate rr = new RegulationRate();
        rr.withUpRegulationPayment(-rateValue * 1.2)
            .withDownRegulationPayment(rateValue * 0.2); // magic numbers
        spec.addRate(rr);
      }
      spec.addRate(rate);
      customerSubscriptions.put(spec, new LinkedHashMap<>());
      tariffCharges.put(spec,0d);
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
    }
  }
 
  // Checks to see whether our tariffs need fine-tuning
@SuppressWarnings("unused")
private void improveTariffs()
  {
    // quick magic-number hack to inject a balancing order
    int timeslotIndex = timeslotRepo.currentTimeslot().getSerialNumber();
    if (371 == timeslotIndex) {
      for (TariffSpecification spec :
           tariffRepo.findTariffSpecificationsByBroker(brokerContext.getBroker())) {
        if (PowerType.INTERRUPTIBLE_CONSUMPTION == spec.getPowerType()) {
          BalancingOrder order = new BalancingOrder(brokerContext.getBroker(),
                                                    spec, 
                                                    0.5,
                                                    spec.getRates().get(0).getMinValue() * 0.9);
          brokerContext.sendMessage(order);
        }
      }
      // add a battery storage tariff with overpriced regulation
      // should get no subscriptions...
      TariffSpecification spec = 
              new TariffSpecification(brokerContext.getBroker(),
                                      PowerType.BATTERY_STORAGE);
      Rate rate = new Rate().withValue(-0.2);
      spec.addRate(rate);
      RegulationRate rr = new RegulationRate();
      rr.withUpRegulationPayment(10.0)
      .withDownRegulationPayment(-10.0); // magic numbers
      spec.addRate(rr);
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
    }
    // magic-number hack to supersede a tariff
    if (380 == timeslotIndex) {
      // find the existing CONSUMPTION tariff
      TariffSpecification oldc = null;
      List<TariffSpecification> candidates =
        tariffRepo.findTariffSpecificationsByBroker(brokerContext.getBroker());
      if (null == candidates || 0 == candidates.size())
        log.error("No tariffs found for broker");
      else {
        // oldc = candidates.get(0);
        for (TariffSpecification candidate: candidates) {
          if (candidate.getPowerType() == PowerType.CONSUMPTION) {
            oldc = candidate;
            break;
          }
        }
        if (null == oldc) {
          log.warn("No CONSUMPTION tariffs found");
        }
        else {
          double rateValue = oldc.getRates().get(0).getValue();
          // create a new CONSUMPTION tariff
          TariffSpecification spec =
            new TariffSpecification(brokerContext.getBroker(),
                                    PowerType.CONSUMPTION)
                .withPeriodicPayment(defaultPeriodicPayment * 1.1);
          Rate rate = new Rate().withValue(rateValue);
          spec.addRate(rate);
          if (null != oldc)
            spec.addSupersedes(oldc.getId());
          //mungId(spec, 6);
          tariffRepo.addSpecification(spec);
          brokerContext.sendMessage(spec);
          // revoke the old one
          TariffRevoke revoke =
            new TariffRevoke(brokerContext.getBroker(), oldc);
          brokerContext.sendMessage(revoke);
        }
      }
    }
    // Exercise economic controls every 4 timeslots
    if ((timeslotIndex % 4) == 3) {
      List<TariffSpecification> candidates = tariffRepo.findTariffSpecificationsByPowerType(PowerType.INTERRUPTIBLE_CONSUMPTION);
      for (TariffSpecification spec: candidates) {
        EconomicControlEvent ece = new EconomicControlEvent(spec, 0.2, timeslotIndex + 1);
        brokerContext.sendMessage(ece);
      }
    }
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
