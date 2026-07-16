/**
 * Configuracion usada por el build de produccion.
 * Conserva /api como base relativa para delegar el enrutamiento al servidor o gateway del ambiente.
 */
export const environment = {
  production: true,
  apiUrl: '/api'
};
