AILE NEZARETI — DESIGN LOCKED PARENT APK

Bu paket danışıqda təsdiqlənən DESIGN_REFERENCE.png görünüşünü əsas götürərək yenidən yığılıb.

Əsas ekranlar:
- Dashboard: profil, online/son görülmə, batareya, Xəritə/Zənglər/Zonalar/Bildirişlər sürətli keçidləri, bu gün statistika, son fəaliyyət.
- Xəritə: açılan kimi yalnız son GPS mövqeyi; Marşrut düyməsi ilə bugünkü/selected date route; GPS sıçrayış filtri; Standard/Peyk; fokus; yenilə; paylaş.
- Zənglər: Hamısı/Gələn/Gedən/Qaçırılan, tarix seçimi və tam günlük interval.
- Zonalar: xəritədə radius dairələri + zona siyahısı, aktiv/deaktiv, əlavə/silmə.
- Bildirişlər/Ayarlar: mövcud server alert-ləri və parametrlər.
- PIN: 5566.
- Firebase FCM və mövcud server API saxlanılıb.

Server API:
https://hesabat.site/usaq/webpanel/api/

GitHub build:
1. NezaretV4 qovluğunun içindəkiləri repo root-a yerləşdir.
2. Actions -> Build Nezaret V4 APK -> Run workflow.
3. Artifact: Nezaret-V4-Premium-APK.

Yoxlama:
- Android res XML faylları parse yoxlamasından keçirilib.
- Drawable resource istinadları yoxlanılıb.
- Kotlin fayllarında struktur/brace yoxlaması aparılıb.
- Bu lokal mühitdə Android SDK + Gradle dependency set olmadığı üçün tam assembleDebug burada icra edilməyib; GitHub workflow Gradle 8.7/Java 17 ilə verilib.
