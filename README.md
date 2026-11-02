# Bas-Konuş Android
İlk prototip: Android 8+ için ücretsiz, sunucusuz, aynı yerel ağda tek hedefe bas-konuş ses iletimi.

## Kullanım
İki telefonda uygulamayı açın, mikrofon izni verin. Her telefonda karşı cihazın **aynı Wi-Fi üzerindeki yerel IP adresini** girip "Hedefi ayarla" düğmesine basın. "Basılı Tut ve Konuş" ile gönderim yapılır. Rehberden kişi seçme şu an yalnızca kişinin adını gösterir; otomatik eşleştirme sağlamaz.

## Sınırlar
- **Farklı şehirler/mobil internet:** CGNAT ve NAT nedeniyle doğrudan bağlantı garanti edilmez. Gerçek WebRTC sinyalleşme, STUN/TURN veya erişilebilir aktarım altyapısı gerekir.
- **Güvenlik:** Bu prototipin UDP PCM sesi şifrelenmemiştir. Güvenilmeyen ağlarda kullanmayın. Karşı tarafın IP adresiyle paket filtreleme kimlik doğrulama değildir.
- Uygulama ön planda açık olmalıdır. Arka plan dinleme, güvenli eşleştirme, grup görüşmesi, internet üzerinden otomatik bağlantı henüz yoktur.
- Ağ gecikmesi ve paket kaybına karşı jitter buffer henüz yoktur.
- APK GitHub Actions çıktısında `Bas-Konus-apk` adıyla oluşur.
