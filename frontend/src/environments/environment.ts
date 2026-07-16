/**
 * Configuracion usada por el build de desarrollo del frontend.
 * Mantiene las llamadas contra /api para aprovechar el proxy local del Angular dev server.
 */
export const environment = {
  production: false,
  apiUrl: '/api'
};
