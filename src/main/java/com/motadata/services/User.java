package com.motadata.services;

import com.motadata.configs.Auth;
import com.motadata.db.Operations;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;

public class    User
{
    private static final String USERS_COLLECTION = "users";

    private static final JWTAuth jwtAuth = Auth.getJwtAuth();

    // Register a new user
    public static void register(RoutingContext context)
    {
        var requestBody = context.body().asJsonObject();

        var username = requestBody.getString("username");

        var password = requestBody.getString("password");

        var query = new JsonObject().put("username", username);

        // Check if user already exists
        Operations.findOne(USERS_COLLECTION, query).onSuccess(existingUser ->
        {
            if (existingUser != null)
            {
                context.response().setStatusCode(400).end("User already exists.");
            }
            else
            {
                JsonObject newUser = new JsonObject().put("username", username).put("password", password);

                // Insert new user
                Operations.insert(USERS_COLLECTION, newUser).onSuccess(id -> context.response().setStatusCode(201).end("User registered successfully.")).onFailure(err -> context.response().setStatusCode(500).end("Error registering user."));
            }
        }).onFailure(err -> context.response().setStatusCode(500).end("Error checking user existence."));
    }

    // User login and JWT token generation
    public static void login(RoutingContext context)
    {
        JsonObject requestBody = context.body().asJsonObject();

        String username = requestBody.getString("username");

        String password = requestBody.getString("password");

        JsonObject query = new JsonObject().put("username", username);

        // Find user by username
        Operations.findOne(USERS_COLLECTION, query).onSuccess(user ->
        {
            if (user != null)
            {
                if (user.getString("password").equals(password))
                {
                    JWTOptions jwtOptions = new JWTOptions().setExpiresInSeconds(3600);

                    String token = jwtAuth.generateToken(new JsonObject().put("username", username), jwtOptions);

                    context.response().putHeader("Content-Type", "application/json").end(new JsonObject().put("token", token).encode());
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
        }).onFailure(err -> context.response().setStatusCode(500).end("Error checking user credentials."));
    }
}
