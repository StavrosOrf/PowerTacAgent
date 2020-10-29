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
package org.powertac.samplebroker.interfaces;

import org.powertac.common.CapacityTransaction;
import org.powertac.common.Competition;

/**
 * Encapsulates broker market interactions.
 * @author John Collins
 */
public interface MarketManager
{

  /**
   * Returns the mean price observed in the market
   */
  public double getMeanMarketPrice ();
  
  public int getCompetitors();
  public double[] getAvgNetusageWd();
  public double[] getAvgNetusageWe();
  public double[] getAvgClearingPriceWd();
  public double[] getAvgClearingPriceWe();
  public double getDistributionCosts();
  public void setDistributionCosts(double v);
  public double getBalancingCosts();
  public void setBalancingCosts(double v);
  public double[] getWholesaleCosts();
  public void setWholesaleCosts(double v);
  public double[] getWholesaleEnergy();
  public void setWholesaleEnergy(double v);
  public double getTotalDistributionEnergy();
  public void setTotalDistributionEnergy(double totalDistributionEnergy);
  public double getTotalBalancingEnergy();
  public void setTotalBalancingEnergy(double totalBalancingEnergy); 
  public Competition getComp();
  
  public void setUsageInBoot(double d[],double threshold);
  public void generateWeatherBootJSON();
  public double[] getNetUsagePredictorWe();
  public double[] getNetUsagePredictorWd();
  
  public CapacityTransaction[] getCapacityFees(); 
  public void resetCapacityFees();
  
  public void trainpredictor() ;
  
}