package com.motadata.db;

import io.vertx.core.json.JsonObject;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Impl
{
    public void applyMigrations()
    {
        try
        {
            // Load schema from databaseSchema.json
            var jsonString = new String(Files.readAllBytes(Paths.get("./src/main/java/resources/databaseSchema.json")));

            // Loop through each collection in the JSON schema
            var collections = new JsonObject(jsonString).getJsonObject("collections");

            collections.stream().forEach(entry ->
            {
                var collectionName = entry.getKey();

                var options = new JsonObject().put("validator", ((JsonObject) entry.getValue()).getJsonObject("validator"));

                // Apply schema to collection
                MongoClient.getMongoClient().runCommand("collMod", new JsonObject()
                        .put("collMod", collectionName)
                        .put("validator", options.getJsonObject("validator")), res -> {
                    if (res.succeeded())
                    {
                        System.out.println("Schema applied to collection: " + collectionName);
                    }
                    else
                    {
                        System.out.println("Error applying schema to " + collectionName + ": " + res.cause().getMessage());
                    }
                });
            });
        }
        catch (Exception exception)
        {
            System.err.println("Failed to apply migrations. " + exception);
        }
    }
}
