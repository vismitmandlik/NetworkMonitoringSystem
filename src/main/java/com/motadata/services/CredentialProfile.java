package com.motadata.services;

import com.motadata.db.Operations;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class CredentialProfile {
    private static final String CREDENTIALS_COLLECTION = "credentials";

    // Method to save device credentials
    public static void saveCredentials(RoutingContext context) {
        JsonObject requestBody = context.body().asJsonObject();

        String deviceId = requestBody.getString("deviceId");
        String username = requestBody.getString("username");
        String password = requestBody.getString("password");

        // Create a new credential object
        JsonObject newCredential = new JsonObject()
                .put("deviceId", deviceId)
                .put("username", username)
                .put("password", password);

        // Check if the deviceId already exists in the database
        JsonObject query = new JsonObject().put("deviceId", deviceId);

        Operations.findOne(CREDENTIALS_COLLECTION, query).onSuccess(existingCredential -> {
            if (existingCredential != null) {
                // Device ID already exists, respond with an error
                context.response().setStatusCode(400).end("Credential for this device ID already exists.");
            } else {
                // Insert new credential into the database
                Operations.insert(CREDENTIALS_COLLECTION, newCredential).onSuccess(id -> {
                    context.response().setStatusCode(201).end("Credential saved successfully.");
                }).onFailure(err -> {
                    context.response().setStatusCode(500).end("Error saving credential: " + err.getMessage());
                });
            }
        }).onFailure(err -> {
            context.response().setStatusCode(500).end("Error checking for existing credentials: " + err.getMessage());
        });
    }

    // Method to find credentials by device ID
    public static void findCredentials(RoutingContext context) {
        String deviceId = context.request().getParam("deviceId");

        JsonObject query = new JsonObject().put("deviceId", deviceId);

        // Find credentials in the database
        Operations.findOne(CREDENTIALS_COLLECTION, query).onSuccess(credential -> {
            if (credential != null) {
                context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(credential.encode());
            } else {
                context.response().setStatusCode(404).end("Credential not found.");
            }
        }).onFailure(err -> {
            context.response().setStatusCode(500).end("Error retrieving credential: " + err.getMessage());
        });
    }
}