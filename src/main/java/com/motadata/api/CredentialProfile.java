package com.motadata.api;

import com.motadata.configs.Auth;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;


public class CredentialProfile {

    private static final JWTAuth jwtAuth = Auth.getJwtAuth();
    // Method to register routes
    public static void initRoutes(Router router)
    {
        router.post("/api/credentials").handler(BodyHandler.create()).handler(JWTAuthHandler.create(jwtAuth)).handler(com.motadata.services.CredentialProfile::saveCredentials);

        router.get("/api/credentials").handler(JWTAuthHandler.create(jwtAuth)).handler(com.motadata.services.CredentialProfile::getAllCredentials);

        router.get("/api/credentials/:id").handler(JWTAuthHandler.create(jwtAuth)).handler(com.motadata.services.CredentialProfile::findCredentials);
    }
}