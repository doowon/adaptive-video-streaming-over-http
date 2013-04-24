package edu.utah.cs.dwkim.movieplayer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import android.media.MediaPlayer;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.VideoView;

public class MainActivity extends Activity {

	//private VideoView videoView;

	private static final String TAG = "MyActivity";

	private String ipAddr = "128.110.79.225";
	private int port = 6000;
	private String videoPath = "http://" + ipAddr + "/mystream.m3u8";

	//traffic monitoring
	private static final int EXPECTED_SIZE_IN_BYTES = 1048576; //1MB 1024*1024
	private static final double EDGE_THRESHOLD = 176.0;
	private static final double BYTE_TO_KILOBIT = 0.0078125;
	private static final double KILOBIT_TO_MEGABIT = 0.0009765625;
	
	private long previousTrafficByte = 0;
	private boolean firstTime = true;
	
	private int battery = 100;
	private int height = 1024;
	private int width = 768;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//videoView = (VideoView) findViewById(R.id.videoView1);

		new Thread(new Runnable() {
			public void run() {
				while(true){
					useTask();
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
		
		final Button button = (Button) findViewById(R.id.button1);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//playVideo();

				//Intent
				try {
					Intent i = Intent.parseUri(videoPath, Intent.URI_INTENT_SCHEME);
					startActivity(i);
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public void useTask() { 
		new MeasureTask().execute(); 
	}

	class MeasureTask extends AsyncTask { 
		@Override
		protected String doInBackground(Object... params) { 
			try {
				//the current usage of CPU
				Process p = Runtime.getRuntime().exec("top -m 1 -n 1 -d 1");
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				String data = reader.readLine();

				data = reader.readLine();
				data = reader.readLine();
				data = reader.readLine();

				String[] tokens = data.split(",");

				String[] tokens2 = tokens[1].split(" ");
				int usageOfCPU = Integer.parseInt(tokens2[2].substring(0, tokens2[2].length() - 1));

				//traffic stats
				SpeedInfo speedInfo = new SpeedInfo();
				long currentTrafficByte = TrafficStats.getTotalRxBytes();
				if(firstTime){
					previousTrafficByte = currentTrafficByte;
					firstTime = false;
				}
				else{
					speedInfo = calculate(1, currentTrafficByte - previousTrafficByte);
					//Log.d(TAG, String.valueOf(previousTrafficByte));
					//Log.d(TAG, String.valueOf(currentTrafficByte));
					//Log.d(TAG, String.valueOf(speedInfo.kilobits));
					previousTrafficByte = currentTrafficByte;
				}
				
				//bandwidth
				WifiManager wifiManager = (WifiManager) getBaseContext().getSystemService(Context.WIFI_SERVICE);
				WifiInfo wifiInfo = wifiManager.getConnectionInfo();
				int linkSpeed = 0;
				if (wifiInfo != null) {
					linkSpeed = wifiInfo.getLinkSpeed();
					Log.d(TAG, String.valueOf(linkSpeed));
				}

				//battery
				batteryLevelUpdate();
				
				//screenszie
				Display display = getWindowManager().getDefaultDisplay(); 
				width = display.getWidth();
				height = display.getHeight();
				
				//send if these measurements are over threshold
				//lower quality of video
				if(usageOfCPU > 80 || speedInfo.kilobits > 500 || linkSpeed < 1 || battery < 10 || width < 300 || height < 240){
					Socket socket = new Socket(ipAddr, port);
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());
					out.writeChars("low");
					out.flush();
					out.close();
				}
				else if(usageOfCPU > 50 || speedInfo.kilobits < 200 || linkSpeed < 20 || width < 500 || height < 500){
					Socket socket = new Socket(ipAddr, port);
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());
					out.writeChars("medium");
					out.flush();
					out.close();
				}
				else{
					Socket socket = new Socket(ipAddr, port);
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());
					out.writeChars("high");
					out.flush();
					out.close();
				}


			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			//publishProgress(10); 
			return "Done"; 
		} 
		protected void onProgressUpdate(Integer... progress) { 
		} 
		protected void onPostExecute(String result) { 
		}
	}

	private void batteryLevelUpdate() {
		BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(final Context context, Intent intent) {
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
				int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
				Log.d(TAG, "level: " + level + "; scale: " + scale);
				battery = (level*100)/scale;

			}
		};
	}
	
	private SpeedInfo calculate(final long downloadTime, final long bytesIn) {
		SpeedInfo info = new SpeedInfo();
		long bytespersecond   = bytesIn / downloadTime;
		double kilobits = bytespersecond / 1024.0;
		double megabits = kilobits / 1024.0;
		info.downspeed = bytespersecond;
		info.kilobits = kilobits;
		info.megabits = megabits;

		return info;
	}

	private static class SpeedInfo { 
		public double kilobits = 0;
		public double megabits = 0;
		public double downspeed = 0;        
	}

	/*
	private void playVideo() {
		videoView.setVideoURI(Uri.parse(videoPath));
		MediaController mediaController = new MediaController(this);
		mediaController.setAnchorView(videoView);
		videoView.setMediaController(mediaController);
		videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			public void onPrepared(MediaPlayer mp) {
				try {
					videoView.start();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	 */
}
