package org.powertac.samplebroker;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.derby.jdbc.EmbeddedDriver;
import org.powertac.common.Broker;
import org.powertac.common.Rate;
import org.powertac.common.RegulationRate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;


public class Database {
    Connection conn = null;
    PreparedStatement pstmt;
    Statement stmt,stmt2;
    ResultSet rs = null;
    ResultSet res = null;
    
	   public static void main(String[] args) {
	      Database e = new Database();
//	      e.InitiateDerby();
//	      ArrayList<Rate> r = new ArrayList<Rate>();
//	      ArrayList<RegulationRate> rr = new ArrayList<RegulationRate>();
//	      r.add(new Rate());
//	      rr.add(new RegulationRate());
//	      r.add(new Rate());
//	      e.addTariff("123",PowerType.CONSUMPTION, 10, 0, 0, 5, 5, 10005, 0,r,rr); 
//	      e.getBestTariff(20, PowerType.CONSUMPTION, new Broker("st"));
//	      System.out.println("-------------");
//	      System.out.println("Number of records: "+e.getNumberOfRecords(PowerType.CONSUMPTION));
//	      e.getBestTariff(2, PowerType.CONSUMPTION, new Broker("st"));
//	      System.out.println("-------------");
//	      e.deleteWorstTariff(PowerType.CONSUMPTION);
//	      System.out.println("Number of records: "+e.getNumberOfRecords(PowerType.CONSUMPTION,"mc0"));
//	      e.getBestTariff(20, PowerType.CONSUMPTION, new Broker("st"));
	      e.shutdown();
	   }
	   
	   
	   public Database() {
	       Driver derbyEmbeddedDriver = new EmbeddedDriver();
	       try {
	    	   DriverManager.registerDriver(derbyEmbeddedDriver);
		       conn = DriverManager.getConnection("jdbc:derby:tariffRepo;create=true");
		       conn.setAutoCommit(false);
		       stmt = conn.createStatement();
		       stmt2 = conn.createStatement();
			} catch (SQLException ex) {
		         System.out.println("in connection" + ex);
		      }

	   }


	public void InitiateDerby() {
  
	      String createSQL = "CREATE TABLE tariffs (\n" + 
	    	    "   id integer not null generated always as" +
	    	    "   identity (start with 1, increment by 1), "+
	      		"	broker VARCHAR(30) NOT NULL,\n" + 
	      		"	powerType VARCHAR(30) NOT NULL,\n" + 
	      		"	contractLength INTEGER NOT NULL,\n" + 
	      		"	ewPenalty DOUBLE NOT NULL,\n" + 
	      		"	signupBonus DOUBLE NOT NULL,\n" + 
	      		"	periodicPayment DOUBLE NOT NULL,\n" + 
	      		"	tariffRate DOUBLE NOT NULL,\n" +
	      		"	fitnessValue DOUBLE NOT NULL,\n" +
	      		"	hasRate INTEGER NOT NULL,\n" +
	      		"	hasRegRate INTEGER NOT NULL,\n" +
	      		"	level INT NOT NULL,\n" +
	      		"	timeslot INTEGER NOT NULL,\n" +
	      		"	Brokers INTEGER NOT NULL,\n" +
	      		"	isInitial BOOLEAN NOT NULL\n" +
	      		")";
	      
	      String createSQLRate = "CREATE TABLE rate (\n" + 
		    	    "   id integer not null generated always as" +
		    	    "   identity (start with 1, increment by 1), "+
		      		"	tarrifId INTEGER NOT NULL,\n" + 
		      		"	weeklyBegin INTEGER NOT NULL,\n" +
		      		"	weeklyEnd INTEGER NOT NULL,\n" + 
		      		"	dailyBegin INTEGER NOT NULL,\n" + 
		      		"	dailyEnd INTEGER NOT NULL,\n" + 
		      		"	minValue DOUBLE NOT NULL,\n" + 
		      		"	maxValue DOUBLE NOT NULL,\n" + 
		      		"	expectedMean DOUBLE NOT NULL,\n" +
		      		"	maxCurtailment DOUBLE NOT NULL,\n" +
		      		"	tierThreshold DOUBLE NOT NULL\n" +
		      		")";

	      String createSQLRegRate = "CREATE TABLE RegRate (\n" + 
		    	    "   id integer not null generated always as" +
		    	    "   identity (start with 1, increment by 1), "+
		      		"	tarrifId INTEGER NOT NULL,\n" + 
		      		"	upRegulationPayment DOUBLE NOT NULL,\n" + 
		      		"	downRegulationPayment DOUBLE NOT NULL\n" + 

		      		")";

	      try {
	    	  
		     stmt.execute("drop table Tariffs");
		     stmt.execute("drop table rate");
		     stmt.execute("drop table regrate");
	         stmt.execute(createSQL);
	         stmt.execute(createSQLRate);
	         stmt.execute(createSQLRegRate);

	         conn.commit();
	         return;

	      } catch (SQLException ex) {
	         System.out.println("in Creation" + ex);
	      }
	}

	public void shutdown() {
	      try {
	    	  
		         DriverManager.getConnection("jdbc:derby:;shutdown=true");
		      } catch (SQLException ex) {
		         if (((ex.getErrorCode() == 50000) &&
		            ("XJ015".equals(ex.getSQLState())))) {
		               System.out.println("Derby shut down normally");
		         } else {
		            System.err.println("Derby did not shut down normally");
		            System.err.println(ex.getMessage());
		         }
		      }
	}
	public ArrayList<TariffSpecification> getBestTariff(int n, PowerType powerType,Broker b,int level ,boolean isInitial,int numberOfBrokers) {
		ArrayList<TariffSpecification> t = new ArrayList<TariffSpecification>();
		TariffSpecification spec = new TariffSpecification(b, powerType);
		//System.out.println("1");
		int counter = 0;
		try{
			rs = stmt.executeQuery("select * from tariffs where powerType = '" +powerType.toString() 
									+"' and level = " + level + " and isInitial = " + isInitial 
									+ " and brokers = " + numberOfBrokers + " order by fitnessValue desc ");
			
	        while (rs.next() && counter < n) {

	           spec.withEarlyWithdrawPayment(rs.getDouble(5));
	           spec.withPeriodicPayment(rs.getDouble(7));
	           spec.withSignupPayment(rs.getDouble(6));
	           spec.withMinDuration(rs.getInt(4));
	           counter ++;
	           
	           res = stmt2.executeQuery("select * from RegRate where tarrifId = " + rs.getInt(1));
	           while(res.next()) {
	        	   RegulationRate rr = new RegulationRate();
	        	   rr.withDownRegulationPayment(res.getDouble(4));
	        	   rr.withUpRegulationPayment(res.getDouble(3));
	        	   spec.addRate(rr);
	           }
	           
	           res = stmt2.executeQuery("select * from rate where tarrifId = " + rs.getInt(1));
	           while(res.next()) {
	        	   Rate r = new Rate();
	        	   r.withWeeklyBegin(res.getInt(3));
	        	   r.withWeeklyEnd(res.getInt(4));
	        	   r.withDailyBegin(res.getInt(5));
	        	   r.withDailyEnd(res.getInt(6));
	        	   r.withMinValue(res.getDouble(7));
	        	   r.withMaxValue(res.getDouble(8));
	        	   r.withExpectedMean(res.getDouble(9));
	        	   r.withMaxCurtailment(res.getDouble(10));
	        	   r.withTierThreshold(res.getDouble(11));

	        	   spec.addRate(r);
	           }
	           
	           t.add(spec);
	        }
		} catch (SQLException ex) {
	         System.out.println(" find: in connection" + ex);
	    }


		 return t;
	}

	public void storeRate(int id,Rate r) {
		
		try {
			pstmt = conn.prepareStatement("insert into rate(tarrifId,weeklyBegin,weeklyEnd,dailyBegin"
					+ ",dailyEnd,minValue,maxValue,expectedMean,maxCurtailment,tierThreshold)"
					+ " values(?,?,?,?,?,?,?,?,?,?)");
			pstmt.setInt(1, id);
			pstmt.setInt(2, r.getWeeklyBegin());
			pstmt.setInt(3, r.getWeeklyEnd());
			pstmt.setInt(4, r.getDailyBegin());
			pstmt.setInt(5, r.getDailyEnd());
			pstmt.setDouble(6, r.getMinValue());
			pstmt.setDouble(7, r.getMaxValue());
			pstmt.setDouble(8, r.getExpectedMean());
			pstmt.setDouble(9, r.getMaxCurtailment());
			pstmt.setDouble(10, r.getTierThreshold());
			
			pstmt.executeUpdate();
			
			conn.commit();			
		} catch (SQLException ex) {
	         System.out.println(" rate in connection" + ex);
		}

	}
	
	public void storeRegRate(int id,RegulationRate r) {
		
		try {
			pstmt = conn.prepareStatement("insert into RegRate(tarrifId, upRegulationPayment,downRegulationPayment) values(?,?,?)");
			pstmt.setInt(1, id);
			pstmt.setDouble(2, r.getUpRegulationPayment());
			pstmt.setDouble(3, r.getDownRegulationPayment());
			
			pstmt.executeUpdate();
			
			conn.commit();			
		} catch (SQLException ex) {
	         System.out.println(" regrate in connection" + ex);
		}

	}
	//Function to Smooth the fitnessvalue of tariffs
	public void decayFittnessValue(int level) {
		
		try {
						
			if(level == 0) {
				stmt.executeUpdate("Update tariffs set fitnessValue = tariffs.fitnessValue - tariffs.fitnessValue * "
									+Parameters.GroundLevelDecayFactor + "where fitnessValue != -1 ");
			}else if(level == 1) {
				stmt.executeUpdate("Update tariffs set fitnessValue = tariffs.fitnessValue - tariffs.fitnessValue * "
						+Parameters.TourLevelDecayFactor + "where fitnessValue != -1 ");
			}else {
				System.out.println("\n ERRORR \n\n\n\n\n\n");
			}
			
			conn.commit();
		} catch (SQLException ex) {
	         System.out.println("Decay: in connection" + ex);
		}
	}
	   
	 public void addTariff(String broker,PowerType powerType,int contractLength,
        		double ewPenalty,double signupBonus,double periodicPayment,double tariffRate,
        		double fitnessValue, int level,List<Rate> rates,List<RegulationRate> regrates,int tms,int brokerNum,boolean isInitial) {
		   	
		 try 
		 {
			 if(getNumberOfRecords(powerType,broker,level,isInitial,brokerNum) > Parameters.NUM_OF_POPULATION) {
				 deleteWorstTariff(powerType,broker,level,isInitial,brokerNum);
			 }
			
			pstmt = conn.prepareStatement("insert into Tariffs(broker,powerType,contractLength,"
			     		+ "ewPenalty,signupBonus,periodicPayment,tariffRate,fitnessValue,"
			     		+ "hasRate,hasRegRate,level,timeslot,Brokers,isInitial) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			pstmt.setString(1, broker);
		    pstmt.setString(2, powerType.toString());
		    pstmt.setInt(3, contractLength);
		    pstmt.setDouble(4, ewPenalty);
		    pstmt.setDouble(5, signupBonus);
		    pstmt.setDouble(6, periodicPayment);
		    pstmt.setDouble(7, tariffRate);
		    pstmt.setDouble(8, fitnessValue);
		    
		    boolean hasRate = false;
		    boolean hasRegRate = false;
		    
			if(!rates.isEmpty()) {
				hasRate = true;
			}
			if(!regrates.isEmpty()) {
				hasRegRate = true;
			}
			
		    if(hasRate == true) {
		    	pstmt.setInt(9, 1);
		    }else {
		    	pstmt.setInt(9, 0);
		    }
		    if(hasRegRate == true) {
		    	pstmt.setInt(10, 1);
		    }else {
		    	pstmt.setInt(10, 0);
		    }
		    
		    pstmt.setInt(11, level);
		    pstmt.setInt(12, tms);
		    pstmt.setInt(13, brokerNum);
		    pstmt.setBoolean(14,isInitial);
		    
		    pstmt.executeUpdate();
		    
		    rs = stmt.executeQuery("values IDENTITY_VAL_LOCAL()");
		    rs.next();
		    int lastAddedId = rs.getInt(1);
		    
		    for (Rate rc : rates) {
					storeRate(lastAddedId, (Rate)rc);
		    }
		    for (RegulationRate rc : regrates) {
					storeRegRate(lastAddedId,rc);
		    }
			
		    
		    
		    conn.commit();
		 } catch (SQLException ex) {
		         System.out.println("in connection" + ex);
		 }
	       
	   }
	 
	public void deleteWorstTariff(PowerType pt,String Broker,int level ,boolean isInitial,int numberOfBrokers) {
		
		try {
			rs = stmt.executeQuery("select fitnessValue,id from tariffs where powerType = '" +pt.toString() 
			+"' and broker = '"+ Broker +"' and level = " + level + " and isInitial = " + isInitial 
			+ " and brokers = " + numberOfBrokers + "  order by fitnessValue asc ");
			rs.next();
//			double worstFittnessValue = rs.getDouble(1);
			int id =rs.getInt(2);
//			System.out.println(worstFittnessValue+ "  , "+ id);
			
			stmt.executeUpdate("DELETE from tariffs where powerType = '" +pt.toString() 
			+"' and id = "+id);
			
			stmt.executeUpdate("DELETE from rate where tarrifId = " +id);
			
			stmt.executeUpdate("DELETE from RegRate where tarrifId = " +id);
			
			conn.commit();
		} catch (SQLException ex) {
	         System.out.println("DELETE: in connection" + ex);
		}
	}
	
	public void resetGameLevel(int level) {
		try {
			res = stmt.executeQuery("select fitnessValue,id from tariffs where level = " + level);
			int id;
			
			ArrayList<Integer> temp = new ArrayList<Integer>();
			while(res.next()) {
				id =res.getInt(2);
				
				temp.add(id);
							
			}
			while(!temp.isEmpty()) {
				id = temp.get(0);
				temp.remove(0);
				
				stmt.executeUpdate("DELETE from tariffs where id = "+id);
				
				stmt.executeUpdate("DELETE from rate where tarrifId = " +id);
				
				stmt.executeUpdate("DELETE from RegRate where tarrifId = " +id);
			}


			conn.commit();
		} catch (SQLException ex) {
	         System.out.println("DELETE Level: in connection" + ex);
		}
	}
	
	public int getNumberOfRecords(PowerType pt, String brokerName,int level ,boolean isInitial,int numberOfBrokers) {
		
		try {
			if( brokerName == "*") {
				rs = stmt.executeQuery("select count(*) from tariffs where powerType = '" +pt.toString() 
				+"' ");
			}else {
				rs = stmt.executeQuery("select count(*) from tariffs where powerType = '" +pt.toString() 
				+"' and broker = '" + brokerName + "' and level = " + level + " and isInitial = " + isInitial 
				+ " and brokers = " + numberOfBrokers );
			}


			rs.next();
//			System.out.println(rs.getInt(1));
			return rs.getInt(1);
			
		} catch (SQLException ex) {
	         System.out.println("number: in connection" + ex);
		}
		
		return -1;
		
	}
}
