# `backend/Dockerfile` — satır satır açıklama

Bu doküman [`backend/Dockerfile`](../backend/Dockerfile)'ı öğretici amaçla satır satır açıklar. Docker'a
yeni başlayanlar için yazıldı; Dockerfile'ın kendisinde yorum yok çünkü asıl açıklama burada.

## Genel fikir: multi-stage build

Dockerfile iki ayrı **stage** (aşama) içerir:

1. **`build` stage** — kaynak kodu derleyip çalıştırılabilir bir `.jar` üretir. Bunun için tam bir
   JDK (Java Development Kit) + Maven gerekir. Bu stage'in imajı ~500MB+ olabilir ama önemli değil,
   çünkü **son imaja dahil olmaz**.
2. **`runtime` stage** — sadece derlenmiş `.jar` dosyasını alır ve onu çalıştırmak için gereken en
   küçük Java çalışma zamanını (JRE) kullanır. Railway'e giden ve gerçekten deploy edilen imaj budur.

Bu ayrımın amacı: Maven, kaynak kod, `.git` geçmişi gibi hiçbir şey production imajına sızmaz —
daha küçük, daha güvenli, daha hızlı başlayan bir imaj elde edilir.

---

## Satır satır

```dockerfile
# syntax=docker/dockerfile:1
```
Docker'a hangi Dockerfile "sözdizimi" sürümünü kullanacağını söyler. Sabit bir versiyon yerine
`1` yazmak, BuildKit'in bu majör versiyon içindeki en güncel iyileştirmeleri otomatik kullanmasını
sağlar. Pratikte "modern Docker özelliklerini kullanabilirsin" demektir.

```dockerfile
FROM eclipse-temurin:21-jdk-jammy AS build
```
İlk stage'i başlatır ve ona `build` adını verir (aşağıda bu isimle referans vereceğiz).
`eclipse-temurin` Java'nın en yaygın kullanılan açık kaynak dağıtımıdır (Adoptium projesi).
`21-jdk` → Java 21, tam JDK (derleyici dahil). `jammy` → Ubuntu 22.04 tabanlı imaj (Alpine değil;
Alpine bazen native kütüphane uyumsuzlukları çıkarabildiği için burada tercih edilmedi).

```dockerfile
WORKDIR /app
```
Konteyner içinde `/app` klasörünü oluşturur ve sonraki tüm komutların (`COPY`, `RUN`, vb.) o
klasörden çalışmasını sağlar. Elle `cd /app` yapmaya gerek kalmaz.

```dockerfile
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
```
Bu üç satır **Docker layer cache**'ini (katman önbelleğini) kullanmak için bilinçli olarak
kaynak koddan (`src/`) önce geliyor. Mantık şu: `pom.xml` (bağımlılık listesi) değişmediği sürece
Docker bu katmanı yeniden çalıştırmaz, önbellekten kullanır. Yani sadece Java kodunda değişiklik
yaptığında her seferinde tüm internet bağımlılıklarını yeniden indirmek zorunda kalmazsın.
- `.mvn/` ve `mvnw` → Maven Wrapper; sunucuda Maven kurulu olmasına gerek bırakmadan projeyle
  aynı Maven sürümünü indirip kullanır (bu projede `.mvn/wrapper/maven-wrapper.properties`'te
  sabitlenmiş: Maven 3.9.16).
- `chmod +x mvnw` → wrapper script'ine çalıştırma izni verir (Git bazen bu biti kaybeder).
- `mvnw dependency:go-offline -B` → `pom.xml`'deki tüm bağımlılıkları indirip yerel Maven
  önbelleğine koyar, henüz derleme yapmaz. `-B` (batch mode) → etkileşimli olmayan, CI'a uygun
  çıktı.

```dockerfile
COPY src ./src
RUN ./mvnw clean package -DskipTests -B
```
Şimdi asıl kaynak kodu kopyalanır (bu satır kod her değiştiğinde cache'i bozar — bilerek en sona
konuldu). `clean package` → derler ve `target/` altında çalıştırılabilir `.jar` üretir.
`-DskipTests` → testleri **atlar**. Bilinçli bir tercih: bu projenin repository/integration
testleri gerçek PostgreSQL için Testcontainers kullanıyor (bkz. `CLAUDE.md`), yani Docker
içindeki bu build ortamında Docker-in-Docker olmadan zaten çalışamazlar. Testler CI'da
(`.github/workflows/ci.yml`) ayrı olarak koşuyor; Docker image build'i "derlensin mi" sorusuna
cevap veriyor, "testler geçti mi" sorusuna değil.

```dockerfile
FROM eclipse-temurin:21-jre-jammy AS runtime
```
**İkinci ve son stage** burada başlıyor. `21-jre` (Development Kit değil, sadece Runtime
Environment) — derleyici yok, sadece Java programı çalıştırmaya yeten daha küçük imaj. Bu satırdan
sonraki her şey nihai, Railway'e giden imajdır.

```dockerfile
WORKDIR /app
```
Aynı mantık, ikinci stage'in kendi (temiz) dosya sistemi için tekrar tanımlanır.

```dockerfile
RUN addgroup --system spring && adduser --system --ingroup spring spring
```
Uygulamayı **root olmayan** bir kullanıcıyla çalıştırmak için `spring` adında sistem kullanıcısı/
grubu oluşturur. Konteyner içinde root olarak çalışmak, konteyner kaçışı (container escape) gibi
bir güvenlik açığı bulunursa etkiyi büyütür; gereksiz risktir.

```dockerfile
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar
```
Burası multi-stage build'in can alıcı noktası: `--from=build` diyerek **ilk stage'in dosya
sisteminden** dosya kopyalıyoruz — kaynak kod, Maven, JDK'nın kendisi falan hiçbiri buraya
gelmiyor, sadece üretilen `.jar` dosyası.

Sahiplik ayrı bir `RUN chown spring:spring app.jar` satırıyla değil, kopyalamanın kendisinde
`--chown` ile veriliyor. Sebep layer'lar: `chown` dosyanın metadata'sını değiştirdiği için
overlay filesystem `.jar`'ın **tamamını** üst layer'a yeniden yazar, yani 96 MB'lık fat jar
imaja iki kez girer (ölçüldü: 469 MB → 373 MB). `--chown` ile sahiplik kopyalama anında
ayarlandığından o ikinci layer hiç oluşmaz.

```dockerfile
USER spring:spring
```
Konteynerin bundan sonraki tüm komutlarını (ve nihayetinde çalışan uygulamayı) az önce
oluşturulan root olmayan kullanıcı olarak çalıştırmaya geçer.

```dockerfile
EXPOSE 8080
```
Bu satır **hiçbir portu gerçekten açmaz** — sadece dokümantasyon amaçlıdır ("bu imaj normalde
8080'i dinler" bilgisini imaja gömer). Gerçek port bağlama işini uygulamanın kendisi
(`server.port`) ve Railway'in verdiği `PORT` ortam değişkeni belirler — ayrıntı için
[railway-deployment-guide.md](./railway-deployment-guide.md)'deki health-check bölümüne bakın.

```dockerfile
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```
Konteyner başladığında çalışacak komut. `sh -c` içine sarmamızın sebebi `$JAVA_OPTS`
değişkeninin genişletilmesini (expand edilmesini) sağlamak — düz `exec` formunda
(`["java", "-jar", ...]`) shell değişken genişletmesi olmaz. `JAVA_OPTS` boş bırakılırsa hiçbir
şey değişmez; Railway'de bellek limiti gibi bir ayar gerekirse (örn. `JAVA_OPTS=-Xmx400m`) konteyneri
yeniden build etmeden, sadece bir env var ekleyerek verebilirsin.

---

## `.dockerignore` ne işe yarıyor?

`backend/.dockerignore`, `COPY` komutlarının **hangi dosyaları hiç görmeyeceğini** belirler —
`.gitignore` ile aynı mantık, ama Git için değil Docker build context'i için. `target/`,
`.idea/`, `.git/` gibi klasörleri dışarıda bırakarak hem build'i hızlandırır hem de yanlışlıkla
yerel `application-local.yml` gibi gizli/sır içeren bir dosyanın imaja sızmasını engeller.

## Railway'de build nasıl tetiklenir?

Railway bir servisin kök dizininde `Dockerfile` bulursa onu otomatik kullanır (Nixpacks yerine).
Bu repo bir monorepo olduğu için — backend `backend/`, frontend `frontend/` altında — Railway
servisinin **Root Directory** ayarını `backend` olarak ayarlaman gerekecek; bu adım Railway panel
tarafı olduğu için burada sana bırakıyorum.
