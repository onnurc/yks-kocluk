import { Link } from "react-router-dom";

export const NoSubscriptionCard: React.FC = () => {
  return (
    <div style={{ padding: "2rem", border: "1px solid #dee2e6", borderRadius: "8px", backgroundColor: "#fff", textAlign: "center", marginBottom: "1.5rem" }}>
      <h3 style={{ margin: "0 0 1rem 0", color: "#495057" }}>Henüz Bir Koçluk Aboneliğiniz Bulunmuyor</h3>
      <p style={{ margin: "0 0 1.5rem 0", color: "#6c757d" }}>
        Sınav hazırlık sürecinizde profesyonel destek alarak hedefinize daha emin adımlarla yürüyebilirsiniz.
        Size en uygun koçu bulup hemen başlayın!
      </p>
      <Link
        to="/coaches"
        style={{
          display: "inline-block",
          padding: "0.75rem 1.5rem",
          backgroundColor: "#007bff",
          color: "white",
          textDecoration: "none",
          borderRadius: "4px",
          fontWeight: "bold",
          cursor: "pointer",
        }}
      >
        Koç Keşfet
      </Link>
    </div>
  );
};
