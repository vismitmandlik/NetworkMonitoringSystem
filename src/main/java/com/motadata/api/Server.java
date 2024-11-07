package com.motadata.api;

import com.motadata.db.Initializer;
import com.motadata.configs.Auth;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class Server {

    private final Vertx vertx;
    private final JWTAuth jwtAuth;
    private final Router router;

    public Server(Vertx vertx) {
        this.vertx = vertx;
        // Initialize MongoClient and JWTAuth using the configuration classes
        MongoClient mongoClient = new Initializer(vertx).getMongoClient();
        this.jwtAuth = new Auth(vertx).getJwtAuth();

        // Initialize the router
        this.router = Router.router(vertx);
    }

    public void startServer() {
        // Setup routes using the User class
        User userApi = new User(new com.motadata.services.User(), jwtAuth);
        userApi.initRoutes(router);

        // Adding BodyHandler for parsing incoming requests
        router.route().handler(BodyHandler.create());

        // Create and start the HTTP server
        HttpServer server = vertx.createHttpServer();
        server.requestHandler(router).listen(8080, res -> {
            if (res.succeeded()) {
                System.out.println("Server started on port 8080");
            } else {
                System.err.println("Failed to start server: " + res.cause());
            }
        });
    }
}
