package com.example.falldetector;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendForm;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    Timer timer;
    TimerTask timerTask;
    final Handler handler = new Handler();

    private SensorManager sensorManager;
    private Sensor sensor;

    // Current read values in raw (m/s^2)
    private float current_registered_val_mss[] = new float[3];
    // Current read values in g
    private float current_registered_val_g[] = new float[3];

    // Circular buffer for computations
    private final int MAX_REGISTERED_VALUES = 20;
    private float registered_values [][] = new float[3][MAX_REGISTERED_VALUES];
    private float registered_sampling_periods [] = new float[MAX_REGISTERED_VALUES];

    // Values for update the state
    private final float REFRESH_IMAGE_TIME = 25; // 25 muestras, 25*20ms =0.5s

    private boolean refresh_image = true;

    private float counter_refresher = 0.0f;


    // Values for algorithm
    private float sma = 0.0f;
    private double svm = 0.0f;
    private float ta = 0.0f;

    // Thresholds
    final private float SMA_THREHSOLD = 1.5f;
    final private float SVM_THREHSOLD = 2.5f;
    final private float TA_THREHSOLD = 40.0f;

    // Filtering variables for smoothing
    private final int MEAN_FILTER_ORDER = 3;
    private float filter_inputs_arrays [][] = new float[3][MEAN_FILTER_ORDER];
    private float filter_outputs [] = new float[3];

    private final int MAX_ELEMENTS_IN_CHART = 200;
    private final int CHART_UPDATE_RATE = 200;
    private int total_elements_in_chart = 0;

    private ImageView image;

    // Log system
    private FileWriter log_file_writter;
    private boolean is_recording = false;

    private Button start_logging;
    private Button stop_logging;

    // others
    final private float RAD_TO_DEG = 57.2958f;
    final private float MS2_TO_G = 9.80665f;

    // Chart
    private LineChart lineChart;
    private final List<Entry> entriesX = new ArrayList<Entry>();
    private final List<Entry> entriesY = new ArrayList<Entry>();
    private final List<Entry> entriesZ = new ArrayList<Entry>();
    private final String [] chart_legend = {"Acceleration X (g)", "Acceleration Y (g)", "Acceleration Z (g)"};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        image = findViewById(R.id.imageView);

        // Set start and stop button callbacks
        start_logging = findViewById(R.id.start_logging);
        stop_logging = findViewById(R.id.stop_logging);

        start_logging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                run_log_system_state_machine();
                is_recording = true;
            }
        });

        stop_logging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop_logging_data();
                is_recording = false;
            }
        });

        lineChart = findViewById(R.id.lineChart);

        if (Environment.isExternalStorageManager()) {
            //todo when permission is granted
//            create_new_log_file("TEST");
        } else {
            //request for the permission
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        }
    }


    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    private long currentTime = (long) 0.0;
    private long lastTime = (long) 0.0;

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        for(int x = 0; x < 3; x++){
            this.current_registered_val_mss[x] = event.values[x] ;
            this.current_registered_val_g[x] = (float) (event.values[x] / MS2_TO_G);
        }
        float x = this.current_registered_val_g[0];
        float y = this.current_registered_val_g[1];
        float z = this.current_registered_val_g[2];

        // Compute time differential in between different samples
        currentTime = System.currentTimeMillis();
        float dt = (float) ((currentTime - lastTime) * 0.001);
        lastTime = currentTime;
        Log.d("SAMPLIG PERIOD (ms)", String.format("%f", dt));

        // Filter
        this.filter(x, y, z);

        x = this.filter_outputs[0];
        y = this.filter_outputs[1];
        z = this.filter_outputs[2];

        // Register values in the circular buffer
        this.register_value(x, y, z, dt);

        this.updateSMA();
        this.updateSVM();
        this.updateTA();
        update_chart();

        add_new_value_to_chart();


        if(this.svm >= SVM_THREHSOLD) Log.d("SVM > TRH (ms)", String.format("%f", this.svm));

        // We compute the state each cycle but we refresh it slower in order to see how it changes
        this.detect_state();
        if(counter_refresher + 1 >= REFRESH_IMAGE_TIME) {
            counter_refresher = 0;
            refresh_image = true;
        }
        else {
            counter_refresher++;
            refresh_image = false;
        }
        this.run_state();

        this.append_new_data_to_log();



        // Print data into app
        TextView x_acc_val = findViewById(R.id.x_acc_val);
        TextView y_acc_val = findViewById(R.id.y_acc_val);
        TextView z_acc_val = findViewById(R.id.z_acc_val);
        TextView sma_val = findViewById(R.id.sma_value);
        TextView svm_val = findViewById(R.id.svm_value);
        TextView ta_val = findViewById(R.id.ta_value);

        x_acc_val.setText(String.format("%f",  x));
        y_acc_val.setText(String.format("%f",  y));
        z_acc_val.setText(String.format("%f",  z));
        sma_val.setText(String.format("%f",  this.sma));
        svm_val.setText(String.format("%f",  this.svm));
        ta_val.setText(String.format("%f",  this.ta));
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
    }

    protected void updateSVM(){
        float x = this.filter_outputs[0];
        float y = this.filter_outputs[1];
        float z = this.filter_outputs[2];

        this.svm = Math.sqrt(Math.pow(x,2) + Math.pow(y,2) + Math.pow(z,2));
    }

    int current_registered_value_index = 0;
    boolean registered_values_overflow = false;
    private void register_value(float x, float y, float z, float dt){

        this.registered_values[0][current_registered_value_index] = x;
        this.registered_values[1][current_registered_value_index] = y;
        this.registered_values[2][current_registered_value_index] = z;
        this.registered_sampling_periods[current_registered_value_index] = dt;

        // Circular buffer
        if(current_registered_value_index + 1 >= MAX_REGISTERED_VALUES) {
            current_registered_value_index = 0;
            registered_values_overflow = true;      // Used as flag for indicating that buffer is full
        }else current_registered_value_index += 1;
    }

    int current_filter_input_index = 0;
    private void filter(float x, float y, float z){

        this.filter_inputs_arrays[0][current_filter_input_index] = x;
        this.filter_inputs_arrays[1][current_filter_input_index] = y;
        this.filter_inputs_arrays[2][current_filter_input_index] = z;

        // Circular buffer
        if(current_filter_input_index + 1 >= MEAN_FILTER_ORDER) {
            current_filter_input_index = 0;
        } else current_filter_input_index++;
//
//        // Compute mean by assuming dt is always the same, which is not true.
        float x_add = 0.0f;
        float y_add = 0.0f;
        float z_add = 0.0f;
//
        for(int i = 0; i < MEAN_FILTER_ORDER; i++){
            x_add += this.filter_inputs_arrays[0][i];
            y_add += this.filter_inputs_arrays[1][i];
            z_add += this.filter_inputs_arrays[2][i];
        }
//
        this.filter_outputs[0] = x_add / 3;
        this.filter_outputs[1] = y_add / 3;
        this.filter_outputs[2] = z_add / 3;
    }

    private void updateTA(){
        this.ta = RAD_TO_DEG * ((float) Math.asin(this.filter_outputs[1] / this.svm));
    }

    private void updateSMA(){
        float total_time = 0.0f;

        for(int i = 0; i < MAX_REGISTERED_VALUES; i++){
            total_time += this.registered_sampling_periods[i];
        }

        float sum_x = 0;
        float sum_y = 0;
        float sum_z = 0;

        for(int i = 0; i < MAX_REGISTERED_VALUES; i++){
            float dt = this.registered_sampling_periods[i];
            sum_x += Math.abs(dt * this.registered_values[0][i]);
            sum_y += Math.abs(dt * this.registered_values[1][i]);
            sum_z += Math.abs(dt * this.registered_values[2][i]);
        }


        this.sma = (sum_x + sum_y + sum_z) / total_time;

        Log.d("SMA_DEBUG", String.format("%f", this.sma));
    }

    // Chart functions:
    private void add_new_value_to_chart(){
        // Check if it is possible to add a new element and delete the first otherwise
        if(this.total_elements_in_chart +1 >= this.MAX_ELEMENTS_IN_CHART){
            this.entriesX.remove(0);
            this.entriesY.remove(0);
            this.entriesZ.remove(0);
        }

        // Add new entry
        Entry new_entry_X = new Entry((float) (this.total_elements_in_chart * this.CHART_UPDATE_RATE * 0.001), this.current_registered_val_g[0]);
        Entry new_entry_Y = new Entry((float) (this.total_elements_in_chart * this.CHART_UPDATE_RATE * 0.001), this.current_registered_val_g[1]);
        Entry new_entry_Z = new Entry((float) (this.total_elements_in_chart * this.CHART_UPDATE_RATE * 0.001), this.current_registered_val_g[2]);

        this.entriesX.add(new_entry_X);
        this.entriesY.add(new_entry_Y);
        this.entriesZ.add(new_entry_Z);
        this.total_elements_in_chart++;
    }

    private void update_chart(){
        LineDataSet dataSetX = new LineDataSet(entriesX, this.chart_legend[0]); // add entries to dataset
        LineDataSet dataSetY = new LineDataSet(entriesY, this.chart_legend[1]); // add entries to dataset
        LineDataSet dataSetZ = new LineDataSet(entriesZ, this.chart_legend[2]); // add entries to dataset

        // More styling
        dataSetX.setColor(Color.BLUE);
        dataSetX.setCircleColor(Color.BLUE);
        dataSetX.setLineWidth(1f);
        dataSetX.setCircleRadius(1f);
        dataSetX.setDrawCircleHole(false);

        dataSetY.setColor(Color.GREEN);
        dataSetY.setCircleColor(Color.GREEN);
        dataSetY.setLineWidth(1f);
        dataSetY.setCircleRadius(1f);
        dataSetY.setDrawCircleHole(false);

        dataSetZ.setColor(Color.RED);
        dataSetZ.setCircleColor(Color.RED);
        dataSetZ.setLineWidth(1f);
        dataSetZ.setCircleRadius(1f);
        dataSetZ.setDrawCircleHole(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setTextColor(Color.WHITE);
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);

        lineChart.getLegend().setTextColor(Color.WHITE);

        LineData lineData = new LineData(dataSetX, dataSetY, dataSetZ);
        lineChart.setData(lineData);

        // Styling
        lineChart.setBackgroundColor(getResources().getColor(R.color.gray_50));
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);

        lineChart.getAxisRight().setEnabled(false);

        // Apply information to chart
        lineChart.invalidate();
    }

    private enum STATE {VERTICAL_ACTIVITY, FALL, HORIZONTAL_ACTIVITY, SITTING, LYING};
    STATE current_state;

    private void detect_state(){
        if(this.sma > SMA_THREHSOLD){
            if(!(this.svm > SVM_THREHSOLD)){
                this.current_state = STATE.HORIZONTAL_ACTIVITY;
            } else {
                if (!(this.ta > TA_THREHSOLD))    this.current_state = STATE.VERTICAL_ACTIVITY;
                else                              this.current_state = STATE.FALL;
            }
        }else{
            if(this.ta > TA_THREHSOLD)          this.current_state = STATE.SITTING;
            else                                this.current_state = STATE.LYING;
        }
    }

    private void run_state(){
        switch (this.current_state){
            case VERTICAL_ACTIVITY:
                Log.d("CURRENT_STATE > ","VERTICAL ACTIVITY");
                if(refresh_image == true) image.setImageResource(R.drawable.vertical_mov);
                break;
            case FALL:
                Log.d("CURRENT_STATE > ", "FALL");
                if(refresh_image == true) image.setImageResource(R.drawable.falling);
                break;
            case HORIZONTAL_ACTIVITY:
                Log.d("CURRENT_STATE > ", "HORIZONTAL ACTIVITY");
                if(refresh_image == true) image.setImageResource(R.drawable.vertical_mov);
                break;
            case SITTING:
                Log.d("CURRENT_STATE > ", "SITTING");
                if(refresh_image == true) image.setImageResource(R.drawable.sitting);
                break;
            case LYING:
                Log.d("CURRENT_STATE > ", "LYING");
                if(refresh_image == true) image.setImageResource(R.drawable.lying);
                break;
        }
    }



    private void start_logging_data(){
        this.create_new_log_file("Fall_detector_log");
    }

    private void stop_logging_data(){
        try {
            this.log_file_writter.close();
        }catch (IOException e){
            Log.d("STOP_LOGGING_DATA_E", String.valueOf(e));
        }
    }

    /**
     * @brief Append all the data to a new lane in the log file
     * */
    private void append_new_data_to_log(){
        if(this.is_recording){
        try {
            // Add time differential
            this.log_file_writter.write(Float.toString(this.registered_sampling_periods[this.current_registered_value_index]));
            this.log_file_writter.write(",");

            // Add X Y Z values filtered
            this.log_file_writter.write(Float.toString(this.current_registered_val_mss[0]));
            this.log_file_writter.write(",");
            this.log_file_writter.write(Float.toString(this.current_registered_val_mss[1]));
            this.log_file_writter.write(",");
            this.log_file_writter.write(Float.toString(this.current_registered_val_mss[2]));
            this.log_file_writter.write(",");

            // Add X Y Z values filtered
            this.log_file_writter.write(Float.toString(this.filter_outputs[0]));
            this.log_file_writter.write(",");
            this.log_file_writter.write(Float.toString(this.filter_outputs[1]));
            this.log_file_writter.write(",");
            this.log_file_writter.write(Float.toString(this.filter_outputs[2]));
            this.log_file_writter.write(",");

            // Add SMA SVM TA
            this.log_file_writter.write(Float.toString(this.sma));
            this.log_file_writter.write(",");
            this.log_file_writter.write(Double.toString(this.svm));
            this.log_file_writter.write(",");
            this.log_file_writter.write(Float.toString(this.ta));

            // Add new lane
            this.log_file_writter.write("\n");

        }catch (IOException e){
            Log.d("RECORD_DATA_E", String.valueOf(e));
        }
    }}

    /**
     * @brief Create a new log file with a given name
     * @path sdcard/Android/data/com.example.falldetector/files/Logs
     * */
    private void create_new_log_file(String file_name){
        String file = file_name + "_" + System.currentTimeMillis() + ".csv";

        File dir = new File(String.valueOf(this.getExternalFilesDir("Logs")));

        if(!dir.exists()){
            dir.mkdir();
        }

        Log.d("CREATE_NEW_LOG", "Writing " + file + " to " + dir.toString() );

        try {
            File log = new File(dir, file);
            this.log_file_writter = new FileWriter(log);

        }catch (IOException e){
            Log.d("CREATE_NEW_LOG", String.valueOf(e));
        }

        this.create_header("dt(s),x(m/s^2) raw,y(m/s^2) raw,z(m/s^2) raw,x(g) filtered,y(g) filtered,z(g) filtered,sma(g),svm(g),ta(º)\n");
    }

    /**
     * @brief Create a given header for the log file
     * @note Call just after create_new_log_file
     * */
    private void create_header(String header){
        try {
            this.log_file_writter.write(header);
        }catch (IOException e){
            Log.d("CREAT_HEADER_E", String.valueOf(e));
        }
    }

    private void run_log_system_state_machine(){
        if(!this.is_recording){
            this.start_logging_data();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.stop_logging_data();   // Close the log file before clossing the app
    }

}