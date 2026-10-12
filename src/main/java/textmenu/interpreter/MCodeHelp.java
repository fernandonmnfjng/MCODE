package textmenu.interpreter;

import java.util.List;
import java.util.Map;

/** Single source of documentation for the Help screen and completion hints. */
public final class MCodeHelp {
    private MCodeHelp() {}

    private static final Map<String, String> MEMBER_DOCS = Map.ofEntries(
            Map.entry("line", "Dibuja una polilínea 3D. mc.line((x,y,z), ... , color='cyan')."),
            Map.entry("box", "Caja 3D: mc.box(x1,y1,z1,x2,y2,z2, fill=True, color='cyan')."),
            Map.entry("point", "Marcador 3D: mc.point(x,y,z, size=0.12)."),
            Map.entry("sphere", "Esfera triangulada: mc.sphere(x,y,z,radius, segments=16, rings=10)."),
            Map.entry("cylinder", "Cilindro: mc.cylinder(x,y,z,radius,height, segments=16)."),
            Map.entry("cone", "Cono: mc.cone(x,y,z,radius,height, segments=16)."),
            Map.entry("mesh", "Malla: mc.mesh(vertices, faces), donde vertices son XYZ y faces son índices."),
            Map.entry("import_model", "Importa OBJ estático: mc.import_model('models/robot.obj', 0, 0, 4, scale=1, axis='blender', color='cyan', fill=True)."),
            Map.entry("circle", "Círculo en un plano del mundo: mc.circle(x,y,z,radius, plane='xz', segments=32)."),
            Map.entry("rect", "Rectángulo en el plano del mundo: mc.rect(x,y,z,width,height, plane='xz')."),
            Map.entry("polygon", "Polígono triangulado: mc.polygon([[x,y],[x,y],...], plane='xz', level=0)."),
            Map.entry("polyline", "Contorno en un plano: mc.polyline([[x,y],[x,y],...], plane='xz', level=0)."),
            Map.entry("begin_frame", "Empieza un frame de dibujo oculto; úsalo dentro del while de animación."),
            Map.entry("end_frame", "Publica el frame y reemplaza los dibujos del frame anterior del mismo script."),
            Map.entry("clear", "Quita las formas del script actual; mc.clear() también borra el staging del frame."),
            Map.entry("remove", "Elimina una forma usando su handle: shape.remove() o mc.remove(shape)."),
            Map.entry("hide", "Oculta una forma sin eliminarla: shape.hide()."),
            Map.entry("show", "Vuelve a mostrar una forma oculta: shape.show()."),
            Map.entry("visible", "Propiedad booleana de visibilidad de una forma: shape.visible = False."),
            Map.entry("color", "mc.color('cyan') cambia el color por defecto; shape.color('orange') cambia una forma existente."),
            Map.entry("alpha", "Establece la transparencia por defecto: mc.alpha(0.5)."),
            Map.entry("width", "Establece el grosor de líneas por defecto: mc.width(2)."),
            Map.entry("mode", "mc.mode('local') o mc.mode('global'); en servidores solo se permite LOCAL."),
            Map.entry("player_pos", "Devuelve [x,y,z] de la posición del jugador."),
            Map.entry("chat", "Escribe en la consola local de MCODE; no manda mensajes al servidor."),
            Map.entry("say", "Envía un mensaje solo donde la política de seguridad de MCODE lo permita."),
            Map.entry("move", "Método de handle: shape.move(dx,dy,dz)."),
            Map.entry("rotate", "Método de handle: shape.rotate(x_degrees[, y_degrees, z_degrees])."),
            Map.entry("scale", "Método de handle: shape.scale(factor) o shape.scale(x,y,z)."),
            Map.entry("reset_transform", "Método de handle: restaura escala y rotación."),
            Map.entry("read_text", "Lee texto bajo el workspace MCODE: read_text('data/save.txt')."),
            Map.entry("write_text", "Guarda texto en el workspace MCODE: write_text('data/save.txt', 'hola')."),
            Map.entry("file_exists", "Comprueba un archivo dentro del workspace: file_exists('data/save.txt')."),
            Map.entry("make_dirs", "Crea carpetas dentro del workspace: make_dirs('data/saves')."),
            Map.entry("list_files", "Lista archivos bajo el workspace: list_files('models')."),
            Map.entry("SetPATH", "Directiva en la primera línea que da a un script una ruta estable: SetPATH games/arena."),
            Map.entry("time", "Módulo time: time.sleep(segundos) pausa y time.time() devuelve segundos desde epoch."),
            Map.entry("math", "Módulo math: sin, cos, tan, sqrt, floor, ceil, log, exp, radians, degrees, pi, tau, e."),
            Map.entry("sqrt", "math.sqrt(x) devuelve la raíz cuadrada de x."),
            Map.entry("sin", "math.sin(x) devuelve el seno de x en radianes."),
            Map.entry("cos", "math.cos(x) devuelve el coseno de x en radianes."),
            Map.entry("tan", "math.tan(x) devuelve la tangente de x en radianes."),
            Map.entry("floor", "math.floor(x) redondea hacia abajo."),
            Map.entry("ceil", "math.ceil(x) redondea hacia arriba."),
            Map.entry("log", "math.log(x) devuelve el logaritmo natural."),
            Map.entry("exp", "math.exp(x) devuelve e elevado a x."),
            Map.entry("radians", "math.radians(grados) convierte grados a radianes."),
            Map.entry("degrees", "math.degrees(radianes) convierte radianes a grados."),
            Map.entry("pi", "math.pi es la constante π."),
            Map.entry("tau", "math.tau es 2π."),
            Map.entry("e", "math.e es la constante de Euler."),
            Map.entry("print", "Muestra un valor en la consola de MCODE: print('Hola')."),
            Map.entry("sleep", "Pausa un script durante segundos: time.sleep(0.1) es lo recomendado en bucles."),
            Map.entry("True", "Literal booleano verdadero; el editor normaliza 'true' a 'True'."),
            Map.entry("False", "Literal booleano falso; el editor normaliza 'false' a 'False'.")
    );

    public static String describeMember(String name) {
        return MEMBER_DOCS.getOrDefault(name, "API de MCODE");
    }

    public static List<String> pages() {
        return List.of(
                "MCODE — Ayuda rápida\n\n" +
                "MCODE ejecuta scripts con sintaxis inspirada en Python dentro de Minecraft. Usa M para abrir el editor. F5 ejecuta/detiene el script seleccionado. Ctrl+S guarda; Ctrl+O abre; Ctrl+W cierra la pestaña actual. Copia el registro completo de la consola con el botón Copiar log o Ctrl+Shift+C.\n" +
                "Con muchas pestañas, arrastra la barra estrecha bajo las pestañas, usa la rueda del ratón sobre esa zona o pulsa ‹ / ›. Ctrl+Tab y Ctrl+PageUp/PageDown también navegan.\n\n" +
                "SetPATH games/arena\nDebe ir en la primera línea. Nombra el script y se usa como ruta relativa dentro de .minecraft/mcode. Los imports de scripts usan el nombre de ruta: import games.arena.\n\n" +
                "True / False / None\nLos booleanos se corrigen al escribir o pegar y el intérprete acepta variantes de mayúsculas/minúsculas.",
                "Dibujo del mundo\n\n" +
                "import mc\nmc.mode('local', sigue=True)\nshape = mc.box(-1,0,3,1,2,5, color='cyan', fill=True)\nshape.move(1,0,0)\nshape.color('orange')\nshape.hide()\nshape.show()\nshape.remove()\n\n" +
                "Primitivas: line, box, point, circle, rect, polygon, polyline, sphere, cylinder, cone, mesh.\n" +
                "Frames de animación: mc.begin_frame() al empezar cada iteración y mc.end_frame() al final; reemplaza el dibujo del frame anterior al instante.\n" +
                "math: sin, cos, tan, sqrt, floor, ceil, log, exp, radians, degrees, pi, tau y e. Las funciones trigonométricas usan radianes.",
                "Importar un modelo de Blender\n\n" +
                "1. En Blender selecciona la malla y expórtala como Wavefront OBJ (.obj).\n" +
                "2. En MCODE abre Recursos > Importar modelo OBJ. Copia el archivo a la carpeta mcode/models.\n" +
                "3. En el script usa:\n\nmodel = mc.import_model('models/mi_modelo.obj', 0, 0, 4, scale=1, axis='blender', color='cyan', fill=True)\n\n" +
                "axis='blender' convierte Z-arriba de Blender a Y-arriba de Minecraft. Usa axis='xyz' si exportaste ya convertido. La importación estática inicial admite vértices y caras; materiales, rigging y animaciones no se importan todavía.",
                "Archivos y proyectos\n\n" +
                "read_text('data/progreso.txt')\nwrite_text('data/progreso.txt', 'Nivel=3')\nfile_exists('data/progreso.txt')\nmake_dirs('data/partidas')\nlist_files('models')\n\n" +
                "Recursos > Exportar proyecto crea un paquete .mcp con scripts y recursos compatibles. Importar un .mcp instala los archivos de proyecto bajo el workspace de MCODE y pide permiso antes de sobrescribir archivos. No instala un JAR de Fabric: empaqueta proyectos de scripts y assets de MCODE.",
                "API nueva y límites\n\n" +
                "mc.import_model(path, x=0, y=0, z=0, scale=1, axis='blender', color='white', fill=True) — importa OBJ estático. Los argumentos x, y, z pueden ser posicionales o keyword.\n" +
                "mc.mesh(vertices, faces) — crea una malla definida por el script.\n" +
                "shape.move(dx,dy,dz), shape.rotate(rx[,ry,rz]), shape.scale(factor), shape.reset_transform().\n\n" +
                "Las rutas de archivos están limitadas al workspace para evitar acceder fuera de la carpeta del proyecto. Los paquetes .mcp filtran archivos ejecutables y bloquean rutas de extracción inseguras."
        );
    }
}
