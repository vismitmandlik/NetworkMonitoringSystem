package com.motadata.db;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class Operations {

    private static final io.vertx.ext.mongo.MongoClient MONGO_CLIENT = MongoClient.getMongoClient();

    // Insert a document into a specified collection
    public static Future<String> insert(String collection, JsonObject document)
    {
        return MONGO_CLIENT.insert(collection, document);
    }

    // Find a single document by query from a specified collection
    public static Future<JsonObject> findOne(String collection, JsonObject query)
    {
        return MONGO_CLIENT.findOne(collection, query, null);
    }

    // Find all documents matching a query from a specified collection
    public static Future<List<JsonObject>> findAll(String collection, JsonObject query)
    {
        return MONGO_CLIENT.find(collection, query);
    }

    // Update a document by query in a specified collection
    public static Future<Void> update(String collection, JsonObject query, JsonObject update)
    {
        return MONGO_CLIENT.updateCollection(collection, query, new JsonObject().put("$set", update)).mapEmpty();
    }

    // Delete a document by query from a specified collection
    public static Future<Void> delete(String collection, JsonObject query)
    {
        return MONGO_CLIENT.removeDocument(collection, query).mapEmpty();
    }
}
