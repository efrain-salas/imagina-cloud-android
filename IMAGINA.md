# Imagina Cloud

Versión de la [app de Nextcloud para Android](https://github.com/nextcloud/android) con la marca de Imagina, fija a la nube de Imagina y con un único acceso: **«Entrar con Imagina»** (móvil y código SMS en una pestaña del navegador que se cierra sola). Forma parte del Cloud de Imagina: `docs/PLAN_CLOUD.md` en el repositorio de Imagina (hito C11.1; prototipo en C0.11).

- **Versión de Nextcloud**: `stable-35.0.1`, el remoto `upstream`.
- **Licencia**: GPL v2 (la de la app de Nextcloud). El código de esta versión tiene que estar publicado (`sourcecode_url` en `setup.xml`).
- **Marca**: la app no puede llevar «Nextcloud» en el nombre ni su logo, y dice en «Acerca de» y en su ficha que no es la oficial ([directrices de marca de Nextcloud](https://nextcloud.com/trademarks/)). Por eso se sustituyen todas sus imágenes de marca, incluido el icono, que está bajo su licencia de marca.

## Principio: todo cambio, aparte y parametrizado

La app de Nextcloud ya está pensada para versiones con otra marca: cada versión es un «sabor» (`productFlavor`) y casi toda la marca sale de recursos (`setup.xml`). Imagina Cloud es el sabor `imagina`:

- Lo de Imagina vive en ficheros que Nextcloud no tiene (`imagina/`, `app/src/imagina/`, `app/src/main/java/build/imagina/`, `app/src/main/res/values/imagina.xml`), así que actualizar Nextcloud no choca con ellos.
- Los ficheros de Nextcloud tocados son pocos, con una o dos líneas marcadas «Imagina (IMAGINA.md)» cada uno, en la tabla de abajo (`grep -rn "Imagina (IMAGINA.md)" app/src` los lista).
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
| `NEXTCLOUD_ANDROID_TAG` | Versión de Nextcloud de la que parte (`stable-35.0.1`); la compilación automática avisa cuando hay una más nueva. |
| `IMAGINA_APPLICATION_ID` | Id de la app (`build.imagina.cloud`). El OAuth vuelve a `<id>://oauth2redirect`. |
| `IMAGINA_CLOUD_URL` | La nube a la que la app está fija (`https://cloud.imagina.build`). Con «Entrar con Imagina» la dirección la da la API de dispositivos; esta solo se usa con el acceso de Nextcloud. |
| `IMAGINA_PUSH_SERVER_URL` | Servidor de notificaciones de Imagina (C7.10, `https://auth.imagina.build/api/push`). Solo se usa si está el `google-services.json` de Imagina (ver «Notificaciones»). |
| `IMAGINA_AUTH_HOST` | Servidor de identidad de Imagina. Vacío: la app usa el acceso de Nextcloud (Login Flow v2). |
| `IMAGINA_OAUTH_CLIENT_ID` | Cliente OAuth público de la app en Imagina (`php artisan passport:client --public`). |
| `IMAGINA_VERSION_NAME_SUFFIX` | Sufijo de versión (`35.0.1-imagina`). |

`imagina/imagina.local.properties` (fuera de git) sobrescribe o añade claves en cada máquina; no hace falta para compilar.

La marca fija (nombre, identificadores únicos para convivir con la app oficial, colores, opciones) está en `app/src/imagina/res/values/setup.xml`, comentada.

## Cambios en ficheros de Nextcloud

| Fichero | Cambio | Motivo |
| --- | --- | --- |
| `app/build.gradle.kts` | Una línea al final: `apply(from = "../imagina/flavor.gradle")` | Cargar el sabor `imagina` sin tocar el resto de la compilación. |
| `app/src/main/java/.../AuthenticatorActivity.java` | Tres enganches marcados «Imagina (IMAGINA.md)»: en `onCreate`, abrir «Entrar con Imagina» en lugar del acceso de Nextcloud; en `authTabResultLauncher`, recoger el resultado y entrar con las credenciales que da Imagina; en `onNewIntent`, recoger la respuesta de Imagina cuando el navegador no tiene Auth Tab y abrió una Custom Tab. Inactivos si `imagina_auth_host` está vacío. Sirven también para volver a entrar cuando la contraseña de aplicación deja de valer (Nextcloud abre esta pantalla para actualizar la cuenta). | El acceso de Nextcloud abre una pestaña que no vuelve sola a la app (Android 16 corta la red a la app en segundo plano y Nextcloud solo comprueba al volver), muestra sus propias páginas y pide autorizar dos veces. |
| `app/src/main/java/.../jobs/AccountRemovalWork.kt` | Un enganche marcado (y su `import`) al final de `doWork`: `ImaginaDevice.accountRemoved(context, user.accountName)`. | Al quitar la cuenta en la app (también el borrado remoto), desconectar el dispositivo en Imagina; antes quedaba conectado en «Mi cuenta». |
| `app/src/main/java/.../jobs/NotificationWork.kt` (2 sitios), `jobs/MediaFoldersDetectionWork.kt`, `org/nextcloud/providers/cursors/RootCursor.java` | `setSubText(user.accountName)` y `COLUMN_SUMMARY` pasan a `ImaginaAccount.label(user)`. | Las notificaciones y la raíz de la app de Ficheros de Android decían «1@cloud.imagina.build»; ahora, el nombre de la persona o nada. |
| `app/src/main/java/.../ui/ChooseAccountDialogFragment.kt`, `ui/adapter/UserListAdapter.java`, `ui/dialog/AccountRemovalDialog.kt`, `ui/activity/UserInfoActivity.java` | La línea «usuario@servidor» bajo el nombre pasa por `ImaginaAccount.setAccountLine(vista, texto)`, que en Imagina la oculta. | Esas pantallas ya muestran el nombre de la persona encima; repetirlo o enseñar «1@cloud.imagina.build» no ayuda. |
| `.gitignore` | `imagina/imagina.local.properties` y una excepción para `app/src/main/java/build/` | Que los valores locales no entren en git, y que sí entre el código de Imagina (paquete `build.imagina`), que la regla `build` de Nextcloud dejaría fuera. |

## Lo propio de Imagina

| Dónde | Qué |
| --- | --- |
| `imagina/flavor.gradle` | El sabor `imagina`: id, sufijo de versión, valores por entorno (`resValue`), el código de variante de la versión de Google Play de Nextcloud (`src/gplay/java`, notificaciones por Firebase) con sus dependencias, y los valores de Firebase leídos del `google-services.json` de Imagina. |
| `app/src/imagina/AndroidManifest.xml` | Lo que el sabor añade al manifiesto: el servicio de Firebase y sus ajustes sin analítica (copiados del manifiesto de `gplay`) y la vuelta de «Entrar con Imagina» por `<id>://oauth2redirect` sin Auth Tab. |
| `app/src/imagina/res/values/setup.xml` | Marca y opciones: nombre, identificadores, una sola cuenta y un solo servidor, sin introducción, sin calendario ni contactos, sin enlaces de Nextcloud, colores de Imagina. |
| `app/src/main/java/build/imagina/cloud/ImaginaSignIn.kt` | «Entrar con Imagina»: OAuth con PKCE contra Imagina (alcance `cloud.devices`) en una Auth Tab que vuelve por `<id>://oauth2redirect`, y la API de dispositivos de Imagina (`POST /api/v1/cloud/devices`), que da la dirección de la nube y una contraseña de aplicación propia de este móvil con el nombre que la persona le puso. Guarda el dispositivo y los tokens con `ImaginaDevice` y desconecta antes el de un acceso anterior. Si la persona cancela o Imagina rechaza el acceso (sin plaza en un Cloud, Cloud sin preparar…), un diálogo lo dice con el texto de Imagina y ofrece volver a intentarlo o salir. |
| `app/src/main/java/build/imagina/cloud/ImaginaDevice.kt` | El dispositivo en Imagina (ver «El dispositivo en Imagina»): guarda su id y los tokens OAuth, lo desconecta (`DELETE /api/v1/cloud/devices/{id}`) al quitar la cuenta o al volver a entrar, y `ImaginaDeviceWork`, el trabajo periódico que renueva los tokens y recoge los dispositivos de cuentas ya quitadas. |
| `app/src/main/java/build/imagina/cloud/ImaginaApi.kt` | Las peticiones HTTPS a Imagina (identidad y API de dispositivos) y los tokens (`ImaginaTokens`), compartidos por el acceso y el dispositivo. |
| `app/src/main/java/build/imagina/cloud/ImaginaAccount.kt` | Cómo se ve una cuenta: el nombre de la persona (o nada) donde Nextcloud muestra «usuario@servidor» (ver «Nombre de la cuenta»). |
| `app/src/main/res/values/imagina.xml` | Valores por defecto (vacíos) de los recursos que usa `ImaginaSignIn`, para que las demás versiones compilen igual. |
| `app/src/imagina/res/drawable/`, `mipmap-anydpi/` | Imágenes de marca generadas (ver abajo). |
| `app/src/imagina/res/values*/strings_rebrand.xml` | Textos con «Nextcloud» en todos los idiomas, generados (ver abajo). |
| `app/src/main/res/values/imagina.xml`, `app/src/imagina/res/values-{es,de,fr,it,pt-rPT}/strings_imagina.xml` | Textos propios: en inglés por defecto y en los idiomas de Imagina. |

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

Copia de `app/src/main/res/values*/strings.xml` los textos que dicen «Nextcloud» y los escribe con «Imagina Cloud» en `app/src/imagina/res/values*/strings_rebrand.xml`, en todos los idiomas. Además, la versión que se ve en Ajustes › Acerca de añade que es una versión modificada de la app de Nextcloud y no la oficial (en los idiomas de Imagina y en inglés en los demás). Solo cambia la palabra con mayúscula, así que los enlaces a nextcloud.com siguen igual. Avisa de los textos de varias líneas que no cambia (hoy, solo la descripción de la tienda en algunos idiomas, que la app no muestra). El nombre que se ve en el buscador («Buscar en …») viene del tema del servidor (`occ theming:config name`).

## Actualizar a una nueva versión de Nextcloud

1. `git fetch upstream --tags` y `git merge stable-<nueva>` sobre `main`.
2. Cambiar `NEXTCLOUD_ANDROID_TAG` en `imagina/imagina.properties`. Los conflictos solo pueden venir de los ficheros de la tabla. En `AuthenticatorActivity.java`, conservar los tres enganches marcados; en los demás, los marcados «Imagina (IMAGINA.md)» (`grep -rn "Imagina (IMAGINA.md)" app/src`).
3. Revisar las claves nuevas o renombradas de la marca: `git diff stable-<anterior> stable-<nueva> -- app/src/main/res/values/setup.xml` y llevar a `app/src/imagina/res/values/setup.xml` lo que haga falta (sobre todo identificadores y autoridades nuevos).
4. Revisar imágenes de marca nuevas: `git diff --stat stable-<anterior> stable-<nueva> -- app/src/main/res/drawable app/src/main/res/mipmap-anydpi`; si aparece una con el logo de Nextcloud, añadirla a `imagina/generate-resources.sh`.
5. Comprobar que `src/gplay/java` sigue teniendo las clases de variante (`VariantComponentsModule`, `PushUtils`…) y llevar a `app/src/imagina/AndroidManifest.xml` lo que cambie en `app/src/gplay/AndroidManifest.xml` (servicio de Firebase, ajustes).
6. Volver a generar los textos (`rebrand_strings.py`) y las imágenes, compilar y probar en el emulador: «Entrar con Imagina» (la pestaña se cierra sola), lista de ficheros, subir un fichero, y que no se vea «Nextcloud» en ninguna pantalla.

## Compilación automática

`.github/workflows/imagina.yml` (GitHub Actions del repositorio público `efrain-salas/imagina-cloud-android`): compila la APK de depuración en cada subida a `main` (queda como artefacto 14 días) y cada lunes abre una incidencia si Nextcloud ha publicado una versión posterior a `NEXTCLOUD_ANDROID_TAG`. El `google-services.json` llega del secreto `GOOGLE_SERVICES_JSON` del repositorio. Los flujos de Nextcloud que trae el fork están desactivados en GitHub (`gh workflow disable`), porque son para su repositorio. La firma de publicación llegará con la cuenta de Google Play (C11.6).

## El dispositivo en Imagina

«Entrar con Imagina» crea un dispositivo en Imagina («Mi cuenta», `POST /api/v1/cloud/devices`). Para poder desconectarlo después, `ImaginaDevice` guarda, por cada nombre de usuario de Nextcloud, el id del dispositivo y los tokens OAuth del acceso (el de acceso dura 15 minutos; el de renovación, 7 días y se cambia en cada renovación) en unas preferencias privadas de la app (`imagina_device`, sin cifrar: la app no usa `androidx.security` y no permite copias de seguridad, así que no salen del móvil; Nextcloud guarda igual la contraseña de aplicación en el `AccountManager`).

- **Quitar la cuenta en la app**: `AccountRemovalWork` (el mismo trabajo del borrado remoto) llama al final a `ImaginaDevice.accountRemoved`, que renueva el token si hace falta, borra el dispositivo en Imagina y olvida lo guardado. Es de mejor esfuerzo: si no hay conexión, lo recoge el trabajo periódico.
- **Volver a entrar**: antes de crear el dispositivo nuevo, se desconectan los guardados (con el token recién conseguido y, si Imagina no lo acepta, con los suyos).
- **Trabajo periódico** (`ImaginaDeviceWork`, cada 2 días con red; empieza al entrar): renueva los tokens mientras la cuenta existe, para que el de renovación no caduque a los 7 días, y desconecta el dispositivo de una cuenta que ya no está (quitada desde los Ajustes de Android, por ejemplo) pasados 10 minutos de crearse. Se cancela solo cuando no queda nada guardado.
- Si el token de renovación ya no vale, no se puede borrar el dispositivo desde la app: se olvida y la persona lo desconecta en «Mi cuenta».

## Nombre de la cuenta

Los usuarios de Nextcloud de Imagina son números (el id de usuario de Imagina), así que el nombre de la cuenta de Nextcloud, «1@cloud.imagina.build», no le dice nada a la persona. El nombre de la cuenta **no cambia** (Nextcloud lo usa de clave de todo); `ImaginaAccount` hace que, en la versión de Imagina, las pantallas enseñen el nombre que Nextcloud guardó al crear la cuenta (`UserAccountManager.getDisplayName`), o nada si no hay:

- Con `ImaginaAccount.label(user)`: el subtítulo de las notificaciones de Nextcloud (push y detección de carpetas multimedia) y el resumen de la raíz en la app de Ficheros de Android.
- Con `ImaginaAccount.setAccountLine(vista, texto)`: la línea bajo el nombre en el selector de cuenta, la lista de cuentas, el diálogo de quitar la cuenta y el perfil. Se oculta, porque el nombre ya está encima.

Con otra versión (`imagina_auth_host` vacío) todo sigue como en Nextcloud. Sin enganche, y por tanto sin cambiar, quedan los sitios que solo se ven con varias cuentas (lista de subidas, widget del panel), el diálogo de inicio de sesión único para otras apps de Nextcloud (`SsoGrantPermissionActivity`), un aviso poco frecuente de `ErrorsWhileCopyingHandlerActivity`, y la pantalla de cuentas de Android (Ajustes › Contraseñas y cuentas), que enseña el nombre de la cuenta tal cual.

## Notificaciones

Las notificaciones llegan por Firebase Cloud Messaging a través del servidor de notificaciones de Imagina (C7.10), no del de Nextcloud. La app usa el código de la versión de Google Play de Nextcloud (`src/gplay/java`), que registra el móvil en ese servidor al entrar. El proyecto de Firebase es `imagina-84bd9` y su `google-services.json` (app `build.imagina.cloud`) va en `app/src/imagina/`, fuera de git: se descarga de la consola de Firebase (Configuración del proyecto › Tus apps). Como en Nextcloud, sus valores pasan a recursos sin el plugin de Google Services (`imagina/flavor.gradle`). Sin el fichero la app compila igual, sin notificaciones.

## Pendiente para la app de verdad

- Probar las notificaciones con un móvil real (un fichero compartido y una mención).
- Probar en el emulador, contra producción, el dispositivo y el nombre de la cuenta: entrar, quitar la cuenta en la app y ver que el dispositivo desaparece de «Mi cuenta»; entrar dos veces y ver que no queda el viejo; ver el nombre (no «1@cloud.imagina.build») en una notificación, el selector de cuenta, la lista de cuentas y la app de Ficheros.
- Los dispositivos creados con versiones anteriores a esta no tienen el id guardado: quitar la cuenta de esa instalación no los desconecta (se desconectan en «Mi cuenta»). Tampoco una reinstalación, que borra lo guardado: para ese caso Imagina podría sustituir el dispositivo anterior del mismo nombre y plataforma.
- Firma de publicación y ficha de Google Play (con el aviso de que no es la app oficial).
