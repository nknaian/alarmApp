package com.example.nickk_000.snoozinapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    //server global vars
    public static final String serverip = "10.0.0.247";

    //Set up shared preferences
    public static final String LOGINPREFERENCES = "LoginPrefs";
    public static final Boolean DEFAULTLOGINSTATE = false;
    public static final String LoginState = "loginStateKey";
    SharedPreferences loginSharedPreferences;

    public static final String REGISTERPREFS = "RegisterPrefs";
    public static final String REGISTER_EMAIL = "registerEmailKey";
    public static final String REGISTER_PASSWORD = "registerPasswordKey";
    SharedPreferences registerSharedPreferences;

    public static final String USERINFOPREFERENCES = "userInfoPrefs";
    public static final String USER_EMAIL = "userEmailKey";
    public static final String USER_PASSWORD = "userPasswordKey";
    public static final String USER_USERNAME = "userUsernameKey";
    SharedPreferences userInfoSharedPreferences;

    //Set up user info stuff
    public static String ThisUserEmail;
    public static String ThisUserUsername;
    public static String ThisUserAlarmList[];

    //Set up UI variables
    Button logoutButton;
    Button randomAlarmButton;
    Button alarmSendButton;
    TextView alarmListText;
    TextView helloUserText;
    EditText alarmSendField;

    //Set up TCP stuff
    private static Socket s;
    private static ServerSocket ss;
    private static InputStreamReader isr;
    private static BufferedReader input;
    private static PrintWriter printWriter;
    String clientMessage = "";
    String serverMessage = "";
    private static int serverPort = 9020;

    //alarm list variables
    private UserAlarmListSync mAlarmSyncTask = null;
    private AlarmSendTask mAlarmSendTask = null;

    enum syncType {
        SERVER_TO_CLIENT_FULL, SERVER_TO_CLIENT_ADD, CLIENT_TO_SERVER_DELETE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* BEGIN LOGIN/LOGOUT CODE */

        //Retrieve login shared preference file
        loginSharedPreferences = getSharedPreferences(LOGINPREFERENCES, Context.MODE_PRIVATE);

        //Check if login is necessary:
        Boolean loggedIn = loginSharedPreferences.getBoolean(LoginState, DEFAULTLOGINSTATE);
        if(!loggedIn) {
            //Retrieve register shared preference file (in case user closed app while registering)
            registerSharedPreferences = getSharedPreferences(REGISTERPREFS, Context.MODE_PRIVATE);
            String registerEmail = registerSharedPreferences.getString(REGISTER_EMAIL, null);
            if(registerEmail == null) {
                Intent myIntent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(myIntent);
            } else { //If the registerSharedPreferences has data, user account is not yet registered
                Intent myIntent = new Intent(MainActivity.this, RegisterAccountActivity.class);
                startActivity(myIntent);
            }
        }

        //Start onclick listener for logging out
        logoutButton = (Button) findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Set login state to false
                SharedPreferences.Editor editor = loginSharedPreferences.edit();
                editor.putBoolean(LoginState, false);
                editor.commit();

                //Switch to login screen
                Intent myIntent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(myIntent);
            }
        });
        /* END LOGIN/LOGOUT CODE */


        /* BEGIN RANDOM ALARM PLAYING CODE */

        // Place user info from shared preferences into variables
        userInfoSharedPreferences = getSharedPreferences(USERINFOPREFERENCES, Context.MODE_PRIVATE);
        ThisUserUsername = userInfoSharedPreferences.getString(USER_USERNAME, "");
        ThisUserEmail = userInfoSharedPreferences.getString(USER_EMAIL, "");

        // Sync alarm list for user from server
        mAlarmSyncTask = new UserAlarmListSync(syncType.SERVER_TO_CLIENT_FULL, "");
        mAlarmSyncTask.execute((Void) null);


        //On click listener for random alarm
        randomAlarmButton = (Button) findViewById(R.id.randomAlarmButton);
        randomAlarmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(ThisUserAlarmList != null) {
                    Uri uri = Uri.parse(pickRandomAlarm()); // missing 'http://' will cause crashed
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                }
                else {
                    //TODO: print message that your alarm list is empty
                }
            }
        });


        //Set text next to "Hello" at top of activity to say the username
        helloUserText = (TextView) findViewById(R.id.helloUser);
        helloUserText.setText(ThisUserUsername);


        /* END RANDOM ALARM PLAYING CODE */

        alarmSendField = (EditText) findViewById(R.id.alarmSendBox);
        alarmSendButton = (Button) findViewById(R.id.sendButton);
        alarmSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAlarmSendTask = new AlarmSendTask(alarmSendField.getText().toString());
                mAlarmSendTask.execute((Void) null);
            }
        });
    }

    /**
     * Represents an asynchronous task to sync the server's alarm list and local copy of alarms
     */
    public class UserAlarmListSync extends AsyncTask<Void, Void, Void> {

        private final syncType mSyncType;
        private final String mAlarm;
        private String mUsername;

        UserAlarmListSync(syncType st, String alarm) {
            mSyncType = st;
            mAlarm = alarm;
        }

        @Override
        protected Void doInBackground(Void... params) {

            try {

                //Create JSON objects and strings to use in the request
                JSONObject clientJson = new JSONObject();
                JSONObject clientData = new JSONObject();
                JSONObject serverJson;
                String serverResponseData;
                String serverResponseType;

                if(mSyncType == syncType.SERVER_TO_CLIENT_FULL) {

                    //Create json request to send to server
                    clientJson.put("requestType", "full_alarm_sync");
                    clientData.put("email", ThisUserEmail);
                    clientJson.put("requestData", clientData);
                    clientMessage = clientJson.toString();

                    //Make request to server
                    serverMessage = serverRequest(clientMessage);

                    //Extract the JSON info from the server message
                    serverJson = new JSONObject(serverMessage);
                    serverResponseType = serverJson.getString("responseType");
                    serverResponseData = serverJson.getString("responseData");

                    //Put the alarm list into the alarm list variable if there are alarms
                    if(serverResponseType.equals("alarms present")) {
                        ThisUserAlarmList = serverResponseData.split(",");
                    }
                    else if(serverResponseType.equals("alarms absent"))
                        ThisUserAlarmList = null;
                    }
                    else {
                        Log.d("debugMessage", "Unrecognized response type");
                    }

            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("debugMessage", "JSON exception");
            }

            return null;
        }

        @Override
        protected void onCancelled() {
            mAlarmSyncTask = null;
        }
    }

    /**
     * Represents an asynchronous task to send an alarm to another user
     */
    public class AlarmSendTask extends AsyncTask<Void, Void, Void> {

        private String mTargetUser;
        private String mUrl;

        AlarmSendTask(String fieldValue) {
            String componentHolder[] = fieldValue.split(":");
            mTargetUser = componentHolder[0];
            mUrl = componentHolder[1];
        }

        @Override
        protected Void doInBackground(Void... params) {

            try {

                //Create JSON objects and strings to use in the request
                JSONObject clientJson = new JSONObject();
                JSONObject clientData = new JSONObject();
                JSONObject serverJson;
                String serverResponseData;
                String serverResponseType;

                //Create json request to send to server
                clientJson.put("requestType", "alarm_send");
                clientData.put("targetUser", mTargetUser);
                clientData.put("url", mUrl);
                clientJson.put("requestData", clientData);
                clientMessage = clientJson.toString();

                //Make request to server
                serverMessage = serverRequest(clientMessage);

                //Extract the JSON info from the server message
                serverJson = new JSONObject(serverMessage);
                serverResponseType = serverJson.getString("responseType");
                serverResponseData = serverJson.getString("responseData");

                //TODO: should have server respond with something


            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("debugMessage", "JSON exception");
            }

            return null;
        }

        @Override
        protected void onCancelled() {
            mAlarmSendTask = null;
        }
    }

    /* Public functions */
    public static String serverRequest(String requestMessage) {

        String serverResponse;

        try {
            //Connect to server and send message
            s = new Socket();
            s.connect(new InetSocketAddress(MainActivity.serverip, serverPort), 9020);
            printWriter = new PrintWriter(s.getOutputStream()); //set output stream
            printWriter.write(requestMessage); //adds data to the print writer
            printWriter.flush(); //send data in the print writer through socket

            //Read the server's message
            input = new BufferedReader(new InputStreamReader(s.getInputStream()));

            serverResponse = input.readLine();

            //Then close socket
            printWriter.close();
            s.close();

            return serverResponse;

        } catch (UnknownHostException e) {
            e.printStackTrace();
            Log.d("debugMessage", "Unknown host exception");
            return "";
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("debugMessage", "IO exception");
            return "";
        }
    }


    /**
     * Picks Random alarm from user's alarm list and plays it. Doesn't delete alarm from list
     */
    private String pickRandomAlarm() {
        Random randNum = new Random();
        int randInt = randNum.nextInt(ThisUserAlarmList.length);
        String selectedAlarm = ThisUserAlarmList[randInt];
        return selectedAlarm;
    }



}

