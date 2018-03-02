package com.ashish.errorhandling.domain.interactors.impl;

import com.ashish.errorhandling.domain.executor.Executor;
import com.ashish.errorhandling.domain.executor.MainThread;
import com.ashish.errorhandling.domain.interactors.LoginInteractor;
import com.ashish.errorhandling.domain.interactors.base.AbstractInteractor;
import com.ashish.errorhandling.domain.repository.UserRepository;
import com.ashish.errorhandling.network.RestClient;
import com.ashish.errorhandling.network.payload.AuthenticatePayload;
import com.ashish.errorhandling.network.payload.GetLastLoginDetailsPayload;
import com.ashish.errorhandling.network.response.AuthenticateResponse;
import com.ashish.errorhandling.network.services.ErrorHandlingRestService;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * @author ashish
 * @since 28/02/18
 */
public class LoginInteractorImpl extends AbstractInteractor implements LoginInteractor {

    private Callback callback;
    private UserRepository userRepository;
    private String userName;
    private String password;

    public LoginInteractorImpl(Executor threadExecutor, MainThread mainThread, Callback callback,
                               UserRepository userRepository, String username, String password) {
        super(threadExecutor, mainThread);

        if(userRepository == null || callback == null) {
            throw new IllegalArgumentException("Arguments can not be null!");
        }

        this.callback = callback;
        this.userRepository = userRepository;
        this.userName = username;
        this.password = password;

    }

    @Override
    public void run() {
        // initializing the REST service we will use
        ErrorHandlingRestService errorHandlingRestService = RestClient.getService(ErrorHandlingRestService.class);

        // initializing payload object for authenticate
        AuthenticatePayload authenticatePayload = new AuthenticatePayload(userName, password);

        errorHandlingRestService.authenticate(authenticatePayload).enqueue(new retrofit2.Callback<AuthenticateResponse>() {
            @Override
            public void onResponse(Call<AuthenticateResponse> call, Response<AuthenticateResponse> response) {
                AuthenticateResponse authenticateResponse = response.body();

                if(response.isSuccessful() && authenticateResponse != null &&
                        authenticateResponse.getStatus() ==1) {
                    userRepository.saveAuthToken(authenticateResponse.getAccessToken());
                    mMainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess();
                        }
                    });
                } else {
                    String message = authenticateResponse != null &&
                            authenticateResponse.getMsg() != null ?
                            authenticateResponse.getMsg() :
                            "Something went wrong !!!";
                    onError(message);
                }
            }

            @Override
            public void onFailure(Call<AuthenticateResponse> call, Throwable error) {
                String errorMessage;
                if (error instanceof IOException) {
                    // Timeout
                    errorMessage = String.valueOf(error.getCause());
                } else if (error instanceof IllegalStateException) {
                    // ConversionError
                    errorMessage = String.valueOf(error.getCause());
                } else {
                    // Other Error
                    errorMessage = String.valueOf(error.getLocalizedMessage());
                }
                onError(errorMessage);
            }
        });
    }

    private void onError(final String errorMessage) {
        mMainThread.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(errorMessage);
            }
        });
    }

}
