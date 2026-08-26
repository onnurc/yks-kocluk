import { Component, type ErrorInfo, type ReactNode } from "react";
import { FullPageError } from "./FullPageError";

type AppErrorBoundaryProps = {
  children: ReactNode;
};

type AppErrorBoundaryState = {
  hasError: boolean;
};

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error("Unhandled application error", error, info);
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <FullPageError
          code="500"
          eyebrow="Beklenmeyen bir hata oluştu"
          title="Bir Şeyler Ters Gitti"
          description="Beklenmeyen bir sorun oluştu. Sayfayı yenileyerek tekrar deneyebilirsiniz."
          primaryAction={{ label: "Sayfayı Yenile", onClick: () => window.location.reload() }}
          secondaryAction={{ label: "Ana Sayfaya Dön", href: "/" }}
        />
      );
    }

    return this.props.children;
  }
}
