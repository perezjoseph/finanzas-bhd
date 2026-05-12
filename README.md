# Personal Finance Analyzer (PFA)

Aplicación de escritorio para análisis de finanzas personales. Importa estados de cuenta PDF del Banco BHD (cuentas de ahorro y tarjetas de crédito), categoriza gastos automáticamente, analiza tendencias y genera reportes financieros. Todos los datos se almacenan localmente.

## Pre-requisitos

| Herramienta | Versión mínima | Notas |
| --- | --- | --- |
| **Java JDK** | 21 | Se recomienda [Amazon Corretto 21](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html) |
| **Maven** | 3.9+ | O usar el wrapper incluido (`mvnw` / `mvnw.cmd`) |
| **JavaFX** | 21.0.5 | Se descarga automáticamente vía Maven |
| **Git** | 2.x | Para clonar el repositorio |

### Opcional (para OCR de PDFs escaneados)

| Herramienta | Versión | Notas |
| --- | --- | --- |
| **Tesseract OCR** | 5.x | Instalar y agregar al PATH. Descargar datos de idioma español (`spa.traineddata`) |

### Verificar instalación

```bash
java -version    # Debe mostrar 21 o superior
mvn -version     # Debe mostrar 3.9+ (o usar ./mvnw -version)
```

## Clonar el repositorio

```bash
git clone git@github.com:perezjoseph/finanzas-bhd.git
cd finanzas-bhd
```

## Compilar el proyecto

Desde la carpeta `pfa-parent`:

```bash
cd pfa-parent
mvnw.cmd clean install
```

O si tienes Maven instalado globalmente:

```bash
cd pfa-parent
mvn clean install
```

Esto compila todos los módulos, ejecuta las pruebas y genera los JARs.

### Compilar sin ejecutar pruebas

```bash
mvnw.cmd clean install -DskipTests
```

## Ejecutar la aplicación

### Opción 1: Con el plugin de JavaFX (recomendado para desarrollo)

```bash
cd pfa-parent/pfa-app
../mvnw.cmd javafx:run
```

### Opción 2: Fat JAR (JAR ejecutable con todas las dependencias)

Primero, generar el fat JAR:

```bash
cd pfa-parent
mvnw.cmd clean package -pl pfa-app -am -Pfat-jar -DskipTests
```

Luego ejecutar:

```bash
java -jar pfa-app/target/pfa-app-1.0.0-SNAPSHOT-fat.jar
```

### Opción 3: Generar instalador Windows (MSI)

Requiere [WiX Toolset](https://wixtoolset.org/) instalado.

```bash
cd pfa-parent
mvnw.cmd clean package -pl pfa-app -am -Pinstaller -DskipTests
```

El instalador se genera en `pfa-app/target/installer/`.

## Estructura del proyecto

```text
pfa-parent/
├── pfa-core/            # Modelo de dominio, interfaces y tipos compartidos
├── pfa-import/          # Parseo de PDFs y CSVs (BHD savings, credit card)
├── pfa-categorization/  # Motor de categorización automática
├── pfa-analytics/       # Análisis de tendencias y reportes
├── pfa-persistence/     # Almacenamiento local (SQLite, cifrado, sesiones)
├── pfa-gmail/           # Integración con Gmail para descargar estados de cuenta
├── pfa-export/          # Exportación a CSV y Excel
├── pfa-ui/              # Interfaz gráfica (JavaFX)
└── pfa-app/             # Punto de entrada, wiring de servicios
```

## Ejecutar pruebas

```bash
cd pfa-parent
mvnw.cmd test
```

Para ejecutar pruebas de un módulo específico:

```bash
mvnw.cmd test -pl pfa-core
mvnw.cmd test -pl pfa-import
```

## Tecnologías principales

- **Java 21** — Lenguaje y plataforma
- **JavaFX 21** — Interfaz gráfica de escritorio
- **Apache PDFBox** — Extracción de texto de PDFs
- **Tess4J** — OCR para PDFs escaneados
- **SQLite** — Base de datos local
- **Gson** — Serialización JSON
- **Apache POI** — Exportación a Excel
- **BouncyCastle** — Cifrado (Argon2id, AES-GCM)
