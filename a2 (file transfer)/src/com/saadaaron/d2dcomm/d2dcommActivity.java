package com.saadaaron.d2dcomm;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

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
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class d2dcommActivity extends Activity {
	private final String TAG = "D2DCOMM";
	private final String BLS_URL = "http://blow.cs.uwaterloo.ca/cgi-bin/bls_query.pl?btmachash=";
	public final String TRACE_FILE = "trace.txt";
	private final int PORT = 62009;
	private final boolean D = true;
	private BluetoothAdapter mBluetoothAdapter = null;
	private TextView mTextLastScan, mTextBtMac, mTextWlanIp, mTextStatus;
	private Button mButtonScan;
	private final Handler mHandler = new Handler();
	private final HashMap<String, HostDetail> mClients = new HashMap<String, HostDetail>();
	private ArrayAdapter<String> mArrayAdapterClients;
	private ListView mListClients;
	private TraceLog mTraceLog;
	private Thread mServer;
	private WifiManager mWifi;

	private int mCountFound;
	private int mCountTotal;

	enum State {
		CMD, LIST_N, LIST_RECV, END, LIST, LIST_SEND, LIST_FILENAME, FILE_NAME, CMD_LIST
	};

	// Intent request codes
	private static final int REQUEST_ENABLE_BT = 1;

	private class HostDetail {
		String mac; // bluetooth mac address
		String name; // bluetooth name
		String ip; // WLAN ip address
		boolean found; // found in last scan

		public HostDetail(String name, String mac, String ip) {
			this.update(name, mac, ip);
		}

		public void update(String name, String mac, String ip) {
			this.name = name;
			this.mac = mac;
			this.ip = ip;
			this.found = true;
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (D)
			Log.e(TAG, "onCreate()");

		// Set up the window layout
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.main);

		// Get UI objects
		mTextLastScan = (TextView) findViewById(R.id.textLastScan);
		mTextBtMac = (TextView) findViewById(R.id.textBtMac);
		mTextWlanIp = (TextView) findViewById(R.id.textWlanIp);
		mTextStatus = (TextView) findViewById(R.id.textStatus);

		// Buttons
		mButtonScan = (Button) findViewById(R.id.buttonScan);
		mButtonScan.setOnClickListener(mClickListener);

		mArrayAdapterClients = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);
		// Find and set up the ListView for paired devices
		mListClients = (ListView) findViewById(R.id.listClients);
		mListClients.setAdapter(mArrayAdapterClients);
		mListClients.setOnItemClickListener(mClickListenerClients);

		// Setup UI handlers

		registerReceiver(foundBluetoothDevices, new IntentFilter(
				BluetoothDevice.ACTION_FOUND));
		registerReceiver(foundBluetoothDevices, new IntentFilter(
				BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			Log.d(TAG, "Bluetoogh is not available");
			finish();
			return;
		}

		mTextBtMac.setText(" " + mBluetoothAdapter.getAddress());

		mWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		/* Listen to WiFi connectivity */
		IntentFilter filter = new IntentFilter(
				WifiManager.NETWORK_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(mNetworkStateReceiver, filter);

		mTraceLog = new TraceLog(TRACE_FILE);

		mServer = new Thread(new Server());
		mServer.start();

		/*String cmac = "38:E7:D8:46:6C:BD";
		HostDetail c = new HostDetail("left", cmac, "172.21.86.31");
		mClients.put(cmac, c);
		mArrayAdapterClients.add("left" + "\n" + cmac);

		String dmac = "38:E7:D8:46:6E:5B";
		HostDetail d = new HostDetail("right", dmac, "172.21.35.232");
		mClients.put(dmac, d);
		mArrayAdapterClients.add("right" + "\n" + dmac);*/
	}

	@Override
	public void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "++ ON START ++");

		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else {
			Log.d(TAG, "Start doing stuff");
		}

		if (mTraceLog == null) {
			mTraceLog = new TraceLog(TRACE_FILE);
		}
		if (!mTraceLog.open()) {
			Toast.makeText(this, "Unable to open trace file", Toast.LENGTH_LONG)
					.show();
			Log.d(TAG, "Unable to open trace file");
			finish();
		}

	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if (D)
			Log.e(TAG, "onResume()");
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		if (D)
			Log.e(TAG, "onPause()");
	}

	@Override
	public void onStop() {
		super.onStop();
		if (D)
			Log.e(TAG, "onStop()");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (D)
			Log.e(TAG, "onDestroy");

		unregisterReceiver(foundBluetoothDevices);
		unregisterReceiver(mNetworkStateReceiver);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (D)
			Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				Log.d(TAG, "Bluetooth enabled, do stuff");
				startClientScan();
			} else {
				// User did not enable Bluetooth or an error occurred
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, "BT not enabled", Toast.LENGTH_SHORT)
						.show();
				finish();
			}
		}
	}

	private final OnClickListener mClickListener = new OnClickListener() {
		@Override
		public void onClick(View src) {
			switch (src.getId()) {
			case R.id.buttonScan:
				startClientScan();
				break;
			}
		}
	};

	private final OnItemClickListener mClickListenerClients = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
			// Get the device MAC address, which is the last 17 chars in the
			// view
			String info = ((TextView) v).getText().toString();
			String address = info.substring(info.length() - 17);

			Log.d(TAG, "Selected client: " + address);

			mTraceLog.close();

			Intent i = new Intent(getApplicationContext(),
					ViewHostActivity.class);
			Bundle b = new Bundle();
			b.putString("ip", mClients.get(address).ip);
			i.putExtras(b);
			startActivity(i);
		}
	};

	public void startClientScan() {
		Log.d(TAG, "startClientScan()");
		// invalidate existing clients so we know which ones to remove
		for (HostDetail c : mClients.values()) {
			c.found = false;
		}

		if (mBluetoothAdapter.isDiscovering())
			mBluetoothAdapter.cancelDiscovery();

		Toast.makeText(this, "Starting client scan", Toast.LENGTH_SHORT).show();
		mBluetoothAdapter.startDiscovery();

		setTitle("Scanning...");
		setProgressBarIndeterminateVisibility(true);
		mCountTotal = 0;
		mCountFound = 0;
		updateStatusCount();
		mTraceLog.log("SCAN_BEGIN");

	}

	private void updateStatusCount() {
		mTextStatus.setText(" found " + mCountFound + " of " + mCountTotal
				+ " neighbouring devices");
	}

	private final BroadcastReceiver foundBluetoothDevices = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				// Got a new client
				updateClient(device.getName(), device.getAddress());
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
					.equals(action)) {
				// Finshed scanning for clients
				endClientScan();
			}
		}
	};

	private void updateClient(String name, String mac) {
		mTraceLog.log("SCAN_DETECT: " + mac);

		HostDetail c = mClients.get(mac);
		if (c != null && c.found) {
			// already found device
			return;
		}

		mCountTotal++;
		updateStatusCount();

		String ip = queryMac(mac);
		if (c == null && ip == null) {
			Log.d(TAG, "updateClient(): not found at all " + name + " " + mac);
			// Add to mClients so we don't querey multiple times for this device
			// if
			// the bluetooth service scans it multiple times
			mClients.put(mac, new HostDetail(name, mac, null));
			// not found and not in list, just return
		} else if (c == null && ip != null) {
			// new client, add to list
			mCountFound++;
			Log.d(TAG, "updateClient(): adding " + name + " " + mac);
			c = new HostDetail(name, mac, ip);
			mClients.put(mac, c);
			mArrayAdapterClients.add(name + "\n" + mac);
		} else if (c != null && ip == null) {
			// device was removed from BLS
			// immediately remove from listView but keep in mClients
			Log.d(TAG, "updateClient(): removing " + name + " " + mac);
			c.ip = null;
			mArrayAdapterClients.remove(name + "\n" + mac);
		} else if (c != null && ip != null) {
			// update client info
			Log.d(TAG, "updateClient(): updating " + name + " " + mac + " "
					+ ip);
			mCountFound++;
			c.update(name, mac, ip);
		}

		updateStatusCount();
	}

	private void endClientScan() {
		mTraceLog.log("SCAN_END");
		Log.d(TAG, "endClientScan()");
		mTextLastScan.setText(" "
				+ android.text.format.DateFormat.format(
						"yyyy-MM-dd hh:mm:ssaa", new java.util.Date()));
		Toast.makeText(this, "Done client scan", Toast.LENGTH_SHORT).show();

		mArrayAdapterClients.clear();
		ArrayList<String> old = new ArrayList<String>();
		for (HostDetail c : mClients.values()) {
			if (c.found == false || c.ip == null) {
				old.add(c.mac);
			} else {
				mArrayAdapterClients.add(c.name + "\n" + c.mac);
			}
		}

		for (String mac : old) {
			mClients.remove(mac);
		}

		setProgressBarIndeterminateVisibility(false);
		setTitle("d2dComm");
		mTextStatus.append(" DONE");
	}

	private String queryMac(String mac) {
		mTraceLog.log("BLS_QUERY: " + mac);
		BufferedReader in = null;
		String lan = null;
		String wan = null;
		String ts = null;

		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(mac.replace(":", "").toLowerCase().getBytes());
			String hash = Base64.encodeToString(md.digest(), 0).replace("\n",
					"");
			String req = new String(BLS_URL + hash);
			Log.d(TAG, "Sending get request: " + req);
			HttpGet get = new HttpGet(req);
			HttpClient client = new DefaultHttpClient();
			HttpResponse response = client.execute(get);
			in = new BufferedReader(new InputStreamReader(response.getEntity()
					.getContent()));
			if ((in.readLine()) != null && (lan = in.readLine()) != null
					&& (wan = in.readLine()) != null
					&& (ts = in.readLine()) != null) {
				mTraceLog.log("BLS_REPLY: " + ts + ", " + lan + ", " + wan);
			} else {
				throw new Exception("bad response");
			}
		} catch (Exception e) {
			Log.d(TAG, "queryMac exception " + e.toString());
			lan = null;
			mTraceLog.log("BLS_REPLY: FAILURE");
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return lan;
	}

	public class Server implements Runnable {
		String line;
		ServerSocket mServer;

		public void close() {
			try {
				mServer.close();
			} catch (IOException e) {
				Log.d(TAG, "error closing socketserver " + e.toString());
			}
		}

		@Override
		public void run() {
			Log.d(TAG, "Server thread started");
			try {
				mServer = new ServerSocket(PORT);
				while (true) {
					// listen for incoming clients
					Socket client = mServer.accept();
					State state = State.CMD;
					Log.d(TAG, "Accepted Client Connection");
					try {
						BufferedReader in = new BufferedReader(
								new InputStreamReader(client.getInputStream()));
						PrintWriter out = new PrintWriter(
								new BufferedWriter(new OutputStreamWriter(
										client.getOutputStream())), true);

						// get command from client
						int n = 0;
						int i = 0;
						while (state != State.END
								&& (line = in.readLine()) != null) {
							Log.d("TraceLog", "state: " + state + ", " + line);
							switch (state) {
							case CMD:
								if (line.compareTo("get_file_list") == 0) {
									Log.d(TAG, "Server: Sending file list");
									out.println("send_file_list");
									ArrayList<String> files = readFilesOnSd();
									out.println(files.size());
									for (String f : files) {
										out.println(f);
									}
									state = State.CMD;
								} else if (line.compareTo("send_file_list") == 0) {
									Log.d(TAG, "Server: receiving files");
									state = State.LIST_N;
								} else if (line.compareTo("get_file") == 0) {
									Log.d(TAG, "Server: sending file");
									state = State.FILE_NAME;
								} else if (line.compareTo("close_connection") == 0) {
									Log.d(TAG, "Server: closing connection");
									state = State.END;
								} else {
									Log.d(TAG,
											"Server: did not receive valid command");
									state = State.END;
								}
								break;
							case LIST_N:
								n = Integer.parseInt(line);
								i = 0;
								if (n >= 0) {
									Log.d(TAG, "Server: Receiving " + n
											+ " files");
									state = State.LIST;
								} else {
									Log.d(TAG, "No files");
									state = State.CMD;
								}
								break;
							case LIST:
								Log.d(TAG, "Server: file: " + line);
								i++;
								if (i >= n) {
									Log.d(TAG, "File list received");
									state = State.CMD;
								}
								break;
							case FILE_NAME:
								String filename = line;
								Log.d(TAG, "Sending file: " + filename);
								out.println("send_file");
								byte []bytes;
								try {
									File file = new File(Environment.getExternalStorageDirectory().toString() + "/" + filename);

									bytes = new byte[(int) file.length()];
									FileInputStream fis = new FileInputStream(file);
									BufferedInputStream bis = new BufferedInputStream(fis);
									bis.read(bytes, 0, bytes.length);
									bis.close();
								} catch (Exception e) {
									out.println("-1");
									break;
								}
								out.println(String.valueOf(bytes.length));
								out.flush();
								//OutputStream os = client.getOutputStream();
								Log.d(TAG, "WRITING TO CLIENT");
								char []chars = new char[bytes.length];
								for (int j = 0; j < bytes.length; j++) {
									chars[j] = (char) bytes[j];
									//out.write((char) b);
								}
								out.write(chars, 0, chars.length);
								out.flush();
								out.close();
								Log.d(TAG, "DONE WRITING TO CLIENT");
								state = State.CMD;
								break;
							}

						}
					} catch (Exception e) {
						Log.d("TAG",
								"\n Oops. Connection interrupted. Please reconnect your phones.");
						// e.printStackTrace();
					} finally {
						client.close();
					}
				}
			} catch (Exception e) {
				Log.d(TAG, "Server exception " + e.toString());
			}
		}
	}

	public static ArrayList<String> readFilesOnSd() {
		File[] file = Environment.getExternalStorageDirectory().listFiles();
		ArrayList<String> files = new ArrayList<String>();
		for (int i = 0; i < file.length; i++) {
			if (file[i].isFile() && !file[i].isHidden()) {
				files.add(file[i].getName().toString());
			}
		}
		return files;
	}

	private final BroadcastReceiver mNetworkStateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateWifiStatus();
		}
	};

	private void updateWifiStatus() {
		WifiInfo info = mWifi.getConnectionInfo();
		if (info != null && info.getIpAddress() != 0) {
			int ipAddress = info.getIpAddress();
			byte[] bytes = BigInteger.valueOf(ipAddress).toByteArray();
			reverse(bytes);
			try {
				InetAddress address = InetAddress.getByAddress(bytes);
				mTextWlanIp.setText(" " + address.getHostAddress());
			} catch (UnknownHostException e) {
				mTextWlanIp.setText(" no ip address");
			}
		} else {
			mTextWlanIp.setText(" no ip address");
		}
	}

	public static void reverse(final byte[] array) {
		if (array == null) {
			return;
		}
		int i = 0;
		int j = array.length - 1;
		byte tmp;
		while (j > i) {
			tmp = array[j];
			array[j] = array[i];
			array[i] = tmp;
			j--;
			i++;
		}
	}
}