package com.example.nickk_000.snoozinapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

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

    //Set up UI variables
    Button logoutButton;
    TextView helloUserText;

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

        //Set text next to "Hello" at top of activity to say the username
        helloUserText = (TextView) findViewById(R.id.helloUser);
        userInfoSharedPreferences = getSharedPreferences(USERINFOPREFERENCES, Context.MODE_PRIVATE);
        helloUserText.setText(userInfoSharedPreferences.getString(USER_USERNAME, ""));
    }
}
