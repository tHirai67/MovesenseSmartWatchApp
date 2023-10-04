package com.example.movesensesmartwatchapp.record;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.movesensesmartwatchapp.R;
import com.example.movesensesmartwatchapp.connection.MovesenseModel;
import com.example.movesensesmartwatchapp.connection.ConnectedListMovesense;

import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsSubscription;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class RecordActivity extends Activity {
    private static final String TAG = "RecordActivity";

    private static ArrayList<MovesenseModel> movesenseList;

    //MDS
    private Mds mds;
    private MdsSubscription mdsSubscription_right;
    private MdsSubscription mdsSubscription_left;

    //UI
    private TextView Time;
    private TextView movesenseInfo_right, movesenseInfo_left;
    private TextView sensorInfo_right, sensorInfo_left;
    private Button RecordBtn;

    //file
    private File file_right, file_left;
    private CsvLogger csvLogger_right, csvLogger_left;

    //record flag
    private boolean isRecord;

    //Other
    private double first_time;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Time = findViewById(R.id.MeasureTime);

        movesenseInfo_right = findViewById(R.id.MovesenseInfo_right);
        movesenseInfo_left = findViewById(R.id.MovesenseInfo_left);

        //sensorInfo_right = findViewById(R.id.SensorInfo_right);
        //sensorInfo_left = findViewById(R.id.SensorInfo_left);

        RecordBtn = findViewById(R.id.RecordBtn);
        RecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Record(view);
            }
        });

        movesenseList = new ArrayList<>();

        if(ConnectedListMovesense.connectMovesenseList != null){
            int i = 0;
            for(MovesenseModel m:ConnectedListMovesense.connectMovesenseList){
                Log.d(TAG, m.toString());
                movesenseList.add(m);
                if(i == 0){
                    //movesenseInfo_right.setText("Right\n"+m.getSerial()+"\n"+m.getMacAddress());
                    movesenseInfo_right.setText("Right："+m.getSerial());
                }else if(i == 1){
                    //movesenseInfo_left.setText("Left\n"+m.getSerial()+"\n"+m.getMacAddress());
                    movesenseInfo_left.setText("Left："+m.getSerial());
                }
                i++;
            }
        }else{
            Log.d(TAG, "null");
        }

        mds = Mds.builder().build(this);
        isRecord = true;
    }

    public void Record(View view){
        if(isRecord){
            // Get Current Timestamp in format suitable for file names (i.e. no : or other bad chars)
            Date date = new Date();
            @SuppressLint("SimpleDateFormat") SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String currentTimestamp = formatter.format(date);
            //file name1
            // timestamp + device serial + data type,
            StringBuilder sb_right = new StringBuilder();
            sb_right.append(movesenseList.get(0).getSerial()).append("_right_").append(currentTimestamp).append(".csv");
            Log.i(TAG,sb_right.toString());

            //file name2
            // timestamp + device serial + data type,
            StringBuilder sb_left = new StringBuilder();
            sb_left.append(movesenseList.get(1).getSerial()).append("_left_").append(currentTimestamp).append(".csv");
            Log.i(TAG,sb_left.toString());

            Context context = getApplicationContext();
            file_right = new File(context.getFilesDir(),sb_right.toString());
            file_left = new File(context.getFilesDir(),sb_left.toString());
            csvLogger_right = new CsvLogger(file_right);
            csvLogger_left = new CsvLogger(file_left);

            isRecord = false;
            first_time = System.currentTimeMillis();
            //subscribeToSensorRight(movesenseList.get(0).getSerial());
            //subscribeToSensorLeft(movesenseList.get(1).getSerial());
            subscribeToSensor(movesenseList.get(0).getSerial(),"Right");
            subscribeToSensor(movesenseList.get(1).getSerial(),"Left");
            RecordBtn.setText("Stop");
        }else{
            isRecord = true;
            unsubscribe("Right");
            unsubscribe("Left");
            showToast("Recording completed");
            RecordBtn.setText("Start");
            csvLogger_right.finishSavingLogs();
            csvLogger_left.finishSavingLogs();
        }
    }


    private void subscribeToSensor(String deviceSerial, String LR){
        if(LR == "Right"){
            if(mdsSubscription_right != null){
                unsubscribe(LR);
            }

            String accURI = Constants.URI_MEAS_ACC_52;

            //Create parameters
            String strContract = "{\"Uri\": \"" + deviceSerial + accURI + "\"}";
            Log.d(TAG, strContract);
            mdsSubscription_right = Mds.builder().build(this).subscribe(Constants.URI_EVENTLISTENER, strContract, new MdsNotificationListener() {

                // センサ値を受け取るメソッド
                @Override
                public void onNotification(String data) {
                    MovesenseAccDataResponse accResponse = new Gson().fromJson(data, MovesenseAccDataResponse.class);
                    if (accResponse != null && accResponse.body.array.length > 0) {
                        StringBuffer sb = new StringBuffer();
                        double time=((System.currentTimeMillis()-first_time)/1000);
                        double x = accResponse.body.array[0].x;
                        double y = accResponse.body.array[0].y;
                        double z = accResponse.body.array[0].z;
                        DecimalFormat df = new DecimalFormat("00.00");

                        //ここ以外は共通．
                        //sensorInfo_right.setText(sb.toString());
                        Time.setText("Time："+df.format(time)+"s");
                        //sensorInfo_right.setText(String.format("X:%.2f Y:%.2f Z:%.2f",x,y,z));
                        csvLogger_right.appendHeader("Sensor time (ms),System time (ms),X (m/s^2),Y (m/s^2),Z (m/s^2)");
                        csvLogger_right.appendLine(String.format(Locale.getDefault(), "%d,%.4f,%.6f,%.6f,%.6f", accResponse.body.timestamp, time,x,y,z));
                    }
                }

                @Override
                public void onError(MdsException error) {
                    Log.e(TAG, "subscription onError(): ", error);
                    unsubscribe(LR);
                    onDestroy();
                }
            });
        }else if(LR == "Left"){
            if(mdsSubscription_left != null){
                unsubscribe(LR);
            }

            String accURI = Constants.URI_MEAS_ACC_52;

            //Create parameters
            String strContract = "{\"Uri\": \"" + deviceSerial + accURI + "\"}";
            Log.d(TAG, strContract);
            mdsSubscription_left = Mds.builder().build(this).subscribe(Constants.URI_EVENTLISTENER, strContract, new MdsNotificationListener() {

                // センサ値を受け取るメソッド
                @Override
                public void onNotification(String data) {
                    MovesenseAccDataResponse accResponse = new Gson().fromJson(data, MovesenseAccDataResponse.class);
                    if (accResponse != null && accResponse.body.array.length > 0) {
                        StringBuffer sb = new StringBuffer();
                        double time=((System.currentTimeMillis()-first_time)/1000);
                        double x = accResponse.body.array[0].x;
                        double y = accResponse.body.array[0].y;
                        double z = accResponse.body.array[0].z;
                        DecimalFormat df = new DecimalFormat("00.00");
                        //ここ以外は共通．
                        //sensorInfo_right.setText(sb.toString());
                        Time.setText("Time："+df.format(time)+"s");
                        //sensorInfo_left.setText(String.format("X:%.2f Y:%.2f Z:%.2f",x,y,z));
                        csvLogger_left.appendHeader("Sensor time (ms),System time (ms),X (m/s^2),Y (m/s^2),Z (m/s^2)");
                        csvLogger_left.appendLine(String.format(Locale.getDefault(), "%d,%.4f,%.6f,%.6f,%.6f", accResponse.body.timestamp, time,x,y,z));
                    }
                }

                @Override
                public void onError(MdsException error) {
                    Log.e(TAG, "subscription onError(): ", error);
                    unsubscribe(LR);
                    onDestroy();
                }
            });
        }

    }

    private void unsubscribe(String LR){
        if(LR == "Right" && mdsSubscription_right != null){
            mdsSubscription_right.unsubscribe();
            mdsSubscription_right = null;
        }else if(LR == "Left" && mdsSubscription_left != null){
            mdsSubscription_left.unsubscribe();
            mdsSubscription_left = null;
        }

    }

    private void showToast(String text) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for(MovesenseModel m: movesenseList){
            mds.disconnect(m.getMacAddress());
        }
    }
}
