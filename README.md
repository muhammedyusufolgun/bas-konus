# Bas-Konuş Android

Android 8+ için basılı tutarak konuşma prototipi.

## Şu anda çalışan kodun kapsamı
- Aynı Wi-Fi ağında iki cihaz arasında IP adresiyle doğrudan UDP ses iletimi.
- Mikrofon izni ve basılı tutarak konuşma.
- AES-256-GCM ile paket şifreleme. Her iki cihazda aynı en az 16 karakterlik eşleştirme anahtarı gereklidir.
- Karşı cihazın IP adresi ve eşleştirme anahtarı uygulama tercihlerinde saklanır (şu anda güvenli anahtar deposu kullanılmaz).
- Rehberden kişi seçme yalnızca isim gösterir; rehber kişisiyle ağ cihazı arasında doğrulanmış eşleştirme YOKTUR.
- GitHub Actions üzerinden debug APK oluşturma.

## Kullanım
İki telefon aynı Wi-Fi ağına bağlanmalı. Her telefonda diğerinin yerel IP adresini ve aynı uzun, rastgele eşleştirme anahtarını girin; "Hedefi ayarla"ya dokunun. Ardından basılı tutarak konuşun. IP adresleri değişirse tekrar ayarlayın.

## Sınırlamalar ve güvenlik
- Farklı şehirlerde veya mobil internet üzerinde NAT/CGNAT nedeniyle doğrudan bağlantı garanti edilmez. Sinyalleşme ve gerekirse STUN/TURN aktarma altyapısı henüz YOK.
- Çoklu alıcı, arka plan servisi, bildirimler, otomatik eşleştirme ve jitter buffer henüz YOK.
- Ağ üzerinden şifreli ses taşınsa da kullanıcı kimliği doğrulanmış değildir. IP adresi doğrulama yöntemi değildir. Anahtar, Android SharedPreferences içinde şimdilik düz metin saklanmaktadır; üretim sürümünde Android Keystore ve güvenli eşleştirme gereklidir.
- Tekrar oynatma saldırısına karşı koruma ve paket sıralama henüz YOK. Güvenilmeyen ağlarda üretim amaçlı kullanmayın.
- APK'nin cihazda çalıştığı ve GitHub derlemesinin başarılı olduğu ayrıca doğrulanmalıdır.
