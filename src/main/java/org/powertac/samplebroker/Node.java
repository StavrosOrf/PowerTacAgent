package org.powertac.samplebroker;

import java.util.ArrayList;
import java.util.Random;




//A Node 
public class Node{
	  public double actionID;
	  public int visitCount;
	  public int hoursAhead;
	  public double avgUnitCost;
	  public Node parent;
	  public ArrayList<Node> children;
	  
	  
	  public static int MAX_ITERATIONS = Parameters.MAX_ITERATIONS;
	  public static int NUM_OF_ACTIONS = Parameters.NUM_OF_ACTIONS;
	  public static int NO_BID = Parameters.NO_BID;
	 
	  public static double OBSERVED_DEVIATION = Parameters.OBSERVED_DEVIATION;
	  public static double D_MIN = Parameters.D_MIN;
	  public static double D_MAX = Parameters.D_MAX;
	  
	  public Node(double id, Node parent,int visitcount,int hoursAhead) {
		  this.actionID = id;
		  this.parent = parent;
		  this.visitCount = visitcount;
		  this.hoursAhead = hoursAhead; 
		  avgUnitCost = 0;
		  children = new ArrayList<Node>();
		  
	  }
	  
	  public void generateRootsKids(double limitPrice) {
		  double minPrice = limitPrice + D_MIN*OBSERVED_DEVIATION;
		  double maxPrice = limitPrice + D_MAX*OBSERVED_DEVIATION;
		  
//		  //temporary fix, may need to  change
//		  if(minPrice >= 0 && maxPrice >= 0) {
//			  minPrice -= maxPrice;
//			  maxPrice = -1;
//		  }
		  
		  double step =  (maxPrice-minPrice)/(NUM_OF_ACTIONS-1);
		  
		  for (int i = 0; i < NUM_OF_ACTIONS; i++) {
			  double bid = minPrice + step*i;
			  
			  Node n = new Node(bid, this, 0, hoursAhead);
			  children.add(n);
		  }
		  // add a NO_BID action
		  Node n = new Node(NO_BID, this, 0, hoursAhead);
		  children.add(n);
	  }
	  public void generateNodeKids(double limitPrice) {
		  double minPrice = limitPrice + D_MIN*OBSERVED_DEVIATION;
		  double maxPrice = limitPrice + D_MAX*OBSERVED_DEVIATION;
		  
		  double step =  (maxPrice-minPrice)/(NUM_OF_ACTIONS-1);
		  
		  for (int i = 0; i < NUM_OF_ACTIONS; i++) {
			  double bid = minPrice + step*i;
			  if(bid > 0 ) {
				  continue;
			  }

			  Node n = new Node(bid, this, 0, hoursAhead - 1);
			  children.add(n);
		  }
		  
		  // add a NO_BID action
		  Node n = new Node(NO_BID, this, 0, hoursAhead - 1);
		  children.add(n);
	  }
	  
	  public boolean hasUnvisitedKidNodes() {
		  for (Node c : this.children) {
			  if(c.visitCount == 0) {
				 return true;
			  }
		  }
		  
		  return false;
	  }
	  
	  //Return a random unvisited child node
	  public Node getRandomUnvisitedChild() {
		  if( children.isEmpty()) {
			  //this.generateNodeKids(limitPrice);
		  }
		  
		  ArrayList<Node> tempList = new ArrayList<Node>();
		  
		  for (Node c : this.children) {
			  if(c.visitCount == 0) {
				 tempList.add(c);
			  }
		  }
		  
		  if(tempList.size() > 1 ) {
			  Random rand = new Random(); 
			  return tempList.get(rand.nextInt(tempList.size()));
		  }
		  
		  return tempList.get(0);
		  
	  }
	  //return the Child Node with the Highest UCT value
	  public Node getBestUCTChild(double CbalUnitPrice) {
		  Node bestNode = null;
		  double uctMax = -100;
		  
		  double t,uct;
		  int sm;
		  
		  for(Node n : children) {
			  t = 1 - (n.avgUnitCost/CbalUnitPrice);
			  
			  if(n.visitCount == 0) {
				  sm = 1;
			  }else {
				  sm = 0;
			  }
			// may need to add a small + e for ties
			  uct = t + Math.sqrt((2*Math.log10(n.parent.visitCount + sm))/(n.visitCount + sm)); 
			  if(uct > uctMax) {
				  uctMax = uct;
				  bestNode = n;
			  }
		  }
		  return bestNode;
	  }
	  
	  public String toString() {
		  String tmp = String.valueOf(this.hashCode()).substring(0,4);
		  
		  
		  if(parent == null) {
			  return "Node: " + tmp + " level: "+ hoursAhead + " parent: No parent"   + " actionId: "+ actionID 
					  + " visitCount: "+ visitCount + " avgUnitCost: " + avgUnitCost;
		  }
		  String tmpParent = String.valueOf(this.parent.hashCode()).substring(0,4);
		  
		  return "Node: " + tmp + " level: "+ hoursAhead + " parent: " + tmpParent  + " actionId: "+ actionID 
				  + " visitCount: "+ visitCount + " avgUnitCost: " + avgUnitCost;
	  }
}
