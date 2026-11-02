# Bas-Konuş Android

Android 8+ için basılı tutarak konuşma prototipi.

## v0.16.0 güvenlik ve CI kontrolü
- Cihaz kimliği parmak izi 64 bitten 128 bite genişletildi; eski onaylı kısa kodlar yeniden doğrulanmalıdır.
- Rehberden kişi seçilmemişse cihaz kimliği onaylanamaz.
- GitHub Actions derlemesine Android lint kontrolü eklendi; lint HTML raporu ayrıca yüklenir.
- Bu değişikliklerin derleme/lint sonucu henüz doğrulanmamıştır; GitHub Actions sonuçları kontrol edilmelidir.

## v0.15.0 imzalı cihaz kimliği doğrulaması
- Şifreli yerel ağ PING/PONG paketleri, Android Keystore EC-P256 cihaz genel anahtarını ve SHA256withECDSA imzasını taşır.
- Gelen imza doğrulanır; ilk bağlantıda kullanıcı karşı cihazın kimlik parmak izini ayrı kanaldan karşılaştırıp onaylar.
- Onaylanan kimlik, seçilen rehber kaydının URI değeriyle ilişkilendirilerek yerel tercihlerde saklanır; farklı anahtar tespitinde bağlantı reddedilir.
- Ses gönderimi ve alımı ancak cihaz kimliği onaylandıktan sonra mümkündür.
- **Sınırlar:** Rehber kaydının sahibi otomatik kanıtlanmaz; ilk kimlik onayı kullanıcıya dayanır. Rehber kaydının URI'si değişirse yeniden onay gerekebilir. Uygulama hâlâ aynı yerel ağ prototipidir. APK ve gerçek telefon testi yapılmadı.

## v0.14.0 cihaz kimliği altyapısı
- Android Keystore üzerinde kalıcı EC P-256 imza anahtarı üretilir; özel anahtar dışarı aktarılmaz.
- Cihazın genel anahtarından türetilen kısa kimlik parmak izi arayüzde gösterilir.
- İmza oluşturma/doğrulama yardımcıları eklendi, ancak ağ üzerinden karşı tarafın imzasını doğrulayan protokol ve rehber kimliğiyle güven ilişkisi **henüz uygulanmadı**.
- APK derlemesi ve gerçek cihaz testi henüz doğrulanmadı.

## v0.13.0 eşleştirme kodu
- Aynı ortak anahtardan türetilen kısa güvenlik kodu ekranda gösterilir; kullanıcılar kodu karşılaştırabilir.
- Kod, gerçek cihaz kimliğinin veya rehber kişisinin doğrulanması yerine geçmez. Kısa kodun kendisi güvenli kimlik doğrulama kanalı değildir.
- APK derlemesi ve gerçek cihaz testi henüz doğrulanmamıştır.

## v0.12.0 eşleştirme akışı düzeltmeleri
- Önceden kaydedilmiş IP adresleri ana ekranda gösterilmez.
- Otomatik hedef yapılandırması başarısızsa doğrulama isteği gönderilmez.
- Hoparlör yönlendirmesi sabit tutulur; iki cihazda da Kişi akışıyla yapılandırma yapılması gerektiği belirtilir.
- Rehber kişisi ile cihaz kimliği henüz doğrulanmış biçimde eşleştirilmez. Otomatik keşif yalnızca aynı yerel ağda tek diğer cihaz bulunduğunda çalışır.

## v0.11.0 arayüz güncellemesi
- Tek ekran: açık mavi küçük Kişi düğmesi ve ortada büyük kırmızı yuvarlak bas-konuş düğmesi; basıldığında yeşile döner.
- Hoparlör çıkışı sabittir, ses çıkışı değiştirme düğmesi kaldırıldı. IP adresi ve eski ayar düğmeleri ana ekranda gösterilmez.
- Rehberden kişi seçildiğinde ortak anahtar istenir ve aynı Wi-Fi ağında tek başka cihaz bulunursa otomatik IP yapılandırması ve doğrulama başlatılır.
- **Önemli:** Rehber kişisinin seçilmesi, bulunan cihazın o kişiye ait olduğunu kanıtlamaz. Telefon numarası ile cihaz kimliğinin güvenli eşleştirilmesi henüz yoktur. Birden fazla cihaz bulunan ağlarda otomatik eşleştirme yapılmaz.
- Farklı şehirlerde/mobil ağlarda bağlantı ve arka planda çalışma henüz desteklenmez. Gerçek cihaz ve APK derleme testi yapılmalıdır.

## v0.10.0 ses oturumu iyileştirmesi
- Devam eden konuşma sırasında da her alıcının doğrulama süresi kontrol edilir; süresi dolan hedefe gönderim kesilir.
- Gönderim hatalarının otomatik durum mesajıyla örtülmesi azaltıldı.
- APK derleme ve gerçek cihaz doğrulaması gereklidir.

## v0.9.0 doğrulama süresi
- Doğrulanmış IP adreslerinin ses yetkisi beş dakika sonra sona erer; yeniden doğrulama gerekir.
- Gönderim başlatırken ve ses alırken yetki süresi kontrol edilir.
- Bu bir kimlik eşleştirmesi veya internet üzerinden bağlantı çözümü değildir.

## v0.8.0 doğrulama güncellemesi
- Her hedef cihaz için ayrı 128-bit rastgele doğrulama isteği oluşturulur.
- Şifreli yanıtın istekle eşleşmesi ve 10 saniye içinde gelmesi aranır.
- Karşılıklı doğrulama hâlâ paylaşılan gizli anahtara dayanır; rehber kimliğiyle kalıcı kriptografik eşleştirme değildir.

## v0.7.0 bakım güncellemesi
- Yerel ağ keşfinde bir yayın adresi başarısız olduğunda diğer adresler denenir.
- Gelen PCM ses paketleri 16-bit örnek sınırlarına göre kontrol edilir; kısa geçerli çerçeveler de işlenir.
- Mikrofon okuma hataları gönderim akışında raporlanır.
- Son sürümün APK derleme ve iki cihazda gerçek ses testi ayrıca doğrulanmalıdır.

## v0.6.0
- Hoparlör ve ahize arasında geçiş düğmesi eklendi; uygulama kapanınca önceki ses modu geri yüklenir.
- Bu sürüm de yerel ağ odaklıdır. Farklı şehirlerde çalışmak için en az bir tarafta internete erişilebilir genel IPv4 adresi ve yönlendiricide UDP 39571 port yönlendirmesi gerekir; mobil operatör CGNAT bağlantılarında bu yöntem çoğunlukla uygulanamaz.
- Sınırsız, sunucusuz ve tüm mobil ağlarda garantili bağlantı vaat edilmez. Güvenilir internet eşleştirmesi için kullanıcı onaylı bir sinyalleşme/aktarma hizmeti seçilmelidir.

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
Telefonlar aynı Wi-Fi ağına bağlanmalı. 'Yerel ağda cihaz ara' düğmesiyle diğer açık uygulamaları arayabilirsiniz. Bazı yönlendiriciler yayın paketlerini veya cihazlar arası trafiği engelleyebilir. Gönderici telefonda alıcıların yerel IPv4 adreslerini virgülle ayırarak girin (en fazla 20). Her telefonda iletişim kuracağı diğer telefonların IP adreslerini ve aynı uzun, rastgele eşleştirme anahtarını girin; "Hedefi ayarla"ya dokunun. Her iki telefonda 'Hedefi ayarla' işleminden sonra en az bir telefonda 'Eşleştirme bağlantısını doğrula' düğmesine dokunun. Doğrulama tamamlanınca basılı tutarak konuşun. IP adresleri değişirse tekrar ayarlayın.

## Son iyileştirmeler
- Konuşma başlatma yarışına karşı tek aktif gönderici kontrolü. 
- Küçük ekranlar için kaydırılabilir arayüz. 
- Anahtar türetme yöntemi değiştiğinden önceki prototip sürümleriyle uyumlu değildir; tüm telefonlarda aynı yeni sürüm kullanılmalıdır.

## v0.5.0 güvenlik güncellemesi
- Ses gönderimi yalnızca şifreli bağlantı doğrulaması tamamlanan IP adreslerine yapılır.
- Alınan sesin çalınması için de bağlantının doğrulanmış olması gerekir.
- Hedef değişikliği ve yeniden doğrulama konuşma sürerken engellenir.
- Bu kontrol ortak anahtarı doğrular; rehber kişisinin gerçek kimliğini doğrulamaz.

## Bağlantı doğrulama
Her iki cihazda da birbirlerinin IP adresleri ve aynı ortak anahtar tanımlanmalı. 'Eşleştirme bağlantısını doğrula' düğmesine basın. Karşı uygulama açıksa ve UDP 39571 erişilebiliyorsa yanıt alınır. Bu işlem rehber kişisinin kimliğini doğrulamaz ve bağlantı kalitesini garanti etmez.

## Sınırlamalar ve güvenlik
- Farklı şehirlerde veya mobil internet üzerinde NAT/CGNAT nedeniyle doğrudan bağlantı garanti edilmez. Sinyalleşme ve gerekirse STUN/TURN aktarma altyapısı henüz YOK.
- Keşif yanıtları kimlik doğrulamaz; bulunan her cihaza güvenmeyin. Gerçek karşılıklı kişi eşleştirmesi henüz YOK.
- Çoklu alıcı için IP tabanlı gönderim vardır; alıcıların durum takibi, arka plan servisi, bildirimler, otomatik eşleştirme ve jitter buffer henüz YOK.
- Ağ üzerinden şifreli ses taşınsa da kullanıcı kimliği doğrulanmış değildir. IP adresi doğrulama yöntemi değildir. Anahtar diske kaydedilmez; ancak manuel ortak anahtar dağıtımı yerine doğrulanmış eşleştirme ve güçlü anahtar türetme protokolü gereklidir.
- Son 2048 doğrulanmış paket nonce değeri için sınırlı tekrar oynatma kontrolü vardır; kalıcı oturum sayacı ve paket sıralama henüz YOK. Güvenilmeyen ağlarda üretim amaçlı kullanmayın.
- 41419584674 numaralı önceki GitHub Actions derlemesinde APK oluşturma ve artifact yükleme başarılıydı. Bu son değişikliklerin derleme ve cihaz testi ayrıca doğrulanmalıdır.
