package textmenu.draw;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import textmenu.interpreter.NativeFunction;
import textmenu.interpreter.NativeObject;
import textmenu.interpreter.MCodeProjectManager;
import textmenu.runtime.ExecutionGuard;
import textmenu.runtime.MainThreadQueue;
import textmenu.runtime.PlayerSnapshot;
import textmenu.runtime.ScriptError;
import textmenu.runtime.ScriptMode;
import textmenu.runtime.ScriptRunner;

import java.time.LocalTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class McApi {

    public static final String MODULE_NAME = "mc";

    private static final double MAX_COORDINATE = 1.0e9;
    private static final float MIN_WIDTH = 0.5f;
    private static final float MAX_WIDTH = 16.0f;
    private static final int MAX_CHAT_CHARS = 256;
    private static final int MAX_GROUP_CHARS = 64;
    private static final double SAY_INTERVAL_SECONDS = 0.05;
    private static final DateTimeFormatter CONSOLE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<String> STYLE_KEYS = Set.of("color", "alpha", "width", "group", "fill", "visible");

    private static final Map<String, Integer> COLOR_NAMES = Map.ofEntries(
            Map.entry("white", 0xFFFFFF), Map.entry("black", 0x000000),
            Map.entry("red", 0xFF0000), Map.entry("green", 0x00FF00),
            Map.entry("blue", 0x0000FF), Map.entry("yellow", 0xFFFF00),
            Map.entry("cyan", 0x00FFFF), Map.entry("magenta", 0xFF00FF),
            Map.entry("orange", 0xFF8000), Map.entry("purple", 0x8000FF),
            Map.entry("pink", 0xFF80C0), Map.entry("gray", 0x808080),
            Map.entry("grey", 0x808080), Map.entry("lime", 0x80FF00),
            Map.entry("brown", 0x8B4513)
    );

    private final Scene scene;
    private final ExecutionGuard guard;
    private final String ownerId;
    private int defaultColor = 0xFFFFFF;
    private float defaultAlpha = 1.0f;
    private float defaultWidth = 2.5f;
    private PlayerSnapshot.Pose localAnchor = PlayerSnapshot.get();
    private boolean followLocalPlayer;

    public McApi(Scene scene, ExecutionGuard guard) {
        this(scene, guard, "");
    }

    public McApi(Scene scene, ExecutionGuard guard, String ownerId) {
        this.scene = scene;
        this.guard = guard;
        this.ownerId = ownerId == null ? "" : ownerId;
    }

    public static NativeObject catalog() {
        return new McApi(Scene.global(), new ExecutionGuard()).module();
    }

    public NativeObject module() {
        return new NativeObject(MODULE_NAME)
                .put("line", (NativeFunction) this::line)
                .put("polyline", (NativeFunction) this::polyline)
                .put("polygon", (NativeFunction) this::polygon)
                .put("circle", (NativeFunction) this::circle)
                .put("rect", (NativeFunction) this::rect)
                .put("box", (NativeFunction) this::box)
                .put("point", (NativeFunction) this::point)
                .put("sphere", (NativeFunction) this::sphere)
                .put("cylinder", (NativeFunction) this::cylinder)
                .put("cone", (NativeFunction) this::cone)
                .put("mesh", (NativeFunction) this::mesh)
                .put("import_model", (NativeFunction) this::importModel)
                .put("begin_frame", (NativeFunction) this::beginFrame)
                .put("end_frame", (NativeFunction) this::endFrame)
                .put("clear", (NativeFunction) this::clear)
                .put("remove", (NativeFunction) this::remove)
                .put("color", (NativeFunction) this::color)
                .put("alpha", (NativeFunction) this::alpha)
                .put("width", (NativeFunction) this::width)
                .put("chat", (NativeFunction) this::chat)
                .put("say", (NativeFunction) this::say)
                .put("mode", (NativeFunction) this::mode)
                .put("player_pos", (NativeFunction) this::playerPos);
    }

    private Object line(List<Object> args, Map<String, Object> kwargs) {
        double[] points = points(args, 2, Integer.MAX_VALUE, "mc.line");
        return create(new LineShape(scene.nextId(), points), kwargs, "mc.line");
    }

    private Object box(List<Object> args, Map<String, Object> kwargs) {
        double[] p = points(args, 2, 2, "mc.box");
        return create(new BoxShape(scene.nextId(), p[0], p[1], p[2], p[3], p[4], p[5]), kwargs, "mc.box");
    }

    private Object point(List<Object> args, Map<String, Object> kwargs) {
        validateGeometryKeys(kwargs, Set.of("size"), "mc.point");
        if (args.size() != 3) throw ScriptError.type("mc.point(x, y, z, size=0.12)");
        double x = coordinate(args.get(0), "mc.point"), y = coordinate(args.get(1), "mc.point"), z = coordinate(args.get(2), "mc.point");
        double size = coordinate(kwargs.getOrDefault("size", 0.12), "mc.point size");
        if (size <= 0 || size > 16) throw ScriptError.value("mc.point(): size debe estar entre 0 y 16");
        Map<String,Object> options = defaultsFill(styleOptions(kwargs), true);
        return create(new BoxShape(scene.nextId(), x-size, y-size, z-size, x+size, y+size, z+size), options, "mc.point");
    }

    private Object sphere(List<Object> args, Map<String, Object> kwargs) {
        validateGeometryKeys(kwargs, Set.of("segments", "rings"), "mc.sphere");
        if (args.size() != 4) throw ScriptError.type("mc.sphere(x, y, z, radius, segments=16, rings=10)");
        double cx=coordinate(args.get(0),"mc.sphere"), cy=coordinate(args.get(1),"mc.sphere"), cz=coordinate(args.get(2),"mc.sphere"), r=coordinate(args.get(3),"mc.sphere");
        if (r <= 0) throw ScriptError.value("mc.sphere(): radius debe ser mayor que cero");
        int seg=intOption(kwargs.getOrDefault("segments",16L),"segments",6,64,"mc.sphere");
        int rings=intOption(kwargs.getOrDefault("rings",10L),"rings",4,48,"mc.sphere");
        double[] v=new double[(2+(rings-1)*seg)*3];
        v[0]=cx; v[1]=cy+r; v[2]=cz;
        for(int row=1;row<rings;row++){
            double theta=Math.PI*row/rings;
            for(int col=0;col<seg;col++){
                double phi=Math.PI*2*col/seg; int index=1+(row-1)*seg+col;
                v[index*3]=cx+r*Math.sin(theta)*Math.cos(phi); v[index*3+1]=cy+r*Math.cos(theta); v[index*3+2]=cz+r*Math.sin(theta)*Math.sin(phi);
            }
        }
        int bottom=v.length/3-1; v[bottom*3]=cx; v[bottom*3+1]=cy-r; v[bottom*3+2]=cz;
        List<Integer> tris=new ArrayList<>(), edges=new ArrayList<>();
        int firstRing=1;
        for(int col=0;col<seg;col++){
            int next=(col+1)%seg;
            addTriangle(tris,0,firstRing+col,firstRing+next);
            addEdge(edges,firstRing+col,firstRing+next);
            addEdge(edges,0,firstRing+col);
        }
        for(int row=0;row<rings-2;row++){
            int upper=1+row*seg, lower=upper+seg;
            for(int col=0;col<seg;col++){
                int next=(col+1)%seg;
                int a=upper+col,b=upper+next,c=lower+next,d=lower+col;
                addTriangle(tris,a,d,b); addTriangle(tris,b,d,c);
                addEdge(edges,a,b); addEdge(edges,a,d);
            }
        }
        int lastRing=1+(rings-2)*seg;
        for(int col=0;col<seg;col++){
            int next=(col+1)%seg;
            addTriangle(tris,lastRing+col,bottom,lastRing+next);
            addEdge(edges,lastRing+col,lastRing+next); addEdge(edges,lastRing+col,bottom);
        }
        return create(new MeshShape(scene.nextId(),v,toIntArray(tris),toIntArray(edges)), defaultsFill(styleOptions(kwargs),true), "mc.sphere");
    }

    private Object cylinder(List<Object> args, Map<String, Object> kwargs) {
        validateGeometryKeys(kwargs, Set.of("segments"), "mc.cylinder");
        if (args.size()!=5) throw ScriptError.type("mc.cylinder(x, y, z, radius, height, segments=16)");
        double cx=coordinate(args.get(0),"mc.cylinder"), cy=coordinate(args.get(1),"mc.cylinder"), cz=coordinate(args.get(2),"mc.cylinder"), r=coordinate(args.get(3),"mc.cylinder"), h=coordinate(args.get(4),"mc.cylinder");
        if(r<=0||h<=0)throw ScriptError.value("mc.cylinder(): radius y height deben ser mayores que cero");
        int seg=intOption(kwargs.getOrDefault("segments",16L),"segments",6,96,"mc.cylinder");
        double[] v=new double[(2*seg+2)*3];
        for(int i=0;i<seg;i++){double a=2*Math.PI*i/seg;v[i*3]=cx+r*Math.cos(a);v[i*3+1]=cy;v[i*3+2]=cz+r*Math.sin(a);int j=seg+i;v[j*3]=v[i*3];v[j*3+1]=cy+h;v[j*3+2]=v[i*3+2];}
        int bottom=2*seg,top=bottom+1;v[bottom*3]=cx;v[bottom*3+1]=cy;v[bottom*3+2]=cz;v[top*3]=cx;v[top*3+1]=cy+h;v[top*3+2]=cz;
        List<Integer> tris=new ArrayList<>(),edges=new ArrayList<>();
        for(int i=0;i<seg;i++){int n=(i+1)%seg;addTriangle(tris,i,n,seg+n);addTriangle(tris,i,seg+n,seg+i);addTriangle(tris,bottom,n,i);addTriangle(tris,top,seg+i,seg+n);addEdge(edges,i,n);addEdge(edges,seg+i,seg+n);addEdge(edges,i,seg+i);}
        return create(new MeshShape(scene.nextId(),v,toIntArray(tris),toIntArray(edges)),defaultsFill(styleOptions(kwargs),true),"mc.cylinder");
    }

    private Object cone(List<Object> args, Map<String, Object> kwargs) {
        validateGeometryKeys(kwargs, Set.of("segments"), "mc.cone");
        if(args.size()!=5)throw ScriptError.type("mc.cone(x, y, z, radius, height, segments=16)");
        double cx=coordinate(args.get(0),"mc.cone"),cy=coordinate(args.get(1),"mc.cone"),cz=coordinate(args.get(2),"mc.cone"),r=coordinate(args.get(3),"mc.cone"),h=coordinate(args.get(4),"mc.cone");
        if(r<=0||h<=0)throw ScriptError.value("mc.cone(): radius y height deben ser mayores que cero");
        int seg=intOption(kwargs.getOrDefault("segments",16L),"segments",6,96,"mc.cone");
        double[] v=new double[(seg+2)*3];
        for(int i=0;i<seg;i++){double a=2*Math.PI*i/seg;v[i*3]=cx+r*Math.cos(a);v[i*3+1]=cy;v[i*3+2]=cz+r*Math.sin(a);} int tip=seg,center=seg+1;v[tip*3]=cx;v[tip*3+1]=cy+h;v[tip*3+2]=cz;v[center*3]=cx;v[center*3+1]=cy;v[center*3+2]=cz;
        List<Integer> tris=new ArrayList<>(),edges=new ArrayList<>();
        for(int i=0;i<seg;i++){int n=(i+1)%seg;addTriangle(tris,i,tip,n);addTriangle(tris,center,n,i);addEdge(edges,i,n);addEdge(edges,i,tip);}
        return create(new MeshShape(scene.nextId(),v,toIntArray(tris),toIntArray(edges)),defaultsFill(styleOptions(kwargs),true),"mc.cone");
    }

    private Object mesh(List<Object> args, Map<String, Object> kwargs) {
        validateGeometryKeys(kwargs, Set.of(), "mc.mesh");
        if(args.size()!=2 || !(args.get(1) instanceof List<?> faces)) throw ScriptError.type("mc.mesh(vertices, faces) necesita una lista de vertices XYZ y otra de caras con indices");
        double[] vertices=points(List.of(args.get(0)),3,10_000,"mc.mesh vertices");
        if(faces.isEmpty()||faces.size()>20_000)throw ScriptError.value("mc.mesh(): número de caras inválido");
        int vertexCount=vertices.length/3;
        List<Integer> triangles=new ArrayList<>(), edges=new ArrayList<>();
        LinkedHashSet<Long> edgeSet=new LinkedHashSet<>();
        for(Object faceObject:faces){
            if(!(faceObject instanceof List<?> face)||face.size()<3||face.size()>512)throw ScriptError.type("mc.mesh(): cada cara debe ser una lista de al menos 3 índices");
            int[] ids=new int[face.size()];
            for(int i=0;i<face.size();i++){if(!(face.get(i) instanceof Number n)||n.doubleValue()!=Math.rint(n.doubleValue()))throw ScriptError.type("mc.mesh(): los índices deben ser enteros");ids[i]=n.intValue();if(ids[i]<0||ids[i]>=vertexCount)throw ScriptError.value("mc.mesh(): índice de vértice fuera de rango");}
            for(int i=1;i<ids.length-1;i++)addTriangle(triangles,ids[0],ids[i],ids[i+1]);
            for(int i=0;i<ids.length;i++){int a=Math.min(ids[i],ids[(i+1)%ids.length]),b=Math.max(ids[i],ids[(i+1)%ids.length]);edgeSet.add(((long)a<<32)|(b&0xffffffffL));}
        }
        for(long e:edgeSet){edges.add((int)(e>>>32));edges.add((int)e);}
        return create(new MeshShape(scene.nextId(),vertices,toIntArray(triangles),toIntArray(edges)),defaultsFill(styleOptions(kwargs),true),"mc.mesh");
    }

    private Object importModel(List<Object> args, Map<String, Object> kwargs) {
        // Signature compatible with the examples: path may be followed by positional x/y/z,
        // while scale, axis and style options can be passed by keyword.
        validateGeometryKeys(kwargs, Set.of("x", "y", "z", "scale", "axis"), "mc.import_model");
        if (args.isEmpty() || args.size() > 4 || !(args.get(0) instanceof String relativePath)) {
            throw ScriptError.type("mc.import_model(path, x=0, y=0, z=0, scale=1, axis='blender', color='white', fill=True)");
        }
        String[] coordinateNames = {"x", "y", "z"};
        for (int i = 1; i < args.size(); i++) {
            if (kwargs.containsKey(coordinateNames[i - 1])) {
                throw ScriptError.type("mc.import_model(): multiple values for argument '" + coordinateNames[i - 1] + "'");
            }
        }
        String axis = String.valueOf(kwargs.getOrDefault("axis", "blender")).toLowerCase(Locale.ROOT);
        if (!Set.of("blender", "xyz").contains(axis)) throw ScriptError.value("mc.import_model(): axis debe ser 'blender' o 'xyz'");
        double x = coordinate(args.size() > 1 ? args.get(1) : kwargs.getOrDefault("x", 0L), "mc.import_model x");
        double y = coordinate(args.size() > 2 ? args.get(2) : kwargs.getOrDefault("y", 0L), "mc.import_model y");
        double z = coordinate(args.size() > 3 ? args.get(3) : kwargs.getOrDefault("z", 0L), "mc.import_model z");
        double scale = coordinate(kwargs.getOrDefault("scale", 1L), "mc.import_model scale");
        if (scale <= 0 || scale > 1000) throw ScriptError.value("mc.import_model(): scale debe estar entre 0 y 1000");
        Path path;
        try {
            path = MCodeProjectManager.normalizeDataPath(relativePath);
        } catch (IllegalArgumentException ex) {
            throw ScriptError.value("mc.import_model(): " + ex.getMessage());
        }
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".obj")) {
            throw ScriptError.value("mc.import_model(): esta fase admite modelos Wavefront OBJ (.obj). Exporta el modelo desde Blender como OBJ.");
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw ScriptError.value("mc.import_model(): no existe o no se puede leer '" + relativePath + "' dentro de la carpeta mcode.");
        }
        try {
            ObjModelImporter.Model imported = ObjModelImporter.read(path, axis);
            double[] vertices = imported.vertices();
            for (int i = 0; i + 2 < vertices.length; i += 3) {
                vertices[i] = vertices[i] * scale + x;
                vertices[i + 1] = vertices[i + 1] * scale + y;
                vertices[i + 2] = vertices[i + 2] * scale + z;
            }
            MeshShape model = new MeshShape(scene.nextId(), vertices, imported.triangles(), imported.edges());
            return create(model, defaultsFill(styleOptions(kwargs), true), "mc.import_model");
        } catch (java.io.IOException | IllegalArgumentException ex) {
            throw ScriptError.value("mc.import_model(): no se pudo importar el OBJ: " + ex.getMessage());
        }
    }

    private static void addTriangle(List<Integer> out,int a,int b,int c){out.add(a);out.add(b);out.add(c);}
    private static void addEdge(List<Integer> out,int a,int b){out.add(a);out.add(b);}
    private static int[] toIntArray(List<Integer> values){int[] out=new int[values.size()];for(int i=0;i<out.length;i++)out[i]=values.get(i);return out;}
    private static Map<String,Object> defaultsFill(Map<String,Object> options, boolean filled){if(!options.containsKey("fill"))options.put("fill",filled);return options;}

    private Object polyline(List<Object> args, Map<String, Object> kwargs) {
        validateGeometryKeys(kwargs, Set.of("plane", "level"), "mc.polyline");
        String plane = planeOption(kwargs, "mc.polyline");
        double level = levelOption(kwargs, "mc.polyline");
        double[] vertices = contourPoints(args, plane, level, 2, "mc.polyline");
        return create(new LineShape(scene.nextId(), vertices), styleOptions(kwargs), "mc.polyline");
    }

    private Object polygon(List<Object> args, Map<String, Object> kwargs) {
        validateGeometryKeys(kwargs, Set.of("plane", "level"), "mc.polygon");
        String plane = planeOption(kwargs, "mc.polygon");
        double level = levelOption(kwargs, "mc.polygon");
        double[] vertices = contourPoints(args, plane, level, 3, "mc.polygon");
        if (vertices.length / 3 > 128) throw ScriptError.value("mc.polygon(): máximo 128 puntos para mantener una triangulación rápida");
        int[] boundary = sequence(vertices.length / 3);
        int[] triangles = triangulate(vertices, plane);
        return create(new PolygonShape(scene.nextId(), vertices, triangles, boundary), styleOptions(kwargs), "mc.polygon");
    }

    private Object circle(List<Object> args, Map<String, Object> kwargs) {
        validateGeometryKeys(kwargs, Set.of("plane", "segments"), "mc.circle");
        if (args.size() != 4) throw ScriptError.type("mc.circle(x, y, z, radius, plane='xz', segments=32)");
        double cx = coordinate(args.get(0), "mc.circle");
        double cy = coordinate(args.get(1), "mc.circle");
        double cz = coordinate(args.get(2), "mc.circle");
        double radius = coordinate(args.get(3), "mc.circle");
        if (radius <= 0) throw ScriptError.value("mc.circle(): radius debe ser mayor que cero");
        String plane = planeOption(kwargs, "mc.circle");
        int segments = intOption(kwargs.getOrDefault("segments", 32L), "segments", 8, 256, "mc.circle");

        // Vertex zero is the center; the rest are the visible contour.
        double[] vertices = new double[(segments + 1) * 3];
        vertices[0] = cx; vertices[1] = cy; vertices[2] = cz;
        int[] boundary = new int[segments];
        int[] triangles = new int[segments * 3];
        for (int i = 0; i < segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            double a = Math.cos(angle) * radius;
            double b = Math.sin(angle) * radius;
            int index = i + 1;
            if (plane.equals("xz")) { vertices[index * 3] = cx + a; vertices[index * 3 + 1] = cy; vertices[index * 3 + 2] = cz + b; }
            else if (plane.equals("xy")) { vertices[index * 3] = cx + a; vertices[index * 3 + 1] = cy + b; vertices[index * 3 + 2] = cz; }
            else { vertices[index * 3] = cx; vertices[index * 3 + 1] = cy + a; vertices[index * 3 + 2] = cz + b; }
            boundary[i] = index;
            triangles[i * 3] = 0;
            triangles[i * 3 + 1] = index;
            triangles[i * 3 + 2] = (i + 1 == segments) ? 1 : index + 1;
        }
        return create(new PolygonShape(scene.nextId(), vertices, triangles, boundary), styleOptions(kwargs), "mc.circle");
    }

    private Object rect(List<Object> args, Map<String, Object> kwargs) {
        validateGeometryKeys(kwargs, Set.of("plane"), "mc.rect");
        if (args.size() != 5) throw ScriptError.type("mc.rect(x, y, z, width, height, plane='xz')");
        double cx = coordinate(args.get(0), "mc.rect");
        double cy = coordinate(args.get(1), "mc.rect");
        double cz = coordinate(args.get(2), "mc.rect");
        double width = coordinate(args.get(3), "mc.rect");
        double height = coordinate(args.get(4), "mc.rect");
        if (width <= 0 || height <= 0) throw ScriptError.value("mc.rect(): width y height deben ser mayores que cero");
        String plane = planeOption(kwargs, "mc.rect");
        double hw = width / 2.0, hh = height / 2.0;
        double[] vertices;
        if (plane.equals("xz")) vertices = new double[]{cx-hw,cy,cz-hh, cx+hw,cy,cz-hh, cx+hw,cy,cz+hh, cx-hw,cy,cz+hh};
        else if (plane.equals("xy")) vertices = new double[]{cx-hw,cy-hh,cz, cx+hw,cy-hh,cz, cx+hw,cy+hh,cz, cx-hw,cy+hh,cz};
        else vertices = new double[]{cx,cy-hw,cz-hh, cx,cy+hw,cz-hh, cx,cy+hw,cz+hh, cx,cy-hw,cz+hh};
        return create(new PolygonShape(scene.nextId(), vertices, new int[]{0,1,2,0,2,3}, new int[]{0,1,2,3}), styleOptions(kwargs), "mc.rect");
    }

    private static void validateGeometryKeys(Map<String, Object> kwargs, Set<String> extras, String function) {
        for (String key : kwargs.keySet()) {
            if (!STYLE_KEYS.contains(key) && !extras.contains(key)) throw ScriptError.type(function + "() no acepta el argumento '" + key + "'");
        }
    }

    private static Map<String, Object> styleOptions(Map<String, Object> kwargs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : STYLE_KEYS) if (kwargs.containsKey(key)) result.put(key, kwargs.get(key));
        return result;
    }

    private static String planeOption(Map<String, Object> kwargs, String function) {
        Object value = kwargs.getOrDefault("plane", "xz");
        if (!(value instanceof String text)) throw ScriptError.type(function + "(): plane debe ser 'xz', 'xy' o 'yz'");
        String plane = text.toLowerCase(Locale.ROOT);
        if (!Set.of("xz", "xy", "yz").contains(plane)) throw ScriptError.value(function + "(): plane debe ser 'xz', 'xy' o 'yz'");
        return plane;
    }

    private static double levelOption(Map<String, Object> kwargs, String function) {
        Object value = kwargs.getOrDefault("level", 0.0);
        return coordinate(value, function + " level");
    }

    private static int intOption(Object value, String name, int min, int max, String function) {
        if (!(value instanceof Number number)) throw ScriptError.type(function + "(): " + name + " debe ser entero");
        double n = number.doubleValue();
        if (!Double.isFinite(n) || n != Math.rint(n) || n < min || n > max) throw ScriptError.value(function + "(): " + name + " debe estar entre " + min + " y " + max);
        return (int) n;
    }

    /** Accepts nested 2D points placed on a selected plane or nested XYZ points. */
    private static double[] contourPoints(List<Object> args, String plane, double level, int minimum, String function) {
        if (args.size() == 1 && args.get(0) instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof List<?>) {
            List<?> points = list;
            if (points.size() < minimum || points.size() > 512) throw ScriptError.value(function + "(): se necesitan entre " + minimum + " y 512 puntos");
            Object first = points.get(0);
            if (!(first instanceof List<?> firstPoint) || (firstPoint.size() != 2 && firstPoint.size() != 3)) {
                throw ScriptError.type(function + "(): cada punto debe tener dos o tres coordenadas");
            }
            int dimensions = firstPoint.size();
            double[] output = new double[points.size() * 3];
            for (int i = 0; i < points.size(); i++) {
                if (!(points.get(i) instanceof List<?> point) || point.size() != dimensions) throw ScriptError.type(function + "(): todos los puntos deben tener la misma dimensión");
                if (dimensions == 3) {
                    output[i*3] = coordinate(point.get(0), function);
                    output[i*3+1] = coordinate(point.get(1), function);
                    output[i*3+2] = coordinate(point.get(2), function);
                } else if (plane.equals("xz")) {
                    output[i*3] = coordinate(point.get(0), function); output[i*3+1] = level; output[i*3+2] = coordinate(point.get(1), function);
                } else if (plane.equals("xy")) {
                    output[i*3] = coordinate(point.get(0), function); output[i*3+1] = coordinate(point.get(1), function); output[i*3+2] = level;
                } else {
                    output[i*3] = level; output[i*3+1] = coordinate(point.get(0), function); output[i*3+2] = coordinate(point.get(1), function);
                }
            }
            return output;
        }
        // Also allow the compact flat form: x1,y1,z1,x2,y2,z2,...
        return points(args, minimum, 512, function);
    }

    private static int[] sequence(int length) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) result[i] = i;
        return result;
    }

    /** Ear-clipping triangulation for a simple contour projected onto its chosen coordinate plane. */
    private static int[] triangulate(double[] vertices, String plane) {
        int count = vertices.length / 3;
        if (count == 3) return new int[]{0, 1, 2};
        double[] px = new double[count], py = new double[count];
        for (int i = 0; i < count; i++) {
            px[i] = vertices[i*3 + (plane.equals("yz") ? 1 : 0)];
            py[i] = vertices[i*3 + (plane.equals("xz") ? 2 : plane.equals("xy") ? 1 : 2)];
        }
        double area = 0;
        for (int i = 0; i < count; i++) { int j = (i + 1) % count; area += px[i]*py[j] - px[j]*py[i]; }
        if (Math.abs(area) < 1.0e-10) throw ScriptError.value("mc.polygon(): los puntos deben formar un área no nula");
        double orientation = area > 0 ? 1.0 : -1.0;
        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < count; i++) remaining.add(i);
        int[] result = new int[(count - 2) * 3];
        int out = 0;
        int attempts = 0;
        while (remaining.size() > 3) {
            boolean clipped = false;
            for (int i = 0; i < remaining.size(); i++) {
                int prev = remaining.get((i + remaining.size() - 1) % remaining.size());
                int curr = remaining.get(i);
                int next = remaining.get((i + 1) % remaining.size());
                double cross = cross(px[prev], py[prev], px[curr], py[curr], px[next], py[next]) * orientation;
                if (cross <= 1.0e-10) continue;
                boolean contains = false;
                for (int candidate : remaining) {
                    if (candidate == prev || candidate == curr || candidate == next) continue;
                    if (insideTriangle(px[candidate], py[candidate], px[prev], py[prev], px[curr], py[curr], px[next], py[next], orientation)) { contains = true; break; }
                }
                if (contains) continue;
                result[out++] = prev; result[out++] = curr; result[out++] = next;
                remaining.remove(i);
                clipped = true;
                break;
            }
            if (!clipped || ++attempts > count * count) throw ScriptError.value("mc.polygon(): contorno inválido o autointersectado; usa puntos simples en orden");
        }
        result[out++] = remaining.get(0); result[out++] = remaining.get(1); result[out] = remaining.get(2);
        return result;
    }

    private static double cross(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx-ax)*(cy-ay) - (by-ay)*(cx-ax);
    }

    private static boolean insideTriangle(double px, double py, double ax, double ay, double bx, double by, double cx, double cy, double orientation) {
        double e = 1.0e-10;
        return cross(ax,ay,bx,by,px,py)*orientation >= -e
                && cross(bx,by,cx,cy,px,py)*orientation >= -e
                && cross(cx,cy,ax,ay,px,py)*orientation >= -e;
    }

    private Object beginFrame(List<Object> args, Map<String, Object> kwargs) {
        if (!args.isEmpty() || !kwargs.isEmpty()) throw ScriptError.type("mc.begin_frame() no acepta argumentos");
        scene.beginFrame(ownerId);
        return null;
    }

    private Object endFrame(List<Object> args, Map<String, Object> kwargs) {
        if (!args.isEmpty() || !kwargs.isEmpty()) throw ScriptError.type("mc.end_frame() no acepta argumentos");
        return (long) scene.endFrame(ownerId);
    }

    private Object clear(List<Object> args, Map<String, Object> kwargs) {
        requireKeys(kwargs, Set.of("group", "all"), "mc.clear");
        if (!args.isEmpty()) throw ScriptError.type("mc.clear() no acepta argumentos posicionales");
        if (kwargs.containsKey("group") && kwargs.containsKey("all")) throw ScriptError.value("mc.clear(): elige group o all, no ambos");
        if (kwargs.containsKey("all") && flag(kwargs.get("all"), "all")) return (long) scene.clear();
        if (kwargs.containsKey("group")) return (long) scene.clearOwnerGroup(ownerId, group(kwargs.get("group")));
        if (scene.isFrameOpen(ownerId)) return (long) scene.clearStagedOwner(ownerId);
        return (long) scene.clearOwner(ownerId);
    }

    private Object remove(List<Object> args, Map<String, Object> kwargs) {
        if (args.size() != 1 || !kwargs.isEmpty()) throw ScriptError.type("mc.remove() espera una forma o un id");
        Object target = args.get(0);
        if (target instanceof NativeObject object) target = object.members().get("id");
        if (!(target instanceof Number number)) throw ScriptError.type("mc.remove() espera una forma o un id");
        return scene.remove(number.longValue());
    }

    private Object color(List<Object> args, Map<String, Object> kwargs) {
        requireKeys(kwargs, Set.of("alpha"), "mc.color");
        defaultColor = parseColor(args);
        if (kwargs.containsKey("alpha")) defaultAlpha = alphaValue(kwargs.get("alpha"));
        return null;
    }

    private Object alpha(List<Object> args, Map<String, Object> kwargs) {
        if (args.size() != 1 || !kwargs.isEmpty()) throw ScriptError.type("mc.alpha() espera un valor entre 0 y 1");
        defaultAlpha = alphaValue(args.get(0));
        return null;
    }

    private Object width(List<Object> args, Map<String, Object> kwargs) {
        if (args.size() != 1 || !kwargs.isEmpty()) throw ScriptError.type("mc.width() espera un grosor");
        defaultWidth = widthValue(args.get(0));
        return null;
    }

    private Object chat(List<Object> args, Map<String, Object> kwargs) {
        if (!kwargs.isEmpty()) throw ScriptError.type("mc.chat() no acepta argumentos con nombre");
        String text = joinText(args);
        ScriptRunner.console().append("[" + LocalTime.now().format(CONSOLE_TIME) + "] [chat local] " + text + "\n");
        MainThreadQueue.post(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) client.player.sendMessage(Text.literal(text), false);
        }, guard);
        return null;
    }

    private Object say(List<Object> args, Map<String, Object> kwargs) {
        if (!kwargs.isEmpty()) throw ScriptError.type("mc.say() no acepta argumentos con nombre");
        ScriptMode.requireGlobal("mc.say");
        String text = joinText(args).trim();
        if (text.isEmpty()) throw ScriptError.value("mc.say() necesita un texto");
        MainThreadQueue.post(() -> {
            if (ScriptMode.effective() != ScriptMode.Mode.GLOBAL) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.getNetworkHandler() == null) return;
            if (text.startsWith("/")) {
                client.getNetworkHandler().sendChatCommand(text.substring(1));
            } else {
                client.getNetworkHandler().sendChatMessage(text);
            }
        }, guard);
        guard.sleep(SAY_INTERVAL_SECONDS);
        return null;
    }

    private Object mode(List<Object> args, Map<String, Object> kwargs) {
        if (args.isEmpty() && kwargs.isEmpty()) {
            return ScriptMode.effective().name().toLowerCase(Locale.ROOT);
        }
        requireKeys(kwargs, Set.of("sigue"), "mc.mode");
        if (args.size() != 1 || !(args.get(0) instanceof String requested)) {
            throw ScriptError.type("mc.mode() espera 'local' o 'global'; opcionalmente sigue=True");
        }

        String normalized = requested.toLowerCase(Locale.ROOT);
        ScriptMode.Mode requestedMode;
        if (normalized.equals("local")) {
            requestedMode = ScriptMode.Mode.LOCAL;
        } else if (normalized.equals("global")) {
            requestedMode = ScriptMode.Mode.GLOBAL;
        } else {
            throw ScriptError.value("modo desconocido: usa 'local' o 'global'");
        }

        boolean follow = false;
        if (kwargs.containsKey("sigue")) {
            follow = flag(kwargs.get("sigue"), "sigue");
            if (follow && requestedMode != ScriptMode.Mode.LOCAL) {
                throw ScriptError.value("sigue=True solo se aplica al modo LOCAL");
            }
        }

        if (requestedMode == ScriptMode.Mode.GLOBAL && ScriptMode.onRemoteServer()) {
            ScriptMode.select(ScriptMode.Mode.LOCAL);
            ScriptRunner.console().append("Modo GLOBAL bloqueado en multijugador; se mantiene LOCAL.\n");
            return ScriptMode.Mode.LOCAL.name().toLowerCase(Locale.ROOT);
        }

        ScriptMode.select(requestedMode);
        if (requestedMode == ScriptMode.Mode.LOCAL) {
            PlayerSnapshot.Pose current = PlayerSnapshot.get();
            if (current == null) {
                throw ScriptError.runtime("mc.mode('local') necesita un jugador dentro de un mundo.");
            }
            localAnchor = current;
            followLocalPlayer = follow;
        }
        return ScriptMode.effective().name().toLowerCase(Locale.ROOT);
    }

    private static String joinText(List<Object> args) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) builder.append(' ');
            builder.append(display(args.get(i)));
        }
        return builder.length() > MAX_CHAT_CHARS ? builder.substring(0, MAX_CHAT_CHARS) : builder.toString();
    }

    private Object playerPos(List<Object> args, Map<String, Object> kwargs) {
        PlayerSnapshot.Pose pose = PlayerSnapshot.get();
        if (pose == null) throw ScriptError.runtime("No hay un jugador dentro de un mundo.");
        return List.of(pose.x(), pose.y(), pose.z());
    }

    private NativeObject create(Shape shape, Map<String, Object> kwargs, String function) {
        requireKeys(kwargs, STYLE_KEYS, function);
        shape.rgb(defaultColor);
        shape.alpha(defaultAlpha);
        shape.thickness(defaultWidth);
        if (kwargs.containsKey("color")) shape.rgb(parseColor(List.of(kwargs.get("color"))));
        if (kwargs.containsKey("alpha")) shape.alpha(alphaValue(kwargs.get("alpha")));
        if (kwargs.containsKey("width")) shape.thickness(widthValue(kwargs.get("width")));
        if (kwargs.containsKey("group")) shape.group(group(kwargs.get("group")));
        if (kwargs.containsKey("fill")) shape.filled(flag(kwargs.get("fill"), "fill"));
        if (kwargs.containsKey("visible")) shape.visible(flag(kwargs.get("visible"), "visible"));
        shape.owner(ownerId);
        shape.transformPivot(shape.center());
        if (ScriptMode.effective() == ScriptMode.Mode.LOCAL) {
            PlayerSnapshot.Pose current = PlayerSnapshot.get();
            if (localAnchor == null) localAnchor = current;
            if (localAnchor == null) {
                throw ScriptError.runtime("Las formas LOCAL necesitan un jugador dentro de un mundo.");
            }
            shape.localTransform(localAnchor, followLocalPlayer);
        } else {
            shape.space(Space.GLOBAL);
        }
        scene.add(shape);
        return handle(shape);
    }

    private NativeObject handle(Shape shape) {
        NativeObject handle = new NativeObject("shape");
        handle.put("id", shape.id())
                .put("remove", (NativeFunction) (a, k) -> scene.remove(shape.id()))
                .put("hide", (NativeFunction) (a, k) -> {
                    shape.visible(false);
                    return handle;
                })
                .put("show", (NativeFunction) (a, k) -> {
                    shape.visible(true);
                    return handle;
                })
                .put("color", (NativeFunction) (a, k) -> {
                    shape.rgb(parseColor(a));
                    return handle;
                })
                .put("alpha", (NativeFunction) (a, k) -> {
                    shape.alpha(alphaValue(single(a, "alpha")));
                    return handle;
                })
                .put("width", (NativeFunction) (a, k) -> {
                    shape.thickness(widthValue(single(a, "width")));
                    return handle;
                })
                .put("fill", (NativeFunction) (a, k) -> {
                    shape.filled(a.isEmpty() || flag(a.get(0), "fill"));
                    return handle;
                })
                .put("move", (NativeFunction) (a, k) -> {
                    double[] delta = points(a, 1, 1, "move");
                    shape.translate(delta[0], delta[1], delta[2]);
                    return handle;
                })
                .put("rotate", (NativeFunction) (a, k) -> {
                    if (!k.isEmpty() || a.isEmpty() || a.size() > 3) throw ScriptError.type("rotate(x_degrees[, y_degrees, z_degrees])");
                    double rx=0,ry=0,rz=0;
                    if(a.size()==1) ry=finiteNumber(a.get(0),"rotate");
                    else {rx=finiteNumber(a.get(0),"rotate");ry=finiteNumber(a.get(1),"rotate");if(a.size()==3)rz=finiteNumber(a.get(2),"rotate");}
                    shape.rotateBy(rx,ry,rz); return handle;
                })
                .put("scale", (NativeFunction) (a, k) -> {
                    if (!k.isEmpty() || (a.size()!=1 && a.size()!=3)) throw ScriptError.type("scale(uniforme) o scale(x, y, z)");
                    double sx=finiteNumber(a.get(0),"scale"), sy=a.size()==1?sx:finiteNumber(a.get(1),"scale"), sz=a.size()==1?sx:finiteNumber(a.get(2),"scale");
                    if(sx==0||sy==0||sz==0||Math.abs(sx)>1000||Math.abs(sy)>1000||Math.abs(sz)>1000)throw ScriptError.value("scale(): factores no nulos y máximo 1000");
                    shape.scaleBy(sx,sy,sz); return handle;
                })
                .put("reset_transform", (NativeFunction) (a, k) -> { if(!a.isEmpty()||!k.isEmpty())throw ScriptError.type("reset_transform() no acepta argumentos");shape.resetTransform();return handle; });
        return handle;
    }

    private static double finiteNumber(Object value, String function) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw ScriptError.type(function + "(): se esperaba un número finito");
        return number.doubleValue();
    }

    private static double[] points(List<Object> args, int min, int max, String function) {
        List<?> items = args;
        if (items.size() == 1 && items.get(0) instanceof List<?> inner) items = inner;
        if (items.isEmpty()) throw ScriptError.type(function + "() necesita coordenadas");

        double[] out;
        if (items.get(0) instanceof List<?>) {
            out = new double[items.size() * 3];
            for (int i = 0; i < items.size(); i++) {
                if (!(items.get(i) instanceof List<?> point) || point.size() != 3) {
                    throw ScriptError.type(function + "(): cada punto debe ser (x, y, z)");
                }
                for (int j = 0; j < 3; j++) out[i * 3 + j] = coordinate(point.get(j), function);
            }
        } else {
            if (items.size() % 3 != 0) throw ScriptError.type(function + "(): las coordenadas deben venir de 3 en 3 (x, y, z)");
            out = new double[items.size()];
            for (int i = 0; i < out.length; i++) out[i] = coordinate(items.get(i), function);
        }
        int count = out.length / 3;
        if (count < min || count > max) {
            throw ScriptError.type(function + "(): se esperaban "
                    + (min == max ? String.valueOf(min) : "al menos " + min) + " punto(s) y se recibieron " + count);
        }
        return out;
    }

    private static double coordinate(Object value, String function) {
        if (!(value instanceof Number number)) throw ScriptError.type(function + "(): las coordenadas deben ser números");
        double d = number.doubleValue();
        if (!Double.isFinite(d) || Math.abs(d) > MAX_COORDINATE) {
            throw ScriptError.value(function + "(): coordenada fuera de rango");
        }
        return d;
    }

    private static int parseColor(List<Object> args) {
        if (args.size() == 1 && args.get(0) instanceof String text) return colorFromText(text);
        List<?> parts = args;
        if (args.size() == 1 && args.get(0) instanceof List<?> inner) parts = inner;
        if (parts.size() != 3) {
            throw ScriptError.type("color inválido: usa un nombre, \"#RRGGBB\" o tres valores 0-255");
        }
        int rgb = 0;
        for (Object part : parts) {
            if (!(part instanceof Number number)) throw ScriptError.type("los componentes de color deben ser números");
            double d = number.doubleValue();
            if (!(d >= 0 && d <= 255)) throw ScriptError.value("los componentes de color van de 0 a 255");
            rgb = (rgb << 8) | (int) Math.round(d);
        }
        return rgb;
    }

    private static int colorFromText(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        Integer named = COLOR_NAMES.get(value);
        if (named != null) return named;
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            if (hex.length() == 3) {
                int r = Integer.parseInt(hex.substring(0, 1), 16);
                int g = Integer.parseInt(hex.substring(1, 2), 16);
                int b = Integer.parseInt(hex.substring(2, 3), 16);
                return ((r * 17) << 16) | ((g * 17) << 8) | (b * 17);
            }
            if (hex.length() == 6) return Integer.parseInt(hex, 16);
        } catch (NumberFormatException ignored) {
        }
        throw ScriptError.value("color desconocido: " + text);
    }

    private static float alphaValue(Object value) {
        if (!(value instanceof Number number)) throw ScriptError.type("alpha debe ser un numero entre 0 y 1");
        double d = number.doubleValue();
        if (!(d >= 0.0 && d <= 1.0)) throw ScriptError.value("alpha debe estar entre 0 y 1");
        return (float) d;
    }

    private static float widthValue(Object value) {
        if (!(value instanceof Number number)) throw ScriptError.type("width debe ser un numero");
        double d = number.doubleValue();
        if (!(d >= MIN_WIDTH && d <= MAX_WIDTH)) {
            throw ScriptError.value("width debe estar entre " + MIN_WIDTH + " y " + MAX_WIDTH);
        }
        return (float) d;
    }

    private static String group(Object value) {
        if (!(value instanceof String text)) throw ScriptError.type("group debe ser texto");
        if (text.length() > MAX_GROUP_CHARS) throw ScriptError.value("group admite máximo " + MAX_GROUP_CHARS + " caracteres");
        return text;
    }

    private static boolean flag(Object value, String name) {
        if (!(value instanceof Boolean flag)) throw ScriptError.type(name + " debe ser True o False");
        return flag;
    }

    private static Object single(List<Object> args, String name) {
        if (args.size() != 1) throw ScriptError.type(name + "() espera un solo valor");
        return args.get(0);
    }

    private static void requireKeys(Map<String, Object> kwargs, Set<String> allowed, String function) {
        for (String key : kwargs.keySet()) {
            if (!allowed.contains(key)) throw ScriptError.type(function + "() no acepta el argumento '" + key + "'");
        }
    }

    private static String display(Object value) {
        if (value == null) return "None";
        if (value instanceof Boolean flag) return flag ? "True" : "False";
        return String.valueOf(value);
    }
}
