# Bas-Konuş Android

Android 8+ için basılı tutarak konuşma prototipi.

## Şu anda çalışan kodun kapsamı
- Aynı Wi-Fi ağında IP adresleriyle en fazla 20 alıcıya doğrudan UDP ses iletimi.
- Mikrofon izni ve basılı tutarak konuşma.
- AES-256-GCM ile paket şifreleme. Her iki cihazda aynı en az 16 karakterlik eşleştirme anahtarı gereklidir.
- Karşı cihazın IP adresi saklanır; eşleştirme anahtarı güvenlik için uygulama yeniden açıldığında tekrar girilmelidir.
- Rehberden kişi seçme yalnızca isim gösterir; rehber kişisiyle ağ cihazı arasında doğrulanmış eşleştirme YOKTUR.
- GitHub Actions üzerinden debug APK oluşturma.

## Kullanım
Telefonlar aynı Wi-Fi ağına bağlanmalı. Gönderici telefonda alıcıların yerel IPv4 adreslerini virgülle ayırarak girin (en fazla 20). Her telefonda iletişim kuracağı diğer telefonların IP adreslerini ve aynı uzun, rastgele eşleştirme anahtarını girin; "Hedefi ayarla"ya dokunun. Ardından basılı tutarak konuşun. IP adresleri değişirse tekrar ayarlayın.

## Sınırlamalar ve güvenlik
- Farklı şehirlerde veya mobil internet üzerinde NAT/CGNAT nedeniyle doğrudan bağlantı garanti edilmez. Sinyalleşme ve gerekirse STUN/TURN aktarma altyapısı henüz YOK.
- Çoklu alıcı için IP tabanlı gönderim vardır; alıcıların durum takibi, arka plan servisi, bildirimler, otomatik eşleştirme ve jitter buffer henüz YOK.
- Ağ üzerinden şifreli ses taşınsa da kullanıcı kimliği doğrulanmış değildir. IP adresi doğrulama yöntemi değildir. Anahtar diske kaydedilmez; ancak manuel ortak anahtar dağıtımı yerine doğrulanmış eşleştirme ve güçlü anahtar türetme protokolü gereklidir.
- Son 2048 doğrulanmış paket nonce değeri için sınırlı tekrar oynatma kontrolü vardır; kalıcı oturum sayacı ve paket sıralama henüz YOK. Güvenilmeyen ağlarda üretim amaçlı kullanmayın.
- APK'nin cihazda çalıştığı ve GitHub derlemesinin başarılı olduğu ayrıca doğrulanmalıdır.
