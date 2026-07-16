/**
 * Representa una planilla cargada y su estado dentro del flujo de aprobación.
 */
export interface Planilla {
    id: number;
    nombreResponsable: string;
    producto: string;
    segmento?: string;
    fechaCorte: string;
    descripcion: string;
    estado: 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';
    nombreArchivo: string;
    pesoArchivo: number;
    fechaCreacion: string;
    fechaAprobacion?: string;
    numeroFilas?: number;
    idLider?: number;
    correoLider?: string;
    sinDatos?: boolean; // Indica si es una certificación de inexistencia de datos
}
