package org.powertac.samplebroker.assistingclasses;

public class CustomerUsage {
	
	
	String customerName ;
	double usage;
	int count;
	int totalConsumers;
	boolean multicontract;
	
	public CustomerUsage(String customerName, double usage, int count, int totalConsumers, boolean multicontract) {
		super();
		this.customerName = customerName;
		this.usage = usage;
		this.count = count;
		this.totalConsumers = totalConsumers;
		this.multicontract = multicontract;
	}
	public boolean isMulticontract() {
		return multicontract;
	}
	public void setMulticontract(boolean multicontract) {
		this.multicontract = multicontract;
	}
	public String getCustomerName() {
		return customerName;
	}
	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}
	public double getUsage() {
		return usage;
	}
	public void setUsage(double usage) {
		this.usage = usage;
	}
	public int getCount() {
		return count;
	}
	public void setCount(int count) {
		this.count = count;
	}
	public int getTotalConsumers() {
		return totalConsumers;
	}
	public void setTotalConsumers(int totalConsumers) {
		this.totalConsumers = totalConsumers;
	}
	
	
	
	
}
