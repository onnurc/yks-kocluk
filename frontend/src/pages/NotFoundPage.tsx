import { FullPageError } from "../errors/FullPageError";

export const NotFoundPage = () => {
  return (
    <FullPageError
      code="404"
      eyebrow="Aradığınız yere ulaşamadık"
      title="Sayfa Bulunamadı"
      description="Aradığınız sayfa mevcut değil, taşınmış veya bağlantı artık geçerli olmayabilir."
      primaryAction={{ label: "Ana Sayfaya Dön", to: "/" }}
    />
  );
};
