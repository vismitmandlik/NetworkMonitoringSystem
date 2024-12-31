package com.motadata.services;

import com.motadata.configs.Auth;
import com.motadata.constants.Constants;
import com.motadata.db.Operations;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.web.RoutingContext;

public class User
{
    // Register a new user
    public static void register(RoutingContext context)
    {
        var requestBody = context.body().asJsonObject();

        var username = requestBody.getString("username");

        var password = requestBody.getString("password");

        var query = new JsonObject().put("username", username);

        try
        {
            // Check if user already exists
            Operations.findOne(Constants.USERS_COLLECTION, query).onSuccess(existingUser ->
            {
                if (existingUser != null)
                {
                    context.response().setStatusCode(400).end("User already exists.");
                }

                else
                {
                    var newUser = new JsonObject().put("username", username).put("password", password);

                    // Insert new user
                    Operations.insert(Constants.USERS_COLLECTION, newUser).onSuccess(id -> context.response().setStatusCode(201).end("User registered successfully.")).onFailure(err -> context.response().setStatusCode(500).end("Error registering user."));
                }

            }).onFailure(error -> context.response().setStatusCode(500).end("Error checking user existence." + error));
        }

        catch (Exception exception)
        {
            System.err.println("Failed to register user. " + exception);
        }
    }

    // User login and JWT token generation
    public static void login(RoutingContext context)
    {
        var requestBody = context.body().asJsonObject();

        var username = requestBody.getString("username");

        var password = requestBody.getString("password");

        var query = new JsonObject().put("username", username);

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
                        if (user.getString("password").equals(password))
                        {
                            var jwtOptions = new JWTOptions().setExpiresInSeconds(3600);

                            var token = Auth.jwtAuth().generateToken(new JsonObject().put("username", username), jwtOptions);

                            context.response().putHeader("Content-Type", "application/json")
                                    .end(new JsonObject().put("token", token).encode());
                        }

                        else
                        {
                            context.response().setStatusCode(401).end("Invalid password");
                        }
                    }

                    else
                    {
                        context.response().setStatusCode(404).end("User not found");
                    }
                }

                else
                {
                    context.response().setStatusCode(500).end("Error checking user credentials. " + asyncResult.cause());
                }
            });

        }

        catch (Exception exception)
        {
            System.err.println("Failed to login. " + exception);
        }
    }
}
