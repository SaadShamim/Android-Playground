package com.example.wifiscan;

import java.util.LinkedList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.widget.Toast;


public class grepWifi extends BroadcastReceiver {
	  WifiscanActivity wifiActivity;
	  LinkedList <String>wifi_link;
	  Boolean justStarted = true;

	  public grepWifi(WifiscanActivity wa) {
	    super();
	    wifiActivity = wa; //keep an instance of main class
	    
	    buildLinkedList(); //setup linked list
	  }
	  
	  //create a new linked list to manage that only 10 wap can show at one time
	  public void buildLinkedList(){
		  wifi_link=new LinkedList<String>();
	  }
	  
	  //add to the front of list and remove the last element if size greater then 10
	  public void addToList(String element){
		wifi_link.addFirst(element);
		if(wifi_link.size() > 10){
			wifi_link.removeLast();
		}
	  }
	  
	  //print the 10 wap on screen
	  public void printAll(){
		  if(wifi_link.size() > 10){
			  wifiActivity.makeToast("Unexpected Error");
		  }else{
			  wifiActivity.wifiInfo.setText("");
			  for(int i = 0; i<wifi_link.size();i++){
				  wifiActivity.wifiInfo.append(wifi_link.get(i) + "\n");
			  }
		  }
	  }
	  
	  //handle wifi
	  @Override
	  public void onReceive(Context c, Intent intent) {
	    List<ScanResult> results = wifiActivity.wifi.getScanResults();
	    if(results != null){
	    for (ScanResult result : results) {
	    	
	    	//get the last known location from gps
	    	Location loc = wifiActivity.lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
	  	    String lon;
	  	    String lat;
	  	    
	  	    //set longitude and latitude values
	  	      if(loc != null){
	  	    	lon = String.valueOf(loc.getLongitude()) + ", ";
	  	    	lat = String.valueOf(loc.getLatitude()) + ", ";
	  	      }else{
	  	    	  lon = "0, ";
	  	    	  lat = "0, ";
	  	      }
	  	    
	  	    //get current time in seconds
	    	String timeStamp = String.valueOf((System.currentTimeMillis())/1000) + ":";
	    	wifiActivity.lastScan.setText(timeStamp + "secs"); //update text field with new time
	    	//get entire wifi string
	    	String entry = timeStamp + " WiFi, " + lat + lon + result.BSSID + ", " + result.frequency + ", " + result.level + "\n";
	    	wifiActivity.writeToFile(entry); //write entry to file trace
	    	addToList(result.BSSID); //add to list for display
	    } //END FOR LOOP
	    	printAll(); //print list to show
	    }else{
	    	//OUT OF RANGE
	    	String timeStamp = String.valueOf((System.currentTimeMillis())/1000) + ":";
	    	
	    	Location loc = wifiActivity.lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
	  	    String lon;
	  	    String lat;
	  	    
	  	      if(loc != null){
	  	    	lon = String.valueOf(loc.getLongitude());
	  	    	lat = String.valueOf(loc.getLatitude()) + ", ";
	  	      }else{
	  	    	  lon = "0";
	  	    	  lat = "0, ";
	  	      }
	  	    
	  	     //make a toast entry saying that wifi is out of coverage
	    	String entry_ooc = timeStamp + " OOC, " +  lat + lon;
	    	wifiActivity.makeToast(entry_ooc);
	    	wifiActivity.writeToFile(entry_ooc+"\n"); //write entry to file trace
	    }
	  }

}
