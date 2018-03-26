package com.example.nickk_000.snoozinapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

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

public class RegisterAccountActivity extends AppCompatActivity {
    //Set up shared preferences stuff
    public static final String LOGINPREFERENCES = "LoginPrefs";
    public static final Boolean DEFAULTLOGINSTATE = false;
    public static final String LoginState = "loginStateKey";
    SharedPreferences loginSharedPreferences;

    public static final String REGISTERPREFS = "RegisterPrefs";
    public static final String REGISTER_EMAIL = "registerEmailKey";
    public static final String REGISTER_PASSWORD = "registerPasswordKey";
    SharedPreferences sharedPreferences;

    //Set up shared preferences for user info
    public static final String USERINFOPREFERENCES = "userInfoPrefs";
    public static final String USER_EMAIL = "userEmailKey";
    public static final String USER_PASSWORD = "userPasswordKey";
    public static final String USER_USERNAME = "userUsernameKey";
    SharedPreferences userInfoSharedPreferences;

    //Set up TCP stuff
    private static Socket s;
    private static ServerSocket ss;
    private static InputStreamReader isr;
    private static BufferedReader input;
    private static PrintWriter printWriter;
    String clientMessage = "";
    String serverMessage = "";
    private static int serverPort = 9020;

    //Create enum for username creation attemps
    enum usernameVerificationResult {
        USERNAME_AVAILABLE, USERNAME_TAKEN, ERROR;
    }
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private RegisterAccountActivity.UsernameVerificationTask mAuthTask = null;

    // UI references.
    private EditText mUsernameView;
    private View mProgressView;
    private View mRegisterFormView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_account);
        setTitle(getResources().getText(R.string.title_activity_register));

        mUsernameView = (EditText) findViewById(R.id.username);

        Button mRegisterUsernameButton = (Button) findViewById(R.id.complete_registration_button);
        mRegisterUsernameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                verifyUsername();
            }
        });

        mRegisterFormView = findViewById(R.id.username_verification_form);
        mProgressView = findViewById(R.id.username_verification_progress);
    }

    /**
     * Attempts to create a username for the new user
     * If there are form errorss(ex the username is taken already), the
     * errors are presented and no actual login attempt is made.
     */
    private void verifyUsername() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mUsernameView.setError(null);

        // Store values at the time of the login attempt.
        String username = mUsernameView.getText().toString();

        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        showProgress(true);
        mAuthTask = new UsernameVerificationTask(username);
        mAuthTask.execute((Void) null);
    }


    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mRegisterFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mRegisterFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mRegisterFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mRegisterFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UsernameVerificationTask extends AsyncTask<Void, Void, usernameVerificationResult> {

        private final String mUsername;
        private String mEmail;
        private String mPassword;

        UsernameVerificationTask(String username) {
            mUsername = username;
        }

        @Override
        protected usernameVerificationResult doInBackground(Void... params) {

            try {

                //Retrieve register shared preference file
                sharedPreferences = getSharedPreferences(REGISTERPREFS, Context.MODE_PRIVATE);
                mEmail = sharedPreferences.getString(REGISTER_EMAIL, null);
                mPassword = sharedPreferences.getString(REGISTER_PASSWORD, null);

                //Make sure we didn't get to the register screen and don't know the email and password
                if(mEmail == null || mPassword == null) {
                    return usernameVerificationResult.ERROR;
                }

                //Create JSON objects and strings to use in the request
                JSONObject json = new JSONObject();
                JSONObject data = new JSONObject();
                JSONObject serverJson;
                String serverResponseType;

                //Create json request to send to server
                json.put("requestType", "username_verify_request");
                data.put("username", mUsername);
                data.put("email", mEmail);
                data.put("password", mPassword);
                json.put("requestData", data);
                clientMessage = json.toString();

                //Make request to server
                serverMessage = MainActivity.serverRequest(clientMessage);

                //Extract the JSON info from the server message (don't need data, not used in this request)
                serverJson = new JSONObject(serverMessage);
                serverResponseType = serverJson.getString("responseType");

                //Determine what the login result is based on the serverMessage
                if(serverResponseType.equals("username available")) {
                    return usernameVerificationResult.USERNAME_AVAILABLE;
                } else if (serverResponseType.equals("username taken")) {
                    return usernameVerificationResult.USERNAME_TAKEN;
                } else {
                    return usernameVerificationResult.ERROR;
                }

            } catch (JSONException e) {
                e.printStackTrace();
                return usernameVerificationResult.ERROR;
            }

        }

        @Override
        protected void onPostExecute(final usernameVerificationResult result) {
            mAuthTask = null;
            showProgress(false);

            if (result.equals(usernameVerificationResult.USERNAME_AVAILABLE)) {
                /* END REGISTRATION PROCESS....REECORD THE USER'S INFO */
                //--->Set registration shared pref values to null to signify there is no registration in proceess anymore
                sharedPreferences = getSharedPreferences(REGISTERPREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor registerEditor = sharedPreferences.edit();
                registerEditor.putString(REGISTER_EMAIL, null);
                registerEditor.putString(REGISTER_PASSWORD, null);
                registerEditor.apply();
                //--->put email, password and username in user info shared pref file for use in main activity
                userInfoSharedPreferences = getSharedPreferences(USERINFOPREFERENCES, Context.MODE_PRIVATE);
                SharedPreferences.Editor userInfoEditor = userInfoSharedPreferences.edit();
                userInfoEditor.putString(USER_EMAIL, mEmail);
                userInfoEditor.putString(USER_PASSWORD, mPassword);
                userInfoEditor.putString(USER_USERNAME, mUsername);
                userInfoEditor.apply();

                /* USERNAME CREATED...SET LOG IN STATE TO TRUE AND SWITCH TO MAIN ACTIVITY*/
                //--->Set login state to true
                loginSharedPreferences = getSharedPreferences(LOGINPREFERENCES, Context.MODE_PRIVATE);
                SharedPreferences.Editor loginEditor = loginSharedPreferences.edit();
                loginEditor.putBoolean(LoginState, true);
                loginEditor.commit();
                //--->Switch to main activity
                Intent myIntent = new Intent(RegisterAccountActivity.this, MainActivity.class);
                startActivity(myIntent);

                /* CLOSE THIS ACTIVITY */
                finish();
            } else if(result.equals(usernameVerificationResult.USERNAME_TAKEN)) {
                mUsernameView.setError(getString(R.string.error_username_taken));
                mUsernameView.requestFocus();
            } else {
                mUsernameView.setError("Oops...we screwed up, try again maybe?"); //Probably should be a different kind of error
                mUsernameView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}

