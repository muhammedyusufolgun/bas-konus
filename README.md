# Bas-Konuş Android

Android 8+ için basılı tutarak konuşma prototipi.

## Şu anda çalışan kodun kapsamı
- Aynı Wi-Fi ağında IP adresleriyle en fazla 20 alıcıya doğrudan UDP ses iletimi.
- Mikrofon izni ve basılı tutarak konuşma.
- PBKDF2-HMAC-SHA256 (120.000 iterasyon) ile türetilen AES-256-GCM anahtarıyla paket şifreleme. Her iki cihazda aynı en az 16 karakterlik eşleştirme anahtarı gereklidir.
- Karşı cihazın IP adresi saklanır; eşleştirme anahtarı güvenlik için uygulama yeniden açıldığında tekrar girilmelidir.
- Rehberden kişi seçme yalnızca isim gösterir; rehber kişisiyle ağ cihazı arasında doğrulanmış eşleştirme YOKTUR.
- GitHub Actions üzerinden debug APK oluşturma.
- Gelen ses paketlerinde kaynak IP adresinin ekranda gösterilmesi.
- Şifreli ping/yanıt ile yapılandırılmış cihazlar arasında ortak anahtar ve erişilebilirlik doğrulaması.
- Aynı Wi-Fi ağında UDP yayın ile cihaz keşfi (39572 portu); bulunan IP adreslerinin alıcı alanına eklenmesi.

## Kullanım
Telefonlar aynı Wi-Fi ağına bağlanmalı. 'Yerel ağda cihaz ara' düğmesiyle diğer açık uygulamaları arayabilirsiniz. Bazı yönlendiriciler yayın paketlerini veya cihazlar arası trafiği engelleyebilir. Gönderici telefonda alıcıların yerel IPv4 adreslerini virgülle ayırarak girin (en fazla 20). Her telefonda iletişim kuracağı diğer telefonların IP adreslerini ve aynı uzun, rastgele eşleştirme anahtarını girin; "Hedefi ayarla"ya dokunun. Ardından basılı tutarak konuşun. IP adresleri değişirse tekrar ayarlayın.

## Son iyileştirmeler
- Konuşma başlatma yarışına karşı tek aktif gönderici kontrolü. 
- Küçük ekranlar için kaydırılabilir arayüz. 
- Anahtar türetme yöntemi değiştiğinden önceki prototip sürümleriyle uyumlu değildir; tüm telefonlarda aynı yeni sürüm kullanılmalıdır.

## Bağlantı doğrulama
Her iki cihazda da birbirlerinin IP adresleri ve aynı ortak anahtar tanımlanmalı. 'Eşleştirme bağlantısını doğrula' düğmesine basın. Karşı uygulama açıksa ve UDP 39571 erişilebiliyorsa yanıt alınır. Bu işlem rehber kişisinin kimliğini doğrulamaz ve bağlantı kalitesini garanti etmez.

## Sınırlamalar ve güvenlik
- Farklı şehirlerde veya mobil internet üzerinde NAT/CGNAT nedeniyle doğrudan bağlantı garanti edilmez. Sinyalleşme ve gerekirse STUN/TURN aktarma altyapısı henüz YOK.
- Keşif yanıtları kimlik doğrulamaz; bulunan her cihaza güvenmeyin. Gerçek karşılıklı kişi eşleştirmesi henüz YOK.
- Çoklu alıcı için IP tabanlı gönderim vardır; alıcıların durum takibi, arka plan servisi, bildirimler, otomatik eşleştirme ve jitter buffer henüz YOK.
- Ağ üzerinden şifreli ses taşınsa da kullanıcı kimliği doğrulanmış değildir. IP adresi doğrulama yöntemi değildir. Anahtar diske kaydedilmez; ancak manuel ortak anahtar dağıtımı yerine doğrulanmış eşleştirme ve güçlü anahtar türetme protokolü gereklidir.
- Son 2048 doğrulanmış paket nonce değeri için sınırlı tekrar oynatma kontrolü vardır; kalıcı oturum sayacı ve paket sıralama henüz YOK. Güvenilmeyen ağlarda üretim amaçlı kullanmayın.
- 41419584674 numaralı önceki GitHub Actions derlemesinde APK oluşturma ve artifact yükleme başarılıydı. Bu son değişikliklerin derleme ve cihaz testi ayrıca doğrulanmalıdır.
