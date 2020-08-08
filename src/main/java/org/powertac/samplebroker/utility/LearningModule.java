package org.powertac.samplebroker.utility;

import java.io.IOException;

public class LearningModule {
	
	public LearningModule(Process p) {
		super();
	}
	
	public static void main(String[] args) {
		getDemand();
	}

	public static void getDemand() {
		try {
			Process p = Runtime.getRuntime().exec("python hello.py.txt");
			
			System.out.println(p.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	
}

