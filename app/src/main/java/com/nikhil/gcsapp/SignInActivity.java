package com.nikhil.gcsapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.hbb20.CountryCodePicker;
import com.nikhil.gcsapp.models.User;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignInActivity extends BaseActivity implements View.OnClickListener {

    private static final String TAG = "SignInActivity";
    private static final Pattern VALID_EMAIL_ADDRESS_REGEX =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private EditText mEmailField;
    private EditText mPasswordField;
    private EditText mPhoneNumberField;
    private EditText mPhoneCodeField;

    private Button mSignInButton;
    private Button mSignUpButton;
    private Button mResetButton;
    private Button mPhoneGetCodeButton;
    private Button mPhoneCodeButton;

    private CountryCodePicker mCountryCodePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        // Views
        mEmailField = findViewById(R.id.field_email);
        mPasswordField = findViewById(R.id.field_password);
        mPhoneNumberField = findViewById(R.id.phone_num_field);
        mPhoneCodeField = findViewById(R.id.phone_code_field);
        mCountryCodePicker = findViewById(R.id.country_code_picker);

        mSignInButton = findViewById(R.id.button_sign_in);
        mSignUpButton = findViewById(R.id.button_sign_up);
        mResetButton = findViewById(R.id.button_reset);
        mPhoneGetCodeButton = findViewById(R.id.button_phone_get_code);
        mPhoneCodeButton = findViewById(R.id.button_phone_login);

        // Click listeners
        mSignInButton.setOnClickListener(this);
        mSignUpButton.setOnClickListener(this);

        mResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(validateEmail()) {
                    sendEmailForPasswordReset();
                }
            }
        });

        mPhoneGetCodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO : Request for a Code
                Toast.makeText(SignInActivity.this, "Will be implemented next week",
                        Toast.LENGTH_SHORT).show();
            }
        });

        mPhoneCodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO : Get Code received via SMS
                Toast.makeText(SignInActivity.this, "Will be implemented next week",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        // Check auth on Activity start
        if (mAuth.getCurrentUser() != null) {
            onAuthSuccess(mAuth.getCurrentUser());
        }
    }

    private void signIn() {
        Log.d(TAG, "signIn");
        if (!validateForm()) {
            return;
        }

        showProgressDialog();
        String email = mEmailField.getText().toString();
        String password = mPasswordField.getText().toString();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signIn:onComplete:" + task.isSuccessful());
                        hideProgressDialog();

                        if (task.isSuccessful()) {
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                            boolean emailVerified = user.isEmailVerified();
                            if(!emailVerified) {
                                Toast.makeText(SignInActivity.this, R.string.verify_email,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                onAuthSuccess(task.getResult().getUser());
                            }
                        } else {
                            try {
                                throw task.getException();
                            } catch (FirebaseAuthInvalidCredentialsException authError) {
                                Toast.makeText(SignInActivity.this, R.string.wrong_credentials,
                                        Toast.LENGTH_SHORT).show();
                            } catch (FirebaseAuthInvalidUserException invalidUserError) {
                                Toast.makeText(SignInActivity.this, R.string.user_not_found,
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(SignInActivity.this, R.string.sign_in_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void signUp() {
        Log.d(TAG, "signUp");
        if (!validateForm()) {
            return;
        }

        showProgressDialog();
        String email = mEmailField.getText().toString();
        String password = mPasswordField.getText().toString();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "createUser:onComplete:" + task.isSuccessful());
                        hideProgressDialog();

                        if (task.isSuccessful()) {
                            sendEmailVerification();
                            mAuth.signOut();
                        } else {
                            try {
                                throw task.getException();
                            } catch (FirebaseAuthUserCollisionException collisionError) {
                                Toast.makeText(SignInActivity.this, R.string.user_exists,
                                        Toast.LENGTH_SHORT).show();
                            } catch (FirebaseAuthWeakPasswordException weakAuth) {
                                Toast.makeText(SignInActivity.this, R.string.min_password_label,
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(SignInActivity.this, R.string.sign_up_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void onAuthSuccess(FirebaseUser user) {
        String username = usernameFromEmail(user.getEmail());
        writeNewUser(user.getUid(), username, user.getEmail());
        startActivity(new Intent(SignInActivity.this, MainActivity.class));
        finish();
    }

    private String usernameFromEmail(String email) {
        if (email.contains("@")) {
            return email.split("@")[0];
        } else {
            return email;
        }
    }

    private boolean validateForm() {
        boolean result = true;
        if (TextUtils.isEmpty(mEmailField.getText().toString())) {
            mEmailField.setError("Required");
            result = false;
        } else {
            Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(mEmailField.getText().toString());
            if (!matcher.find()) {
                mEmailField.setError("Not An Email");
                result = false;
            } else {
                mEmailField.setError(null);
            }
        }

        if (TextUtils.isEmpty(mPasswordField.getText().toString())) {
            mPasswordField.setError("Required");
            result = false;
        } else {
            mPasswordField.setError(null);
        }

        return result;
    }

    private boolean validateEmail() {
        boolean result = true;
        if (TextUtils.isEmpty(mEmailField.getText().toString())) {
            mEmailField.setError("Required");
            result = false;
        } else {
            Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(mEmailField.getText().toString());
            if (!matcher.find()) {
                mEmailField.setError("Not An Email");
                result = false;
            } else {
                mEmailField.setError(null);
            }
        }

        return result;
    }

    private boolean validatePhone() {
        boolean result = true;
        if (TextUtils.isEmpty(mEmailField.getText().toString())) {
            mEmailField.setError("Required");
            result = false;
        } else {
            Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(mEmailField.getText().toString());
            if (!matcher.find()) {
                mEmailField.setError("Not An Email");
                result = false;
            } else {
                mEmailField.setError(null);
            }
        }

        return result;
    }

    private void writeNewUser(String userId, String name, String email) {
        User user = new User(name, email);
        mDatabase.child("users").child(userId).setValue(user);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.button_sign_in) {
            signIn();
        } else if (i == R.id.button_sign_up) {
            signUp();
        }
    }

    private void sendEmailVerification() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null){
            user.sendEmailVerification().addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()){
                        Toast.makeText(SignInActivity.this, R.string.email_verification,
                                Toast.LENGTH_SHORT).show();
                        FirebaseAuth.getInstance().signOut();
                    }
                }
            });
        }
    }

    private void sendEmailForPasswordReset() {
        FirebaseAuth.getInstance().sendPasswordResetEmail(mEmailField.getText().toString())
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Email Sent.");
                            Toast.makeText(SignInActivity.this, R.string.email_password_reset,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            try {
                                throw task.getException();
                            }  catch (FirebaseAuthInvalidUserException invalidUserError) {
                                Toast.makeText(SignInActivity.this, R.string.user_not_found,
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(SignInActivity.this, R.string.reset_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithPhoneAuth:success");
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                            boolean emailVerified = user.isEmailVerified();
                            if(!emailVerified) {
                                Toast.makeText(SignInActivity.this, R.string.verify_email,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                onAuthSuccess(task.getResult().getUser());
                            }
                        } else {
                            Log.w(TAG, "signInWithPhoneAuth:failure", task.getException());
                            try {
                                throw task.getException();
                            } catch (FirebaseAuthInvalidCredentialsException authError) {
                                Toast.makeText(SignInActivity.this, "Invalid Code",
                                        Toast.LENGTH_SHORT).show();
                            } catch (FirebaseAuthInvalidUserException invalidUserError) {
                                Toast.makeText(SignInActivity.this, R.string.user_not_found,
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(SignInActivity.this, R.string.sign_in_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

}
