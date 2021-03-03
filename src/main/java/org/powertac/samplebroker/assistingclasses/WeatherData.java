package org.powertac.samplebroker.assistingclasses;

public class WeatherData {

	private int hour;	  
	private int day;	 
	private int month;	  
	private int year;	
	private double temperature;
	private double windSpeed;
	private double windDirection;
	private double cloudCover;
	private int timeslot;	  
	  

	public WeatherData(int hour, int day, int month, int year, double temperature, double windSpeed,
			double windDirection, double cloudCover, int timeslot) {
		super();
		this.hour = hour;
		this.day = day;
		this.month = month;
		this.year = year;
		this.temperature = temperature;
		this.windSpeed = windSpeed;
		this.windDirection = windDirection;
		this.cloudCover = cloudCover;
		this.timeslot = timeslot;
	}

	public int getMonth() {
		return month;
	}

	public void setMonth(int month) {
		this.month = month;
	}

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
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
