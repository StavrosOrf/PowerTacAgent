package org.powertac.samplebroker.assistingclasses;

public class WeatherDataWithPeaks {
		
	  private int day;	 
	  private int hour;	  
	  private int timeslot;	  
	  private double temperature;
	  private double windSpeed;
	  private double windDirection;
	  private double cloudCover;
	  private double netUsageMWh;
	  private boolean isPeak;

	public WeatherDataWithPeaks(int day, int hour, int timeslot, double temperature, double windSpeed,
								double windDirection, double cloudCover, double netUsageMWh, boolean isPeak) {
		super();
		this.day = day;
		this.hour = hour;
		this.timeslot = timeslot;
		this.temperature = temperature;
		this.windSpeed = windSpeed;
		this.windDirection = windDirection;
		this.cloudCover = cloudCover;
		this.netUsageMWh = netUsageMWh;
		this.isPeak = isPeak;
	}

	public boolean isPeak() {
		return isPeak;
	}

	public void setPeak(boolean isPeak) {
		this.isPeak = isPeak;
	}

	public double getNetUsageMWh() {
		return netUsageMWh;
	}

	public void setNetUsageMWh(double netUsageMWh) {
		this.netUsageMWh = netUsageMWh;
	}

	public int getDay() {
		return day;
	}

	public void setDay(int day) {
		this.day = day;
	}

	public int getHour() {
		return hour;
	}

	public void setHour(int hour) {
		this.hour = hour;
	}

	public double getTemperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}

	public double getWindSpeed() {
		return windSpeed;
	}

	public void setWindSpeed(double windSpeed) {
		this.windSpeed = windSpeed;
	}

	public double getWindDirection() {
		return windDirection;
	}

	public void setWindDirection(double windDirection) {
		this.windDirection = windDirection;
	}

	public double getCloudCover() {
		return cloudCover;
	}

	public void setCloudCover(double cloudCover) {
		this.cloudCover = cloudCover;
	}

	public int getTimeslot() {
		return timeslot;
	}

	public void setTimeslot(int timeslot) {
		this.timeslot = timeslot;
	}
	
}
