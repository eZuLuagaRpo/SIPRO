
-- =====================================================
-- VERIFICACIÓN
-- =====================================================

-- Verificar que los datos se insertaron correctamente
SELECT 
    ul.id_usuario,
    ul.usuario,
    up.nombres,
    up.apellidos,
    up.correo,
    ua.area_nombre,
    ua.jefe_nombre
FROM usuario_login ul
LEFT JOIN usuario_persona up ON ul.id_usuario = up.id_usuario
LEFT JOIN usuario_area ua ON ul.id_usuario = ua.id_usuario
ORDER BY ul.id_usuario;

-- =====================================================
-- NOTAS IMPORTANTES
-- =====================================================

-- ⚠️ SEGURIDAD: Las contraseñas están en texto plano solo para desarrollo
-- En producción, usar BCrypt o similar:
-- UPDATE usuario_login SET clave = crypt('12345', gen_salt('bf'));

-- Para habilitar pgcrypto en PostgreSQL:
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================
-- COMANDOS ÚTILES
-- =====================================================

-- Ver todos los usuarios
-- SELECT * FROM usuario_login;

-- Eliminar un usuario (y sus datos relacionados por CASCADE)
-- DELETE FROM usuario_login WHERE usuario = 'junortiz';

-- Actualizar contraseña
-- UPDATE usuario_login SET clave = 'nueva_clave' WHERE usuario = 'junortiz';

-- Contar usuarios
-- SELECT COUNT(*) FROM usuario_login;
