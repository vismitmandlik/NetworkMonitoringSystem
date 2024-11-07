package com.motadata.db;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Impl {
    private final MongoClient mongoClient;

    public Impl(Initializer initializer) {
        this.mongoClient = initializer.getMongoClient();
    }

    public void applyMigrations() {
        try {
            // Load schema from databaseSchema.json
            String schemaFile = "./src/main/resources/databaseSchema.json";
            String jsonString = new String(Files.readAllBytes(Paths.get(schemaFile)));
            JsonObject schema = new JsonObject(jsonString);

            // Loop through each collection in the JSON schema
            JsonObject collections = schema.getJsonObject("collections");
            collections.stream().forEach(entry -> {
                String collectionName = entry.getKey();
                JsonObject options = new JsonObject().put("validator", ((JsonObject) entry.getValue()).getJsonObject("validator"));

                // Apply schema to collection
                mongoClient.runCommand("collMod", new JsonObject()
                        .put("collMod", collectionName)
                        .put("validator", options.getJsonObject("validator")), res -> {
                    if (res.succeeded()) {
                        System.out.println("Schema applied to collection: " + collectionName);
                    } else {
                        System.out.println("Error applying schema to " + collectionName + ": " + res.cause().getMessage());
                    }
                });
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
