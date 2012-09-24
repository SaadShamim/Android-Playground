package com.saadaaron.d2dcomm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import android.os.Environment;
import android.util.Log;

public class TraceLog {
	private final String TAG = "TraceLog";
	private final File mRoot = Environment.getExternalStorageDirectory();
	private File mFile;
	private FileOutputStream mFOS;
	private PrintStream mPS;

	public TraceLog(String filename) {
		mFile = new File(mRoot, filename);
	}

	public boolean open() {
		try {
			mFOS = new FileOutputStream(mFile, true);
		} catch (Exception e) {
			Log.d(TAG, e.toString());
			Log.d(TAG, "unable to open file");
			return false;
		}

		mPS = new PrintStream(mFOS);
		return true;
	}

	public void close() {
		try {
			mFOS.close();
		} catch (Exception e) {
			Log.d(TAG, "Unable to close file");
		}
	}

	public int log(String line) {
		String out = System.currentTimeMillis() + ": " + line + "\n";
		Log.d(TAG, out);
		mPS.append(out);
		mPS.flush();
		return 0;
	}



}
