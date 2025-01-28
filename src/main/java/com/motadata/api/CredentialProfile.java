package com.motadata.api;

import io.vertx.ext.web.Router;

public class CredentialProfile
{
    // Method to register routes
    public static void init(Router router)
    {
        router.post("/  ").handler(com.motadata.services.CredentialProfile::save);

        router.get("/").handler(com.motadata.services.CredentialProfile::getAll);

        router.post("/:name").handler(com.motadata.services.CredentialProfile::find);

        router.put("/:name").handler(com.motadata.services.CredentialProfile::update);
    }
}