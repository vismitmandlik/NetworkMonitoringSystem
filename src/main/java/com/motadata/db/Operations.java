package com.motadata.db;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;

public class Operations {

    private static final MongoClient mongoClient = Initializer.getMongoClient();

    // Insert a document into a specified collection
    public static Future<String> insert(String collection, JsonObject document)
    {
        return mongoClient.insert(collection, document);
    }

    // Find a single document by query from a specified collection
    public static Future<JsonObject> findOne(String collection, JsonObject query)
    {
        return mongoClient.findOne(collection, query, null);
    }

    // Find all documents matching a query from a specified collection
    public static Future<List<JsonObject>> findAll(String collection, JsonObject query)
    {
        return mongoClient.find(collection, query);
    }

    // Update a document by query in a specified collection
    public static Future<Void> update(String collection, JsonObject query, JsonObject update)
    {
        return mongoClient.updateCollection(collection, query, new JsonObject().put("$set", update)).mapEmpty();
    }

    // Delete a document by query from a specified collection
    public static Future<Void> delete(String collection, JsonObject query)
    {
        return mongoClient.removeDocument(collection, query).mapEmpty();
    }
}
