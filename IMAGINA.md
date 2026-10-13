# Imagina Cloud

Versión de la [app de Nextcloud para Android](https://github.com/nextcloud/android) con la marca de Imagina, fija a la nube de Imagina y con un único acceso: **«Entrar con Imagina»** (móvil y código SMS en una pestaña del navegador que se cierra sola). Forma parte del Cloud de Imagina: `docs/PLAN_CLOUD.md` en el repositorio de Imagina (hito C11.1; prototipo en C0.11).

- **Versión de Nextcloud**: `stable-35.0.1`, el remoto `upstream`.
- **Licencia**: GPL v2 (la de la app de Nextcloud). El código de esta versión tiene que estar publicado (`sourcecode_url` en `setup.xml`).
- **Marca**: la app no puede llevar «Nextcloud» en el nombre ni su logo, y dice en «Acerca de» y en su ficha que no es la oficial ([directrices de marca de Nextcloud](https://nextcloud.com/trademarks/)). Por eso se sustituyen todas sus imágenes de marca, incluido el icono, que está bajo su licencia de marca.

## Principio: todo cambio, aparte y parametrizado

La app de Nextcloud ya está pensada para versiones con otra marca: cada versión es un «sabor» (`productFlavor`) y casi toda la marca sale de recursos (`setup.xml`). Imagina Cloud es el sabor `imagina`:

- Lo de Imagina vive en ficheros que Nextcloud no tiene (`imagina/`, `app/src/imagina/`, `app/src/main/java/build/imagina/`, `app/src/main/res/values/imagina.xml`), así que actualizar Nextcloud no choca con ellos.
- Los ficheros de Nextcloud tocados son pocos, con una o dos líneas marcadas «Imagina (IMAGINA.md)» cada uno, en la tabla de abajo (`grep -rn "Imagina (IMAGINA.md)" app/src/main` los lista).
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
| `IMAGINA_VERSION_CODE_OFFSET` | Se suma al `versionCode` de Nextcloud (0 a 99; con `35.0.1`, 350000190 + el valor). Google Play no acepta dos veces el mismo `versionCode`: subirlo uno por cada compilación que se suba sobre la misma `NEXTCLOUD_ANDROID_TAG`, y volver a 0 al actualizar Nextcloud (cada versión de Nextcloud suma 100 o más, así que el siguiente `versionCode` de Nextcloud siempre es mayor que cualquiera de este rango). |

`imagina/imagina.local.properties` (fuera de git) sobrescribe o añade claves en cada máquina; no hace falta para compilar.

La marca fija (nombre, identificadores únicos para convivir con la app oficial, esquema de los enlaces de acceso `imagina-cloud://` en lugar del `nc://` de Nextcloud, enlaces del botón «Informar» de la pantalla de fallo hacia el repositorio de Imagina en lugar del de Nextcloud, colores, opciones) está en `app/src/imagina/res/values/setup.xml`, comentada.

## Cambios en ficheros de Nextcloud

| Fichero | Cambio | Motivo |
| --- | --- | --- |
| `app/build.gradle.kts` | Una línea al final: `apply(from = "../imagina/flavor.gradle")` | Cargar el sabor `imagina` sin tocar el resto de la compilación. |
| `app/src/main/java/.../AuthenticatorActivity.java` | Tres enganches marcados «Imagina (IMAGINA.md)»: en `onCreate`, abrir «Entrar con Imagina» en lugar del acceso de Nextcloud (más una línea, `ImaginaSignIn.styleSystemBars(this)`, que deja las barras del sistema del color de la pantalla); en `authTabResultLauncher`, recoger el resultado y entrar con las credenciales que da Imagina; en `onNewIntent`, recoger la respuesta de Imagina cuando el navegador no tiene Auth Tab y abrió una Custom Tab. Inactivos si `imagina_auth_host` está vacío. Sirven también para volver a entrar cuando la contraseña de aplicación deja de valer (Nextcloud abre esta pantalla para actualizar la cuenta). | El acceso de Nextcloud abre una pestaña que no vuelve sola a la app (Android 16 corta la red a la app en segundo plano y Nextcloud solo comprueba al volver), muestra sus propias páginas y pide autorizar dos veces. Y al empezar pinta la barra de estado con el primario (teal), lo que deja sus iconos en blanco: sobre el fondo crema no se verían. |
| `app/src/main/java/.../jobs/AccountRemovalWork.kt` | Un enganche marcado (y su `import`) al final de `doWork`: `ImaginaDevice.accountRemoved(context, user.accountName)`. Y, marcado también, la baja de las notificaciones pasa a antes de quitar la cuenta, y después se borra su estado (`PushUtils.KEY_PUSH`). | Al quitar la cuenta en la app (también el borrado remoto), desconectar el dispositivo en Imagina; antes quedaba conectado en «Mi cuenta». La baja de Nextcloud solo recorre las cuentas que tiene el móvil: hecha después de quitarla no daba de baja nada y dejaba el estado «por borrar», con lo que la misma cuenta, al volver a entrar, nunca se registraba y no recibía notificaciones (visto en la prueba de C11.5). Fallo de Nextcloud, candidato a enviarse. |
| `app/src/main/java/.../jobs/NotificationWork.kt` (2 sitios), `jobs/MediaFoldersDetectionWork.kt`, `org/nextcloud/providers/cursors/RootCursor.java` | `setSubText(user.accountName)` y `COLUMN_SUMMARY` pasan a `ImaginaAccount.label(user)`. | Las notificaciones y la raíz de la app de Ficheros de Android decían «1@cloud.imagina.build»; ahora, el nombre de la persona o nada. |
| `app/src/main/java/.../ui/ChooseAccountDialogFragment.kt`, `ui/adapter/UserListAdapter.java`, `ui/dialog/AccountRemovalDialog.kt`, `ui/activity/UserInfoActivity.java` | La línea «usuario@servidor» bajo el nombre pasa por `ImaginaAccount.setAccountLine(vista, texto)`, que en Imagina la oculta. | Esas pantallas ya muestran el nombre de la persona encima; repetirlo o enseñar «1@cloud.imagina.build» no ayuda. |
| `app/src/main/java/.../utils/theme/MaterialSchemesProviderImpl.kt` | Dos enganches marcados (y su `import`), en `getMaterialSchemesForCapability` y `getDefaultMaterialSchemes`: `ImaginaColors.schemes(context, color) ?: …` antes de los esquemas de Nextcloud. Inactivos si `imagina_auth_host` está vacío. | Nextcloud saca todos los tonos de sus esquemas del color del servidor (el teal), así que lo que pinta con ellos (la barra de búsqueda, la barra inferior, las hojas) salía menta. Ver «Colores». |
| `app/src/main/java/.../ui/composeActivity/ComposeActivity.kt` | Una línea marcada (y su `import`) en el `MaterialTheme` de `setContent`: `typography = ImaginaTypography.typography(this)`. Con otra versión devuelve la tipografía de Material, así que no cambia nada. | Las pantallas de Compose (el asistente) no leen los temas de las vistas y salían con la fuente del sistema. Es el único envoltorio de tema de las pantallas de Compose de Nextcloud (ver «Tipografía»). |
| `app/src/main/java/.../ui/fragment/albums/AlbumItemsFragment.kt` | `FloatingActionButton(requireContext())` pasa a `ImaginaFloatingActionButton.create(requireContext())` (más su `import` y un comentario marcado). Con otra versión, el botón es el de Material. | Es el único botón flotante que Nextcloud crea en código; los de los layouts los pone el inflador de vistas del tema (ver «Botón flotante»). |
| `app/src/main/java/.../ui/fragment/UnifiedSearchFragment.kt`, `ui/activity/FileDisplayActivity.kt` (2 sitios), `ui/activity/SettingsActivity.java`, `ui/fragment/FileDetailSharingFragment.kt` | Una condición cada uno con el recurso `phone_contacts_calendar_enabled` (verdadero salvo en el sabor): la búsqueda no lee ni pide los contactos y el calendario del móvil (`supportsOpeningCalendarContactsLocally`), un `.vcf` no se ofrece como contactos para importar, «Copia de contactos» desaparece de Ajustes y el botón de elegir un contacto al compartir se oculta. | Google Play pide declarar los permisos de contactos y calendario, y Imagina Cloud no tiene ni uno ni otro (ver «Contactos y calendario»). |
| `app/src/main/java/.../ui/fileInfo/ImageDetailInfo.kt` | Una condición con `image_location_map_enabled` (verdadero salvo en el sabor): sin mapa ni nombre del lugar en los detalles de una foto. | El mapa pide teselas a tile.openstreetmap.org y el lugar a nominatim.openstreetmap.org, con las coordenadas de la foto y el correo de Nextcloud (ver «Mapa de las fotos»). |
| `app/src/main/java/.../ui/adapter/ShareViewHolder.java` | Una línea tras `colorImageViewBackgroundAndIcon`: `avatar.clearColorFilter()`. | `makeRoundedWithIcon` pone un filtro blanco en el avatar de una compartición sin nombre y gana al tinte del esquema: salía un icono blanco sobre menta. |
| `app/src/main/java/.../client/assistant/AssistantScreen.kt`, `translate/TranslationScreen.kt`, `conversation/ConversationScreen.kt` | El `import` de `FloatingActionButton` de Material pasa a ser el de `build.imagina.cloud` (una línea cada uno; las llamadas no cambian). | Los botones flotantes del asistente salían con el contenedor primario (menta); ver «Botón flotante». |
| `.gitignore` | `imagina/imagina.local.properties` y una excepción para `app/src/main/java/build/` | Que los valores locales no entren en git, y que sí entre el código de Imagina (paquete `build.imagina`), que la regla `build` de Nextcloud dejaría fuera. |

### Recursos de Nextcloud que el sabor sustituye

No se toca ningún fichero de Nextcloud, pero el sabor trae su propia **copia** de estos recursos: un recurso del sabor con el mismo nombre gana al de `main`. Por eso, al actualizar Nextcloud hay que compararlos con los de la versión nueva y llevar sus cambios (paso 7 de «Actualizar a una nueva versión de Nextcloud»). Cada fichero dice en un comentario de cuál es copia y qué cambia. Todos están en `app/src/imagina/res/`, salvo `network_security_config.xml`, que está en `app/src/imaginaRelease/res/` (solo la versión de publicación).

| Recurso | Qué cambia respecto al de Nextcloud |
| --- | --- |
| `layout/drawer_header.xml` | Cabecera del menú: crema (oscura en el tema oscuro) con el logotipo a la izquierda y una línea fina debajo, en lugar de teal con el icono. Sin el bloque de apps del ecosistema (en Imagina siempre está oculto, `is_branded_client`): queda su contenedor oculto con cuatro marcadores vacíos, para que `DrawerActivity` encuentre sus ids. |
| `layout/user_info_layout.xml` | La pantalla de perfil: el nombre y el avatar con texto tinta sobre crema, en lugar del banner teal (o la imagen de fondo de Nextcloud) con texto blanco. El banner (`userinfo_background`) queda en el diseño, oculto, para que `UserInfoActivity` lo encuentre. El resto es idéntico. |
| `layout/account_setup_webview.xml` | La primera pantalla (mientras «Entrar con Imagina» está abierto en el navegador, o al cancelar): el logotipo sobre el indicador de progreso, en crema. Mismos ids. |
| `layout/login_flow_info_layout_v2.xml` | El texto y el botón «Cancelar» de esa pantalla: tinta y botón con borde, en lugar de blanco sobre teal. |
| `layout/activity_splash.xml` | Pantalla de carga (la de `LauncherActivity`, justo después de la del sistema): crema con el logotipo al centro. |
| `layout/empty_list.xml` | Solo el titular de la lista vacía, en Fraunces. La fuente va dos veces, `android:fontFamily` y `app:fontFamily`: con la de los temas base (Instrument Sans) y solo la primera, el titular salía en Instrument Sans (medido en el emulador). |
| `values/styles_imagina.xml` | De `values/styles.xml`: `BaseTheme.ownCloud` y `FallbackThemingThemeBase` (más la tipografía, y en `BaseTheme.ownCloud` el inflador de vistas de los botones flotantes); `Theme.ownCloud.Launcher`, `Theme.App.Starting`, `Theme.ownCloud.noActionBar.LoginBase`, `…Login` y `Theme.ownCloud.Widget.ActionBar`, con fondo crema en lugar de teal. |
| `values/themes_imagina.xml`, `values-night/themes_imagina.xml` | De `values/themes.xml` y `values-night/themes.xml`: `BaseTheme.ownCloud.Toolbar`, el tema de casi todas las pantallas, más la tipografía y el inflador de vistas de los botones flotantes (`viewInflaterClass`). |
| `values/colors_imagina.xml`, `values-night/colors_imagina.xml` | No son copias: dan nuevo valor, por nombre, a los colores de Nextcloud (ver «Colores»). |
| `values/colors_material.xml`, `values/typography_imagina.xml` | Dan nuevo valor a `m3_sys_color_*` y `m3_ref_typeface_*`, de la librería Material (ver «Colores» y «Tipografía»). |
| `values/brand_images.xml` | `logo`, `nextcloud_splash_logo` y `nextcloud_logo` pasan a ser el logotipo de Imagina (ver «Imágenes de marca»). |
| `xml/network_security_config.xml` (en `app/src/imaginaRelease/res/`) | Solo la versión de publicación: sin http y sin certificados instalados por la persona. El de Nextcloud permite las dos cosas (cada persona escribe su servidor, y los móviles gestionados traen certificados propios); Imagina Cloud solo habla con `cloud.imagina.build` y `auth.imagina.build`, con certificados públicos. La versión de depuración conserva el de Nextcloud, para probar contra un Nextcloud local. Una empresa que inspeccione el tráfico TLS con un certificado propio no podrá usar la app. |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.xml` | El mismo icono adaptable de `mipmap-anydpi` bajo cada densidad, para que no viajen en la APK los PNG del icono de Nextcloud (con su logo, que está bajo su licencia de marca). Nunca se mostraban (con `minSdk` 28 gana el adaptable). Los copia `imagina/generate-resources.sh`. |

## Lo propio de Imagina

| Dónde | Qué |
| --- | --- |
| `imagina/flavor.gradle` | El sabor `imagina`: id, sufijo de versión, valores por entorno (`resValue`), el código de variante de la versión de Google Play de Nextcloud (`src/gplay/java`, notificaciones por Firebase) con sus dependencias, y los valores de Firebase leídos del `google-services.json` de Imagina. |
| `app/src/imagina/AndroidManifest.xml` | Lo que el sabor añade o quita en el manifiesto: el servicio de Firebase y sus ajustes sin analítica (copiados del manifiesto de `gplay`), la vuelta de «Entrar con Imagina» por `<id>://oauth2redirect` sin Auth Tab, sin el permiso `REQUEST_INSTALL_PACKAGES` (que nada usa y Google Play restringe; el manifiesto de `gplay` de Nextcloud también lo quita), sin `READ_CONTACTS`, `WRITE_CONTACTS`, `READ_CALENDAR` ni `WRITE_CALENDAR` (ver «Contactos y calendario») y `usesCleartextTraffic="false"`. |
| `app/src/imagina/res/values/setup.xml` | Marca y opciones: nombre, identificadores, una sola cuenta y un solo servidor, sin introducción, sin calendario ni contactos del móvil, sin mapa en las fotos, sin enlaces de Nextcloud y con el de la política de privacidad de Imagina, colores de Imagina. |
| `app/src/main/java/build/imagina/cloud/ImaginaSignIn.kt` | «Entrar con Imagina»: OAuth con PKCE contra Imagina (alcance `cloud.devices`) en una Auth Tab que vuelve por `<id>://oauth2redirect`, y la API de dispositivos de Imagina (`POST /api/v1/cloud/devices`), que da la dirección de la nube y una contraseña de aplicación propia de este móvil con el nombre que la persona le puso. Guarda el dispositivo y los tokens con `ImaginaDevice` y desconecta antes el de un acceso anterior. Si la persona cancela o Imagina rechaza el acceso (sin plaza en un Cloud, Cloud sin preparar…), un diálogo lo dice con el texto de Imagina y ofrece volver a intentarlo o salir. `styleSystemBars` deja las barras del sistema del color de la pantalla (crema, o el fondo oscuro) con los iconos del contraste justo. |
| `app/src/main/java/build/imagina/cloud/ImaginaDevice.kt` | El dispositivo en Imagina (ver «El dispositivo en Imagina»): guarda su id y los tokens OAuth, lo desconecta (`DELETE /api/v1/cloud/devices/{id}`) al quitar la cuenta o al volver a entrar, y `ImaginaDeviceWork`, el trabajo periódico que renueva los tokens y recoge los dispositivos de cuentas ya quitadas. |
| `app/src/main/java/build/imagina/cloud/ImaginaApi.kt` | Las peticiones HTTPS a Imagina (identidad y API de dispositivos) y los tokens (`ImaginaTokens`), compartidos por el acceso y el dispositivo. |
| `app/src/main/java/build/imagina/cloud/ImaginaColors.kt` | Los esquemas de color de Material con los neutros de Imagina (ver «Colores»); lo engancha `MaterialSchemesProviderImpl`. |
| `app/src/main/java/build/imagina/cloud/ImaginaFloatingButton.kt` | `ImaginaViewInflater` (el inflador de vistas de los temas de Imagina: el de Material, que da un `ImaginaFloatingActionButton` donde un layout declara un botón flotante) y `ImaginaFloatingActionButton`, el botón flotante siempre en el acento (ver «Botón flotante»). |
| `app/src/main/java/build/imagina/cloud/ImaginaComposeFloatingButton.kt` | `FloatingActionButton` de Compose en el acento (`R.color.primary` con `R.color.primary_button_text_color`, en claro y en oscuro), que importan las tres pantallas del asistente en lugar del de Material (ver «Botón flotante»). |
| `app/src/main/java/build/imagina/cloud/ImaginaTypography.kt` | La tipografía de Material para las pantallas de Compose, en Instrument Sans (ver «Tipografía»). |
| `app/src/main/java/build/imagina/cloud/ImaginaAccount.kt` | Cómo se ve una cuenta: el nombre de la persona (o nada) donde Nextcloud muestra «usuario@servidor» (ver «Nombre de la cuenta»). |
| `app/src/main/res/values/imagina.xml` | Valores por defecto (vacíos) de los recursos que usa `ImaginaSignIn`, para que las demás versiones compilen igual, y los dos interruptores que el sabor apaga en `setup.xml` (`phone_contacts_calendar_enabled` e `image_location_map_enabled`, verdaderos por defecto). |
| `app/src/imagina/res/drawable/`, `drawable-night/`, `mipmap-anydpi/`, `values/brand_images.xml` | Imágenes de marca generadas (ver «Imágenes de marca»). |
| `app/src/imagina/res/font/` | Instrument Sans (regular, medium, semibold, bold; `instrument_sans.xml` es la familia) y Fraunces semibold, con licencia OFL 1.1 (`LICENSES/OFL-1.1.txt`, con la línea de copyright de cada familia). Ver «Tipografía». |
| `app/src/imagina/res/values/` (`colors_*.xml`, `typography_imagina.xml`, `bools_imagina.xml`) y `values-night/` | Paleta, tipografía y lo que necesitan los recursos sustituidos de la tabla de arriba. Ver «Colores» y «Tipografía». |
| `app/src/imagina/res/values*/strings_rebrand.xml` | Textos con «Nextcloud» en todos los idiomas, y la licencia de Ajustes con la de las fuentes, generados (ver «Textos»). |
| `app/src/main/res/values/imagina.xml`, `app/src/imagina/res/values-{es,de,fr,it,pt-rPT}/strings_imagina.xml` | Textos propios: en inglés por defecto y en los idiomas de Imagina. |

## Imágenes de marca

Las fuentes están en `imagina/brand/`: `imagina-cloud-mark.svg` (el icono: la loseta tinta de Imagina con una nube y el punto teal) y `imagina-cloud-wordmark.svg` y `imagina-cloud-wordmark-reverse.svg` (el logotipo «imagina /cloud», trazado y no texto vivo, copiado del kit de marca de Imagina; el reverso es para fondos oscuros). Se generan, no se editan:

```sh
imagina/generate-resources.sh
```

Genera el icono adaptativo (fondo, primer plano y capa monocroma), el icono de notificación y el logotipo (`imagina_cloud_wordmark`, de 180 × 28,5 dp: el claro en `drawable` y el reverso en `drawable-night`), con `imagina/tools/svg_to_vector.py`. Las tres imágenes con las que Nextcloud pone su logo son alias de ese logotipo (`values/brand_images.xml`): `logo` (primera pantalla y accesos de Nextcloud que Imagina no usa), `nextcloud_splash_logo` (pantalla de carga) y `nextcloud_logo` (cabecera del menú).

Regla de la marca: el logotipo va donde cabe (al menos 20 dp de alto) y el icono solo donde no cabe. Aquí, el logotipo está en la primera pantalla (240 dp de ancho), la pantalla de carga (240 dp) y la cabecera del menú; el icono, en el lanzador, las notificaciones y el icono pequeño de un diálogo. La pantalla de carga *del sistema* (Android 12 y posterior) solo admite un icono, y Nextcloud ya lo deja transparente por razones de marca: lleva el fondo crema y el logotipo aparece en la pantalla de `LauncherActivity` que viene justo después. «Ajustes › Acerca de» es una preferencia de texto, sin pantalla ni logo propios. Nunca el icono junto al logotipo, ni el nombre de la app como texto junto a uno de los dos.

## Colores

La app usa los tokens «Light Craft» del sistema de diseño de Imagina (`resources/ui/tokens.css` en el repositorio de Imagina): las pantallas, barras y la cabecera del menú son **crema** (`#F8F7F4`; en el tema oscuro `#111219`) con texto tinta, y el teal (`#0F766E`; en oscuro `#2DD4BF`) solo resalta: acción principal, selección, elemento actual, enlaces, interruptores y el botón flotante. Con ellos:

- `values/colors_imagina.xml` y `values-night/colors_imagina.xml` definen los tokens (`imagina_canvas`, `imagina_ink`, `imagina_accent`…) y apuntan a ellos los nombres de color de Nextcloud (`bg_default`, `appbar`, `text_color`, `secondary_text_color`, `list_divider_background`, `selected_item_background`, `action_mode_background`…). Donde Nextcloud define también el color en `values-night/colors.xml`, el sabor lo repite en `values-night`: un recurso con calificador gana a uno sin él. Los tokens cambian con el tema, y el acento (`primary`, `primary_dark` y `color_accent`, que en Nextcloud no cambian) también: en oscuro es el teal claro `#2DD4BF`, con texto oscuro encima.
- `values/colors_material.xml` da los tokens a los colores base de Material 3 (`m3_sys_color_light_*` y `…_dark_*`). Los temas de Nextcloud heredan de `Theme.Material3`, y todo lo que la app no colorea ella misma (diálogos, menús, hojas, tarjetas, campos, interruptores, chips) toma esos valores, que sin esto son el morado base de la librería. Un recurso de la app con el nombre del de la librería gana al de la librería. Correspondencia: diálogos, menús, hojas y tarjetas = superficie (blanco); campos y pistas = superficie secundaria; seleccionado = `accent-soft`; contornos = `border-control`; errores = `danger`.
- Los estilos que pintaban el teal de fondo (ventana de arranque, pantalla de carga del sistema, pantallas de entrada, barra de acción) están copiados en `values/styles_imagina.xml` con fondo crema.

**Lo que dice el servidor.** De las capacidades del tema del servidor (`theming`), la app solo usa `color` como semilla de los esquemas de Material que construye `MaterialSchemesProviderImpl` (`color-element` y `color-text` se leen, pero esos esquemas no los usan), y `background`, que solo servía para el banner de la cabecera del menú (apagado en las versiones con marca propia) y el de la pantalla de perfil (que en Imagina ya no lo tiene); `background-plain` y `background-default` solo se guardan. El fondo de las pantallas sale de los recursos de arriba, no del servidor: el `#F8F7F4` del servidor coincide, pero no manda.

**Los esquemas de Material.** Lo que `ViewThemeUtils` colorea desde esos esquemas (la barra de búsqueda, la barra inferior, las hojas, los botones y controles del acento, el selector del menú) usa el esquema tonal de Material para la semilla `#0F766E`, como en Nextcloud, pero con los neutros de Imagina (`ImaginaColors`, enganchado en `MaterialSchemesProviderImpl`): las superficies, el texto y los contornos toman el matiz y el croma del crema `#F8F7F4` en claro y del oscuro `#111219` en oscuro, y los textos secundarios y contornos los de `ink-secondary` `#474B57`, en lugar de los del teal, que los dejaban menta. El acento sigue saliendo de la semilla: primario `#006A63` en claro y `#81D5CB` en oscuro, el mismo matiz que `#0F766E` y `#2DD4BF` pero no el hex exacto. El botón flotante, que Nextcloud pinta con el contenedor primario (un menta claro con icono oscuro), ya no sale de estos esquemas: es el acento (ver «Botón flotante»).

## Botón flotante

Nextcloud pinta el botón flotante («+») en tiempo de ejecución con `viewThemeUtils.material.themeFAB(...)`, desde nueve sitios (la lista de ficheros, las pantallas de álbumes, de texto y de escaneo), con el contenedor primario del esquema de Material (`primaryContainer` y `onPrimaryContainer`: un menta claro con icono oscuro). El sistema de diseño lo quiere en el acento con su color encima: `#0F766E` con blanco en claro y `#2DD4BF` con `#0B2B28` en oscuro. Se compararon cuatro maneras:

1. **Cambiar el esquema** (`ImaginaColors`) para que `primaryContainer` sea el acento. Se midió con la librería de colores de Material (la de android-common 0.34.0): hace falta una variante *fidelity* (el contenedor toma el tono de la semilla), que con la semilla `#0F766E` da exactamente `#0F766E` y en oscuro `#2DD4BF`, pero (a) el color encima sale teñido, `#A3FAEF` en claro y `#00574D` en oscuro, no blanco ni `#0B2B28`; (b) el primario baja de `#006A63` a `#005C55` en claro (y sube en oscuro) y los contenedores secundarios y terciarios cambian, o sea, cambia todo lo que se pinta con el esquema; y (c) lo leen otras cosas que suponen un contenedor claro con texto oscuro: `SimpleAlertDialog` pone su texto en `onPrimaryContainer` sobre fondo de superficie (quedaría blanco sobre blanco), los botones de la hoja de compartir de Compose de la librería usan `primaryContainer` con `onSurface` fijo (ilegibles en oscuro), `AuthenticatorActivity` colorea su indicador con `ON_PRIMARY_CONTAINER` y `colorImageViewBackgroundAndIcon` (los avatares de compartir) usa los dos. Descartado.
2. **Darle otro esquema solo a `viewThemeUtils.material`** (una línea en `ViewThemeUtils.kt`, que lo construye). Aísla lo de (c), pero sigue necesitando la variante fidelity, con (a) y (b) para todo lo que pinta `material` (botones, chips, interruptores, pestañas). Descartado.
3. **Un enganche en cada sitio donde se pinta** (nueve líneas en cinco ficheros de Nextcloud). Exacto, pero es lo que más líneas de Nextcloud toca.
4. **Que el botón no acepte otro color** (elegida): `ImaginaFloatingActionButton`, un `FloatingActionButton` que a toda tinta de fondo o de icono que le pidan responde con el acento y su color encima (`R.color.primary` y `R.color.primary_button_text_color`, que el sabor apunta a los tokens, en claro y en oscuro) y conserva el estado desactivado de Nextcloud (gris con icono blanco, el de `setFabEnabled(false)`). Los de los layouts los crea `ImaginaViewInflater`, el inflador de vistas de Material, que los temas de Imagina nombran en `viewInflaterClass` (cambio de recursos del sabor, ninguna línea de Nextcloud). El único botón creado en código (`AlbumItemsFragment`) usa `ImaginaFloatingActionButton.create`: un enganche. Con otra versión el botón es el de Material.

Se eligió la 4 porque da los colores exactos y toca lo mínimo: nada de los esquemas, y una línea de Nextcloud en vez de nueve. Se comprobó en el emulador: píxel `#0F766E` en claro y `#2DD4BF` en oscuro. Sin comprobar a ojo: el estado desactivado (las carpetas de la nube de la prueba admiten crear) y el botón de álbumes dentro de un álbum (el servidor de la prueba no da álbumes). Ojo: R8 no se usa (no hay minimización), así que el nombre de la clase en el tema no necesita regla de conservación; si algún día se activa, hace falta una para `ImaginaViewInflater`.

Los botones flotantes de Compose del asistente (`AssistantScreen`, `TranslationScreen` y `ConversationScreen`) toman el contenedor primario del esquema (menta). Cambiar el esquema de `ComposeActivity` no sirve: `SimpleAlertDialog` escribe su texto en `onPrimaryContainer` sobre la superficie y quedaría blanco sobre blanco. Esas tres pantallas importan `build.imagina.cloud.FloatingActionButton` (`ImaginaComposeFloatingButton.kt`) en lugar del de Material: mismas llamadas, y el acento con su color encima (`#0F766E` con blanco, `#2DD4BF` con `#0B2B28` en oscuro); con otra versión es el de Material. Sin comprobar a ojo: el asistente no está en el servidor de Imagina (ver «Pendiente»).

## Tipografía

La app usa **Instrument Sans** en todo su texto (400 cuerpo, 500 controles, 600 títulos, 700 poco), y **Fraunces** (soft, 600) solo en el titular de la lista vacía (`layout/empty_list.xml`). No se usa Geist Mono: la app no enseña direcciones ni códigos como tales. Las fuentes (`app/src/imagina/res/font/`) son las instancias estáticas del kit de marca (desde el 13 oct 2026 cubren latín y latín extendido, 321 y 502 caracteres, y llevan nombres propios: `InstrumentSans-Regular`, `-Medium`, `-SemiBold`, `-Bold` y `Fraunces-SemiBold`), con licencia SIL Open Font License 1.1 (`LICENSES/OFL-1.1.txt`, que lleva la línea de copyright de cada familia tal como la llevan los ficheros). El `REUSE.toml` de Nextcloud no anota nada del sabor, así que tampoco las fuentes. En Ajustes › Acerca de, la línea de la licencia dice también «Instrument Sans and Fraunces — SIL Open Font License 1.1» (ver «Textos»). Llegan al texto de tres maneras:

- Los temas base (`BaseTheme.ownCloud`, `BaseTheme.ownCloud.Toolbar`, `FallbackThemingThemeBase`, copiados en `values/styles_imagina.xml` y `themes_imagina.xml`) llevan `android:fontFamily` y `fontFamily` con la familia `@font/instrument_sans`: lo lee todo `TextView` que no pone fuente propia.
- Los estilos de texto de Material 3 (botones, títulos de barra, diálogos, campos) sacan su fuente de cuatro cadenas de la librería, `m3_ref_typeface_{plain,brand}_{regular,medium}` (`values/typography_imagina.xml`): `plain` es cuerpo, etiquetas y botones (regular y medium) y `brand` los títulos grandes (semibold).
- Las pantallas de Jetpack Compose de Nextcloud (el asistente, en `ComposeActivity`) no leen esos temas: `ImaginaTypography` da a Material la misma tipografía con Instrument Sans en sus quince estilos, y `ComposeActivity` la usa en su único `MaterialTheme` (un enganche). Comprobado en el emulador.

Dos cosas que hay que saber: el titular de la lista vacía necesita `app:fontFamily` además de `android:fontFamily` (ver «Recursos que el sabor sustituye»), y lo mismo puede pasar con cualquier otra fuente que se ponga en el layout de una vista mientras los temas base nombren una.

Sin cubrir: la pantalla de compartir de Compose de la librería android-common (`initShareScreen`, que el servidor de la prueba no activa) abre su propio `MaterialTheme` con la tipografía por defecto y no hay dónde engancharse; y el texto que Nextcloud dibuja con una fuente del sistema fijada en el código.

## Textos

```sh
python3 -I imagina/tools/rebrand_strings.py "Imagina Cloud"
```

Copia de `app/src/main/res/values*/strings.xml` los textos que dicen «Nextcloud» y los escribe con «Imagina Cloud» en `app/src/imagina/res/values*/strings_rebrand.xml`, en todos los idiomas. Además, la versión que se ve en Ajustes › Acerca de añade que es una versión modificada de la app de Nextcloud y no la oficial, y la licencia de la misma pantalla (`prefs_gpl_v2`, «GNU General Public License, version 2» y su traducción) lleva una segunda línea con la de las fuentes: «Instrument Sans and Fraunces — SIL Open Font License 1.1» (en los idiomas de Imagina y en inglés en los demás; el nombre de las familias y de la licencia no se traducen, y un espacio duro mantiene «License 1.1» junto). Solo cambia la palabra con mayúscula, así que los enlaces a nextcloud.com siguen igual. Avisa de los textos de varias líneas que no cambia (hoy, solo la descripción de la tienda en algunos idiomas, que la app no muestra). El nombre que se ve en el buscador («Buscar en …») viene del tema del servidor (`occ theming:config name`).

## Contactos y calendario

Imagina Cloud no tiene contactos ni calendario (`docs/PLAN_CLOUD.md`), así que la app no toca los del móvil. El manifiesto del sabor quita `READ_CONTACTS`, `WRITE_CONTACTS`, `READ_CALENDAR` y `WRITE_CALENDAR` (`tools:node="remove"`), y el recurso `phone_contacts_calendar_enabled` (falso en `setup.xml` del sabor, verdadero por defecto en `values/imagina.xml`) cierra por código todo lo que los pedía: la búsqueda (solo busca en el servidor y no pide permisos al abrirse), «Copia de contactos» de Ajustes (con `show_calendar_backup` y `davdroid_integration_enabled` ya apagados no queda copia de calendario ni DAVx5), abrir un `.vcf` como lista de contactos que importar (se abre como texto) y el botón de elegir un contacto al compartir por correo. Con los permisos quitados, el resto del código que los comprueba (`checkSelfPermission`) solo ve «denegado». Al actualizar Nextcloud: `grep -rn "READ_CONTACTS\|WRITE_CONTACTS\|READ_CALENDAR\|WRITE_CALENDAR\|ContactsContract\|CalendarContract" app/src/main/java` no debe listar ningún sitio nuevo fuera de los ya cerrados (`ContactManager`, `CalendarEventManager`, `contactsbackup/`, `ContactsBackupWork`, `ContactsImportWork`, `thirdparties/`).

## Mapa de las fotos

Los detalles de una foto con ubicación llevan en Nextcloud un mapa (osmdroid, con teselas de `tile.openstreetmap.org`) y el nombre del lugar (`nominatim.openstreetmap.org`, con las coordenadas y el correo `android@nextcloud.com`). En el sabor, `image_location_map_enabled` es falso: `ImageDetailInfo` no monta el mapa ni pregunta el lugar, y `osm_geocoder_url` y `osm_geocoder_contact` van vacíos en `setup.xml`. La tarjeta de la ubicación sigue en el diseño pero oculta, así que ninguna petición sale hacia OpenStreetMap. La dependencia osmdroid sigue compilada (la vista está en `file_info_fragment.xml`, que no se copia).

## Política de privacidad

`privacy_enabled` y `privacy_url` (`https://imagina.build/privacy`) están en `setup.xml` del sabor: Ajustes › Acerca de › «Privacidad» la abre dentro de la app (`ExternalSiteWebView`). Es el enlace que Google Play pide encontrar en la app.

## Actualizar a una nueva versión de Nextcloud

1. `git fetch upstream --tags` y `git merge stable-<nueva>` sobre `main`.
2. Cambiar `NEXTCLOUD_ANDROID_TAG` en `imagina/imagina.properties`. Los conflictos solo pueden venir de los ficheros de la tabla. En `AuthenticatorActivity.java`, conservar los tres enganches marcados; en los demás, los marcados «Imagina (IMAGINA.md)» (`grep -rn "Imagina (IMAGINA.md)" app/src/main`).
3. Revisar las claves nuevas o renombradas de la marca: `git diff stable-<anterior> stable-<nueva> -- app/src/main/res/values/setup.xml` y llevar a `app/src/imagina/res/values/setup.xml` lo que haga falta (sobre todo identificadores y autoridades nuevos).
4. Revisar imágenes de marca nuevas: `git diff --stat stable-<anterior> stable-<nueva> -- app/src/main/res/drawable app/src/main/res/mipmap-anydpi`; si aparece una con el logo de Nextcloud, añadirla a `imagina/generate-resources.sh`.
5. Comprobar que `src/gplay/java` sigue teniendo las clases de variante (`VariantComponentsModule`, `PushUtils`…) y llevar a `app/src/imagina/AndroidManifest.xml` lo que cambie en `app/src/gplay/AndroidManifest.xml` (servicio de Firebase, ajustes).
6. Volver a generar los textos (`rebrand_strings.py`) y las imágenes, compilar y probar en el emulador: «Entrar con Imagina» (la pestaña se cierra sola), lista de ficheros, subir un fichero, y que no se vea «Nextcloud» en ninguna pantalla.
7. Comparar los recursos que el sabor sustituye («Recursos de Nextcloud que el sabor sustituye») con los de la versión nueva y llevar a las copias lo que haya cambiado:

   ```sh
   git diff stable-<anterior> stable-<nueva> -- app/src/main/res/layout/drawer_header.xml app/src/main/res/layout/user_info_layout.xml app/src/main/res/layout/account_setup_webview.xml app/src/main/res/layout/login_flow_info_layout_v2.xml app/src/main/res/layout/activity_splash.xml app/src/main/res/layout/empty_list.xml app/src/main/res/values/styles.xml app/src/main/res/values/themes.xml app/src/main/res/values-night/themes.xml app/src/main/res/values-night/colors.xml
   ```

   Un color nuevo en `values-night/colors.xml` que el sabor también redefina en `values/colors_imagina.xml` hay que repetirlo en `values-night/colors_imagina.xml`. Y probar en claro y en oscuro: primera pantalla, pantalla de carga, menú, lista vacía, un diálogo y una hoja inferior.

   Y comprobar que los botones flotantes siguen en el acento: `grep -rn "FloatingActionButton(" app/src/main/java` lista los que Nextcloud crea en código (hoy uno, en `AlbumItemsFragment`, que lleva el enganche); los de los layouts salen de `ImaginaViewInflater` (el `viewInflaterClass` de las copias de los temas), también los layouts nuevos. Y los de Compose: `grep -rn "material3.FloatingActionButton" app/src/main/java` no debe listar nada (las pantallas del asistente importan el de `build.imagina.cloud`).

## Compilación automática

`.github/workflows/imagina.yml` (GitHub Actions del repositorio público `efrain-salas/imagina-cloud-android`): compila la APK de depuración en cada subida a `main` (queda como artefacto 14 días) y cada lunes abre una incidencia si Nextcloud ha publicado una versión posterior a `NEXTCLOUD_ANDROID_TAG`. El `google-services.json` llega del secreto `GOOGLE_SERVICES_JSON` del repositorio. Los flujos de Nextcloud que trae el fork están desactivados en GitHub (`gh workflow disable`), porque son para su repositorio. La firma de publicación llegará con la cuenta de Google Play (C11.6).

## Publicar en Google Play

Auditoría previa a la primera subida (13 oct 2026). El paquete que pide Google Play se compila con:

```sh
./gradlew :app:bundleImaginaRelease    # app/build/outputs/bundle/imaginaRelease/app-imagina-release.aab
```

- **Firma**: el sabor no tiene `signingConfig`, así que el AAB (y la APK `assembleImaginaRelease`) salen **sin firmar**. Google Play pide un AAB firmado con la clave de subida de Imagina (Play firma luego con la suya). La clave y sus contraseñas van fuera de git (por ejemplo en `imagina/imagina.local.properties`, que ya está ignorado) y se firma el AAB con `jarsigner` o con un `signingConfig` que las lea de ahí.
- **Versión**: `versionCode` = el de Nextcloud (`35.0.1` da 350000190) más `IMAGINA_VERSION_CODE_OFFSET`; `versionName` = `35.0.1-imagina`. Sube con cada versión de Nextcloud; para una segunda subida sobre la misma versión de Nextcloud, subir el parámetro.
- **SDK**: `minSdk` 28, `targetSdk` 36 (Android 16, lo que Google Play pide a las apps nuevas desde el 31 de agosto de 2026), `compileSdk` 37. Solo `arm64-v8a` y `x86_64` (sin móviles de 32 bits).
- **16 KB**: las siete bibliotecas nativas de cada ABI están alineadas a 16 KB (cabeceras `LOAD` de `0x4000`, `zipalign -P 16` correcto).
- **R8**: la versión de publicación no minimiza ni ofusca (Nextcloud no lo activa), así que no hay fichero de mapeo y las clases que los temas nombran (`ImaginaViewInflater`) no necesitan regla. Con R8 haría falta una.
- **Red**: la versión de publicación solo admite https y los certificados del sistema (ver «Recursos de Nextcloud que el sabor sustituye»). Por sí sola la app habla con `cloud.imagina.build`, `auth.imagina.build` y, para las notificaciones, con Firebase Cloud Messaging (`firebaseinstallations.googleapis.com`, `fcmregistrations.googleapis.com` y los servidores de Google Play Services); `imagina.build` (política de privacidad), `github.com` (código fuente, «Informar») y `www.gnu.org` (licencia) solo si la persona abre su enlace. Ya no hay peticiones a OpenStreetMap (ver «Mapa de las fotos»); las clases de osmdroid siguen en el paquete, sin que nada las use.
- **Permisos** que Google Play revisa y por qué los tiene la app (los de Nextcloud, salvo `REQUEST_INSTALL_PACKAGES` y los de contactos y calendario, que el sabor quita): `MANAGE_EXTERNAL_STORAGE` («acceso a todos los ficheros»: subida automática desde cualquier carpeta y sincronización de carpetas locales), `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` y `READ_MEDIA_VISUAL_USER_SELECTED` (subida automática de fotos y vídeos, galería), `FOREGROUND_SERVICE_DATA_SYNC` (subidas y descargas) y `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (reproductor de audio y vídeo), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (aviso para que la subida automática no la corte el ahorro de batería). No lleva `READ_CONTACTS`, `WRITE_CONTACTS`, `READ_CALENDAR`, `WRITE_CALENDAR` (ver «Contactos y calendario»), `QUERY_ALL_PACKAGES`, ubicación, alarmas exactas ni intención de pantalla completa.
- **Pendiente de Imagina** (la ficha y las declaraciones de Play Console y la cuenta de pruebas para los revisores, que no pueden recibir un SMS): ver la lista en el informe de esta auditoría. La política de privacidad ya está enlazada dentro de la app (ver «Política de privacidad»).

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

Las notificaciones llegan por Firebase Cloud Messaging a través del servidor de notificaciones de Imagina (C7.10), no del de Nextcloud. La app usa el código de la versión de Google Play de Nextcloud (`src/gplay/java`), que registra el móvil en ese servidor al entrar. El proyecto de Firebase es `imagina-84bd9` y su `google-services.json` (app `build.imagina.cloud`) va en `app/src/imagina/`, fuera de git: se descarga de la consola de Firebase (Configuración del proyecto › Tus apps). Como en Nextcloud, sus valores pasan a recursos sin el plugin de Google Services (`imagina/flavor.gradle`). Sin el fichero la app compila igual, sin notificaciones. Al entrar con Imagina se olvida el estado de notificaciones de un acceso anterior de la misma cuenta (`ImaginaSignIn`), que se fue con su contraseña de app, para que el móvil se registre de nuevo.

## Pendiente para la app de verdad

- Probar las notificaciones con un móvil real (un fichero compartido y una mención).
- Probar en el emulador, contra producción, el dispositivo y el nombre de la cuenta: entrar, quitar la cuenta en la app y ver que el dispositivo desaparece de «Mi cuenta»; entrar dos veces y ver que no queda el viejo; ver el nombre (no «1@cloud.imagina.build») en una notificación, el selector de cuenta, la lista de cuentas y la app de Ficheros.
- Los dispositivos creados con versiones anteriores a esta no tienen el id guardado: quitar la cuenta de esa instalación no los desconecta (se desconectan en «Mi cuenta»). Tampoco una reinstalación, que borra lo guardado: para ese caso Imagina podría sustituir el dispositivo anterior del mismo nombre y plataforma.
- Ver en el emulador lo que falta del aspecto nuevo, en claro y en oscuro: la primera pantalla, diálogos y hojas inferiores. Visto el 13 oct 2026: la pantalla de carga, la lista de ficheros y el menú, en crema (oscuro de Imagina en el tema oscuro), con el logotipo e Instrument Sans; y, en claro y en oscuro, el botón flotante en el acento, la hoja del «+» y la del menú de un fichero, la pantalla de compartir, Ajustes con la licencia de las fuentes, el titular de la lista vacía en Fraunces y el asistente en Instrument Sans.
- Contraste en el tema oscuro: en las pantallas que se pueden ver con el servidor de la prueba (lista, menú, hojas, Ajustes, compartir, subidas, papelera, perfil, selección de ficheros) no hay texto blanco sobre el teal claro `#2DD4BF`: lo pintado con el acento usa su color encima (`#0B2B28`) y lo pintado con los esquemas de Material lleva su propio color de contraste. El avatar sin nombre de una compartición (`ShareViewHolder.setUserImage`) ya no lleva el filtro blanco que ganaba al tinte del esquema (icono blanco sobre menta); se alcanza con un nombre vacío y está sin ver en el emulador.
- Los botones flotantes de Compose del asistente (traducir, nueva conversación) están en el acento (ver «Botón flotante»); sin ver en el emulador: el servidor de Imagina no ofrece el asistente, el compartir de Compose ni los álbumes. Revisar cuando se activen.
- Firma de publicación y ficha de Google Play (con el aviso de que no es la app oficial): ver «Publicar en Google Play». Antes de la primera subida falta:
  - un canal de soporte público para la ficha (las plantillas de `.github/ISSUE_TEMPLATE`, a las que lleva el botón «Informar» de la pantalla de fallo, ya son de Imagina Cloud y están en español);
  - los registros de cambios de `fastlane/metadata` son los de Nextcloud: no usarlos como «novedades» de la ficha.
