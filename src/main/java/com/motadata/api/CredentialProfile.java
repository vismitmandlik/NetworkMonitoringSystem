package com.motadata.api;

import com.motadata.configs.Auth;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class CredentialProfile {

    private static final JWTAuth JWT_AUTH = Auth.jwtAuth();

    // Method to register routes
    public static void initRoutes(Router router)
    {
        router.post("/").handler(com.motadata.services.CredentialProfile::saveCredentials);

        router.get("/").handler(com.motadata.services.CredentialProfile::getAllCredentials);

        router.post("/:name").handler(com.motadata.services.CredentialProfile::findCredentials);
    }
}