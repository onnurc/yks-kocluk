import React from "react";
import { Link } from "react-router-dom";

export const NotFoundPage: React.FC = () => {
  return (
    <div style={{ padding: "4rem", textAlign: "center" }}>
      <h1>404 - Sayfa Bulunamadı</h1>
      <p style={{ margin: "1.5rem 0" }}>Aradığınız sayfa mevcut değil veya taşınmış olabilir.</p>
      <Link to="/dashboard" style={{ padding: "0.75rem 1.5rem", backgroundColor: "#007bff", color: "white", textDecoration: "none", borderRadius: "4px" }}>
        Anasayfaya Dön
      </Link>
    </div>
  );
};
