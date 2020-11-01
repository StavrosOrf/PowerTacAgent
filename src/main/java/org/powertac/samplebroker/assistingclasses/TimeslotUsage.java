package org.powertac.samplebroker.assistingclasses;

import java.util.ArrayList;

public class TimeslotUsage {
	
	int timeslot;
	WeatherData weather; 
	ArrayList<CustomerUsage> c;
	

	
	public TimeslotUsage(int timeslot) {
		super();
		this.timeslot = timeslot;
		this.c = new ArrayList<CustomerUsage>();
	}
	
	public int getTimeslot() {
		return timeslot;
	}
	public void setTimeslot(int timeslot) {
		this.timeslot = timeslot;
	}
	public WeatherData getWeather() {
		return weather;
	}
	public void setWeather(WeatherData weather) {
		this.weather = weather;
	}
	public ArrayList<CustomerUsage> getC() {
		return c;
	}
	public void setC(ArrayList<CustomerUsage> c) {
		this.c = c;
	}
	
	
}
