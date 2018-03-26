package com.example.nickk_000.snoozinapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.app.LoaderManager.LoaderCallbacks;

import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity implements LoaderCallbacks<Cursor> {

    //Set up shared preferences stuff
    public static final String LOGINPREFERENCES = "LoginPrefs";
    public static final Boolean DEFAULTLOGINSTATE = false;
    public static final String LoginState = "loginStateKey";
    SharedPreferences sharedPreferences;

    public static final String REGISTERPREFS = "RegisterPrefs";
    public static final String REGISTER_EMAIL = "registerEmailKey";
    public static final String REGISTER_PASSWORD = "registerPasswordKey";
    SharedPreferences registerSharedPreferences;

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

    //Create enum for login attempt results
    enum loginResult {
        NEW_USER, ACCESS_DENIED, ACCESS_GRANTED, ERROR;
    }

    /**
     * Id to identity READ_CONTACTS permission request.
     */
    private static final int REQUEST_READ_CONTACTS = 0;

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    // UI references.
    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Set up the login form.
        mEmailView = (AutoCompleteTextView) findViewById(R.id.email);
        populateAutoComplete();

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
    }

    private void populateAutoComplete() {
        if (!mayRequestContacts()) {
            return;
        }

        getLoaderManager().initLoader(0, null, this);
    }

    private boolean mayRequestContacts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (checkSelfPermission(READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (shouldShowRequestPermissionRationale(READ_CONTACTS)) {
            Snackbar.make(mEmailView, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, new View.OnClickListener() {
                        @Override
                        @TargetApi(Build.VERSION_CODES.M)
                        public void onClick(View v) {
                            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
                        }
                    });
        } else {
            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
        }
        return false;
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                populateAutoComplete();
            }
        }
    }


    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password
        if (!isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(email, password);
            mAuthTask.execute((Void) null);
        }
    }

    private boolean isEmailValid(String email) {
        //TODO: Replace this with your own logic
        return email.contains("@");
    }

    private boolean isPasswordValid(String password) {
        //TODO: Replace this with your own logic
        return password.length() >= 4;
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

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
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
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(this,
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,

                // Select only email addresses.
                ContactsContract.Contacts.Data.MIMETYPE +
                        " = ?", new String[]{ContactsContract.CommonDataKinds.Email
                .CONTENT_ITEM_TYPE},

                // Show primary email addresses first. Note that there won't be
                // a primary email address if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        List<String> emails = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            emails.add(cursor.getString(ProfileQuery.ADDRESS));
            cursor.moveToNext();
        }

        addEmailsToAutoComplete(emails);

    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {

    }

    private void addEmailsToAutoComplete(List<String> emailAddressCollection) {
        //Create adapter to tell the AutoCompleteTextView what to show in its dropdown list.
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(LoginActivity.this,
                        android.R.layout.simple_dropdown_item_1line, emailAddressCollection);

        mEmailView.setAdapter(adapter);
    }


    private interface ProfileQuery {
        String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
        };

        int ADDRESS = 0;
        int IS_PRIMARY = 1;
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, loginResult> {

        private final String mEmail;
        private final String mPassword;
        private String mUsername;

        UserLoginTask(String email, String password) {
            mEmail = email;
            mPassword = password;
        }

        @Override
        protected loginResult doInBackground(Void... params) {

            try {

                //Create JSON objects and strings to use in the request
                JSONObject clientJson = new JSONObject();
                JSONObject clientData = new JSONObject();
                JSONObject serverJson;
                JSONObject serverResponseData;
                String serverResponseType;

                //Create json request to send to server
                clientJson.put("requestType", "auth_request");
                clientData.put("email", mEmail);
                clientData.put("password", mPassword);
                clientJson.put("requestData", clientData);
                clientMessage = clientJson.toString();

                //Connect to server and send message
                s = new Socket();
                s.connect(new InetSocketAddress(MainActivity.serverip, serverPort), 9020);
                printWriter = new PrintWriter(s.getOutputStream()); //set output stream
                printWriter.write(clientMessage); //adds data to the print writer
                printWriter.flush(); //send data in the print writer through socket

                //Read the server's message
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                serverMessage = br.readLine();

                //Then close socket
                printWriter.close();
                s.close();

                //Extract the JSON info from the server message
                serverJson = new JSONObject(serverMessage);
                serverResponseType = serverJson.getString("responseType");

                //Determine what the login result is based on the serverMessage
                if(serverResponseType.equals("new user request")) {
                    return loginResult.NEW_USER;
                } else if (serverResponseType.equals("access denied")) {
                    return loginResult.ACCESS_DENIED;
                } else if (serverResponseType.equals("access granted")) {
                    serverResponseData = serverJson.getJSONObject("responseData");
                    mUsername = serverResponseData.getString("username");
                    return loginResult.ACCESS_GRANTED;
                } else {
                    return loginResult.ERROR;
                }

            } catch (UnknownHostException e) {
                e.printStackTrace();
                return loginResult.ERROR;
            } catch (IOException e) {
                e.printStackTrace();
                return loginResult.ERROR;
            } catch (JSONException e) {
                e.printStackTrace();
                return loginResult.ERROR;
            }

        }

        @Override
        protected void onPostExecute(final loginResult result) {
            mAuthTask = null;
            showProgress(false);

            if (result.equals(loginResult.NEW_USER)) {
                /* NEW ACCOUNT CREATED...RECORD EMAIL AND PASSWORD...SWITCH TO RESGISTER ACTIVITY*/
                //--->Set email and password
                registerSharedPreferences = getSharedPreferences(REGISTERPREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor registerEditor = registerSharedPreferences.edit();
                registerEditor.putString(REGISTER_EMAIL, mEmail);
                registerEditor.putString(REGISTER_PASSWORD, mPassword);
                registerEditor.commit();
                //--->Switch to main activity
                Intent myIntent = new Intent(LoginActivity.this, RegisterAccountActivity.class);
                startActivity(myIntent);
                //--->Close this activity
                finish();
            } else if(result.equals(loginResult.ACCESS_DENIED)) {
                mPasswordView.setError(getString(R.string.error_incorrect_password));
                mPasswordView.requestFocus();
            } else if(result.equals(loginResult.ACCESS_GRANTED)) {
                 /* PUT EMAIL, PASSWORD, USERNAME IN USER INFO PREFS */
                userInfoSharedPreferences = getSharedPreferences(USERINFOPREFERENCES, Context.MODE_PRIVATE);
                SharedPreferences.Editor userInfoEditor = userInfoSharedPreferences.edit();
                userInfoEditor.putString(USER_EMAIL, mEmail);
                userInfoEditor.putString(USER_PASSWORD, mPassword);
                userInfoEditor.putString(USER_USERNAME, mUsername);
                userInfoEditor.apply();

                /* USER HAS BEEN VERIFIED...SET LOG IN STATE TO TRUE AND SWITCH TO MAIN ACTIVITY*/
                //--->Retrieve login shared preference file
                sharedPreferences = getSharedPreferences(LOGINPREFERENCES, Context.MODE_PRIVATE);
                //--->Set login state to true
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(LoginState, true);
                editor.commit();
                //--->Switch to main activity
                Intent myIntent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(myIntent);

                /* CLOSE THIS ACTIVITY */
                finish();
            } else {
                mPasswordView.setError("Oops...we screwed up, try again maybe?"); //Probably should be a different kind of error
                mPasswordView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}

