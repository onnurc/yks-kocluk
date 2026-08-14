export type PublicNavigationItem = {
  label: string;
  to: string;
  available: boolean;
};

export const publicNavigation: readonly PublicNavigationItem[] = [
  { label: "Koçluk", to: "/kocluk", available: true },
  { label: "Koçlarımız", to: "/coaches", available: true },
  { label: "Biz Kimiz", to: "/biz-kimiz", available: true },
];

export const publicQuickLinks: readonly PublicNavigationItem[] = [
  { label: "Koçluk Paketleri", to: "/kocluk", available: true },
  { label: "Koçlarımız", to: "/coaches", available: true },
  { label: "Giriş Yap", to: "/login", available: true },
  { label: "Hesap Oluştur", to: "/register", available: true },
];

export const publicSiteConfig = {
  brandName: "uniform",
  accessibleBrandName: "Uniform Akademi",
  description:
    "Akademik başarıya giden yolda, prestijli bir rehberlik deneyimi. Her öğrencinin potansiyelini en üst düzeye çıkarmak için tasarlanmış modern bir ekosistem.",
  contactPlaceholder: "Güncel iletişim bilgileri yakında paylaşılacaktır.",
} as const;
