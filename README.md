````writing
:::writing{variant="document" id="58321" title="MCODE — README"}
# MCODE

MCODE es un lenguaje de programación integrado en Minecraft. Su sintaxis está inspirada principalmente en Python y está diseñada para ser simple de escribir.

## Variables

Las variables se crean usando `=`.

```mcode
nombre = "Fernando"
edad = 18
altura = 1.71
activo = True
````

No es necesario declarar el tipo de variable.

## Tipos de datos

### Enteros

```mcode
edad = 18
vidas = 3
x = 100
```

### Decimales

```mcode
velocidad = 2.5
altura = 1.71
pi = 3.14159
```

### Booleanos

```mcode
activo = True
muerto = False
```

### Strings

Se pueden utilizar comillas simples o dobles.

```mcode
nombre = "Fernando"
mensaje = 'Hola'
```

Los strings admiten escapes:

```mcode
mensaje = "Hola\nMundo"
tab = "A\tB"
```

### None

Representa la ausencia de un valor.

```mcode
resultado = None
```

## Operadores matemáticos

### Suma

```mcode
resultado = 10 + 5
```

### Resta

```mcode
resultado = 10 - 5
```

### Multiplicación

```mcode
resultado = 10 * 5
```

### División

```mcode
resultado = 10 / 5
```

### División entera

```mcode
resultado = 10 // 3
```

### Módulo

```mcode
resultado = 10 % 3
```

### Potencia

```mcode
resultado = 2 ** 4
```

### Operadores en expresiones

```mcode
x = 10
y = 20

resultado = x + y * 2
```

Se pueden utilizar paréntesis para cambiar el orden:

```mcode
resultado = (10 + 5) * 2
```

## Comparaciones

```mcode
a = 10 == 10
b = 10 != 5
c = 10 < 20
d = 10 > 5
e = 10 <= 10
f = 10 >= 10
```

Las comparaciones producen `True` o `False`.

## Operadores lógicos

### `and`

```mcode
resultado = True and False
```

### `or`

```mcode
resultado = True or False
```

### `not`

```mcode
resultado = not True
```

También pueden combinarse:

```mcode
edad = 18
tiene_permiso = True

resultado = edad >= 18 and tiene_permiso
```

## Listas

Las listas utilizan `[]`.

```mcode
numeros = [10, 20, 30, 40]
```

Se puede acceder a un elemento mediante su índice:

```mcode
numeros = [10, 20, 30, 40]

primero = numeros[0]
segundo = numeros[1]
```

Los índices negativos empiezan desde el final:

```mcode
ultimo = numeros[-1]
```

Los elementos pueden modificarse:

```mcode
numeros = [10, 20, 30]

numeros[1] = 999
```

Las listas también pueden contener diferentes tipos:

```mcode
datos = [
    "Fernando",
    18,
    True,
    3.14
]
```

## Tuplas

Las tuplas utilizan `()`.

```mcode
posicion = (100, 64, 200)
```

Se puede acceder a sus elementos:

```mcode
x = posicion[0]
y = posicion[1]
z = posicion[2]
```

También pueden contener diferentes tipos:

```mcode
datos = ("Fernando", 18, True)
```

## Sets

Los conjuntos utilizan `{}` cuando contienen solamente valores separados por comas.

```mcode
numeros = {1, 2, 3, 4}
```

## Diccionarios

Los diccionarios utilizan pares `clave: valor`.

```mcode
jugador = {
    "nombre": "Fernando",
    "nivel": 10
}
```

Se accede a los valores mediante su clave:

```mcode
nombre = jugador["nombre"]
nivel = jugador["nivel"]
```

También pueden contener diferentes tipos:

```mcode
jugador = {
    "nombre": "Fernando",
    "nivel": 10,
    "activo": True
}
```

Los valores pueden modificarse:

```mcode
jugador["nivel"] = 11
```

## Función `print`

`print()` muestra valores en la salida del programa.

```mcode
print("Hola")
```

También puede mostrar variables:

```mcode
nombre = "Fernando"
print(nombre)
```

Y expresiones:

```mcode
x = 10
y = 20

print(x + y)
```

Se pueden imprimir listas, tuplas y diccionarios:

```mcode
numeros = [1, 2, 3]
print(numeros)
```

## Función `len`

Devuelve el tamaño de un valor compatible.

```mcode
nombre = "Fernando"

longitud = len(nombre)

print(longitud)
```

También puede utilizarse con listas:

```mcode
numeros = [10, 20, 30, 40]

cantidad = len(numeros)

print(cantidad)
```

## Función `type`

Devuelve el tipo de un valor.

```mcode
numero = 10
print(type(numero))
```

Ejemplos:

```mcode
print(type(10))
print(type(3.14))
print(type("Hola"))
print(type(True))
print(type(None))
```

## Conversión de tipos

### `int`

Convierte un valor a entero.

```mcode
numero = int("10")
```

### `float`

Convierte un valor a decimal.

```mcode
numero = float("3.14")
```

### `str`

Convierte un valor a texto.

```mcode
numero = 123
texto = str(numero)
```

### `bool`

Convierte un valor a booleano.

```mcode
valor = bool(1)
```

### `list`

Convierte un valor compatible en una lista.

```mcode
valores = list(...)
```

### `tuple`

Convierte un valor compatible en una tupla.

```mcode
valores = tuple(...)
```

### `set`

Convierte un valor compatible en un conjunto.

```mcode
valores = set(...)
```

## Funciones matemáticas

### `abs`

Devuelve el valor absoluto.

```mcode
numero = abs(-20)
```

### `round`

Redondea un número.

```mcode
numero = round(3.14)
```

### `min`

Devuelve el valor menor.

```mcode
numero = min(10, 5, 20)
```

### `max`

Devuelve el valor mayor.

```mcode
numero = max(10, 5, 20)
```

## Índices

Los strings, listas y tuplas pueden utilizar índices.

```mcode
texto = "Hola"

print(texto[0])
print(texto[-1])
```

```mcode
numeros = [10, 20, 30]

print(numeros[0])
print(numeros[-1])
```

Los elementos de una lista pueden modificarse mediante su índice:

```mcode
numeros = [10, 20, 30]

numeros[0] = 100
```

## Comentarios

Los comentarios comienzan con `#`.

```mcode
# Este es un comentario

x = 10 # También puede estar después del código
```

Todo lo que aparece después de `#` en una línea se considera comentario.

## Strings y operaciones

Los strings pueden concatenarse:

```mcode
nombre = "Fernando"
mensaje = "Hola " + nombre
```

También pueden repetirse:

```mcode
texto = "Hola " * 3
```

## Listas y operaciones

Las listas pueden concatenarse:

```mcode
a = [1, 2]
b = [3, 4]

resultado = a + b
```

También pueden repetirse:

```mcode
resultado = [1, 2] * 3
```

## Variables dentro de expresiones

Las variables pueden utilizarse entre sí:

```mcode
a = 10
b = 20
c = a + b
d = c * 2
```

El valor se calcula al ejecutar la expresión.

## Ejemplo completo

```mcode
# Datos del jugador

nombre = "Fernando"
nivel = 10
vida = 100
velocidad = 2.5
activo = True

posicion = [100, 64, 200]

print("Jugador:")
print(nombre)

print("Nivel:")
print(nivel)

print("Vida:")
print(vida)

print("Posición:")
print(posicion)

distancia = velocidad * 10

print("Distancia:")
print(distancia)

puede_jugar = activo and vida > 0

print("Puede jugar:")
print(puede_jugar)
```

## Sintaxis básica

La estructura general de una instrucción es:

```mcode
variable = valor
```

Los valores pueden ser:

```mcode
10
3.14
"Hola"
True
False
None
[1, 2, 3]
(1, 2, 3)
{1, 2, 3}
{"nombre": "Fernando"}
```

Y pueden combinarse mediante expresiones:

```mcode
resultado = (10 + 20) * 2
```

MCODE utiliza una sintaxis inspirada en Python, por lo que los programas se escriben de forma directa y con poca sintaxis adicional.
