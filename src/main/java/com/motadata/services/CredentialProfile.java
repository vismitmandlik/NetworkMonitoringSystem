package com.motadata.services;

import com.motadata.constants.Constants;
import com.motadata.db.Operations;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class CredentialProfile
{
    // Method to save device credentials
    public static void saveCredentials(RoutingContext context)
    {
        var requestBody = context.body().asJsonObject();

        var name = requestBody.getString("name");

        var username = requestBody.getString("username");

        var password = requestBody.getString("password");

        // Create a new credential object
        var newCredential = new JsonObject().put("name", name).put("username", username).put("password", password);

        // Check if the name already exists in the database
        var query = new JsonObject().put("name", name);

        Operations.findOne(Constants.CREDENTIALS_COLLECTION, query).onSuccess(existingCredential ->
        {
            if (existingCredential != null)
            {
                // Device ID already exists, respond with an error
                context.response().setStatusCode(400).end("Credential for this cred name : "+name+" already exists.");
            }
            else
            {
                // Insert new credential into the database
                Operations.insert(Constants.CREDENTIALS_COLLECTION, newCredential).onSuccess(id -> context.response().setStatusCode(201).end("Credential saved successfully.")).onFailure(err -> context.response().setStatusCode(500).end("Error saving credential: " + err.getMessage()));
            }
        }).onFailure(err -> context.response().setStatusCode(500).end("Error checking for existing credentials: " + err.getMessage()));
    }

    public static void getAllCredentials(RoutingContext context)
    {
        // Retrieve all credentials from the database without any query
        Operations.findAll(Constants.CREDENTIALS_COLLECTION, new JsonObject()).onSuccess(credentials ->
        {
            if (credentials != null && !credentials.isEmpty())
            {
                context.response().putHeader("Content-Type", "application/json").end(new JsonArray(credentials).encode());
            }
            else
            {
                context.response().setStatusCode(404).end("No credentials found.");
            }
        }).onFailure(err -> context.response().setStatusCode(500).end("Error retrieving credentials: " + err.getMessage()));
    }

    // Method to find credentials by device ID
    public static void findCredentials(RoutingContext context)
    {
        var name = context.request().getParam("name");

        if (name == null || name.isEmpty())
        {
            context.response().setStatusCode(400).end("Invalid request: 'name' is missing");

            return;
        }
        var query = new JsonObject().put("name", name);

        // Find credentials in the database
        Operations.findOne(Constants.CREDENTIALS_COLLECTION, query).onSuccess(credential ->
        {
            if (credential != null)
            {
                context.response().putHeader("Content-Type", "application/json").end(credential.encode());
            }
            else
            {
                context.response().setStatusCode(404).end("Credential not found.");
            }
        }).onFailure(err -> context.response().setStatusCode(500).end("Error retrieving credential: " + err.getMessage()));
    }
}