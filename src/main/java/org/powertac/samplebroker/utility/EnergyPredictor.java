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
package org.powertac.samplebroker.utility;
 
import java.net.*;
 
import org.powertac.samplebroker.Parameters;
 
import java.io.*;
 
public class EnergyPredictor {
	private int failure_counter = 0;
	private static int ALLOWED_TIMEOUTS = 15;
       
    public EnergyPredictor() {

        }
 
        public static void main(String args[]) {
               
                 Socket clientSocket;
                    PrintWriter out;
                    BufferedReader in;
                   
                        try {
                                clientSocket = new Socket("localhost", 8098);
                                clientSocket.setSoTimeout(1500);
                            out = new PrintWriter(clientSocket.getOutputStream(), true);
                            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            System.out.println("sending");
                        out.println("reset");
                        System.out.println("waiting..");
                        String resp;
                        while((resp = in.readLine()) != null) {                        
                                if(resp.equals("ok")) {
                                        System.out.println("Succesfully deleted old models.");
                                        break;
                                }  
                        }
                        clientSocket.close();
                        if(out != null) {
                                out.close();
                        }
                        if(in != null) {
                                in.close();
                        }
                       
                        } catch (UnknownHostException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (IOException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        }
                       
                        try {
                                clientSocket = new Socket("localhost", 8098);
                                clientSocket.setSoTimeout(1500000);
                            out = new PrintWriter(clientSocket.getOutputStream(), true);
                            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            System.out.println("sending");
                        out.println("boot_train");
                        System.out.println("waiting..");
                        String resp;
                        while((resp = in.readLine()) != null) {
 
                                if(resp.equals("ok")) {
                                        System.out.println("Succesfully Trained models.");
                                        clientSocket.close();
                                        if(out != null) {
                                                out.close();
                                        }
                                        if(in != null) {
                                                in.close();
                                        }
                                }
                        }                          
                       
                        } catch (UnknownHostException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (IOException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        }
               
                System.out.println("training done");
               
//              en.getKWhPredictionLSTMClient(1,1);
 
    }
 
    public double[] predict(){
        double[] results = new double[24];
        for(int i = 0; i < 24; i++) {
        	results[i] = -1;
        }
        int counter = 0;
        Socket clientSocket;
        PrintWriter out;
        BufferedReader in;
        
        if(failure_counter > ALLOWED_TIMEOUTS) {
        	return results;
        }
        
        try {
            clientSocket = new Socket("localhost", Parameters.Predictor_Port);
            clientSocket.setSoTimeout(1000);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//            System.out.println("sending");
            out.println("predict");
//            System.out.println("waiting..");
            String resp;
            while((resp = in.readLine()) != null) {
//                System.out.println("Resp: " + resp);
                if(resp.equals("ok")) {
                    clientSocket.close();
                    if(out != null) {
                        out.close();
                    }
                    if(in != null) {
                        in.close();
                    }
//                    for(double d : results){
//                        System.out.println(d);
//                    }
                    return results;
                }else {
                    resp = resp.replaceAll("\\[", "");
                    resp = resp.replaceAll("\\]", "");
                    resp = resp.trim();
 
                    String arr[] = resp.split("\\s+");
                    for(String s : arr) {
                        results[counter] = Double.parseDouble(s);
                        counter ++;
                    }
                }
            }
 
            out.close();
            in.close();
 
        } catch (Exception e) {
//            e.printStackTrace();
        	failure_counter ++;
        	System.out.println("Timeout in prediction!");
        }
        return results;
    }
 
    public void retraintData(int threshold){
        Socket clientSocket;
        PrintWriter out;
        BufferedReader in;
        
        if(failure_counter > ALLOWED_TIMEOUTS) {
        	return ;
        }
        
        try {
            clientSocket = new Socket("localhost", Parameters.Predictor_Port);
            clientSocket.setSoTimeout(1000);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//            System.out.println("sending");
            out.println("retrain " + threshold);
//            System.out.println("waiting..");
            String resp;
            while((resp = in.readLine()) != null) {
                System.out.println("Response: " + resp);
                if(resp.equals("ok")) {
                    System.out.println("Succesfully Re-Trained models.");
                    clientSocket.close();
                    break;
                }
            }
 
            out.close();
            in.close();
 
        } catch (Exception e) {
        	failure_counter ++;
        	System.out.println("Timeout in re-train!");
//            e.printStackTrace();
        }
    }
    
    public void trainBootData(){
        Socket clientSocket;
        PrintWriter out;
        BufferedReader in;
        
        if(failure_counter > ALLOWED_TIMEOUTS) {
        	return ;
        }
        
        try {
            clientSocket = new Socket("localhost", Parameters.Predictor_Port);
            clientSocket.setSoTimeout(500);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            System.out.println("sending");
            out.println("boot_train");
            System.out.println("waiting..");
            String resp;
            while((resp = in.readLine()) != null) {
                System.out.println("Response: " + resp);
                if(resp.equals("ok")) {
                    System.out.println("Succesfully Trained models.");
                    clientSocket.close();
                    break;
                }
            }
 
            out.close();
            in.close();
 
        } catch (Exception e) {
        	failure_counter ++;
        	System.out.println("Timeout in train!");
//            e.printStackTrace();
        }
    }
    
    public void fitData(){
        Socket clientSocket;
        PrintWriter out;
        BufferedReader in;
        
        if(failure_counter > ALLOWED_TIMEOUTS) {
        	return ;
        }
        
        try {
            clientSocket = new Socket("localhost", Parameters.Predictor_Port);
            clientSocket.setSoTimeout(1500);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//            System.out.println("sending");
            out.println("fit");
//            System.out.println("waiting..");
            String resp;
            while((resp = in.readLine()) != null) {
//                System.out.println("Response: " + resp);
                if(resp.equals("ok")) {
//                    System.out.println("Succesfully fitted models.");
                    clientSocket.close();
                    break;
                }
            }
 
            out.close();
            in.close();
 
        } catch (Exception e) {
        	failure_counter ++;
        	System.out.println("Timeout in fit!");
//            e.printStackTrace();
        }
    }
 
    public void resetModels(){
        Socket clientSocket;
        PrintWriter out;
        BufferedReader in;
 
        try {
            clientSocket = new Socket("localhost", Parameters.Predictor_Port);
            clientSocket.setSoTimeout(1500);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            System.out.println("sending");
            out.println("reset");
            System.out.println("waiting..");
            String resp;
            while((resp = in.readLine()) != null) {
                if(resp.equals("ok")) {
                    System.out.println("Successfully deleted old models.");
                    break;
                }
            }
            clientSocket.close();
            out.close();
            in.close();
 
        } catch (Exception e) {
//            e.printStackTrace();
        	System.out.println("Timeout in reset models!");
        }
    }
       
        public double[] getKWhPredictionLSTMClient(int hour, int day) {
                double result[] = new double[24];
               
            Socket clientSocket;
            PrintWriter out;
            BufferedReader in;
            int counter = 0;
 
                try {
                        clientSocket = new Socket("localhost", 8098);
                        clientSocket.setSoTimeout(1500);
                    out = new PrintWriter(clientSocket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//                  System.out.println("sending");
                out.println("predict");
//              System.out.println("waiting..");
                String resp;
                while((resp = in.readLine()) != null) {
//                      System.out.println(resp);
                        if(resp.equals("-")) {
                                clientSocket.close();
                                if(out != null) {
                                        out.close();
//                                      System.out.print("c1");
                                }
                                if(in != null) {
                                        in.close();
//                                      System.out.print("c2");
                                }
                                return result;
                        }else {
                                resp = resp.replaceAll("\\[", "");
                                resp = resp.replaceAll("\\]", "");
                                resp = resp.trim();                    
                               
                                String arr[] = resp.split("\\s+");
                                for(String s : arr) {
                                        result[counter] = Double.parseDouble(s);
                                    counter ++;
                                }                  
                        }
                }
                   
 
                } catch (UnknownHostException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                }
           
               
                return result;
        }
        /*public double[] getKWhPredictionLSTMClient(int hour, int day) {
                double result[] = new double[24];
               
            Socket clientSocket;
            PrintWriter out;
            BufferedReader in;
            int counter = 0;
 
                try {
                        clientSocket = new Socket("localhost", 8098);
                        clientSocket.setSoTimeout(1500);
                    out = new PrintWriter(clientSocket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//                  System.out.println("sending");
                out.println(day + " "+ hour);
//              System.out.println("waiting..");
                String resp;
                while((resp = in.readLine()) != null) {
//                      System.out.println(resp);
                        if(resp.equals("-")) {
                                clientSocket.close();
                                if(out != null) {
                                        out.close();
//                                      System.out.print("c1");
                                }
                                if(in != null) {
                                        in.close();
//                                      System.out.print("c2");
                                }
                                return result;
                        }else {
                                resp = resp.replaceAll("\\[", "");
                                resp = resp.replaceAll("\\]", "");
                                resp = resp.trim();                    
                               
                                String arr[] = resp.split("\\s+");
                                for(String s : arr) {
                                        result[counter] = Double.parseDouble(s);
                                    counter ++;
                                }                  
                        }
                }
                   
 
                } catch (UnknownHostException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                }
           
               
                return result;
        }
*/
 
   
   
    public void getKWhPredictorScript() {
        Process p;
                try {
//                      p = Runtime.getRuntime().exec("python3 dataMWH.py 1 20 11.7 5 330 0.5");
                        p = Runtime.getRuntime().exec("python prediction.py ");
                        BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        String s ;
//                      double d;
//                      System.out.println(stdInput.readLine());
//                      stdInput.readLine();
                        while ((s = stdInput.readLine()) != null) {                                                    
                System.out.print(" " + s);
 
//                s = s.split("\\[",2)[1].split("\\[",2)[1].split("\\]",2)[0].split("\\]",2)[0];
//                System.out.println(s);
//                d =  Double.parseDouble(s);
                        }
                       
                        stdInput.close();
                } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                }
                System.out.println();
    }

	public int getFailure_counter() {
		return failure_counter;
	}

	public void setFailure_counter(int failure_counter) {
		this.failure_counter = failure_counter;
	}

	public static int getALLOWED_TIMEOUTS() {
		return ALLOWED_TIMEOUTS;
	}

	public static void setALLOWED_TIMEOUTS(int aLLOWED_TIMEOUTS) {
		ALLOWED_TIMEOUTS = aLLOWED_TIMEOUTS;
	}
    
    
}