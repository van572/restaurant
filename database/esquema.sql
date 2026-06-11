-- ESQUEMA COMPLETO Y TOTALMENTE OPERATIVO PARA EL SISTEMA DE GESTIÓN DE RESTAURANTE
-- Base de Datos: PostgreSQL (Supabase)

-- Habilitar extensiones requeridas para generación de UUIDs si es necesario
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. TABLA DE PEDIDOS
CREATE TABLE IF NOT EXISTS public.pedidos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mesa VARCHAR(50) NOT NULL,
    mesero VARCHAR(100) NOT NULL,
    items JSONB NOT NULL, -- Array de objetos: [{"producto": "Tacos", "cantidad": 3, "precio": 2.50, "notas": "Sin cebolla"}]
    total NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(50) NOT NULL DEFAULT 'pendiente', -- 'pendiente', 'cocinando', 'listo', 'entregado', 'pagado'
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL,
    actualizado_en TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- 2. TABLA DE LOGS DE AUDITORÍA FINANCIERA
CREATE TABLE IF NOT EXISTS public.auditoria_financiera (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    monto NUMERIC(10, 2) NOT NULL,
    metodo_pago VARCHAR(50) NOT NULL, -- 'efectivo', 'tarjeta', 'transferencia'
    mesero VARCHAR(100) NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- 3. ACTIVAR REALTIME PARA SUPERVISIÓN DE CAMBIOS DESDE LA COCINA Y CAJA
-- En Supabase, para escuchar cambios de tablas por WebSockets, debemos agregarlas a la publicación 'supabase_realtime'
BEGIN;
  -- Remover si ya existían para evitar duplicación
  ALTER PUBLICATION supabase_realtime DROP TABLE IF EXISTS public.pedidos;
  ALTER PUBLICATION supabase_realtime DROP TABLE IF EXISTS public.auditoria_financiera;
  
  -- Añadir las tablas a publicación en tiempo real
  ALTER PUBLICATION supabase_realtime ADD TABLE public.pedidos;
  ALTER PUBLICATION supabase_realtime ADD TABLE public.auditoria_financiera;
COMMIT;

-- 4. FUNCIÓN Y TRIGGERS PARA ACTUALIZAR EL CAMPO 'actualizado_en' DE MANERA AUTOMÁTICA
CREATE OR REPLACE FUNCTION public.actualizar_timestamp_actualizado_en()
RETURNS TRIGGER AS $$
BEGIN
    NEW.actualizado_en = TIMEZONE('utc'::text, NOW());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_actualizar_pedidos_actualizado_en
    BEFORE UPDATE ON public.pedidos
    FOR EACH ROW
    EXECUTE FUNCTION public.actualizar_timestamp_actualizado_en();

-- 5. SEGURIDAD A NIVEL DE FILAS (RLS) - CONFIGURACIÓN SIMPLE PARA DEMO/PROTOTIPO
-- Nota: De manera predeterminada para un prototipo rápido, permitimos acceso público.
-- En producción, se recomienda configurar políticas de autenticación fina de Supabase Auth.
ALTER TABLE public.pedidos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.auditoria_financiera ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Permitir lectura pública de pedidos" 
    ON public.pedidos FOR SELECT 
    USING (true);

CREATE POLICY "Permitir inserción pública de pedidos" 
    ON public.pedidos FOR INSERT 
    WITH CHECK (true);

CREATE POLICY "Permitir actualización pública de pedidos" 
    ON public.pedidos FOR UPDATE 
    USING (true)
    WITH CHECK (true);

CREATE POLICY "Permitir borrado público de pedidos" 
    ON public.pedidos FOR DELETE 
    USING (true);

CREATE POLICY "Permitir lectura pública de auditoria" 
    ON public.auditoria_financiera FOR SELECT 
    USING (true);

CREATE POLICY "Permitir inserción pública de auditoria" 
    ON public.auditoria_financiera FOR INSERT 
    WITH CHECK (true);

-- 6. TABLA DE MENÚ (compartida entre cocina, cliente QR y meseros)
CREATE TABLE IF NOT EXISTS public.menu (
    id BIGINT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    precio NUMERIC(10, 2) NOT NULL,
    categoria VARCHAR(50) NOT NULL, -- 'comida', 'acompa', 'bebida'
    emoji VARCHAR(10) DEFAULT '🍔',
    descripcion TEXT DEFAULT '',
    disponible BOOLEAN DEFAULT true
);

ALTER PUBLICATION supabase_realtime ADD TABLE public.menu;

ALTER TABLE public.menu ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Permitir lectura pública de menu" 
    ON public.menu FOR SELECT USING (true);

CREATE POLICY "Permitir inserción pública de menu" 
    ON public.menu FOR INSERT WITH CHECK (true);

CREATE POLICY "Permitir actualización pública de menu" 
    ON public.menu FOR UPDATE USING (true) WITH CHECK (true);

CREATE POLICY "Permitir borrado público de menu" 
    ON public.menu FOR DELETE USING (true);

-- Insertar datos iniciales del menú
INSERT INTO public.menu (id, nombre, precio, categoria, emoji, descripcion, disponible) VALUES
    (1, 'Hamburguesa Premium',   12.50, 'comida', '🍔', 'Queso cheddar, tocino, aderezo gourmet.', true),
    (2, 'Pizza Personal Pepperoni', 15.00, 'comida', '🍕', 'Salsa de la casa, pepperoni, mozzarella.', true),
    (3, 'Tacos de Res (x3)',     8.50,  'comida', '🌮', 'Cebollitas asadas, cilantro, salsas.', true),
    (4, 'Alitas BBQ',            9.50,  'comida', '🍗', '10 piezas bañadas en salsa barbacoa.', true),
    (5, 'Papas Fritas',          4.00,  'acompa', '🍟', 'Doraditas y crujientes con sal marina.', true),
    (6, 'Té Frío Limón',         3.00,  'bebida', '🍹', 'Infusión de té negro con zumo fresco.', true),
    (7, 'Refresco Sabor Cola',   2.50,  'bebida', '🥤', 'Vaso grande con hielo y limón.', true),
    (8, 'Agua Mineral',          2.00,  'bebida', '💧', 'Agua gasificada purificada fría.', true)
ON CONFLICT (id) DO NOTHING;
