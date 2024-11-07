package com.motadata.api;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;


public class User {

    private final com.motadata.services.User user;
    private final JWTAuth jwtAuth;

    public User(com.motadata.services.User user, JWTAuth jwtAuth) {
        this.user = user;
        this.jwtAuth = jwtAuth;
    }

    // Initialize routes and register them in the Router
    public void initRoutes(Router router) {
        // Setup routes
        router.post("/api/user/login").handler(BodyHandler.create()).handler(User::login);
        router.post("/api/user/register").handler(BodyHandler.create()).handler(this::register);
    }

    // Register a new user
    private void register(RoutingContext context) {
        JsonObject requestBody = context.body().asJsonObject();
        String username = requestBody.getString("username");
        String password = requestBody.getString("password");

        System.out.println("Registering user: " + username);

        // You can delegate the registration logic to your service layer if needed
        // For now, you can just simulate the response.
        context.response().setStatusCode(201).end("User registered successfully.");
    }

    // User login and JWT token generation
    private static void login(RoutingContext context) {
        JsonObject requestBody = context.body().asJsonObject();
        String username = requestBody.getString("username");
        String password = requestBody.getString("password");

        // Delegate to the service layer for login
        user.login(username, password, jwtAuth, res -> {
            if (res.succeeded()) {
                // On successful login, send the token as response
                context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("token", res.result()).encode());
            } else {
                // Send error response if login fails
                context.response().setStatusCode(401).end("Invalid credentials.");
            }
        });
    }
}
