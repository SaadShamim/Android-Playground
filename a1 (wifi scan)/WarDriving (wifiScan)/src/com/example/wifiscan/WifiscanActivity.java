package com.example.wifiscan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class WifiscanActivity extends Activity {
   /** Called when the activity is first created. */
	
	TextView wifiInfo;
	WifiManager wifi;
	BroadcastReceiver receiver;
	LocationListener locl;
	LocationManager lm;
	Timer timer;
	Boolean started = false;
	Button start_button;
	EditText intervalTime;
	int interval; //3 minutes == 180000ms
	Button interval_button;
	Button uploadTrace_button;
	TextView isConnected;
	TextView lastScan;
	Handler mHandler = new Handler();


	 
   @Override
   public void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       
       setContentView(R.layout.main);
       
       //setup wifimanager
       wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
       wifi.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "myDebuggingTag");
       
       //set initial interval time in ms
       interval = 180000;
       
       //create obj representation of obj in layout
       intervalTime = (EditText) findViewById(R.id.intervalText_text);
       wifiInfo = (TextView) findViewById(R.id.wifiInfo_text);
       isConnected = (TextView) findViewById(R.id.isConnected_text);
       lastScan = (TextView) findViewById(R.id.lastScan_text);
      
       //set initial last scan
       lastScan.setText("NOPE!"); 

      
      //checks current connection status
      ConnectivityManager conManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo conInfo = conManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
  
      if(conInfo.isConnected()){
    	 isConnected.setText("Connected");
      }else{
    	 isConnected.setText("Not Connected");
      }
   
      //initialize broadcast receiver object
      if (receiver == null){
    	  receiver= new grepWifi(this);
      }     
      
      //setup gps listeners
      locl = new locationListeners();
      lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this.locl);
    
      
      //set startup button action to call startstop() function
     start_button = (Button) findViewById(R.id.start_button);
     start_button.setOnClickListener(new View.OnClickListener() {
		
		//@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			startstop();
			
		}
	});
     
     //set the timer interval
     interval_button = (Button) findViewById(R.id.intervalSubmit);
     interval_button.setOnClickListener(new View.OnClickListener() {
		
		//@Override
		public void onClick(View v) {
			try {
				//convert seconds to ms
			    interval = Integer.parseInt(intervalTime.getText().toString())*1000;
			} catch(NumberFormatException err) {
			   System.out.println(err);
			} 
			
		}
	});
     
     //call readFromFile() function to upload trace file
     uploadTrace_button = (Button) findViewById(R.id.uploadTrace);
     uploadTrace_button.setOnClickListener(new View.OnClickListener() {
		
		//@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			// sendData();
			readFromFile();
		}
	});
     
     //remove any queues from thread
     mHandler.removeCallbacks(mTimerFunction);   
   }
   
   //Timer function, gets wap at time "interval"
   private Runnable mTimerFunction = new Runnable() {
	   public void run() {
		   wifi.startScan();
	       mHandler.postDelayed(this, interval);
	   }
	};
	
	//a quick function to display toast widget
	public void makeToast(String ooc){
		Toast.makeText(this, ooc, Toast.LENGTH_LONG).show();
	}
   
	//handles the starting and stopping for the app
   public void startstop(){
	   //to stop app
	   if(started){
		   started = false;
		   Toast.makeText(this, "Stoping", Toast.LENGTH_LONG).show();
		   unregisterReceiver(receiver); //unregister wifi receiver
		   mHandler.removeCallbacks(mTimerFunction); //stop timer
	   }else{ //to start app
		   started = true;
		   Toast.makeText(this, "Starting", Toast.LENGTH_LONG).show();
		   registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)); //register wifi receiver
		   mHandler.postDelayed(mTimerFunction, interval); //resume timer
	   }
   }
   
   //write to trace file on sd
   public void writeToFile(String entry){   
	   try {
		   File root = Environment.getExternalStorageDirectory(); //get location
		   	if (root.canWrite()){ //check if writable
			  File traceFile = new File(root, "trace.txt"); //get file
			  FileWriter traceWriter = new FileWriter(traceFile,true);
			  BufferedWriter out = new BufferedWriter(traceWriter);
			  out.write(entry); //write to file
			  out.close(); //close
			} else{
				Toast.makeText(this, "ERROR: Don't Have Write Permission", Toast.LENGTH_SHORT).show();
			}   
	   }catch (java.io.IOException e) {
			 makeToast("ERROR: Could Not Write to File");
		}
   }
   
   //send the trace data sb to server using http
   public void sendData(StringBuilder sb){
	   
		//setup http client and header
	    HttpClient httpclient = new DefaultHttpClient();
	    HttpPost httppost = new HttpPost("http://blow.cs.uwaterloo.ca/cgi-bin/cs456_a1_submit.py");

	    try {
	        // setup list
	        List<NameValuePair> packet = new ArrayList<NameValuePair>(2); //create list
	        packet.add(new BasicNameValuePair("uid", "5195031111")); //uid
	        packet.add(new BasicNameValuePair("trace", sb.toString())); //trace value
	        httppost.setEntity(new UrlEncodedFormEntity(packet));

	        //run http post
	        HttpResponse response = httpclient.execute(httppost);
	        makeToast("Trace Sent");
	        deleteDialogBox(); //call function to run delete trace menu
	    } catch (ClientProtocolException e) {
	    	 makeToast("ERROR: ClientProtocolException " + e.toString());
	    } catch (IOException e) {
	    	makeToast("ERROR: IOEXCEPTION " + e.toString());
	    }   
   }
   
   //delete trace file from sd
   public void deleteFile(){
	   File root = Environment.getExternalStorageDirectory(); //get location
	   File traceFile = new File(root, "trace.txt"); //get file
	   if(traceFile.delete()){ //delete file
		   makeToast("File Deleted");
	   }else{
		   makeToast("Could Not Delete File");
	   }
	   
   }
   
   //read trace file, add to sb and send to server using sendData function
   public void readFromFile(){
	   //since its all on one thread, we have to stop writing to file before requesting a read
	   try {
		    File root = Environment.getExternalStorageDirectory(); //get location
		   	File someFile = new File(root, "trace.txt"); //get file
		    BufferedReader br = new BufferedReader(new FileReader(someFile));
		    String line;
		    StringBuilder sb = new StringBuilder();
		    //read from file
		    while ((line = br.readLine()) != null) {
		        sb.append(line + "\n");
		    }
		    sendData(sb); //send for posting to server
		}
		catch (IOException e) {
			makeToast("ERROR: Could Not Read From File");
		}	   
   }
   
   public void deleteDialogBox(){
	   
       AlertDialog.Builder alertbox = new AlertDialog.Builder(this);
       alertbox.setMessage("Sending Data; Delete Trace File?");
       
       alertbox.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
    	   //call deleteFile function to delete file
           public void onClick(DialogInterface arg0, int arg1) {
        	   deleteFile();
           }
       });
       
       alertbox.setNegativeButton("No", new DialogInterface.OnClickListener() {
    	   //file not deleted
           public void onClick(DialogInterface arg0, int arg1) {
               Toast.makeText(getApplicationContext(), "Trace File not Deleted", Toast.LENGTH_SHORT).show();
           }
       });

       alertbox.show();   
   }
   
   private final class locationListeners implements LocationListener {

       @Override
       public void onLocationChanged(Location locFromGps) {
    	  
    	  //location updates here
       }

       @Override
       public void onProviderDisabled(String provider) {
          //called when gps is off
       }

       @Override
       public void onProviderEnabled(String provider) {
          //called when gps is on
       }

       @Override
       public void onStatusChanged(String provider, int status, Bundle extras) {

       }
   }
}