package com.motadata.api;

import com.motadata.configs.Auth;
import com.motadata.constants.Constants;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;

public class Server extends AbstractVerticle
{
    private Router router;

    @Override
    public void start()
    {
        // Get configuration from the Vert.x context (set in Main)
        var config = vertx.getOrCreateContext().config();

        if (config == null)
        {
            System.err.println("Failed to load configuration");

            return;
        }

        System.out.println("Loaded all Configs");

        Auth.initialize(vertx,config);

        router = Router.router(vertx);

        var port = config.getInteger("http_port", Constants.HTTP_PORT);

        vertx.createHttpServer().requestHandler(router).listen(port, response ->
        {
            if (response.succeeded())
            {
                System.out.println("Server started on port 8080");
            }
            else
            {
                System.err.println("Failed to start server: " + response.cause());
            }
        });

        setupRoutes();
    }

    private void setupRoutes()
    {
        // Creating subrouters for each resource
        var userRouter = Router.router(vertx);

        var credentialRouter = Router.router(vertx);

        var discoveryRouter = Router.router(vertx);

        var objectRouter = Router.router(vertx);

        // Initialize routes for each resource
        User.initRoutes(userRouter);

        CredentialProfile.initRoutes(credentialRouter);

        Discovery.initRoutes(discoveryRouter);

        Object.initRoutes(objectRouter);

        // Mount the subrouters to the main router
        router.mountSubRouter("/api/user", userRouter);

        router.mountSubRouter("/api/credentials", credentialRouter);

        router.mountSubRouter("/api/discovery", discoveryRouter);

        router.mountSubRouter("/api/object", objectRouter);

    }
}
