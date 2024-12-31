package com.motadata.services;

import com.motadata.constants.Constants;
import com.motadata.db.Operations;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class CredentialProfile
{
    // Saves device credentials
    public static void save(RoutingContext context)
    {
        var requestBody = context.body().asJsonObject();

        var name = requestBody.getString(Constants.NAME);

        var username = requestBody.getString(Constants.USERNAME);

        var password = requestBody.getString(Constants.PASSWORD);

        // Create a new credential object
        var newCredential = new JsonObject().put(Constants.NAME, name).put(Constants.USERNAME, username).put(Constants.PASSWORD, password);

        // Check if the name already exists in the database
        var query = new JsonObject().put(Constants.NAME, name);

        try
        {
            Operations.findOne(Constants.CREDENTIALS_COLLECTION, query).onComplete(result ->
            {
                if (result.succeeded())
                {
                    // Device ID already exists, respond with an error
                    context.response().setStatusCode(Constants.SC_404).end("Credential for this cred name : " + name + " already exists.");
                }
                else
                {
                    // Insert new credential into the database
                    Operations.insert(Constants.CREDENTIALS_COLLECTION, newCredential).onComplete(asyncResult ->
                    {
                        if (asyncResult.succeeded())
                        {
                            context.response().setStatusCode(Constants.SC_200).end("Credential saved successfully.");
                        }
                        else
                        {
                            context.response().setStatusCode(Constants.SC_500).end("Error saving credential: " + asyncResult.cause());
                        }
                    });
                }
            });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to save credentials. " + exception);

            context.response().setStatusCode(Constants.SC_500).end("Error checking for existing credentials: " + exception);
        }

    }

    // Gets all the credentials from database
    public static void getAll(RoutingContext context)
    {
        try
        {
            // Retrieve all credentials from the database without any query
            Operations.findAll(Constants.CREDENTIALS_COLLECTION, new JsonObject()).onComplete(asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    context.response().putHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON).end(new JsonArray(asyncResult.result()).encode());
                }

                else
                {
                    context.response().setStatusCode(Constants.SC_404).end("No credentials found.");
                }

            });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to get credentials from database. " + exception);

            context.response().setStatusCode(Constants.SC_500).end("Error retrieving credentials: ");
        }

    }

    // Finds credentials by device-id
    public static void find(RoutingContext context)
    {
        var name = context.request().getParam(Constants.NAME);

        if (name == null || name.isEmpty())
        {
            context.response().setStatusCode(Constants.SC_404).end("Invalid request: 'name' is missing");

            return;
        }

        var query = new JsonObject().put(Constants.NAME, name);

        try
        {
            // Find credentials in the database
            Operations.findOne(Constants.CREDENTIALS_COLLECTION, query).onComplete(result ->
            {
                if (result.succeeded())
                {
                    context.response().putHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON).end(result.result().encode());
                }

                else
                {
                    context.response().setStatusCode(Constants.SC_404).end("Credential not found.");
                }
            });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to find credentials. " + exception);

            context.response().setStatusCode(Constants.SC_500).end("error in finding credentials.");
        }

    }
}