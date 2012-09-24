package com.saadaaron.d2dcomm;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.saadaaron.d2dcomm.d2dcommActivity;

public class ViewHostActivity extends Activity {
    // Debugging
    private static final String TAG = "ViewHostActivity";
    private static final String TRACE_FILE = "trace.txt";
    private static final boolean D = true;
    private final Handler mHandler = new Handler();
    private TextView mTextLog;
    private Thread mClient;
    private Thread mTransfer;
	private ArrayAdapter<String> mArrayAdapterFiles;
	private String mIp;
	private ListView mListFiles;
	private TraceLog mTraceLog = new TraceLog(TRACE_FILE);
	enum State { START, LIST_N, LIST , END, CMD_LIST, LIST_SEND, FILE, WAIT_RECV_FILE_SIZE, FILE_N, FILE_RECV, CMD_FILE};

    private void addtext(String line) {
    	final String l = line;
    	mHandler.post(new Runnable() {
			@Override
			public void run() {
				mTextLog.append(l + "\n");
			}
		});
    }

    private void addfile(String line) {
    	final String l = line;
    	mHandler.post(new Runnable() {
			@Override
			public void run() {
				mArrayAdapterFiles.add(l);
			}
		});
    }


    private void setProgressVisibility(boolean visible) {
    	final boolean v = visible;
    	mHandler.post(new Runnable() {
			@Override
			public void run() {
				setProgressBarIndeterminateVisibility(v);
			}
		});
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.host);


        mTextLog = (TextView) findViewById(R.id.textLog);

        mArrayAdapterFiles = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);
		// Find and set up the ListView for paired devices
		mListFiles = (ListView) findViewById(R.id.listFiles);
		mListFiles.setAdapter(mArrayAdapterFiles);
		mListFiles.setOnItemClickListener(mClickListenerFiles);

        Bundle b = getIntent().getExtras();
        mIp = b.getString("ip");

        Log.d(TAG, "Connecting to ip " + mIp + " with tcp");

        if (!mTraceLog.open()) {
        	Toast.makeText(this, "unable to open trace file", Toast.LENGTH_LONG);
        }

        /*
        mClient = new Thread(new Client(ip));
        mClient.start();
        */
        new ClientRequest().execute(mIp);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private final OnItemClickListener mClickListenerFiles = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
			// Get the device MAC address, which is the last 17 chars in the
			// view
			String file = ((TextView) v).getText().toString();

			Log.d(TAG, "Selected file: " + file);

			ClientRequest ft = new ClientRequest();
			ft.setFilename(file);;
			ft.execute(mIp);

			/*
			Intent i = new Intent(getApplicationContext(),

					ViewHostActivity.class);
			Bundle b = new Bundle();
			b.putString("ip", mClients.get(address).ip);
			i.putExtras(b);
			startActivity(i);
			*/
		}
	};

	private class ClientRequest extends AsyncTask<String, Integer, Void> {
    	private final ProgressDialog mDialog = new ProgressDialog(ViewHostActivity.this);
    	private String mServerIp;
		private State state;
		private String mFilename = null;
		private int n;
		private int i;

		private FileOutputStream fos;
		private BufferedOutputStream bos;


		public void setFilename(String filename) {
			mFilename = filename;
		}

		@Override
	    protected void onPreExecute() {
			mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			if (mFilename == null) {
				mDialog.setTitle("Getting file list");
			} else {
				mDialog.setTitle("Getting file: " + mFilename);
			}
			mDialog.show();
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {
	         this.mDialog.setProgress(progress[0]);
	     }

    	@Override
		protected Void doInBackground(String... params) {
    		// (string IP)


    		mServerIp = params[0];

    		String line;
			state = State.START;
			String files = new String("");

			try {
				setProgressVisibility(true);
				InetAddress serverAddr = InetAddress.getByName(mServerIp);
				Socket server = new Socket(serverAddr, 62009);
				try {
					Log.d("ClientActivity", "C: Sending command.");
					// someShit.append("\n sending command");
					BufferedReader in = new BufferedReader(
							new InputStreamReader(server.getInputStream()));
					PrintWriter out = new PrintWriter(
							new BufferedWriter(new OutputStreamWriter(
									server.getOutputStream())), true);

					// where you issue the commands
					if (mFilename == null) {
						addtext("Getting file list");
						this.mDialog.setMessage("Getting file list...");
						out.println("get_file_list");
						state = State.CMD_LIST;
					} else {
						addtext("Downloading file: " + mFilename);
						out.println("get_file");
						out.println(mFilename);
						state = State.CMD_FILE;
					}

					line = null;
					n = 0;
					i = 0;
					while (state != State.END && (line = in.readLine()) != null) {
						switch (state) {
						case CMD_LIST:
							if (line.compareTo("send_file_list") != 0) {
								addtext("Error: Remote device did not respond\nto file list request");
								state = State.END;
							} else {
								state = State.LIST_N;
							}
							break;
						case LIST_N:
							n = Integer.parseInt(line);
							i = 0;
							if (n >= 0) {
								addtext("Receiving " + n + " files");
								mDialog.setMax(n);
								state = State.LIST;
							} else {
								addtext("No files");
								state = State.END;
							}
							break;
						case LIST:
							files = files.concat(", " + line);
							addfile(line);
							i++;
							publishProgress(i);
							//publishProgress((int) ((i / (float) n) * 100));
							if (i >= n)
								state = State.END;
							break;
						case CMD_FILE:
							if (line.compareTo("send_file") != 0) {
								addtext("Error: Remote device did not respond\nto file get request");
								state = State.END;
							} else {
								state = State.FILE_N;
							}
							break;
						case FILE_N:
							n = Integer.parseInt(line);
							i = 0;
							if (n >= 0) {
								mDialog.setMax(n);
								addtext("Receiving file size: " + n);
								fos = new FileOutputStream(Environment.getExternalStorageDirectory() + "/dl-" + mFilename);
								bos = new BufferedOutputStream(fos);
								Log.d(TAG, "READING FILE FROM SERVER");
								/*for (int j = 0; j < n; j++) {
									bytes[j] = (char)in.read();
									publishProgress(j);
								}*/
								char []chars = new char[n];
								int bytes_read = 0;
								int current;
								while (bytes_read < n) {
									current = in.read(chars, bytes_read, n - bytes_read);
									bytes_read += current;
									publishProgress(bytes_read);
								}

								Log.d(TAG, "DONE READING FILE FROM SERVER");
								addtext("Got " + bytes_read + " bytes");
								for (char c: chars) {
									bos.write((byte)c);
								}
								bos.flush();
								bos.close();
								addtext("Saved file as dl-" + mFilename);
								state = State.END;
								break;
							} else {
								addtext("No such file: " + mFilename);
								state = State.END;
							}
							break;
						}
					}

					if (mFilename == null ) {
						out.println("send_file_list");

						ArrayList<String> filelist = d2dcommActivity.readFilesOnSd();
						out.println(filelist.size());
						for (String f: filelist) {
							out.println(f);
						}
					}
					out.println("close_connection");
					mTraceLog.log("CONNECT " + mIp + " SUCCESS");
					mTraceLog.log("FILE_LIST: " + mIp + files);

				} catch (Exception e) {
					Log.d(TAG, "S: Error", e);
					addtext("Error: " + e.toString());
					mTraceLog.log("CONNECT " + mIp + " FAILURE");
				} finally {
					server.close();
				}

				// someShit.append("\n connection closed");
				Log.d(TAG, "C: Closed.");
			} catch (Exception e) {
				// someShit.append("\n error2");
				addtext("Error: " + e.toString());
				mTraceLog.log("CONNECT " + mIp + " FAILURE");
				Log.e(TAG, "C: Error", e);
			}

			setProgressVisibility(false);
			this.mDialog.dismiss();

			Log.d(TAG, "Client: done " + mServerIp);

			return null;
		}


	}
}

