package com.motadata.services;

import io.vertx.core.Handler;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTAuth;
import io.vertx.ext.auth.jwt.JWTOptions;
import com.motadata.db.Impl;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class User {

    private final MongoClient mongoClient;
    private final Impl dbImpl;

    public User(MongoClient mongoClient, Impl dbImpl) {
        this.mongoClient = mongoClient;
        this.dbImpl = dbImpl;
    }

    // Register a new user
    public void register(RoutingContext context) {
        JsonObject query = new JsonObject().put("username", username);
        mongoClient.findOne("users", query, null, res -> {
            if (res.succeeded() && res.result() != null) {
                resultHandler.handle(Future.failedFuture("User already exists."));
            } else {
                JsonObject newUser = new JsonObject().put("username", username).put("password", password);
                dbImpl.insertUser(newUser, resultHandler);
            }
        });
    }

    // User login and JWT token generation
    public void login(String username, String password, JWTAuth jwtAuth, Handler<AsyncResult<String>> resultHandler) {
        JsonObject query = new JsonObject().put("username", username);
        mongoClient.findOne("users", query, null, res -> {
            if (res.succeeded() && res.result() != null) {
                JsonObject user = res.result();
                if (user.getString("password").equals(password)) {
                    JWTOptions tokenConfig = new JWTOptions().setExpiresInSeconds(60 * 60 * 1000);
                    String token = jwtAuth.generateToken(new JsonObject().put("username", username), tokenConfig);
                    resultHandler.handle(Future.succeededFuture(token));
                } else {
                    resultHandler.handle(Future.failedFuture("Invalid password"));
                }
            } else {
                resultHandler.handle(Future.failedFuture("User not found"));
            }
        });
    }
}
