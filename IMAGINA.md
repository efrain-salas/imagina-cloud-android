# Imagina Cloud

Versión de la [app de Nextcloud para Android](https://github.com/nextcloud/android) con la marca de Imagina, fija a la nube de Imagina y con un único acceso: **«Entrar con Imagina»** (móvil y código SMS en una pestaña del navegador que se cierra sola). Forma parte del Cloud de Imagina: `docs/PLAN_CLOUD.md` en el repositorio de Imagina (hito C11.1; prototipo en C0.11).

- **Versión de Nextcloud**: `stable-35.0.1`, el remoto `upstream`.
- **Licencia**: GPL v2 (la de la app de Nextcloud). El código de esta versión tiene que estar publicado (`sourcecode_url` en `setup.xml`).
- **Marca**: la app no puede llevar «Nextcloud» en el nombre ni su logo, y dice en «Acerca de» y en su ficha que no es la oficial ([directrices de marca de Nextcloud](https://nextcloud.com/trademarks/)). Por eso se sustituyen todas sus imágenes de marca, incluido el icono, que está bajo su licencia de marca.

## Principio: todo cambio, aparte y parametrizado

La app de Nextcloud ya está pensada para versiones con otra marca: cada versión es un «sabor» (`productFlavor`) y casi toda la marca sale de recursos (`setup.xml`). Imagina Cloud es el sabor `imagina`:

- Lo de Imagina vive en ficheros que Nextcloud no tiene (`imagina/`, `app/src/imagina/`, `app/src/main/java/build/imagina/`, `app/src/main/res/values/imagina.xml`), así que actualizar Nextcloud no choca con ellos.
- Los ficheros de Nextcloud tocados son tres, con pocas líneas, en la tabla de abajo.
- Lo que cambia entre entornos va en `imagina/imagina.properties`; las imágenes y los textos con «Nextcloud» se generan con scripts, no se editan a mano.

## Compilar

Requisitos: Java 21 (vale el de Android Studio), el SDK de Android con la plataforma 37, CMake 4.1.2 y el NDK 29 (`ndk.env`).

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleImaginaDebug
```

La APK queda en `app/build/outputs/apk/imagina/debug/`.

## Parámetros

`imagina/imagina.properties` (en git, sin secretos), leído por `imagina/flavor.gradle`:

| Clave | Para qué |
| --- | --- |
| `IMAGINA_APPLICATION_ID` | Id de la app (`build.imagina.cloud`). El OAuth vuelve a `<id>://oauth2redirect`. |
| `IMAGINA_CLOUD_URL` | La nube a la que la app está fija (hoy la de pruebas, `https://cloud-test.imagina.build`). |
| `IMAGINA_PUSH_SERVER_URL` | Servidor de notificaciones de Imagina (C7.10). Vacío: sin notificaciones todavía. |
| `IMAGINA_AUTH_HOST` | Servidor de identidad de Imagina. Vacío: la app usa el acceso de Nextcloud (Login Flow v2). |
| `IMAGINA_OAUTH_CLIENT_ID` | Cliente OAuth público de la app en Imagina (`php artisan passport:client --public`). |
| `IMAGINA_VERSION_NAME_SUFFIX` | Sufijo de versión (`35.0.1-imagina`). |

`imagina/imagina.local.properties` (fuera de git) sobrescribe o añade claves en cada máquina. Hoy lleva el usuario y la contraseña de aplicación de Nextcloud de prueba (`IMAGINA_TEST_NC_USER`, `IMAGINA_TEST_NC_APP_PASSWORD`) que la app recibe hasta que exista la API de dispositivos de Imagina (C7.1).

La marca fija (nombre, identificadores únicos para convivir con la app oficial, colores, opciones) está en `app/src/imagina/res/values/setup.xml`, comentada.

## Cambios en ficheros de Nextcloud

| Fichero | Cambio | Motivo |
| --- | --- | --- |
| `app/build.gradle.kts` | Una línea al final: `apply(from = "../imagina/flavor.gradle")` | Cargar el sabor `imagina` sin tocar el resto de la compilación. |
| `app/src/main/java/.../AuthenticatorActivity.java` | Dos enganches marcados «Imagina (IMAGINA.md)»: en `onCreate`, abrir «Entrar con Imagina» en lugar del acceso de Nextcloud; en `authTabResultLauncher`, recoger el resultado y entrar con las credenciales que da Imagina. Inactivos si `imagina_auth_host` está vacío. | El acceso de Nextcloud abre una pestaña que no vuelve sola a la app (Android 16 corta la red a la app en segundo plano y Nextcloud solo comprueba al volver), muestra sus propias páginas y pide autorizar dos veces. |
| `.gitignore` | `imagina/imagina.local.properties` | Que las credenciales de prueba no entren en git. |

## Lo propio de Imagina

| Dónde | Qué |
| --- | --- |
| `imagina/flavor.gradle` | El sabor `imagina`: id, sufijo de versión, valores por entorno (`resValue`) y el código de variante de la versión F-Droid (`src/generic/java`), sin servicios de Google hasta las notificaciones (C11.5). |
| `app/src/imagina/res/values/setup.xml` | Marca y opciones: nombre, identificadores, una sola cuenta y un solo servidor, sin introducción, sin calendario ni contactos, sin enlaces de Nextcloud, colores de Imagina. |
| `app/src/main/java/build/imagina/cloud/ImaginaSignIn.kt` | «Entrar con Imagina»: OAuth con PKCE contra Imagina en una Auth Tab que vuelve por `<id>://oauth2redirect`, y las credenciales de Nextcloud del dispositivo. |
| `app/src/main/res/values/imagina.xml` | Valores por defecto (vacíos) de los recursos que usa `ImaginaSignIn`, para que las demás versiones compilen igual. |
| `app/src/imagina/res/drawable/`, `mipmap-anydpi/` | Imágenes de marca generadas (ver abajo). |
| `app/src/imagina/res/values*/strings_rebrand.xml` | Textos con «Nextcloud» en todos los idiomas, generados (ver abajo). |
| `app/src/imagina/res/values-es/strings_imagina.xml` | Textos propios en español. |

## Imágenes de marca

La fuente es `imagina/brand/imagina-cloud-mark.svg`: la loseta tinta de Imagina con una nube y el punto teal. Se generan, no se editan:

```sh
imagina/generate-resources.sh
```

Genera el icono adaptativo (fondo, primer plano y capa monocroma), la pantalla de carga (`nextcloud_splash_logo`), el logo de la primera pantalla (`logo`), el de la cabecera del menú (`nextcloud_logo`, solo la nube en marfil, como pide el manual de marca sobre teal) y el icono de notificación, con `imagina/tools/svg_to_vector.py`.

## Textos

```sh
python3 -I imagina/tools/rebrand_strings.py "Imagina Cloud"
```

Copia de `app/src/main/res/values*/strings.xml` los textos que dicen «Nextcloud» y los escribe con «Imagina Cloud» en `app/src/imagina/res/values*/strings_rebrand.xml`, en todos los idiomas. Solo cambia la palabra con mayúscula, así que los enlaces a nextcloud.com siguen igual. Avisa de los textos de varias líneas que no cambia (hoy, solo la descripción de la tienda en algunos idiomas, que la app no muestra). El nombre que se ve en el buscador («Buscar en …») viene del tema del servidor (`occ theming:config name`).

## Actualizar a una nueva versión de Nextcloud

1. `git fetch upstream --tags` y `git merge stable-<nueva>` sobre `main`.
2. Los conflictos solo pueden venir de los tres ficheros de la tabla. En `AuthenticatorActivity.java`, conservar los dos enganches marcados.
3. Revisar las claves nuevas o renombradas de la marca: `git diff stable-<anterior> stable-<nueva> -- app/src/main/res/values/setup.xml` y llevar a `app/src/imagina/res/values/setup.xml` lo que haga falta (sobre todo identificadores y autoridades nuevos).
4. Revisar imágenes de marca nuevas: `git diff --stat stable-<anterior> stable-<nueva> -- app/src/main/res/drawable app/src/main/res/mipmap-anydpi`; si aparece una con el logo de Nextcloud, añadirla a `imagina/generate-resources.sh`.
5. Comprobar que `src/generic/java` sigue teniendo las clases de variante (`VariantComponentsModule`, `PushUtils`…).
6. Volver a generar los textos (`rebrand_strings.py`) y las imágenes, compilar y probar en el emulador: «Entrar con Imagina» (la pestaña se cierra sola), lista de ficheros, subir un fichero, y que no se vea «Nextcloud» en ninguna pantalla.

## Pendiente para la app de verdad

- Credenciales desde la API de dispositivos de Imagina (C7.1) en lugar de las de prueba; sin pantalla de autorización de Imagina (C3.1).
- Notificaciones (C11.5): código de variante de Google Play (`src/gplay/java`) con el proyecto de Firebase de Imagina y su servidor de notificaciones (`IMAGINA_PUSH_SERVER_URL`).
- Si la Auth Tab no está disponible, el navegador vuelve a la app por `<id>://oauth2redirect`: falta el `intent-filter` para ese caso.
- Firma de publicación, ficha de Google Play y aviso de «no es la app oficial» en «Acerca de».
