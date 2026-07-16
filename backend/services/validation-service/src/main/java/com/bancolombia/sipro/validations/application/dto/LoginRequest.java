package com.bancolombia.sipro.validations.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos mínimos que el frontend envía para bootstrap de sesión con Entra ID.
 */
public class LoginRequest {

    @NotBlank(message = "El token de acceso de SIPRO es obligatorio")
    private String apiAccessToken;

    private String idToken;

    @NotBlank(message = "El token delegado de Microsoft Graph es obligatorio")
    private String graphAccessToken;

    // Constructores
    public LoginRequest() {
    }

    public LoginRequest(String apiAccessToken, String idToken, String graphAccessToken) {
        this.apiAccessToken = apiAccessToken;
        this.idToken = idToken;
        this.graphAccessToken = graphAccessToken;
    }

    // Getters y Setters
    public String getApiAccessToken() {
        return apiAccessToken;
    }

    public void setApiAccessToken(String apiAccessToken) {
        this.apiAccessToken = apiAccessToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getGraphAccessToken() {
        return graphAccessToken;
    }

    public void setGraphAccessToken(String graphAccessToken) {
        this.graphAccessToken = graphAccessToken;
    }

    @Override
    public String toString() {
        return "LoginRequest{" +
                "apiAccessTokenPresent=" + (apiAccessToken != null && !apiAccessToken.isBlank()) +
                ", idTokenPresent=" + (idToken != null && !idToken.isBlank()) +
                ", graphAccessTokenPresent=" + (graphAccessToken != null && !graphAccessToken.isBlank()) +
                '}';
    }
}
