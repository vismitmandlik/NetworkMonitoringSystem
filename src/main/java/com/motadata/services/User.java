package com.motadata.services;

import com.motadata.Main;
import com.motadata.configs.Auth;
import com.motadata.constants.Constants;
import com.motadata.db.Operations;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class User
{
    private static final Logger LOGGER = LoggerFactory.getLogger(User.class);

    public static final String TOKEN = "token";

    public static final String JWT_EXPIRY_TIME_IN_SECONDS = "jwtExpiryTimeInSeconds";

    // Register a new user
    public static void register(RoutingContext context)
    {
        var requestBody = context.body().asJsonObject();

        var username = requestBody.getString(Constants.USERNAME);

        var password = requestBody.getString(Constants.PASSWORD);

        var query = new JsonObject().put(Constants.USERNAME, username);

        try
        {
            // Check if user already exists
            Operations.findOne(Constants.USERS_COLLECTION, query).onComplete(result ->
            {
                if (result.succeeded())
                {
                    context.response().setStatusCode(Constants.SC_400).end("User already exists.");
                }
                else
                {
                    var newUser = new JsonObject().put(Constants.USERNAME, username).put(Constants.PASSWORD, password);

                    // Insert new user
                    Operations.insert(Constants.USERS_COLLECTION, newUser).onComplete(asyncResult ->
                    {
                        if (asyncResult.succeeded())
                        {
                            context.response().setStatusCode(Constants.SC_201).end("User registered successfully.");
                        }
                        else
                        {
                            context.response().setStatusCode(Constants.SC_500).end("Error registering user.");
                        }
                    });
                }

            }).onFailure(error -> context.response().setStatusCode(Constants.SC_500).end("Error checking user existence." + error));
        }

        catch (Exception exception)
        {
            LOGGER.error("Failed to register user: {}", exception.getMessage());
        }
    }

    // User login and JWT token generation
    public static void login(RoutingContext context)
    {
        var requestBody = context.body().asJsonObject();

        var username = requestBody.getString(Constants.USERNAME);

        var password = requestBody.getString(Constants.PASSWORD);

        var query = new JsonObject().put(Constants.USERNAME, username);

        try
        {
            // Find user by username
            Operations.findOne(Constants.USERS_COLLECTION, query).onComplete(asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    var user = asyncResult.result();

                    if (user != null)
                    {
                        if (user.getString(Constants.PASSWORD).equals(password))
                        {
                            var jwtOptions = new JWTOptions().setExpiresInSeconds(Main.vertx().getOrCreateContext().config().getInteger(JWT_EXPIRY_TIME_IN_SECONDS));

                            var token = Auth.jwtAuth().generateToken(new JsonObject().put(Constants.USERNAME, username), jwtOptions);

                            context.response().putHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON).end(new JsonObject().put(TOKEN, token).encode());
                        }

                        else
                        {
                            context.response().setStatusCode(Constants.SC_401).end("Invalid password");
                        }
                    }

                    else
                    {
                        context.response().setStatusCode(Constants.SC_404).end("User not found");
                    }
                }

                else
                {
                    context.response().setStatusCode(Constants.SC_500).end("Error checking user credentials. " + asyncResult.cause());
                }
            });

        }

        catch (Exception exception)
        {
            LOGGER.error("Failed to login user: {}", exception.getMessage());
        }
    }
}
