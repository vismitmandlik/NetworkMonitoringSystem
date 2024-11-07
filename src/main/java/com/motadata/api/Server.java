package com.motadata.api;

import com.motadata.db.Initializer;
import com.motadata.configs.Auth;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class Server extends AbstractVerticle {

    private Router router;

    @Override
    public void start()
    {
        // Get MongoClient and JWTAuth instances using the static methods
        MongoClient mongoClient = Initializer.getMongoClient();

        Auth.initialize(vertx);

        JWTAuth jwtAuth = Auth.getJwtAuth();

        // Initialize the router
        router = Router.router(vertx);

        setupRoutes(mongoClient, jwtAuth);

        // Create and start the HTTP server
        HttpServer server = vertx.createHttpServer();

        server.requestHandler(router).listen(8080, res ->
        {
            if (res.succeeded())
            {
                System.out.println("Server started on port 8080");
            }
            else
            {
                System.err.println("Failed to start server: " + res.cause());
            }
        });
    }

    private void setupRoutes(MongoClient mongoClient, JWTAuth jwtAuth)
    {

        // Set up your API routes for user
        User userApi = new User();

        userApi.initRoutes(router);

        // Additional routes can be added here as needed
    }
}
