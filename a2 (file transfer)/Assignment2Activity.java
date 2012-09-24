package com.assignment2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class Assignment2Activity extends Activity {
    /** Called when the activity is first created. */
	
	BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	TextView someShit;
	ArrayAdapter<String> btArrayAdapter;
	WifiManager wifi;
	String serverIp;
	private Handler handler = new Handler();
	private ServerSocket serverSocket;
	private String socketServerIp;
	Boolean connected;
	private Handler mHandler = new Handler();
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        initDisplay();
        init();
       // initBroadcastReceiver();
     //   getWifiInfo();
        //initServer();
        //initClient();
        readFilesOnSd();
    }
    
    public void init(){
    	if(mBluetoothAdapter != null)
    	{
    		if (mBluetoothAdapter.isEnabled()) {
    		    initScan();
    		}
    		else
    		{
    			Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_LONG).show();
    		}
    	}
    	 mHandler.removeCallbacks(mUpdateTimeTask);
         mHandler.postDelayed(mUpdateTimeTask, 1000);
    }
    
    private Runnable mUpdateTimeTask = new Runnable() {
 	   public void run() {
 		   //wifiInfo.append("timer");
 		   //wifi.startScan();
 	       //mHandler.postDelayed(this, interval);
 		   someShit.append("hey");
 	   }
 	};
    
    public void initScan() {  	
    	//someShit.append("shits Working");
    }
    
    public void initDisplay(){
    	 someShit = (TextView) findViewById(R.id.shit);
    	
    }
    
    public void initBroadcastReceiver(){
    	Toast.makeText(this, "in broadcast receiver", Toast.LENGTH_SHORT).show();
    	mBluetoothAdapter.startDiscovery();
    	registerReceiver(foundBluetoothDevices, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }
    
    private final BroadcastReceiver foundBluetoothDevices = new BroadcastReceiver() {
    	
    	  @Override
    	  public void onReceive(Context context, Intent intent) {
    	   // TODO Auto-generated method stub
    	   //someShit.append("aa");
    	   String action = intent.getAction();
    	   if(BluetoothDevice.ACTION_FOUND.equals(action)) {
    	             BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    	             //btArrayAdapter.add(device.getName() + "\n" + device.getAddress());
    	            // someShit.append(device.getName() + "\n" + device.getAddress());
    	             //btArrayAdapter.notifyDataSetChanged();
    	         }
    	  }
    	  
    };
    
    public void getWifiInfo(){
    	wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    	WifiInfo info = wifi.getConnectionInfo();
    	//getLocalIpAddress();
    	//someShit.append("\n\nWiFi Status: " + info.getBSSID());
    	//encryptThatShit((info.getBSSID()).toString());
    }
    
    public void encryptThatShit(String poop){
    	String macFiltered = poop.replace(":", "");
    	//MessageDigest md = MessageDigest.getInstance("SHA-1");
    	//md.update("something"); 
    	//String BtMacHash = Base64.encodeToString(md.digest(), 0); 
    	someShit.append("\n mac: " + poop + "f: " + macFiltered + "\n");
    }
    
    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) { 
                    	return inetAddress.getHostAddress().toString(); 
                    	//someShit.append("\n ip: " + inetAddress.getHostAddress().toString());
                    }
                }
            }
        } catch (SocketException ex) {
        	someShit.append("\n fuck: " + ex);
            Log.e("ServerActivity", ex.toString());
        }
        return null;
    }
    
    public void initServer(){
    	socketServerIp = getLocalIpAddress();
    	someShit.append("\n ip: " + socketServerIp);
    	
    	Thread fst = new Thread(new sThread());
    	Log.d("asm2", "calling");
        fst.start();
    }
    
    public class sThread implements Runnable {
    	String line;
    	
    	public void run() {
    		 try {
    			 if (socketServerIp != null) {
    				 Log.d("asm2","connection");
    				 serverSocket = new ServerSocket(62009);
    				 
    				 while (true) {
                         // listen for incoming clients
                      Socket client = serverSocket.accept();
                      Log.d("asm2","connected");
                      
                      try {
                          BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                          PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client
                                  .getOutputStream())), true);
                          
                          //should I loop this?
                          //what happens if connection disrupts?
                          out.println("_getFile");
                          
                          line = null;
                          while ((line = in.readLine()) != null) {
                        	  //someShit.append(line);
                        	  handler.post(new Runnable() {
                                  @Override
                                  public void run() {
                                	 // Log.d("ServerActivity", "insideRun");
                                	 // someShit.append("\n" + line);
                                      // do whatever you want to the front end
                                      // this is where you can be creative
                                  }
                              });
                        	  
                        	  
                        	  
                              Log.d("ServerActivity", line);
                              //someShit.append("\n do shit here");
                          }
                          
                          handler.post(new Runnable() {
                              @Override
                              public void run() {
                            	  String filtered = filterInput(line);
                            	  if(filtered != null){
                            		  //out.println(filtered);
                            	  }
                            	 // Log.d("ServerActivity", "insideRun");
                            	 // someShit.append("\n" + line);
                                  // do whatever you want to the front end
                                  // this is where you can be creative
                              }
                          });
                          
                          break;
                      } catch (Exception e) {
                          Log.d("asm2", "\n Oops. Connection interrupted. Please reconnect your phones.");
                          //e.printStackTrace();
                      }
    				 }
    			 }else{
    				 Log.d("asm2", "\n could not detect internet");
    			 }
    		 }catch(Exception e){
    			 Log.d("asm2", "\n error2 " + e );
    		 }
    	}
    }
    
    public void initClient(){
    	Thread cThread = new Thread(new cThread());
        cThread.start();
    }
    
    public class cThread implements Runnable {
    	String line;
        public void run() {
            try {
                InetAddress serverAddr = InetAddress.getByName("172.21.84.51");
                Socket socket = new Socket(serverAddr, 62009);
                connected = true;
                while (connected) {
                    try {
                        Log.d("ClientActivity", "C: Sending command.");
                        //someShit.append("\n sending command");
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket
                                    .getOutputStream())), true);
                            // where you issue the commands
                            out.println("Hey Server!");
                            
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                              	  someShit.append("\n sending");
                                    // do whatever you want to the front end
                                    // this is where you can be creative
                                }
                            });
               
                            
                            
                            line = null;
                            while ((line = in.readLine()) != null) {
                          	  //someShit.append(line);
                          	  handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                  	  Log.d("ServerActivity", "insideRun");
                                  	  someShit.append("\n" + line);
                                        // do whatever you want to the front end
                                        // this is where you can be creative
                                    }
                                });
                            }
                            
                            
                            
                            Log.d("ClientActivity", "C: Sent.");
                    } catch (Exception e) {
                    	//someShit.append("\n error1");
                        Log.d("ClientActivity", "S: Error", e);
                    }
                }
                socket.close();
                //someShit.append("\n connection closed");
                Log.d("ClientActivity", "C: Closed.");
            } catch (Exception e) {
            	//someShit.append("\n error2");
                Log.e("ClientActivity", "C: Error", e);
                connected = false;
            }
        }
    }
    
    public StringBuilder readFilesOnSd(){
    	File[] file = Environment.getExternalStorageDirectory().listFiles();
    	StringBuilder files = new StringBuilder();
    	for(int i=0; i<file.length; i++){
    		if(file[i].isFile() && !file[i].isHidden()){
    			files.append(file[i].getName().toString() + "\n");
    		}
    	}
    	return files;
    }
    
    public String filterInput(String input){
    	if(input.startsWith("_")){
    		String result = handleRequest(input);
    		if(result != null && result != ""){
    			return result;
    		}
    	}else{
    		someShit.append(input);
    	}
    	return null;
    }
    
    public String handleRequest(String request){
    	String fileList;
    	if(request == "_getFile"){
    		fileList = readFilesOnSd().toString();
    		return fileList;
    	}
    	return null;
    }
    
    
}