package com.motadata.api;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.jwt.JWTOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.mongo.MongoClient;

public class User {

    private final MongoClient mongoClient;
    private final JWTAuth jwtAuth;

    public User(MongoClient mongoClient, JWTAuth jwtAuth) {
        this.mongoClient = mongoClient;
        this.jwtAuth = jwtAuth;
    }

    public void mount(Router router) {
        router.post("/api/user/login").handler(BodyHandler.create()).handler(this::login);
        router.post("/api/user/register").handler(BodyHandler.create()).handler(this::register);
    }

    // Register a new user
    private void register(RoutingContext context) {
        JsonObject requestBody = context.body().asJsonObject();
        String username = requestBody.getString("username");
        String password = requestBody.getString("password");

        if (username == null || password == null) {
            context.response().setStatusCode(400).end("Username and password are required.");
            return;
        }

        JsonObject query = new JsonObject().put("username", username);
        mongoClient.findOne("users", query, null, res -> {
            if (res.succeeded() && res.result() != null) {
                context.response().setStatusCode(409).end("User already exists.");
            } else {
                JsonObject newUser = new JsonObject()
                        .put("username", username)
                        .put("password", hashPassword(password)); // Use a proper hashing method
                mongoClient.insert("users", newUser, insertRes -> {
                    if (insertRes.succeeded()) {
                        context.response().setStatusCode(201).end("User registered successfully.");
                    } else {
                        context.response().setStatusCode(500).end("Error registering user.");
                    }
                });
            }
        });
    }

    // User login and JWT token generation
    private void login(RoutingContext context) {
        JsonObject requestBody = context.body().asJsonObject();
        String username = requestBody.getString("username");
        String password = requestBody.getString("password");

        if (username == null || password == null) {
            context.response().setStatusCode(400).end("Username and password are required.");
            return;
        }

        JsonObject query = new JsonObject().put("username", username);
        mongoClient.findOne("users", query, null, res -> {
            if (res.succeeded() && res.result() != null) {
                JsonObject user = res.result();
                if (checkPassword(password, user.getString("password"))) { // Add password validation
                    String token = jwtAuth.generateToken(
                            new JsonObject().put("username", username),
                            new JWTOptions().setExpiresInMinutes(60) // Token expiration
                    );
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("token", token).encode());
                } else {
                    context.response().setStatusCode(401).end("Invalid credentials.");
                }
            } else {
                context.response().setStatusCode(404).end("User not found.");
            }
        });
    }

    // Hash password (use a secure hashing library)
    private String hashPassword(String password) {
        // Implement hashing logic, such as using BCrypt or another secure hashing method
        return password;
    }

    // Check hashed password
    private boolean checkPassword(String password, String hashedPassword) {
        // Implement password check logic, comparing the hash
        return password.equals(hashedPassword);
    }
}
