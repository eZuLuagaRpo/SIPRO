package com.bancolombia.sipro.validations.application.dto;

/**
 * Resultado del login con los datos básicos del usuario y sus permisos.
 */
public class LoginResponse {

    private boolean success;
    private String mensaje;
    private Integer sessionTimeoutMinutes;
    private Long idUsuario;
    private String usuario;
    private String nombres;
    private String apellidos;
    private String correo;
    private String areaNombre;
    private String jefeNombre;
    private UsuarioPermisosResponse permisos;

    // Constructores
    public LoginResponse() {
    }

    public LoginResponse(boolean success, String mensaje) {
        this.success = success;
        this.mensaje = mensaje;
    }

    /**
     * Construye una respuesta exitosa con la información principal del usuario.
     */
    public static LoginResponse success(Long idUsuario, String usuario, String nombres, 
                                        String apellidos, String correo, String areaNombre, 
                                        String jefeNombre) {
        LoginResponse response = new LoginResponse();
        response.success = true;
        response.mensaje = "Autenticación exitosa";
        response.idUsuario = idUsuario;
        response.usuario = usuario;
        response.nombres = nombres;
        response.apellidos = apellidos;
        response.correo = correo;
        response.areaNombre = areaNombre;
        response.jefeNombre = jefeNombre;
        return response;
    }

    // Constructor para respuesta fallida
    public static LoginResponse failure(String mensaje) {
        return new LoginResponse(false, mensaje);
    }

    // Getters y Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Integer getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public void setSessionTimeoutMinutes(Integer sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getAreaNombre() {
        return areaNombre;
    }

    public void setAreaNombre(String areaNombre) {
        this.areaNombre = areaNombre;
    }

    public String getJefeNombre() {
        return jefeNombre;
    }

    public void setJefeNombre(String jefeNombre) {
        this.jefeNombre = jefeNombre;
    }

    public UsuarioPermisosResponse getPermisos() {
        return permisos;
    }

    public void setPermisos(UsuarioPermisosResponse permisos) {
        this.permisos = permisos;
    }

    @Override
    public String toString() {
        return "LoginResponse{" +
                "success=" + success +
                ", mensaje='" + mensaje + '\'' +
                ", usuario='" + usuario + '\'' +
                '}';
    }
}
