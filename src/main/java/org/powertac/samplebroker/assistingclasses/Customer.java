package org.powertac.samplebroker.assistingclasses;

import org.powertac.common.enumerations.PowerType;
import org.powertac.common.xml.DoubleArrayConverter;
import com.thoughtworks.xstream.annotations.XStreamConverter;

public class Customer {
	
	  private String customerName;

	  private String powerType;

	  @XStreamConverter(DoubleArrayConverter.class)
	  private double[] netUsage;

	public Customer(String customerName, PowerType powerType, double[] netUsage) {
		super();
		this.customerName = customerName;
		this.powerType = powerType.toString();
		this.netUsage = netUsage;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public double[] getNetUsage() {
		return netUsage;
	}

	public String getPowerType() {
		return powerType;
	}

	public void setPowerType(String powerType) {
		this.powerType = powerType;
	}

	public void setNetUsage(double[] netUsage) {
		this.netUsage = netUsage;
	}
	  
	
	  
	  
	  
}
