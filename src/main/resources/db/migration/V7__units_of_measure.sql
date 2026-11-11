-- =============================================================================
-- V7 · Unidades de medida con magnitud y conversión
--
--   units_measurement  code (único entre no borrados), symbol, dimension
--                      (LENGTH, MASS, VOLUME, AREA, COUNT, OTHER), factor respecto a la
--                      unidad base de su magnitud, is_base, display_decimals, active.
--                      El nombre pasa a ser único solo entre no borrados.
--                      Se precargan las unidades habituales; las existentes cuyo nombre
--                      coincide (p. ej. "cm", "Kilogramo") se completan en lugar de duplicarse.
--   products / product_childs  net_content + net_content_unit_id (contenido neto: 500 ml, 1 kg…).
--
-- No destructivo e idempotente.
-- =============================================================================

SET @has_units := (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'units_measurement');

-- 3. Catálogo de unidades estándar (tabla auxiliar, se elimina al final) -------------
DROP TABLE IF EXISTS v7_unit_seed;

CREATE TABLE v7_unit_seed (
  code VARCHAR(20) NOT NULL PRIMARY KEY,
  symbol VARCHAR(20) NOT NULL,
  name VARCHAR(100) NOT NULL,
  dimension VARCHAR(20) NOT NULL,
  factor DECIMAL(24,12) NOT NULL,
  is_base TINYINT(1) NOT NULL,
  display_decimals INT NOT NULL,
  aliases VARCHAR(200) NOT NULL
);

INSERT INTO v7_unit_seed VALUES
  ('mm',    'mm',    'Milímetro',          'LENGTH', 0.001,            0, 0, 'mm,milimetro,milímetro,milimetros,milímetros'),
  ('cm',    'cm',    'Centímetro',         'LENGTH', 0.01,             0, 1, 'cm,centimetro,centímetro,centimetros,centímetros'),
  ('m',     'm',     'Metro',              'LENGTH', 1,                1, 2, 'm,metro,metros,mts,mt'),
  ('km',    'km',    'Kilómetro',          'LENGTH', 1000,             0, 3, 'km,kilometro,kilómetro,kilometros,kilómetros'),
  ('in',    'in',    'Pulgada',            'LENGTH', 0.0254,           0, 2, 'in,pulgada,pulgadas'),
  ('ft',    'ft',    'Pie',                'LENGTH', 0.3048,           0, 2, 'ft,pie,pies'),
  ('mg',    'mg',    'Miligramo',          'MASS',   0.000001,         0, 0, 'mg,miligramo,miligramos'),
  ('g',     'g',     'Gramo',              'MASS',   0.001,            0, 1, 'g,gr,grs,gramo,gramos'),
  ('kg',    'kg',    'Kilogramo',          'MASS',   1,                1, 3, 'kg,kgs,kilo,kilos,kilogramo,kilogramos'),
  ('t',     't',     'Tonelada',           'MASS',   1000,             0, 3, 't,ton,tonelada,toneladas'),
  ('oz',    'oz',    'Onza',               'MASS',   0.028349523125,   0, 2, 'oz,onza,onzas'),
  ('lb',    'lb',    'Libra',              'MASS',   0.45359237,       0, 2, 'lb,lbs,libra,libras'),
  ('ml',    'ml',    'Mililitro',          'VOLUME', 0.001,            0, 0, 'ml,mililitro,mililitros,cc'),
  ('cl',    'cl',    'Centilitro',         'VOLUME', 0.01,             0, 1, 'cl,centilitro,centilitros'),
  ('l',     'L',     'Litro',              'VOLUME', 1,                1, 2, 'l,lt,lts,litro,litros'),
  ('m3',    'm³',    'Metro cúbico',       'VOLUME', 1000,             0, 3, 'm3,m³,metro cubico,metro cúbico'),
  ('fl_oz', 'fl oz', 'Onza líquida',       'VOLUME', 0.0295735295625,  0, 2, 'fl oz,fl_oz,onza liquida,onza líquida'),
  ('gal',   'gal',   'Galón (EE. UU.)',    'VOLUME', 3.785411784,      0, 2, 'gal,galon,galón,galones'),
  ('cm2',   'cm²',   'Centímetro cuadrado','AREA',   0.0001,           0, 1, 'cm2,cm²,centimetro cuadrado,centímetro cuadrado'),
  ('m2',    'm²',    'Metro cuadrado',     'AREA',   1,                1, 2, 'm2,m²,metro cuadrado,metros cuadrados'),
  ('und',   'und',   'Unidad',             'COUNT',  1,                1, 0, 'und,un,u,unidad,unidades,pieza,piezas'),
  ('par',   'par',   'Par',                'COUNT',  2,                0, 0, 'par,pares'),
  ('dz',    'dz',    'Docena',             'COUNT',  12,               0, 0, 'dz,docena,docenas');

-- 3a. Unidades existentes que corresponden a una estándar: se completan (la de menor id
--     si varias coinciden con la misma estándar; las demás siguen en 3b).
SET @sql := IF(@has_units = 1,
  'UPDATE units_measurement u
     JOIN (SELECT s.code AS seed_code, MIN(x.id_unit_measurement) AS unit_id
             FROM v7_unit_seed s
             JOIN units_measurement x ON FIND_IN_SET(LOWER(TRIM(x.name)), s.aliases) > 0
            WHERE x.deleted = 0 AND x.code IS NULL
            GROUP BY s.code) m ON m.unit_id = u.id_unit_measurement
     JOIN v7_unit_seed s ON s.code = m.seed_code
      SET u.code = s.code, u.symbol = s.symbol, u.dimension = s.dimension, u.factor = s.factor,
          u.is_base = s.is_base, u.display_decimals = s.display_decimals', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3b. El resto de existentes: magnitud OTHER, código derivado del nombre + id (único).
SET @sql := IF(@has_units = 1,
  'UPDATE units_measurement
      SET code = CONCAT(LEFT(TRIM(BOTH ''_'' FROM LOWER(REGEXP_REPLACE(name, ''[^A-Za-z0-9]+'', ''_''))), 12), ''_'', id_unit_measurement),
          symbol = LEFT(name, 20)
    WHERE code IS NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3c. Estándar que faltan. Si su nombre ya lo usa otra unidad se añade "(code)".
SET @sql := IF(@has_units = 1,
  'INSERT INTO units_measurement (usr_crea, deleted, name, code, symbol, dimension, factor, is_base, display_decimals, active)
   SELECT 1, 0,
          IF(EXISTS (SELECT 1 FROM units_measurement x WHERE x.deleted = 0 AND LOWER(x.name) = LOWER(s.name)),
             CONCAT(s.name, '' ('', s.code, '')''), s.name),
          s.code, s.symbol, s.dimension, s.factor, s.is_base, s.display_decimals, 1
     FROM v7_unit_seed s
    WHERE NOT EXISTS (SELECT 1 FROM units_measurement u WHERE u.code = s.code AND u.deleted = 0)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS v7_unit_seed;
