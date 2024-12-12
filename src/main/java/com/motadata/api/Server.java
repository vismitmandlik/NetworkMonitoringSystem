package com.motadata.api;

import com.motadata.configs.Auth;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class Server extends AbstractVerticle {

    private Router router;

    @Override
    public void start()
    {
        Auth.initialize(vertx);

        router = Router.router(vertx);

        setupRoutes();

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

    private void setupRoutes()
    {
        // API routes for user
        User userApi = new User();

        userApi.initRoutes(router);

        CredentialProfile credentialProfile = new CredentialProfile();

        credentialProfile.initRoutes(router);

        Discovery discovery = new Discovery();

        discovery.initRoutes(router);

        // Additional routes can be added here as needed
    }
}
