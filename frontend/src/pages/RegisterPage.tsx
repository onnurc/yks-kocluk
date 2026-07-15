import React, { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";
import { ApiError } from "../api/ApiError";

export const RegisterPage: React.FC = () => {
  const { register, isAuthenticated, user, isSuspended } = useAuth();
  const navigate = useNavigate();

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"STUDENT" | "COACH">("STUDENT");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [error, setError] = useState<ApiError | Error | string | null>(null);
  const [loading, setLoading] = useState(false);

  // Redirect users who are already logged in
  useEffect(() => {
    if (isSuspended) {
      navigate("/suspended");
    } else if (isAuthenticated && user) {
      if (user.role === "ADMIN") {
        navigate("/admin");
      } else {
        navigate("/dashboard");
      }
    }
  }, [isAuthenticated, user, isSuspended, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!firstName.trim() || !lastName.trim()) {
      setError("Ad ve Soyad alanları gereklidir.");
      return;
    }
    if (!email.trim() || !password.trim()) {
      setError("E-posta ve şifre gereklidir.");
      return;
    }
    if (password.length < 8) {
      setError("Şifre en az 8 karakter olmalıdır.");
      return;
    }
    if (role === "STUDENT") {
      if (!dateOfBirth) {
        setError("Öğrenci kaydı için doğum tarihi zorunludur.");
        return;
      }
      const birthDate = new Date(dateOfBirth);
      const today = new Date();
      if (birthDate > today) {
        setError("Doğum tarihi gelecekte olamaz.");
        return;
      }
    }

    setLoading(true);
    try {
      const fullName = `${firstName.trim()} ${lastName.trim()}`;
      await register(email, password, fullName, role, role === "STUDENT" ? dateOfBirth : undefined);
      // Success auto-login redirects via useEffect
    } catch (err) {
      setError(err as ApiError | Error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: "400px", margin: "4rem auto", padding: "2rem", border: "1px solid #ccc", borderRadius: "8px", backgroundColor: "#fff" }}>
      <h2 style={{ marginTop: 0, marginBottom: "1.5rem" }}>Kayıt Ol</h2>

      <FormError error={error} />

      <form onSubmit={handleSubmit}>
        <div style={{ display: "flex", gap: "1rem", marginBottom: "1rem" }}>
          <div style={{ flex: 1 }}>
            <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Ad:</label>
            <input
              type="text"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              required
              disabled={loading}
              style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
            />
          </div>
          <div style={{ flex: 1 }}>
            <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Soyad:</label>
            <input
              type="text"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              required
              disabled={loading}
              style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
            />
          </div>
        </div>
        <div style={{ marginBottom: "1rem" }}>
          <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>E-posta:</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            disabled={loading}
            style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
          />
        </div>
        <div style={{ marginBottom: "1rem" }}>
          <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Şifre (Min 8 karakter):</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            disabled={loading}
            style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
          />
        </div>
        {role === "STUDENT" && (
          <div style={{ marginBottom: "1rem" }}>
            <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Doğum Tarihi:</label>
            <input
              type="date"
              value={dateOfBirth}
              onChange={(e) => setDateOfBirth(e.target.value)}
              required
              disabled={loading}
              style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
            />
            {dateOfBirth && (() => {
              const birth = new Date(dateOfBirth);
              const today = new Date();
              let age = today.getFullYear() - birth.getFullYear();
              const m = today.getMonth() - birth.getMonth();
              if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
                age--;
              }
              if (age < 18 && age >= 0) {
                return (
                  <small style={{ color: "#d9534f", display: "block", marginTop: "0.25rem" }}>
                    18 yaş altı kullanıcılar için veli onayı gerekmektedir.
                  </small>
                );
              }
              return null;
            })()}
          </div>
        )}
        <div style={{ marginBottom: "1.5rem" }}>
          <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Rol Seçimi:</label>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as "STUDENT" | "COACH")}
            disabled={loading}
            style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc", backgroundColor: "#fff" }}
          >
            <option value="STUDENT">Öğrenci</option>
            <option value="COACH">Koç</option>
          </select>
        </div>
        <button
          type="submit"
          disabled={loading}
          style={{ width: "100%", padding: "0.75rem", backgroundColor: "#28a745", color: "white", border: "none", borderRadius: "4px", cursor: loading ? "not-allowed" : "pointer", fontSize: "1rem" }}
        >
          {loading ? "Kayıt Yapılıyor..." : "Kayıt Ol"}
        </button>
      </form>
      <p style={{ marginTop: "1.5rem", textAlign: "center", marginBottom: 0 }}>
        Zaten hesabınız var mı? <Link to="/login">Giriş Yap</Link>
      </p>
    </div>
  );
};
