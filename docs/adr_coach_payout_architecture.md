# Architecture Decision Record (ADR): Coach Payout Architecture

**Status:** UNDER REVIEW  
**Author:** AI Coding Assistant  
**Date:** 2026-07-15  

## Context and Problem Statement

YKS Koçluk matches students with university student coaches. Students purchase coaching packages, and the platform must pay the coaches their earnings after deducting the platform commission. We need to decide on the payout architecture model to handle these transfers safely, legally, and with minimal operational overhead in Turkey.

## Proposed Models Under Review

### Model A: Direct Marketplace / Recipient Settlement (Doğrudan Alıcı Transferi)
Payments are split automatically at the payment gateway level (e.g., using Iyzico Marketplace API). When a student pays, the payment gateway splits the funds:
- Platform commission goes to YKS Koçluk's merchant account.
- Coach's portion goes directly to the coach's sub-merchant account.

**Pros:**
- Minimal platform liability (funds do not sit in the platform's bank account).
- Automated splitting, reducing manual reconciliation.

**Cons:**
- High friction: Every coach must register as a sub-merchant with the payment provider, which requires a commercial company registration (şahıs şirketi, Vergi Levhası, etc.). Most university student coaches do not have companies.
- Complex onboarding and API integration.

### Model B: Pooled Wallet & Periodic Payout (Havuzlu Cüzdan & Periyodik Payout)
The platform collects all payments into a single central merchant account. The platform maintains an internal accounting ledger showing how much each coach has earned. Periodically (e.g., monthly/bi-weekly), the platform runs a payout process to transfer the accumulated earnings to coaches' personal bank accounts (IBANs) via bank transfer/EFT or automated payout APIs.

**Pros:**
- Minimal onboarding friction: Coaches only need to provide their personal bank IBAN. No company registration is forced on day one.
- The platform has full control over the payout schedule, refund holding periods, and dispute resolution.
- Easier to integrate legal tax-exemption schemes (e.g., Esnaf Muafiyeti or Serbest Meslek Makbuzu) for student coaches.

**Cons:**
- The platform holds student funds temporarily, requiring strict reconciliation and financial safety logs (double-entry ledger).

---

## Current Status and Open Decisions

> [!IMPORTANT]
> **No decision has been finalized between Model A and Model B.**
> The final model is subject to financial and legal reviews by the platform's accountant/lawyers.

### Erteleme ve Açık Konular:
1. **Gerçek Payout Entegrasyonu Yapılmadı:** Şu anda sistemde herhangi bir banka, Iyzico Marketplace veya ödeme aracısı ile gerçek transfer entegrasyonu bulunmamaktadır.
2. **Kişisel Veri Kısıtı:** Kullanıcılardan (Koçlar) banka hesap bilgisi veya IBAN verisi toplanmamaktadır.
3. **Şema Ertelemesi:** `PayoutLedger` ve benzeri veritabanı tablolarının şeması, mali/hukuki model kararı verilene kadar ertelenmiştir.
4. **Mali Akışlar:** Komisyon kesinleşme süreleri, refund/iade durumlarında havuzdaki paranın durumu, chargeback (ters ibraz) riskleri, koçların fatura/makbuz kesme yöntemleri ve vergi stopaj yükümlülükleri henüz karara bağlanmamıştır.

---

## Önerilen Teknik Milestone İlkeleri

Gelecekte payout altyapısı kurulurken uygulanması önerilen temel mimari ilkeler:

1. **Model-Bağımsız Interface (Seam):** Ödeme dağıtım motorunu soyutlayan ve Mock/No-Op implementasyonlarla kolayca test edilebilen bir interface yapısı.
2. **Güvenli Idempotency:** Çift ödeme yapılmasını (double payout) kesin olarak engellemek için her payout isteğine benzersiz bir `idempotency_key` (örneğin `payout_attempt_uuid`) ve veritabanı seviyesinde `UNIQUE` kısıtlaması uygulanmalıdır.
3. **Immutable Ledger (Salt-Eklenebilir Muhasebe):** Geçmişe dönük kayıt silme veya güncelleme yapılamayan, her işlemin (komisyon, ödeme, iade, ceza) yeni bir satır olarak eklendiği mutlak bir muhasebe günlüğü (`PayoutLedger`).
4. **Currency ve Ondalık Hassasiyeti:** Para biriminin (`currency` - varsayılan `TRY`) açıkça modellenmesi ve tüm hesaplamaların `BigDecimal` veri tipiyle, en az 4 ondalık basamak hassasiyetinde ve `RoundingMode.HALF_UP` yuvarlama yöntemiyle yapılması.
