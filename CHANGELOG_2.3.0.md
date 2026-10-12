# MCODE 2.3.0

## Fase 6 — geometría 3D y ciclo de vida
- Añadidas `mc.point`, `mc.sphere`, `mc.cylinder`, `mc.cone` y `mc.mesh`.
- Añadidos `.rotate()`, `.scale()` y `.reset_transform()` para los handles de formas.
- Añadido render por frame con `mc.begin_frame()` y `mc.end_frame()` para reemplazar formas de forma atómica durante animaciones.
- Cada ejecución tiene su propia escena lógica. Cuando termina o se detiene, MCODE limpia sus formas y no borra las de otros scripts.
- `mc.clear()` ahora limpia la escena del script actual. `mc.clear(all=True)` limpia la escena global de forma explícita.
- Botón `×` en cada pestaña; una única pestaña no puede cerrarse. Si tiene cambios, se pregunta si se guardan o se descartan.
- Persistencia de `savedCode` para calcular el estado sucio de pestañas y conservar compatibilidad con sesiones antiguas.
- Documentado claramente que los dibujos de fase 5 están en el mundo 3D, no en el HUD de la GUI.

## Estado de validación
- Revisiones estáticas y pruebas aisladas de las clases geométricas/escena.
- Una compilación Fabric completa y la validación visual dentro de Minecraft siguen siendo necesarias.
- Añadido el ejemplo `07_3d_beacon_hunt.mcode`, mini juego 3D con colisión por distancia y récord guardado en el área de trabajo de MCODE.
- Optimizada la comprobación de distancia de render para reutilizar buffers y no asignar dos arrays por forma en cada frame.
