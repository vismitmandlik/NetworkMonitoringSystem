package com.motadata.api;

import io.vertx.ext.web.Router;

public class CredentialProfile
{
    // Method to register routes
    public static void initRoutes(Router router)
    {
        router.post("/").handler(com.motadata.services.CredentialProfile::saveCredentials);

        router.get("/").handler(com.motadata.services.CredentialProfile::getAllCredentials);

        router.post("/:name").handler(com.motadata.services.CredentialProfile::findCredentials);
    }
}