package com.motadata.api;

import com.motadata.configs.Auth;
import com.motadata.constants.Constants;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Server extends AbstractVerticle
{
    public static final String HTTP_PORT = "http_port";

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    private static final int SERVER_IDLE_TIMEOUT = 60000;

    private static Router router;

    @Override
    public void start()
    {
        try
        {
            // Get configuration from the Vert.x context (set in Main)
            var config = vertx.getOrCreateContext().config();

            if (config == null)
            {
                LOGGER.error("Failed to load configuration");

                return;
            }

            Auth.initialize(vertx,config);

            router = Router.router(vertx);

            var port = config.getInteger(HTTP_PORT, Constants.HTTP_PORT_VALUE);

            vertx.createHttpServer(new HttpServerOptions()
                    .setIdleTimeout(SERVER_IDLE_TIMEOUT))  // closes idle connections after 1 minute
                    .requestHandler(router).listen(port, response ->
            {
                if (response.succeeded())
                {
                    setupRoutes();
                }
                else
                {
                    LOGGER.error("Failed to start server: {}", response.cause().getMessage());

                    LOGGER.error("Decoded SSL keystore password: {}", new String(Base64.getDecoder().decode(config.getString(Constants.SSL_KEYSTORE_PASSWORD)), StandardCharsets.UTF_8));  // Debugging line (avoid this in production)
                }
            });
        }

        catch (Exception exception)
        {
            LOGGER.error("Failed to start server. {}", exception.getMessage());
        }

    }

    private void setupRoutes()
    {
        try
        {
            // Apply BodyHandler and JWTAuthHandler globally to all routes
            router.route().handler(BodyHandler.create());

            // Creating sub-routers for each resource
            var userRouter = Router.router(vertx);

            var credentialRouter = Router.router(vertx);

            var discoveryRouter = Router.router(vertx);

            var objectRouter = Router.router(vertx);

            router.route("/api/user/*").subRouter(userRouter);

            router.route().handler(JWTAuthHandler.create(Auth.jwtAuth()));

            router.route("/api/credentials/*").subRouter(credentialRouter);

            router.route("/api/discovery/*").subRouter(discoveryRouter);

            router.route("/api/object/*").subRouter(objectRouter);

            // Initialize routes for each resource
            User.init(userRouter);

            CredentialProfile.init(credentialRouter);

            Discovery.init(discoveryRouter);

            Object.init(objectRouter);

        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to set up routes. {}", exception.getMessage());
        }

    }
}
